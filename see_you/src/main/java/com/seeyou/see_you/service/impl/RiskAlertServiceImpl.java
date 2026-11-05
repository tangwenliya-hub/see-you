package com.seeyou.see_you.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeyou.see_you.algorithm.CrisisWordMatcher;
import com.seeyou.see_you.algorithm.EmotionConst;
import com.seeyou.see_you.algorithm.RiskRuleEngine;
import com.seeyou.see_you.config.RiskProperties;
import com.seeyou.see_you.entity.PsyDiary;
import com.seeyou.see_you.entity.RiskAlert;
import com.seeyou.see_you.entity.SysUser;
import com.seeyou.see_you.mapper.RiskAlertMapper;
import com.seeyou.see_you.service.CrisisAiJudge;
import com.seeyou.see_you.service.PsyDiaryService;
import com.seeyou.see_you.service.RiskAlertService;
import com.seeyou.see_you.service.RiskSseHub;
import com.seeyou.see_you.service.SysUserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 情绪风险预警实现。
 *
 * <h3>防打扰设计</h3>
 * <ul>
 *   <li>同一天只可能有一条记录（唯一键保证），天然去重；</li>
 *   <li>冷静期：最近 cooldownDays 天内已发过同级或更高等级的预警时不再打扰；</li>
 *   <li>用户点过「我知道了」之后，同一等级的预警在该日期内不再弹窗。</li>
 * </ul>
 *
 * <h3>为什么强调"不是单次情绪触发"</h3>
 * 单次难过是正常的人类情绪。只有**窗口内的负面占比、连续天数与下滑趋势**
 * 三个指标共同越界，才判为需要关注 —— 这既是统计上的严谨性，也是伦理上的克制。
 */
@Service
public class RiskAlertServiceImpl implements RiskAlertService {

    @Resource private RiskAlertMapper alertMapper;
    @Resource private PsyDiaryService diaryService;
    @Resource private RiskProperties props;
    @Resource private RiskSseHub sseHub;
    @Resource private SysUserService userService;
    @Resource private com.seeyou.see_you.service.CrisisAiJudge aiJudge;

    /** 危机通道的规则名（与趋势通道的 L1/L2/L3_* 区分） */
    public static final String CRISIS_RULE = "CRISIS_KEYWORD";
    /** AI 精判通道的规则名：危机词未命中时由 LLM 做风险分类 */
    public static final String AI_RULE = "AI_JUDGE";
    /** 预警词通道：两可表达（如「撑不下去了」）触发二级中度提醒，不进红色危机通道 */
    public static final String WARNING_RULE = "WARNING_WORD";

