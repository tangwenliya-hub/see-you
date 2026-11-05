package com.seeyou.see_you.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeyou.see_you.algorithm.BehaviorPredictor;
import com.seeyou.see_you.algorithm.EmotionConst;
import com.seeyou.see_you.algorithm.EmotionLexiconAnalyzer;
import com.seeyou.see_you.config.SenseProperties;
import com.seeyou.see_you.dto.BehaviorReportDTO;
import com.seeyou.see_you.dto.SenseConfigDTO;
import com.seeyou.see_you.dto.SenseConfirmDTO;
import com.seeyou.see_you.entity.*;
import com.seeyou.see_you.mapper.*;
import com.seeyou.see_you.service.PsyDiaryService;
import com.seeyou.see_you.service.SenseService;
import com.seeyou.see_you.service.SysUserService;
import com.seeyou.see_you.util.BizException;
import com.seeyou.see_you.util.IpMaskUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 无感记录模块实现。
 *
 * <h3>三通路分工</h3>
 * <ul>
 *   <li>行为：跨 today 的 7 维向量 → 加权 KNN 找「和你今天最像的历史日」</li>
 *   <li>文本：自建九类情绪词典 → 否定翻转 + 程度副词加权</li>
 *   <li>时序：情绪指数序列 → AR(1) 一阶自回归外推</li>
 * </ul>
 *
 * <h3>隐私约束落地位置</h3>
 * <ul>
 *   <li>{@code report}：写库前做 scene 归一化、位图截断、数值上限裁剪</li>
 *   <li>{@code analyzeText}：只返回分布与命中的**词典词**，不返回原文、不落库</li>
 *   <li>{@code confirm}：先查归属再写库，且写出的日记强制 source=2</li>
 *   <li>任何 Mapper 的 SQL 都带 user_id 条件（见 BehaviorFeatureMapper / EmotionPredictionMapper）</li>
 * </ul>
 */
@Service
public class SenseServiceImpl implements SenseService {

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM月dd日");

    @Resource private BehaviorFeatureMapper behaviorMapper;
    @Resource private EmotionPredictionMapper predictionMapper;
    @Resource private EmotionLexiconMapper lexiconMapper;
    @Resource private SensePrivacyLogMapper privacyLogMapper;
    @Resource private PsyDiaryService diaryService;
    @Resource private SysUserService userService;
    @Resource private SenseProperties props;

    /** 词典常驻内存：240+ 条数据量很小，避免每次推理都查库 */
    private volatile EmotionLexiconAnalyzer analyzer;

    @PostConstruct
    @Override
    public void reloadLexicon() {
        analyzer = new EmotionLexiconAnalyzer(lexiconMapper.selectList(null));
    }

    @Override
    public int lexiconSize() {
        return analyzer == null ? 0 : analyzer.size();
    }

    // =========================================================
    // 一、开关
    // =========================================================