    /** AI 研判单线程池（daemon）：异步执行，绝不阻塞日记保存；串行化也顺带限流了 LLM 调用 */
    private final java.util.concurrent.ExecutorService aiPool =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "crisis-ai-judge");
                t.setDaemon(true);
                return t;
            });

    /** 该规则是否属于「即时关怀」类（危机词 / AI 精判）：后台标签、通道优先级判断用 */
    public static boolean isCrisisRule(String rule) {
        return CRISIS_RULE.equals(rule) || AI_RULE.equals(rule);
    }

    /** 每人每天已发出的 AI 研判次数（key = userId@date）；只做成本控制，不需要持久化 */
    private final Map<String, Integer> aiCalls = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public RiskAlert evaluate(Long userId) {
        if (!props.isEnabled()) return null;

        LocalDate today = LocalDate.now();
        int window = props.getWindowDays();
        LocalDate from = today.minusDays(window - 1);

        Map<LocalDate, Integer> emotions = loadEmotions(userId, from, today);
        RiskRuleEngine.WindowStats stats = RiskRuleEngine.stats(today, emotions);
        RiskRuleEngine.Verdict v = RiskRuleEngine.judge(stats, props);
        if (v.level() <= 0) return null;

        // 当天已由危机通道生成过提示 → 趋势通道不覆盖它（危机通道优先级更高，且不能把等级算低）
        RiskAlert todayCrisis = alertMapper.selectOne(new LambdaQueryWrapper<RiskAlert>()
                .eq(RiskAlert::getUserId, userId)
                .eq(RiskAlert::getAlertDate, today));
        if (todayCrisis != null && isCrisisRule(todayCrisis.getTriggerRule())) return todayCrisis;

        // 冷静期：最近 cooldownDays 天内已存在同级或更高等级的预警 → 不重复打扰
        LocalDate cooldownFrom = today.minusDays(props.getCooldownDays());
        Long recent = alertMapper.selectCount(new LambdaQueryWrapper<RiskAlert>()
                .eq(RiskAlert::getUserId, userId)
                .ge(RiskAlert::getAlertDate, cooldownFrom)
                .ge(RiskAlert::getAlertLevel, v.level()));
        if (recent != null && recent > 0) return null;

        RiskAlert exist = alertMapper.selectOne(new LambdaQueryWrapper<RiskAlert>()
                .eq(RiskAlert::getUserId, userId)
                .eq(RiskAlert::getAlertDate, today));

        RiskAlert a = exist == null ? new RiskAlert() : exist;
        if (exist == null) {
            a.setUserId(userId);
            a.setAlertDate(today);
            a.setCreateTime(LocalDateTime.now());
        }
        a.setAlertLevel(v.level());
        a.setWindowDays(window);
        a.setRecordDays(stats.recordDays());
        a.setNegativeDays(stats.negativeDays());
        a.setMaxDowncastDays(stats.downcastDays());
        a.setNegativeRatio(bd(stats.negativeRatio()));
        a.setAvgIndex(bd(stats.avgIndex()));
        a.setTrendSlope(bd(stats.slope()));
        a.setTriggerRule(v.rule());
        a.setTriggerDetail(v.detail());
        a.setAdvice(v.advice());
        // 等级提升时重置未读状态：更严重的提示必须让用户看到
        boolean resetUnread = exist == null || exist.getAlertLevel() == null || v.level() > exist.getAlertLevel();
        if (resetUnread) {
            a.setIsRead(0);
            a.setAckStatus(0);
            a.setAckTime(null);
        }
        a.setUpdateTime(LocalDateTime.now());
        if (exist == null) alertMapper.insert(a); else alertMapper.updateById(a);
        // 新预警 / 等级升高的预警 → 实时推给在线管理员
        if (resetUnread) pushAdmin(a);
        return a;
    }

    // ==================== 危机通道：关键词命中，立即提醒 ====================

    @Override
    public RiskAlert evaluateCrisis(Long userId, String text) {
        if (userId == null || !props.isEnabled() || !props.isCrisisEnabled()) return null;

        // 两档词表：危机词（三级红色关怀）优先；未命中再看预警词（二级中度提醒）。
        // 「撑不下去了」这类日常口语也常出现的表达放预警词档，避免红色误报吓到用户。
        List<String> crisisHits = CrisisWordMatcher.hit(text, props.getCrisisWords(), props.getCrisisExcludes());
        boolean crisisHit = !crisisHits.isEmpty();
        List<String> warningHits = crisisHit
                ? List.of()
                : CrisisWordMatcher.hit(text, props.getWarningWords(), props.getCrisisExcludes());
        boolean warningHit = !warningHits.isEmpty();
        if (!crisisHit && !warningHit) {
            // 两档词表都未命中 → 两级漏斗的第二级：LLM 异步精判（不阻塞日记保存，失败静默降级）
            maybeAiJudge(userId, text);
            return null;
        }

        LocalDate today = LocalDate.now();
        RiskAlert exist = alertMapper.selectOne(new LambdaQueryWrapper<RiskAlert>()
                .eq(RiskAlert::getUserId, userId)
                .eq(RiskAlert::getAlertDate, today));

        // 当天已经因词表提醒过、且用户点过「我知道了」→ 不再重复打扰
        boolean sameChannel = exist != null && (CRISIS_RULE.equals(exist.getTriggerRule())
                || WARNING_RULE.equals(exist.getTriggerRule()));
        if (sameChannel && Integer.valueOf(1).equals(exist.getAckStatus())) {
            return exist;
        }

        int level = crisisHit ? props.getCrisisLevel() : props.getWarningLevel();
        if (level < 1 || level > 3) level = crisisHit ? 3 : 2;
        // 只升不降：当天若已有更高等级的预警（如趋势三级），保留更高severity的那条，不覆盖
        if (exist != null && exist.getAlertLevel() != null && exist.getAlertLevel() > level) {
            return exist;
        }

        RiskAlert a = exist == null ? new RiskAlert() : exist;
        if (exist == null) {
            a.setUserId(userId);
            a.setAlertDate(today);
            a.setCreateTime(LocalDateTime.now());
        }
        a.setAlertLevel(level);
        // 词表通道不走统计窗口：窗口指标整体置空，避免前端展示「低落 0 天」这种自相矛盾的说明
        a.setWindowDays(null);
        a.setRecordDays(null);
        a.setNegativeDays(null);
        a.setMaxDowncastDays(null);
        a.setNegativeRatio(null);
        a.setAvgIndex(null);
        a.setTrendSlope(null);
        a.setTriggerRule(crisisHit ? CRISIS_RULE : WARNING_RULE);
        a.setTriggerDetail(crisisHit ? crisisDetail(crisisHits) : warningDetail(warningHits));
        a.setAdvice(crisisHit ? crisisAdvice(crisisHits) : warningAdvice(warningHits));
        // 词表提示必须让用户看到：新建或等级升高时重置未读未确认；
        // 同级重复触发（同一天又写了带词的日记）不打回未读——用户已经看过，
        // 反复重置会让后台「已读」状态失真（用户看过却显示未读）。
        if (exist == null || exist.getAlertLevel() == null || level > exist.getAlertLevel()) {
            a.setIsRead(0);
            a.setAckStatus(0);
            a.setAckTime(null);
        }
        a.setUpdateTime(LocalDateTime.now());
        // 当天首条词表预警，或上一条已被「我知道了」消化过 → 推给在线管理员；重复骚扰不推
        boolean notifyAdmin = exist == null || Integer.valueOf(1).equals(exist.getAckStatus());
        if (exist == null) alertMapper.insert(a); else alertMapper.updateById(a);
        if (notifyAdmin) pushAdmin(a);
        return a;
    }

    // ==================== AI 精判：词表未命中时的第二级漏斗 ====================

    /**
     * 危机词未命中时，把正文交给 LLM 做风险分类（异步，绝不阻塞日记保存）。
     * 成本控制：同一用户同一天最多一次 AI 研判（当天已产出过 AI 预警就不再判）。
     * 任何异常静默吞掉：AI 是附加能力，失效只能降级、不能影响日记主链路。
     */
    private void maybeAiJudge(Long userId, String text) {
        if (!props.isAiJudgeEnabled()) return;
        // 成本上限：每人每天 ≤ aiJudgeDailyLimit 次 LLM 调用（yml 可配，默认 3）。
        // 不设成 1：一天多次换着说法测试/表达时，限额会把后续精判全静默掉，表现为「没预警」。
        //
        // ⚠️ 计数口径必须是「已发出的调用次数」，不能是「已落库的 AI 预警条数」：
        // AI 预警只在判定为高危时才 insert，用户写 20 篇平静日记 → 一条记录都没有 →
        // 旧实现每次都放行，20 次 LLM 调用打了 20 次，所谓「每人每天 3 次」形同虚设。
        int limit = Math.max(1, props.getAiJudgeDailyLimit());
        String key = userId + "@" + LocalDate.now();
        int used = aiCalls.getOrDefault(key, 0);
        if (used >= limit) return;
        aiCalls.put(key, used + 1);
        // 跨天后清理旧 key：单机内存计数，重启即归零，只用于成本控制，不需要持久化
        if (aiCalls.size() > 1000) {
            String todayKey = "@" + LocalDate.now();
            aiCalls.keySet().removeIf(k -> !k.endsWith(todayKey));
        }

        aiPool.execute(() -> {
            try {
                CrisisAiJudge.Verdict v = aiJudge.judge(text);
                if (v != null && v.risk()) insertAiAlert(userId, v.reason());
            } catch (Exception ignored) {
                // 降级红线：AI 任何异常都不外抛
            }
        });
    }

    /**
     * AI 判定为高危 → 走与危机词同款的三级预警落库 + SSE 推管理员。
     * 等级/文案复用危机通道（app.alert.crisis-level、crisisAdvice 含免责声明）。
     */
    private void insertAiAlert(Long userId, String reason) {
        LocalDate today = LocalDate.now();
        RiskAlert exist = alertMapper.selectOne(new LambdaQueryWrapper<RiskAlert>()
                .eq(RiskAlert::getUserId, userId)
                .eq(RiskAlert::getAlertDate, today));
        // 双保险：异步执行期间用户可能又写了日记触发词表命中——危机词通道优先，AI 不覆盖
        if (exist != null && CRISIS_RULE.equals(exist.getTriggerRule())) return;

        int level = props.getCrisisLevel();
        if (level < 1 || level > 3) level = 3;
        if (exist != null && exist.getAlertLevel() != null && exist.getAlertLevel() > level) {
            level = exist.getAlertLevel();
        }

        RiskAlert a = exist == null ? new RiskAlert() : exist;
        if (exist == null) {
            a.setUserId(userId);
            a.setAlertDate(today);
            a.setCreateTime(LocalDateTime.now());
        }
        a.setAlertLevel(level);
        a.setWindowDays(null);
        a.setRecordDays(null);
        a.setNegativeDays(null);
        a.setMaxDowncastDays(null);
        a.setNegativeRatio(null);
        a.setAvgIndex(null);
        a.setTrendSlope(null);
        a.setTriggerRule(AI_RULE);
        a.setTriggerDetail("AI 情绪研判认为这条记录的表达可能需要被认真对待。判定理由：" + reason);
        a.setAdvice(crisisAdvice(List.of()));
        // 只在新建或等级升高时重置已读：同级重复判定不打回未读（与危机通道口径一致）
        boolean resetUnread = exist == null || exist.getAlertLevel() == null || level > exist.getAlertLevel();
        if (resetUnread) {
            a.setIsRead(0);
            a.setAckStatus(0);
            a.setAckTime(null);
        }
        a.setUpdateTime(LocalDateTime.now());
        if (exist == null) alertMapper.insert(a); else alertMapper.updateById(a);
        if (resetUnread) pushAdmin(a);
    }

    /** 危机提示的判定依据：只说命中的词条，不引用用户原文 */
    private String crisisDetail(List<String> hits) {
        return "你在日记里写下了需要被认真对待的表达（命中：" + String.join("、", hits) + "）。"
                + "这类话不该被算法忽略，所以我们不等 " + props.getWindowDays() + " 天的统计窗口，"
                + "而是立刻给你这条提示。";
    }

    /** 预警词（二级）的判定依据：语气与三级危机通道区分开，不制造惊吓 */
    private String warningDetail(List<String> hits) {
        return "你在日记里写下了「" + String.join("、", hits) + "」。"
                + "最近可能辛苦了——绪见没有等 " + props.getWindowDays() + " 天的统计窗口，想先跟你说说话。";
    }

    /**
     * 危机文案：先接住，再给可执行的求助动作，最后必带免责声明。
     * 严禁出现「你有抑郁症」「确诊」这类结论性判断。
     */
    private String crisisAdvice(List<String> hits) {
        return "绪见读到你写的这句话，很想认真地问一句：你现在还好吗？\n\n"
                + "如果此刻真的很难受，请先别一个人扛着：\n"
                + "· 现在就可以拨通 " + props.primaryHotline() + "，电话那头有人会接住你；\n"
                + "· 或者联系任何一个你现在能想到的、信任的人，哪怕只说一句「我不太好」；\n"
                + "· 先待在有人的地方，把身边让你不放心的东西放到够不到的地方。\n\n"
                + "你记录下的这些话，绪见不会当作数据忽略掉。"
                + "但请记得：绪见只是基于文字匹配的提示，真正能帮到你的，是愿意听你说话的人。\n\n"
                + "【重要】" + props.getDisclaimer();
    }

    /**
     * 预警词（二级）文案：与三级红色关怀的关键差异是「不预设危急」——
     * 二级针对的常见是日常抱怨，所以语气是「我看到你了 + 轻量建议」，
     * 求助资源放在最后作为「如果变频繁」的条件性提示，而不是「现在就打」。
     */
    private String warningAdvice(List<String> hits) {
        return "绪见看到你写下的这句话了。听起来最近真的不容易。\n\n"
                + "也许可以试试：\n"
                + "· 允许自己慢一点，今天先把吃饭和睡觉照顾好；\n"
                + "· 把压在心里的事具体写出来，哪怕只是列个清单；\n"
                + "· 找一个信得过的人说说，说出来本身就是一种松绑。\n\n"
                + "如果这种「撑不下去」的感觉最近越来越频繁，"
                + "学校心理咨询中心、当地精神卫生中心，或 " + props.primaryHotline() + " 都是可以随时联系的资源。\n\n"
                + "【重要】" + props.getDisclaimer();
    }

    @Override
    public Map<String, Object> latest(Long userId) {
        List<RiskAlert> list = alertMapper.selectList(new LambdaQueryWrapper<RiskAlert>()
                .eq(RiskAlert::getUserId, userId)
                .eq(RiskAlert::getAckStatus, 0)
                .ge(RiskAlert::getAlertDate, LocalDate.now().minusDays(props.getCooldownDays() + 3L))
                .orderByDesc(RiskAlert::getAlertLevel)
                .orderByDesc(RiskAlert::getAlertDate));
        if (list.isEmpty()) return Map.of("hasAlert", false);
        RiskAlert a = list.get(0);
        Map<String, Object> m = toVO(a);
        m.put("hasAlert", true);
        return m;
    }

    @Override
    public Map<String, Object> list(Long userId, long page, long size) {
        size = Math.min(Math.max(size, 1), 50);
        Page<RiskAlert> pg = alertMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<RiskAlert>()
                        .eq(RiskAlert::getUserId, userId)
                        .orderByDesc(RiskAlert::getAlertDate)
                        .orderByDesc(RiskAlert::getAlertId));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", pg.getTotal());
        m.put("page", pg.getCurrent());
        m.put("size", pg.getSize());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RiskAlert a : pg.getRecords()) rows.add(toVO(a));
        m.put("records", rows);
        m.put("disclaimer", props.getDisclaimer());
        return m;
    }

    @Override
    public void ack(Long userId, Long alertId) {
        RiskAlert a = alertMapper.selectById(alertId);
        if (a == null) return;
        if (!a.getUserId().equals(userId)) throw new com.seeyou.see_you.util.BizException(403, "无权操作他人的预警记录");
        a.setAckStatus(1);
        a.setIsRead(1);
        a.setAckTime(LocalDateTime.now());
        alertMapper.updateById(a);
    }

    @Override
    public void markRead(Long userId, Long alertId) {
        RiskAlert a = alertMapper.selectById(alertId);
        if (a == null) return;
        if (!a.getUserId().equals(userId)) throw new com.seeyou.see_you.util.BizException(403, "无权操作他人的预警记录");
        a.setIsRead(1);
        alertMapper.updateById(a);
    }

    @Override
    public void markMisreport(Long userId, Long alertId) {
        RiskAlert a = alertMapper.selectById(alertId);
        if (a == null) return;
        if (!a.getUserId().equals(userId)) throw new com.seeyou.see_you.util.BizException(403, "无权操作他人的预警记录");
        // 安全红线：危机词通道不允许标误报（宁可错弹不可漏接）；
        // 统计趋势 / 预警词 / AI 精判通道允许，用于论文量化准确率。
        if (isCrisisRule(a.getTriggerRule())) {
            throw new com.seeyou.see_you.util.BizException(400, "危机关怀提示不支持标记误报，如有疑问请联系学校心理老师");
        }
        // 幂等：重复点击不重复记时间
        if (a.getMisreport() != null && a.getMisreport() == 1) return;
        a.setMisreport(1);
        a.setMisreportTime(LocalDateTime.now());
        // 标记误报的同时视为「已知晓」：用户已经明确表态，不该再被同一条弹窗打扰
        a.setAckStatus(1);
        a.setIsRead(1);
        if (a.getAckTime() == null) a.setAckTime(LocalDateTime.now());
        alertMapper.updateById(a);
        // 实时告知在线管理员：该用户对这条预警有异议，处置前建议先核实
        pushMisreportAdmin(a);
    }

    @Override
    public Map<String, Object> overview(Long userId) {
        Map<String, Object> raw = alertMapper.overview(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unread", alertMapper.countUnread(userId));
        m.put("total", num(raw == null ? null : raw.get("total_cnt")));
        m.put("maxLevel", num(raw == null ? null : raw.get("max_level")));
        m.put("maxLevelName", RiskRuleEngine.levelName((int) num(raw == null ? null : raw.get("max_level"))));
        m.put("lastDate", raw == null ? null : raw.get("last_date"));
        // 把当前阈值一并回给前端：答辩演示时可以直观看到"阈值可配置"
        // 注意：Map.of 最多 10 对键值，这里超过 10 项，必须用 LinkedHashMap，否则编译不过
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", props.isEnabled());
        cfg.put("windowDays", props.getWindowDays());
        cfg.put("minRecordDays", props.getMinRecordDays());
        cfg.put("level2DowncastDays", props.getLevel2DowncastDays());
        cfg.put("level3DowncastDays", props.getLevel3DowncastDays());
        cfg.put("level3Ratio", props.getLevel3Ratio());
        cfg.put("level3AvgIndex", props.getLevel3AvgIndex());
        cfg.put("level1Ratio", props.getLevel1Ratio());
        cfg.put("level1Slope", props.getLevel1Slope());
        cfg.put("cooldownDays", props.getCooldownDays());
        // 危机通道：命中即提醒，不受窗口/冷静期约束；预警词是它的二级档
        cfg.put("crisisEnabled", props.isCrisisEnabled());
        cfg.put("crisisLevel", props.getCrisisLevel());
        cfg.put("crisisWordCount", props.getCrisisWords() == null ? 0 : props.getCrisisWords().size());
        cfg.put("warningLevel", props.getWarningLevel());
        cfg.put("warningWordCount", props.getWarningWords() == null ? 0 : props.getWarningWords().size());
        m.put("config", cfg);
        m.put("disclaimer", props.getDisclaimer());
        return m;
    }

    @Override
    public int scanAll() {
        if (!props.isEnabled()) return 0;
        LocalDate from = LocalDate.now().minusDays(props.getWindowDays() - 1L);
        // 只扫描窗口内有过记录的活跃用户，避免全表扫所有注册用户
        List<PsyDiary> recent = diaryService.lambdaQuery()
                .select(PsyDiary::getUserId)
                .ge(PsyDiary::getDiaryDate, from)
                .list();
        Set<Long> uids = new HashSet<>();
        for (PsyDiary d : recent) if (d.getUserId() != null) uids.add(d.getUserId());
        int hit = 0;
        for (Long uid : uids) {
            try { if (evaluate(uid) != null) hit++; } catch (Exception ignored) { /* 单个用户失败不影响全局 */ }
        }
        return hit;
    }

    // ==================== 私有 ====================

    /**
     * 实时推送给在线管理员（SSE）。
     * 推送是锦上添花：<b>任何失败都不允许影响预警落库主流程</b>，所以整段 try 包裹。
     */
    private void pushAdmin(RiskAlert a) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("alertId", a.getAlertId());
            m.put("alertDate", a.getAlertDate() == null ? null : a.getAlertDate().toString());
            m.put("level", a.getAlertLevel());
            m.put("levelName", RiskRuleEngine.levelName(a.getAlertLevel() == null ? 0 : a.getAlertLevel()));
            m.put("triggerRule", a.getTriggerRule());
            m.put("triggerRuleName", RiskRuleEngine.ruleName(a.getTriggerRule()));
            m.put("triggerDetail", a.getTriggerDetail());
            m.put("crisis", isCrisisRule(a.getTriggerRule()));
            SysUser u = userService.getById(a.getUserId());
            m.put("userName", u == null ? ("用户#" + a.getUserId())
                    : (u.getNickname() == null || u.getNickname().isBlank() ? u.getUsername() : u.getNickname()));
            sseHub.pushToAdmins("risk-alert", m);
        } catch (Exception ignored) { /* 单条推送失败不影响主流程 */ }
    }

    /**
     * 用户标记误报 → 实时推送给在线管理员。
     * 与新预警不同，这条不带「催处理」的压迫感：等级不变、通知可自动关闭，
     * 提示语是「用户反馈与实际不符，处置前建议先核实」。
     */
    private void pushMisreportAdmin(RiskAlert a) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("alertId", a.getAlertId());
            m.put("alertDate", a.getAlertDate() == null ? null : a.getAlertDate().toString());
            m.put("level", a.getAlertLevel());
            m.put("levelName", RiskRuleEngine.levelName(a.getAlertLevel() == null ? 0 : a.getAlertLevel()));
            m.put("triggerRule", a.getTriggerRule());
            m.put("triggerRuleName", RiskRuleEngine.ruleName(a.getTriggerRule()));
            m.put("crisis", isCrisisRule(a.getTriggerRule()));
            SysUser u = userService.getById(a.getUserId());
            m.put("userName", u == null ? ("用户#" + a.getUserId())
                    : (u.getNickname() == null || u.getNickname().isBlank() ? u.getUsername() : u.getNickname()));
            sseHub.pushToAdmins("risk-misreport", m);
        } catch (Exception ignored) { /* 推送失败不影响标记主流程 */ }
    }

    /** 载入窗口内每日情绪；同一天多条取最后一条 */
    private Map<LocalDate, Integer> loadEmotions(Long userId, LocalDate from, LocalDate to) {
        /*
         * 只取 source=1（用户手动记录）：无感推测生成的日记是被系统"建议"出来的，
         * 用它参与预警判定等于让模型给自己的输出打分（自证循环）。
         * 与 KNN 训练侧、统计侧保持同一口径。
         */
        List<PsyDiary> list = diaryService.lambdaQuery()
                .select(PsyDiary::getDiaryDate, PsyDiary::getEmotion)
                .eq(PsyDiary::getUserId, userId)
                .eq(PsyDiary::getSource, 1)
                .ge(PsyDiary::getDiaryDate, from)
                .le(PsyDiary::getDiaryDate, to)
                // 同一天多篇的裁决规则与统计侧一致：diary_id 最大（最后写的那篇）代表当天。
                // 只按日期排序时并列行顺序不确定，会导致「同一天，看板和预警取到不同日记」。
                .orderByAsc(PsyDiary::getDiaryDate)
                .orderByAsc(PsyDiary::getDiaryId)
                .list();
        Map<LocalDate, Integer> map = new LinkedHashMap<>();
        for (PsyDiary d : list) {
            if (d.getDiaryDate() == null || d.getEmotion() == null) continue;
            map.put(d.getDiaryDate(), d.getEmotion());
        }
        return map;
    }

    private Map<String, Object> toVO(RiskAlert a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("alertId", a.getAlertId());
        m.put("alertDate", a.getAlertDate() == null ? null : a.getAlertDate().toString());
        m.put("level", a.getAlertLevel());
        m.put("levelName", RiskRuleEngine.levelName(a.getAlertLevel() == null ? 0 : a.getAlertLevel()));
        m.put("windowDays", a.getWindowDays());
        m.put("recordDays", a.getRecordDays());
        m.put("negativeDays", a.getNegativeDays());
        m.put("maxDowncastDays", a.getMaxDowncastDays());
        m.put("negativeRatio", a.getNegativeRatio());
        m.put("avgIndex", a.getAvgIndex());
        m.put("trendSlope", a.getTrendSlope());
        m.put("triggerRule", a.getTriggerRule());
        // 面向人的展示一律用中文名；原枚举保留给检索/存储
        m.put("triggerRuleName", RiskRuleEngine.ruleName(a.getTriggerRule()));
        m.put("triggerDetail", a.getTriggerDetail());
        m.put("advice", a.getAdvice());
        m.put("isRead", a.getIsRead());
        m.put("ackStatus", a.getAckStatus());
        m.put("createTime", a.getCreateTime());
        // 误报反馈：用户端「这不是我」标记，后台列表/详情/统计三处消费
        m.put("misreport", a.getMisreport() != null && a.getMisreport() == 1);
        m.put("misreportTime", a.getMisreportTime());
        // 危机通道标记：前端据此换成「危机关怀」版式（红色、置顶、自动展开）
        m.put("crisis", isCrisisRule(a.getTriggerRule()));
        // 求助渠道由后端统一下发（yml 的 app.alert.crisis-hotlines）：
        // crisisHotline 是主热线（旧字段，兼容 RiskAlertCard），crisisHotlines 是完整清单
        m.put("crisisHotline", props.primaryHotline());
        m.put("crisisHotlines", props.getCrisisHotlines());
        m.put("disclaimer", props.getDisclaimer());
        m.put("emotionLabels", EmotionConst.NAMES);
        return m;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(Math.round(v * 1000) / 1000.0);
    }

    private static long num(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }
}