    @Override
    public Map<String, Object> getConfig(Long userId) {
        SysUser u = userService.getById(userId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("featureEnabled", props.isEnabled());
        m.put("enabled", n1(u.getSenseEnabled()));
        m.put("behavior", n1(u.getSenseBehavior()));
        m.put("text", n1(u.getSenseText()));
        m.put("grantAt", u.getSenseGrantAt());
        m.put("revokeAt", u.getSenseRevokeAt());
        m.put("retentionDays", props.getRetentionDays());
        m.put("minConfidence", props.getMinConfidence());
        m.put("expireDays", props.getExpireDays());
        m.put("lexiconSize", lexiconSize());

        Map<String, Object> ov = behaviorMapper.overview(userId);
        m.put("behaviorRows", ov == null ? 0 : ov.get("rows_cnt"));
        m.put("firstDate", ov == null ? null : ov.get("first_date"));
        m.put("lastDate", ov == null ? null : ov.get("last_date"));

        // 今日待确认候选数量：让用户清楚系统手里有几个「等你处理」的东西
        Long pending = predictionMapper.selectCount(new LambdaQueryWrapper<EmotionPrediction>()
                .eq(EmotionPrediction::getUserId, userId)
                .eq(EmotionPrediction::getConfirmStatus, 0)
                .ge(EmotionPrediction::getExpireTime, LocalDateTime.now()));
        m.put("pendingCount", pending == null ? 0 : pending);
        return m;
    }

    @Override
    public void updateConfig(Long userId, SenseConfigDTO dto) {
        SysUser u = userService.getById(userId);
        if (u == null) throw new BizException(404, "用户不存在");
        boolean wasEnabled = Integer.valueOf(1).equals(u.getSenseEnabled());

        // 记录旧值，用于「值真的变了才写审计日志」+ 只更新变化的列
        int oldEnabled = u.getSenseEnabled() == null ? 0 : u.getSenseEnabled();
        int oldBehavior = u.getSenseBehavior() == null ? 0 : u.getSenseBehavior();
        int oldText = u.getSenseText() == null ? 0 : u.getSenseText();

        if (dto.getEnabled() != null) u.setSenseEnabled(onOff(dto.getEnabled()));
        if (dto.getBehavior() != null) u.setSenseBehavior(onOff(dto.getBehavior()));
        if (dto.getText() != null) u.setSenseText(onOff(dto.getText()));

        /*
         * 总开关 = 急停开关，子开关 = 用户意图记忆。
         *
         * 旧实现把总开关关闭时「顺手把两个子开关清零」，导致：关一次再打开，
         * 行为采集和文本分析都还是关的，界面却显示总开关已开启 → 用户表现
         * 为「开了却一条数据都没有」。子开关被系统覆写后，用户的选择就丢了。
         *
         * 现在：总开关只决定「采不采」，不覆写子开关。真正的采集条件是
         *   enabled == 1 && behavior/text == 1
         * （report() 与 generate() 都按这个条件判断），所以总开关关闭时
         * 子开关即使显示为开，也不会产生任何采集——语义更清楚，也不用猜。
         */
        boolean nowEnabled = Integer.valueOf(1).equals(u.getSenseEnabled());
        boolean wantBehavior = Integer.valueOf(1).equals(u.getSenseBehavior());
        LocalDateTime now = LocalDateTime.now();

        if (!wasEnabled && nowEnabled) {
            u.setSenseGrantAt(now);
        } else if (wasEnabled && !nowEnabled) {
            u.setSenseRevokeAt(now);
        }

        /*
         * A7：审计日志只记「真的变化」，否则用户连点几次保存就把 GRANT/REVOKE
         * 挤出最近 50 条之外，这份「可审计」证据链反而失效。
         */
        boolean changed = oldEnabled != nz(u.getSenseEnabled())
                || oldBehavior != nz(u.getSenseBehavior())
                || oldText != nz(u.getSenseText());
        if (!changed) return;

        if (!wasEnabled && nowEnabled) {
            log(userId, "GRANT", "用户开启无感记录（行为=" + (wantBehavior ? "开" : "关")
                    + ", 文本=" + (Integer.valueOf(1).equals(u.getSenseText()) ? "开" : "关") + "）");
        } else if (wasEnabled && !nowEnabled) {
            log(userId, "REVOKE", "用户关闭无感记录，采集立即停止");
        } else {
            log(userId, "SWITCH", "enabled=" + u.getSenseEnabled()
                    + ",behavior=" + u.getSenseBehavior() + ",text=" + u.getSenseText());
        }
        userService.updateById(u);
    }

    // =========================================================
    // 二、行为上报
    // =========================================================

    @Override
    public int report(Long userId, List<BehaviorReportDTO> items) {
        if (!props.isEnabled()) throw new BizException(403, "无感记录功能未开启");
        SysUser u = userService.getById(userId);
        if (u == null) throw new BizException(404, "用户不存在");
        if (!Integer.valueOf(1).equals(u.getSenseEnabled()) || !Integer.valueOf(1).equals(u.getSenseBehavior())) {
            // 开关关着：丢弃数据，且明确告知调用方（前端收到后应立即停止上报）
            throw new BizException(403, "无感记录未授权，数据已丢弃");
        }
        if (items == null || items.isEmpty()) return 0;
        if (items.size() > props.getMaxBatch()) {
            throw new BizException(400, "单次上报条目过多");
        }

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        int n = 0;
        for (BehaviorReportDTO it : items) {
            if (it == null) continue;
            LocalDate d = it.getDate() == null ? today : it.getDate();
            // 防御：客户端时钟不可信，只允许补昨天/今天；更早的数据可能是伪造或回放攻击
            if (d.isBefore(yesterday) || d.isAfter(today)) d = today;

            BehaviorFeature f = new BehaviorFeature();
            f.setUserId(userId);
            f.setStatDate(d);
            f.setSceneCode(BehaviorPredictor.normalizeScene(it.getSceneCode()));
            f.setStayMs(clampStay(it.getStayMs()));
            f.setVisitCnt(clampInt(it.getVisitCnt(), 0, 10000));
            f.setClickCnt(clampInt(it.getClickCnt(), 0, props.getMaxClickCnt()));
            // 位图只允许低 24 位
            f.setHourMask(it.getHourMask() == null ? 0L : (it.getHourMask() & 0xFFFFFFL));
            behaviorMapper.upsert(f);
            n++;
        }
        return n;
    }

    @Override
    public List<BehaviorFeature> myBehavior(Long userId, LocalDate from, LocalDate to) {
        return behaviorMapper.selectRange(userId, from.toString(), to.toString());
    }

    @Override
    public int clearBehavior(Long userId) {
        int rows = behaviorMapper.delete(new LambdaQueryWrapper<BehaviorFeature>()
                .eq(BehaviorFeature::getUserId, userId));
        log(userId, "DELETE_ALL", "删除行为特征 " + rows + " 行");
        return rows;
    }

    // =========================================================
    // 三、推测生成（三通路融合）
    // =========================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generate(Long userId, Integer triggerType, String text) {
        return doGenerate(userId, triggerType, text, props.getMinConfidence());
    }

    /**
     * 推测生成主体。阈值做成参数的原因见 {@link SenseProperties#getAutoFillMinConfidence()}：
     * 主动推荐要谨慎（0.45），夜里自动代记是保底打卡（0.30）。
     */
    private Map<String, Object> doGenerate(Long userId, Integer triggerType, String text, double minConfidence) {
        if (!props.isEnabled()) throw new BizException(403, "无感记录功能未开启");
        SysUser u = userService.getById(userId);
        if (u == null) throw new BizException(404, "用户不存在");
        if (!Integer.valueOf(1).equals(u.getSenseEnabled())) {
            throw new BizException(403, "无感记录未开启，无法生成推测");
        }
        int trigger = (triggerType == null || triggerType < 1 || triggerType > 3) ? 2 : triggerType;

        LocalDate today = LocalDate.now();
        LocalDate targetDate = today;

        // ---- 通路 A：行为 KNN ----
        LocalDate behFrom = today.minusDays(props.getHistoryDays());
        List<BehaviorFeature> features = behaviorMapper.selectRange(userId, behFrom.toString(), today.toString());
        Map<LocalDate, BehaviorPredictor.DayAgg> agg = BehaviorPredictor.aggregateByDate(features);
        Map<LocalDate, double[]> vectors = BehaviorPredictor.vectorize(agg);
        Map<LocalDate, Integer> manualLabels = loadEmotions(userId, behFrom, today, true);

        double[] targetVec = vectors.get(targetDate);
        Map<Integer, Double> behDist = null;
        LocalDate nearestDay = null;
        if (targetVec != null) {
            // ⚠️ 必须把 targetDate 作为 exclude 传进去：manualLabels 取的是 [from, today] 区间，
            // 用户若今天已经写过日记，今天的真值就在 labels 里，不排除就会出现「自己和自己距离 0」
            // 的退化推测（置信度接近 1.0，理由写成「你和今天很接近」），命中率也会虚高。
            behDist = BehaviorPredictor.knn(vectors, manualLabels, targetVec, props.getKnnK(), targetDate);
            nearestDay = BehaviorPredictor.nearest(vectors, manualLabels, targetVec, targetDate);
        }

        // ---- 通路 B：文本词典 ----
        Map<Integer, Double> txtDist = null;
        List<String> hits = List.of();
        if (text != null && !text.isBlank() && Integer.valueOf(1).equals(u.getSenseText())) {
            EmotionLexiconAnalyzer.TextScore ts = analyzer.analyze(text);
            if (ts != null && ts.distribution() != null && !ts.distribution().isEmpty()) {
                txtDist = ts.distribution();
                hits = ts.hitWords();
            }
        }

        // ---- 通路 C：AR(1) 时序外推 ----
        // series 区间（近 seriesDays 天）完全落在 KNN 标签区间（近 historyDays 天）内，
        // 直接从已查询出的 manualLabels 切片，避免对 psy_diary 的第二次重复查询
        List<Double> series = seriesFromLabels(manualLabels,
                today.minusDays(props.getSeriesDays()), today.minusDays(1));
        Double ar1Next = BehaviorPredictor.ar1Predict(series);
        Map<Integer, Double> serDist = ar1Next == null ? null : BehaviorPredictor.indexToDistribution(ar1Next);

        // ---- 融合 ----
        BehaviorPredictor.FuseResult fr = BehaviorPredictor.fuse(behDist, txtDist, serDist);
        if (fr == null) {
            return noSuggestion("还没有足够的可用数据。多记录几天日记、或打开行为采集开关，我就能猜啦。");
        }
        if (fr.confidence() < minConfidence) {
            return noSuggestion("线索还不够充分（置信度 " + pct(fr.confidence())
                    + "%），为了不误导你，这次就不猜了。");
        }

        // ---- 保存候选（同一天同一 trigger 只保留最新一条）----
        EmotionPrediction p = predictionMapper.selectOne(new LambdaQueryWrapper<EmotionPrediction>()
                .eq(EmotionPrediction::getUserId, userId)
                .eq(EmotionPrediction::getTargetDate, targetDate)
                .eq(EmotionPrediction::getTriggerType, trigger));
        boolean isNew = p == null;
        if (isNew) {
            p = new EmotionPrediction();
            p.setUserId(userId);
            p.setTargetDate(targetDate);
            p.setTriggerType(trigger);
            p.setCreateTime(LocalDateTime.now());
        }
        p.setSuggestEmotion(fr.bestEmotion());
        p.setSuggestIntensity(guessIntensity(fr));
        p.setConfidence(BigDecimal.valueOf(fr.confidence()).setScale(3, RoundingMode.HALF_UP));
        p.setCandidates(toJson(BehaviorPredictor.topN(fr.distribution(), 3)));
        p.setReason(buildReason(nearestDay, manualLabels, hits, ar1Next, series));
        p.setDetail(toJson(fr.weights()));
        p.setFeatureSnapshot(targetVec == null ? null : toJson(vecToList(targetVec)));
        p.setConfirmStatus(0);
        p.setExpireTime(LocalDateTime.now().plusDays(props.getExpireDays()));
        if (isNew) predictionMapper.insert(p); else predictionMapper.updateById(p);

        log(userId, "GENERATE", "trigger=" + trigger + ",emotion=" + fr.bestEmotion()
                + ",conf=" + p.getConfidence());

        return toSuggestionVO(p, true);
    }

    @Override
    public Map<String, Object> today(Long userId) {
        List<EmotionPrediction> todayList = predictionMapper.selectList(
                new LambdaQueryWrapper<EmotionPrediction>()
                        .eq(EmotionPrediction::getUserId, userId)
                        .eq(EmotionPrediction::getTargetDate, LocalDate.now())
                        .eq(EmotionPrediction::getConfirmStatus, 0)
                        .ge(EmotionPrediction::getExpireTime, LocalDateTime.now())
                        .orderByDesc(EmotionPrediction::getCreateTime));
        if (todayList.isEmpty()) {
            return noSuggestion("今日暂无推荐。可以在写日记时点「帮我猜一下」触发一次推测。");
        }
        return toSuggestionVO(todayList.get(0), true);
    }

    @Override
    public Map<String, Object> analyzeText(Long userId, String text) {
        if (!props.isEnabled()) throw new BizException(403, "无感记录功能未开启");
        SysUser u = userService.getById(userId);
        if (u == null) throw new BizException(404, "用户不存在");
        boolean textAllowed = Integer.valueOf(1).equals(u.getSenseText())
                || props.isAllowStandaloneText();
        if (!textAllowed) throw new BizException(403, "未开启文本情感分析");

        EmotionLexiconAnalyzer.TextScore ts = analyzer.analyze(text);
        Map<String, Object> m = new LinkedHashMap<>();
        if (ts == null || ts.distribution() == null || ts.distribution().isEmpty()) {
            m.put("hit", false);
            m.put("reason", "没有匹配到情绪词，你可以自己选一个情绪标签。");
            return m;
        }
        int best = ts.distribution().entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(9);
        double bestP = ts.distribution().get(best);
        m.put("hit", true);
        m.put("emotion", best);
        m.put("emotionName", EmotionConst.nameOf(best));
        m.put("intensity", guessIntensityByP(bestP));
        m.put("confidence", Math.round(bestP * 1000) / 1000.0);
        // 只回传命中的「词典词」，不是用户的原文 —— 可解释性与隐私的折中点
        m.put("hitWords", ts.hitWords());
        m.put("candidates", BehaviorPredictor.topN(ts.distribution(), 3));
        m.put("disclaimer", "文本分析仅在你点击时运行，原文不会被保存。");
        return m;
    }

    // =========================================================
    // 四、确认闭环
    // =========================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long confirm(Long userId, Long predictionId, SenseConfirmDTO dto) {
        EmotionPrediction p = predictionMapper.selectById(predictionId);
        if (p == null) throw new BizException(404, "推测不存在或已被清理");
        // 归属校验：这才是「用户拥有最终决定权」的安全前提
        if (!p.getUserId().equals(userId)) throw new BizException(403, "无权操作他人的推测");
        if (Integer.valueOf(3).equals(p.getConfirmStatus())) throw new BizException(400, "该推测已过期，请重新生成");
        // 幂等：重复点击不产生第二条日记
        if (Integer.valueOf(1).equals(p.getConfirmStatus())) return p.getDiaryId();

        PsyDiary d = new PsyDiary();
        d.setUserId(userId);
        d.setSource(2);                       // ← 关键：永久标记这条数据来自推测
        d.setPredictionId(predictionId);      // ← 溯源
        d.setEmotion(dto.getEmotion() != null ? dto.getEmotion() : p.getSuggestEmotion());
        d.setIntensity(dto.getIntensity() != null ? dto.getIntensity() : p.getSuggestIntensity());
        d.setDiaryContent(dto.getContent());
        d.setSleepHours(dto.getSleepHours());
        d.setStressLevel(dto.getStressLevel());
        d.setExerciseMin(dto.getExerciseMin());
        d.setDiaryDate(dto.getDiaryDate() != null ? dto.getDiaryDate() : p.getTargetDate());
        d.setIsPublic(0);
        diaryService.save(d);

        p.setConfirmStatus(1);
        p.setDiaryId(d.getDiaryId());
        p.setConfirmEmotion(d.getEmotion());   // 用户可能改了，这个差值就是算力命中率的样本
        predictionMapper.updateById(p);

        log(userId, "CONFIRM", "predictionId=" + predictionId + ",finalEmotion=" + d.getEmotion());
        return d.getDiaryId();
    }

    // =========================================================
    // 四·二、系统自动代记（无感补齐打卡）
    // =========================================================

    /**
     * 为什么需要这一步：
     * 原链路是「20:00 生成候选 → 用户打开 App 点采用 → 才产生日记」，
     * 当天没打开，候选就在 03:00 过期，打卡与连续天数直接断掉 ——
     * 这和无感记录「免打卡」的设计初衷正好相反。
     * 这里补上最后一公里：夜里替用户把候选落成 diary.source=3 的记录，
     * 打卡口径仍然成立，用户第二天随时可以把它改成亲笔日记。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String autoFill(Long userId, boolean force) {
        if (!props.isEnabled()) return "无感模块未启用";
        SysUser u = userService.getById(userId);
        if (u == null) return "用户不存在";
        if (!Integer.valueOf(1).equals(u.getSenseEnabled())) return "还没开启无感记录";

        LocalDate today = LocalDate.now();
        // 当天已有任何日记（手动 / 已确认推测 / 之前代记过）就不再重复写
        Long cnt = diaryService.lambdaQuery()
                .eq(PsyDiary::getUserId, userId)
                .eq(PsyDiary::getDiaryDate, today).count();
        if (cnt != null && cnt > 0) return "今天已经有记录了";

        // 当天没有任何使用痕迹就不代记：无感 ≠ 凭空捏造，没有线索就不该有记录
        // （把 app.sense.auto-fill-require-behavior 设为 false 可以放开，仅靠历史数据外推）
        if (!force && props.isAutoFillRequireBehavior()) {
            List<BehaviorFeature> feats = behaviorMapper.selectRange(userId, today.toString(), today.toString());
            if (feats == null || feats.isEmpty()) return "今天还没有使用痕迹，暂时猜不出来";
        }

        Map<String, Object> sug = doGenerate(userId, 1, null, props.getAutoFillMinConfidence());
        if (!Boolean.TRUE.equals(sug.get("hasSuggestion"))) {
            Object r = sug.get("reason");
            return r == null ? "线索不足，暂时猜不出来" : String.valueOf(r);
        }
        Object pidObj = sug.get("predictionId");
        if (!(pidObj instanceof Number)) return "推测结果异常，暂时无法代记";
        Long pid = ((Number) pidObj).longValue();

        Integer emotion = sug.get("emotion") instanceof Number ? ((Number) sug.get("emotion")).intValue() : null;
        Integer intensity = sug.get("intensity") instanceof Number ? ((Number) sug.get("intensity")).intValue() : null;
        double conf = sug.get("confidence") instanceof Number ? ((Number) sug.get("confidence")).doubleValue() : 0;
        String reason = sug.get("reason") == null ? "" : String.valueOf(sug.get("reason"));

        PsyDiary d = new PsyDiary();
        d.setUserId(userId);
        d.setSource(3);                       // 3 = 系统自动代记（可被用户改写成 source=1）
        d.setPredictionId(pid);
        d.setEmotion(emotion == null ? 9 : emotion);
        d.setIntensity(intensity);
        d.setDiaryDate(today);
        d.setDiaryContent(autoContent(emotion, conf, reason));
        d.setIsPublic(0);
        diaryService.save(d);

        // 候选标记为已消费，但**不写 confirmEmotion**：
        // 那是「用户最终选了什么」的样本，自动代记没有用户选择，写进去会让命中率虚高
        EmotionPrediction p = predictionMapper.selectById(pid);
        if (p != null) {
            p.setConfirmStatus(1);
            p.setDiaryId(d.getDiaryId());
            predictionMapper.updateById(p);
        }
        log(userId, "AUTO_FILL", "predictionId=" + pid + ",emotion=" + d.getEmotion() + ",conf=" + conf);
        return null;
    }

    @Override
    public int autoFillForAll() {
        List<SysUser> users = userService.lambdaQuery().eq(SysUser::getSenseEnabled, 1).list();
        int ok = 0, skip = 0;
        for (SysUser u : users) {
            try {
                // 注意：同类调用不走事务代理，单条失败只跳过该用户，不影响其它人
                if (autoFill(u.getUserId(), false) == null) ok++; else skip++;
            } catch (Exception e) {
                skip++;
            }
        }
        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("[无感记录] 自动代记完成：写入 {} 人，跳过 {} 人", ok, skip);
        return ok;
    }

    /** 自动代记的正文：说清「是推测 + 依据 + 可改写」，不能让用户以为是自己写过的 */
    private String autoContent(Integer emotion, double conf, String reason) {
        String name = EmotionConst.nameOf(emotion == null ? 9 : emotion);
        // reason 是给「确认卡片」写的，末尾带交互引导（"；你可以修改后再提交。"），
        // 直接塞进正文会变成"……你可以修改后再提交。。"，所以先剪掉这一段
        String r = reason == null ? "" : reason.trim()
                .replace("；你可以修改后再提交。", "")
                .replace("你可以修改后再提交。", "")
                .trim();
        if (!r.isEmpty() && !r.endsWith("。") && !r.endsWith("！")) r = r + "。";

        StringBuilder sb = new StringBuilder();
        sb.append("【系统代记】根据你今天的使用痕迹，推测此刻的心情偏「").append(name).append("」。");
        if (!r.isEmpty()) sb.append(r);
        sb.append("这条记录由无感模式自动生成（把握约 ").append(Math.round(conf * 100)).append("%），")
          .append("你不用打开也能留住今天；想让它更像你，补一句话就会变成亲笔日记。");
        return sb.toString();
    }

    @Override
    public void reject(Long userId, Long predictionId) {
        EmotionPrediction p = predictionMapper.selectById(predictionId);
        if (p == null) throw new BizException(404, "推测不存在或已被清理");
        if (!p.getUserId().equals(userId)) throw new BizException(403, "无权操作他人的推测");
        if (Integer.valueOf(1).equals(p.getConfirmStatus())) throw new BizException(400, "该推测已被采用，不能忽略");
        p.setConfirmStatus(2);
        predictionMapper.updateById(p);
    }

    @Override
    public Map<String, Object> history(Long userId, long page, long size) {
        size = Math.min(Math.max(size, 1), 50);
        Page<EmotionPrediction> pg = predictionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EmotionPrediction>()
                        .eq(EmotionPrediction::getUserId, userId)
                        .orderByDesc(EmotionPrediction::getTargetDate)
                        .orderByDesc(EmotionPrediction::getPredictionId));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", pg.getTotal());
        m.put("page", pg.getCurrent());
        m.put("size", pg.getSize());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EmotionPrediction p : pg.getRecords()) rows.add(toSuggestionVO(p, false));
        m.put("records", rows);
        return m;
    }

    @Override
    public Map<String, Object> accuracy(Long userId) {
        Map<String, Object> raw = predictionMapper.accuracyOf(userId);
        long total = num(raw.get("total")), adopted = num(raw.get("adopted"));
        long rejected = num(raw.get("rejected")), expired = num(raw.get("expired"));
        long hit = num(raw.get("exact_hit"));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("adopted", adopted);
        m.put("rejected", rejected);
        m.put("expired", expired);
        m.put("pending", total - adopted - rejected - expired);
        // 采纳率：处理的候选里有多少被认可（哪怕用户改了情绪也算认可）
        m.put("adoptRate", round(adopted + rejected == 0 ? 0 : adopted * 1.0 / (adopted + rejected)));
        // 精确命中率：推测值与用户最终确认值完全一致的比例
        m.put("hitRate", round(adopted == 0 ? 0 : hit * 1.0 / adopted));
        m.put("exactHit", hit);

        List<Map<String, Object>> byEmotion = new ArrayList<>();
        for (Map<String, Object> row : predictionMapper.accuracyByEmotion(userId)) {
            Integer e = ((Number) row.get("emotion")).intValue();
            long cnt = num(row.get("cnt")), h = num(row.get("hit_cnt"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("emotion", e);
            item.put("name", EmotionConst.nameOf(e));
            item.put("total", cnt);
            item.put("hit", h);
            item.put("rate", round(cnt == 0 ? 0 : h * 1.0 / cnt));
            byEmotion.add(item);
        }
        m.put("byEmotion", byEmotion);
        return m;
    }

    // =========================================================
    // 五、可视化数据
    // =========================================================

    @Override
    public Map<String, Object> correlation(Long userId, int days) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(Math.min(Math.max(days, 7), 365));

        Map<LocalDate, BehaviorPredictor.DayAgg> agg = BehaviorPredictor.aggregateByDate(
                behaviorMapper.selectRange(userId, from.toString(), to.toString()));
        Map<LocalDate, double[]> vectors = BehaviorPredictor.vectorize(agg);
        Map<LocalDate, Integer> emo = loadEmotions(userId, from, to, false);

        // 散点：当日使用时长（分钟） vs 情绪指数
        List<List<Object>> scatter = new ArrayList<>();
        // 热力：活跃小时 × 情绪类别
        Map<String, Long> heat = new HashMap<>();
        // 雷达：低情绪日 vs 高情绪日的 7 维均值
        double[] lowSum = new double[BehaviorPredictor.DIM], highSum = new double[BehaviorPredictor.DIM];
        int lowN = 0, highN = 0;

        for (Map.Entry<LocalDate, BehaviorPredictor.DayAgg> e : agg.entrySet()) {
            Integer emotion = emo.get(e.getKey());
            if (emotion == null) continue;
            int index = EmotionConst.indexOf(emotion);
            double minutes = e.getValue().stayMs() / 60000.0;
            scatter.add(Arrays.asList(e.getKey().toString(),
                    Math.round(minutes * 10) / 10.0, index, emotion));

            for (int h = 0; h < 24; h++) {
                if ((e.getValue().hourMask() & (1L << h)) != 0) {
                    heat.merge(h + "_" + emotion, 1L, Long::sum);
                }
            }
            double[] v = vectors.get(e.getKey());
            if (v == null) continue;
            if (index < 50) { addTo(lowSum, v); lowN++; }
            else if (index >= 70) { addTo(highSum, v); highN++; }
        }

        List<List<Object>> heatData = new ArrayList<>();
        for (Map.Entry<String, Long> he : heat.entrySet()) {
            String[] parts = he.getKey().split("_");
            heatData.add(Arrays.asList(Integer.valueOf(parts[0]), Integer.valueOf(parts[1]), he.getValue()));
        }

        List<String> radarNames = List.of("活跃强度", "操作密度", "沉浸度", "夜间占比", "日记占比", "FM占比", "打开频次");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("indicators", radarNames);
        m.put("scatter", scatter);
        m.put("heat", heatData);
        m.put("radarLow", avgOf(lowSum, lowN));
        m.put("radarHigh", avgOf(highSum, highN));
        m.put("lowDays", lowN);
        m.put("highDays", highN);
        m.put("days", Math.min(Math.max(days, 7), 365));
        return m;
    }

    @Override
    public Map<String, Object> timeline(Long userId, int days) {
        LocalDate to = LocalDate.now();
        // days 就是返回的点数：今天算在内，所以起点是「今天往前 days-1 天」
        LocalDate from = to.minusDays(Math.min(Math.max(days, 7), 365) - 1L);

        List<PsyDiary> list = diaryService.lambdaQuery()
                .select(PsyDiary::getDiaryDate, PsyDiary::getEmotion,
                        PsyDiary::getSource, PsyDiary::getPredictionId)
                .eq(PsyDiary::getUserId, userId)
                .ge(PsyDiary::getDiaryDate, from)
                .le(PsyDiary::getDiaryDate, to)
                .list();

        // 同一天同时存在手动与推测时，两者都要保留（图表要画两条线）
        Map<LocalDate, PsyDiary> manual = new LinkedHashMap<>();
        Map<LocalDate, PsyDiary> inferred = new LinkedHashMap<>();
        for (PsyDiary d : list) {
            if (d.getDiaryDate() == null) continue;
            // 只有 source=1 是用户亲笔；2=确认推测、3=自动代记 都画在「推测」那条线
            if (Integer.valueOf(1).equals(d.getSource())) manual.put(d.getDiaryDate(), d);
            else inferred.put(d.getDiaryDate(), d);
        }

        List<String> labels = new ArrayList<>(), dates = new ArrayList<>();
        List<Object> manualVals = new ArrayList<>(), inferredVals = new ArrayList<>();
        List<Object> manualEmo = new ArrayList<>(), inferredEmo = new ArrayList<>();
        long manualCnt = 0, inferredCnt = 0;

        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            labels.add(d.format(MD));
            dates.add(d.toString());
            PsyDiary m = manual.get(d);
            manualVals.add(m == null ? null : EmotionConst.indexOf(m.getEmotion()));
            manualEmo.add(m == null ? null : m.getEmotion());
            if (m != null) manualCnt++;
            PsyDiary i = inferred.get(d);
            inferredVals.add(i == null ? null : EmotionConst.indexOf(i.getEmotion()));
            inferredEmo.add(i == null ? null : i.getEmotion());
            if (i != null) inferredCnt++;
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("labels", labels);
        res.put("dates", dates);
        res.put("manual", manualVals);
        res.put("inferred", inferredVals);
        res.put("manualEmotions", manualEmo);
        res.put("inferredEmotions", inferredEmo);
        res.put("manualCount", manualCnt);
        res.put("inferredCount", inferredCnt);
        return res;
    }

    // =========================================================
    // 六、定时任务支持
    // =========================================================

    @Override
    public int expireOld() {
        List<Long> ids = predictionMapper.selectExpired();
        for (Long id : ids) {
            EmotionPrediction p = new EmotionPrediction();
            p.setPredictionId(id);
            p.setConfirmStatus(3);
            predictionMapper.updateById(p);
        }
        return ids.size();
    }

    @Override
    public int cleanRetention() {
        LocalDate deadline = LocalDate.now().minusDays(props.getRetentionDays());
        int rows = behaviorMapper.delete(new LambdaQueryWrapper<BehaviorFeature>()
                .lt(BehaviorFeature::getStatDate, deadline));
        if (rows > 0) log(null, "RETENTION_CLEAN", "清理 " + rows + " 行过期行为数据");
        return rows;
    }

    /** 定时器用：给所有开启无感记录且今天还没写日记的用户生成推测 */
    public void generateForAll() {
        List<SysUser> users = userService.lambdaQuery()
                .eq(SysUser::getSenseEnabled, 1).list();
        LocalDate today = LocalDate.now();
        int ok = 0, fail = 0;
        for (SysUser u : users) {
            try {
                // 今天已经手动记过日记就不打扰
                Long cnt = diaryService.lambdaQuery()
                        .eq(PsyDiary::getUserId, u.getUserId())
                        .eq(PsyDiary::getDiaryDate, today).count();
                if (cnt != null && cnt > 0) continue;
                generate(u.getUserId(), 1, null);
                ok++;
            } catch (Exception ignored) {
                fail++;
            }
        }
        org.slf4j.LoggerFactory.getLogger(getClass())
                .info("[无感记录] 定时生成完成：成功 {} 用户，跳过/失败 {}", ok, fail);
    }

    // =========================================================
    // 私有工具
    // =========================================================

    /** 加载某用户在 [from,to] 的每日情绪；onlyManual=true 时只取用户手动录入的（防自证循环） */
    private Map<LocalDate, Integer> loadEmotions(Long userId, LocalDate from, LocalDate to, boolean onlyManual) {
        LambdaQueryWrapper<PsyDiary> w = new LambdaQueryWrapper<PsyDiary>()
                .select(PsyDiary::getDiaryDate, PsyDiary::getEmotion, PsyDiary::getSource)
                .eq(PsyDiary::getUserId, userId)
                .ge(PsyDiary::getDiaryDate, from)
                .le(PsyDiary::getDiaryDate, to)
                /*
                 * ⚠️ 排序必须带上 diary_id：MySQL 对 ORDER BY diary_date 的并列行
                 * 不保证顺序，而下面的 map.put 是「后者覆盖前者」的语义。
                 * 只按日期排序时，同一天多篇日记里哪篇胜出是不确定的——
                 * 统计侧却固定用 MAX(diary_id)，两边会算出不同的「当天情绪」。
                 * 统一裁决规则：**同一天多篇 → diary_id 最大（最后写的那篇）代表当天**。
                 */
                .orderByAsc(PsyDiary::getDiaryDate)
                .orderByAsc(PsyDiary::getDiaryId);
        if (onlyManual) w.eq(PsyDiary::getSource, 1);
        Map<LocalDate, Integer> map = new LinkedHashMap<>();
        for (PsyDiary d : diaryService.list(w)) {
            if (d.getDiaryDate() == null || d.getEmotion() == null) continue;
            map.put(d.getDiaryDate(), d.getEmotion());   // 升序遍历 → 留下的就是 diary_id 最大者
        }
        return map;
    }

    /**
     * 从已查询出的情绪标签里切出 [from,to] 区间的指数序列（跳过缺失日）。
     * 取代旧的 loadIndexSeries：后者会对 psy_diary 再发一次重叠区间的查询。
     */
    private List<Double> seriesFromLabels(Map<LocalDate, Integer> labels, LocalDate from, LocalDate to) {
        List<Double> out = new ArrayList<>();
        for (Map.Entry<LocalDate, Integer> e : labels.entrySet()) {
            LocalDate d = e.getKey();
            if (d.isBefore(from) || d.isAfter(to)) continue;
            out.add((double) EmotionConst.indexOf(e.getValue()));
        }
        return out;
    }

    private String buildReason(LocalDate nearestDay, Map<LocalDate, Integer> labels,
                               List<String> hits, Double ar1Next, List<Double> series) {
        StringBuilder sb = new StringBuilder();
        if (nearestDay != null) {
            sb.append("你今天的使用节奏与 ").append(nearestDay.format(MD))
              .append(" 很接近（那天记录了『").append(EmotionConst.nameOf(labels.get(nearestDay)))
              .append("』）；");
        }
        if (hits != null && !hits.isEmpty()) {
            sb.append("随笔里出现了 ").append(String.join("、", hits.subList(0, Math.min(4, hits.size()))))
              .append("；");
        }
        if (ar1Next != null) {
            double slope = BehaviorPredictor.slope(series);
            if (slope < -1.5) sb.append("近期情绪指数持续走低；");
            else if (slope > 1.5) sb.append("近期情绪指数持续回升；");
            else sb.append("近期情绪基线相对平稳；");
        }
        if (sb.length() == 0) sb.append("基于你最近的行为与记录做出的判断；");
        sb.append("你可以修改后再提交。");
        return sb.toString();
    }

    private Map<String, Object> toSuggestionVO(EmotionPrediction p, boolean withCandidates) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hasSuggestion", true);
        m.put("predictionId", p.getPredictionId());
        m.put("targetDate", p.getTargetDate() == null ? null : p.getTargetDate().toString());
        m.put("triggerType", p.getTriggerType());
        m.put("emotion", p.getSuggestEmotion());
        m.put("emotionName", EmotionConst.nameOf(p.getSuggestEmotion()));
        m.put("intensity", p.getSuggestIntensity());
        m.put("confidence", p.getConfidence() == null ? 0 : p.getConfidence().doubleValue());
        m.put("reason", p.getReason());
        m.put("detail", p.getDetail());
        m.put("status", p.getConfirmStatus());
        m.put("expireTime", p.getExpireTime());
        m.put("createTime", p.getCreateTime());
        if (withCandidates) m.put("candidates", parseTop3(p.getCandidates()));
        return m;
    }

    /** candidates 字段自身已是 JSON 字符串，反序列化回对象给前端（避免前端二次解析） */
    private Object parseTop3(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private Map<String, Object> noSuggestion(String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hasSuggestion", false);
        m.put("reason", reason);
        return m;
    }

    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "{}";
        }
    }

    private List<Double> vecToList(double[] v) {
        List<Double> l = new ArrayList<>();
        for (double x : v) l.add(Math.round(x * 1000) / 1000.0);
        return l;
    }

    /** 置信度越高 → 强度给得越足（轻量启发式，避免复杂模型） */
    private int guessIntensity(BehaviorPredictor.FuseResult fr) {
        return guessIntensityByP(fr.bestP());
    }

    private int guessIntensityByP(double p) {
        if (p >= 0.75) return 3;
        if (p >= 0.55) return 2;
        return 1;
    }

    private void log(Long userId, String action, String detail) {
        try {
            SensePrivacyLog l = new SensePrivacyLog();
            l.setUserId(userId);
            l.setAction(action);
            l.setDetail(detail);
            l.setIpMasked(maskedIp());
            l.setCreateTime(LocalDateTime.now());
            privacyLogMapper.insert(l);
        } catch (Exception ignored) {
            // 审计日志失败绝不能影响主流程
        }
    }

    private String maskedIp() {
        try {
            var attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                String ip = sra.getRequest().getHeader("X-Forwarded-For");
                if (ip == null || ip.isBlank()) ip = sra.getRequest().getRemoteAddr();
                return IpMaskUtil.mask(ip);
            }
        } catch (Exception ignored) { /* 定时任务线程里没有 request，忽略 */ }
        return null;
    }

    private static Integer n1(Integer v) { return v == null ? 0 : v; }
    private static Integer onOff(Integer v) { return (v != null && v == 1) ? 1 : 0; }

    /** null 安全取整：开关字段在历史数据里可能为 NULL，比较前统一成 0 */
    private static int nz(Integer v) { return v == null ? 0 : v; }
    private static long num(Object v) { return v == null ? 0L : ((Number) v).longValue(); }
    private static double round(double v) { return Math.round(v * 1000) / 1000.0; }
    private static String pct(double v) { return String.format("%.0f", v * 100); }

    private Long clampStay(Long raw) {
        long v = raw == null ? 0L : Math.max(0L, raw);
        return Math.min(v, props.getMaxStayMs());
    }

    private Integer clampInt(Integer raw, int min, int max) {
        if (raw == null) return min;
        return Math.max(min, Math.min(raw, max));
    }

    private static void addTo(double[] sum, double[] v) {
        for (int i = 0; i < sum.length; i++) sum[i] += v[i];
    }

    private List<Double> avgOf(double[] sum, int n) {
        List<Double> out = new ArrayList<>();
        for (double v : sum) out.add(n == 0 ? 0.0 : Math.round(v / n * 1000) / 1000.0);
        return out;
    }
}
