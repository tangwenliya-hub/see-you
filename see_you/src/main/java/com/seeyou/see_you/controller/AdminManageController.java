package com.seeyou.see_you.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeyou.see_you.algorithm.EmotionConst;
import com.seeyou.see_you.entity.*;
import com.seeyou.see_you.mapper.*;
import com.seeyou.see_you.service.*;
import com.seeyou.see_you.util.PageLimit;
import com.seeyou.see_you.util.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 后台扩展管理接口：情绪预警 / 无感记录 / 情绪词典 / 系统参数 /
 * AI 对话 / 测评记录 / 账号安全 七个模块的管理端数据。
 *
 * <h3>权限</h3>
 * 所有路径以 /admin/ 开头，JwtInterceptor 已强制要求 role=1，本类不再重复校验。
 *
 * <h3>隐私红线（务必遵守）</h3>
 * <ul>
 *   <li>不返回任何日记正文；预警详情只给统计指标（负面占比、连续天数、平均指数）；</li>
 *   <li>不返回行为明细（psy_behavior_feature）；推测记录剔除特征快照字段；</li>
 *   <li>会话列表不返回完整 token；密码找回列表不返回验证码。</li>
 * </ul>
 */
import lombok.extern.slf4j.Slf4j;
import com.seeyou.see_you.config.RequireAdmin;

@Slf4j
@RequireAdmin
@RestController
@RequestMapping("/admin")
public class AdminManageController {

    @Resource private SysUserService userService;
    @Resource private PsyTestPaperService testPaperService;
    @Resource private SysUserSessionMapper sessionMapper;
    @Resource private SysLoginAttemptMapper attemptMapper;
    @Resource private SysPwdResetMapper pwdResetMapper;
    @Resource private RiskAlertMapper riskAlertMapper;
    @Resource private EmotionPredictionMapper predictionMapper;
    @Resource private EmotionLexiconMapper lexiconMapper;
    @Resource private AiChatMapper aiChatMapper;
    @Resource private PsyRecordMapper recordMapper;
    @Resource private SenseService senseService;
    @Resource private AdminSettingsService settingsService;
    @Resource private com.seeyou.see_you.config.RiskProperties riskProps;
    @Resource private com.seeyou.see_you.config.CommentProperties commentProps;

    /** userId -> 展示名（昵称优先，没有就用账号） */
    private Map<Long, String> nameMap() {
        Map<Long, String> map = new HashMap<>();
        try {
            userService.list().forEach(u -> map.put(u.getUserId(),
                    (u.getNickname() != null && !u.getNickname().isBlank()) ? u.getNickname() : u.getUsername()));
        } catch (Exception ignored) { log.warn("可忽略的异常，已跳过: {}", ignored.toString()); }
        return map;
    }

    private static String maskToken(String token) {
        if (token == null) return "—";
        return token.length() <= 8 ? "****" : token.substring(0, 6) + "…" + token.substring(token.length() - 4);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ==================================================================
    // 一、情绪风险预警管理
    // ==================================================================

    /** 预警统计：总数 / 未读 / 未确认 / 三级分布 / 近 30 天趋势 */
    @GetMapping("/alert/stats")
    public Result<Map<String, Object>> alertStats() {
        Map<String, Object> data = new HashMap<>();
        try {
            data.put("total", riskAlertMapper.selectCount(null));
            data.put("unread", riskAlertMapper.selectCount(new LambdaQueryWrapper<RiskAlert>().eq(RiskAlert::getIsRead, 0)));
            data.put("unacked", riskAlertMapper.selectCount(new LambdaQueryWrapper<RiskAlert>().eq(RiskAlert::getAckStatus, 0)));
            data.put("l1", riskAlertMapper.selectCount(new LambdaQueryWrapper<RiskAlert>().eq(RiskAlert::getAlertLevel, 1)));
            data.put("l2", riskAlertMapper.selectCount(new LambdaQueryWrapper<RiskAlert>().eq(RiskAlert::getAlertLevel, 2)));
            data.put("l3", riskAlertMapper.selectCount(new LambdaQueryWrapper<RiskAlert>().eq(RiskAlert::getAlertLevel, 3)));

            LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
            data.put("today", riskAlertMapper.selectCount(new LambdaQueryWrapper<RiskAlert>().ge(RiskAlert::getCreateTime, todayStart)));

            // 误报反馈与准确率（论文量化指标）：
            // 准确率 = (触发总数 - 用户标记误报数) / 触发总数 × 100%
            // 危机词通道不允许标误报，所以这里的误报只会来自统计趋势 / 预警词 / AI 精判通道
            long misreportCnt = riskAlertMapper.selectCount(new LambdaQueryWrapper<RiskAlert>().eq(RiskAlert::getMisreport, 1));
            long totalCnt = riskAlertMapper.selectCount(null);
            data.put("misreport", misreportCnt);
            data.put("accuracy", totalCnt == 0 ? null
                    : Math.round((totalCnt - misreportCnt) * 1000.0 / totalCnt) / 10.0);

            // 分通道准确率：各触发规则的误报倾向差别很大（趋势类最容易误报、危机词几乎不会），
            // 分组统计后论文里可以讲「哪条通道最准、瓶颈在哪」，比一个总数更有说服力。
            // 危机通道（CRISIS_KEYWORD / AI_JUDGE）按安全设计不允许用户标误报，准确率记为 null。
            List<RiskAlert> allAlerts = riskAlertMapper.selectList(new LambdaQueryWrapper<RiskAlert>()
                    .select(RiskAlert::getTriggerRule, RiskAlert::getMisreport));
            Map<String, long[]> byRule = new LinkedHashMap<>();
            for (RiskAlert a : allAlerts) {
                String rule = a.getTriggerRule() == null ? "UNKNOWN" : a.getTriggerRule();
                long[] cell = byRule.computeIfAbsent(rule, k -> new long[2]);
                cell[0]++;
                if (Integer.valueOf(1).equals(a.getMisreport())) cell[1]++;
            }
            List<Map<String, Object>> channels = new ArrayList<>();
            byRule.forEach((rule, c) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("rule", rule);
                m.put("ruleName", com.seeyou.see_you.algorithm.RiskRuleEngine.ruleName(rule));
                m.put("crisis", com.seeyou.see_you.service.impl.RiskAlertServiceImpl.isCrisisRule(rule));
                m.put("total", c[0]);
                m.put("misreport", c[1]);
                m.put("accuracy", c[0] == 0 ? null
                        : Math.round((c[0] - c[1]) * 1000.0 / c[0]) / 10.0);
                channels.add(m);
            });
            // 触发多的通道排前面，一眼看出主要来源
            channels.sort((x, y) -> Long.compare((long) y.get("total"), (long) x.get("total")));
            data.put("channels", channels);

            // 近 30 天趋势：按 alert_date 分组统计
            List<RiskAlert> recent = riskAlertMapper.selectList(new LambdaQueryWrapper<RiskAlert>()
                    .ge(RiskAlert::getAlertDate, LocalDate.now().minusDays(29)));
            Map<String, long[]> acc = new TreeMap<>();
            for (int i = 29; i >= 0; i--) acc.put(LocalDate.now().minusDays(i).toString(), new long[4]);
            for (RiskAlert a : recent) {
                if (a.getAlertDate() == null) continue;
                long[] cell = acc.get(a.getAlertDate().toString());
                if (cell == null) continue;
                cell[0]++;
                int lv = a.getAlertLevel() == null ? 0 : a.getAlertLevel();
                if (lv >= 1 && lv <= 3) cell[lv]++;
            }
            List<Map<String, Object>> trend = new ArrayList<>();
            acc.forEach((d, c) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", d); m.put("total", c[0]);
                m.put("l1", c[1]); m.put("l2", c[2]); m.put("l3", c[3]);
                trend.add(m);
            });
            data.put("trend", trend);
            data.put("disclaimer", settingsService.disclaimer());
        } catch (Exception e) {
            data.put("error", "预警统计失败：" + e.getMessage());
        }
        return Result.success(data);
    }

    /** 预警记录分页（带用户名，可按等级 / 是否已读筛选） */
    @GetMapping("/alert/list")
    public Result<Map<String, Object>> alertList(@RequestParam(defaultValue = "1") long page,
                                                 @RequestParam(defaultValue = "10") long size,
                                                 @RequestParam(required = false) Integer level,
                                                 @RequestParam(required = false) Integer isRead,
                                                 @RequestParam(required = false) String keyword) {
        Map<Long, String> names = nameMap();
        LambdaQueryWrapper<RiskAlert> q = new LambdaQueryWrapper<>();
        if (level != null && level > 0) q.eq(RiskAlert::getAlertLevel, level);
        if (isRead != null && isRead >= 0) q.eq(RiskAlert::getIsRead, isRead);
        if (keyword != null && !keyword.isBlank()) {
            Set<Long> ids = new HashSet<>();
            names.forEach((id, n) -> { if (n.contains(keyword.trim())) ids.add(id); });
            if (ids.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("list", new ArrayList<>());
                empty.put("total", 0L);
                return Result.success(empty);
            }
            q.in(RiskAlert::getUserId, ids);
        }
        q.orderByDesc(RiskAlert::getAlertDate).orderByDesc(RiskAlert::getAlertId);

        Page<RiskAlert> p = riskAlertMapper.selectPage(new Page<>(PageLimit.page(page), PageLimit.of(size)), q);
        List<Map<String, Object>> list = new ArrayList<>();
        for (RiskAlert a : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("alertId", a.getAlertId());
            m.put("userId", a.getUserId());
            m.put("userName", names.getOrDefault(a.getUserId(), "用户#" + a.getUserId()));
            m.put("alertDate", a.getAlertDate());
            m.put("level", a.getAlertLevel());
            m.put("levelName", a.getAlertLevel() == null ? "—" :
                    switch (a.getAlertLevel()) { case 1 -> "轻度关注"; case 2 -> "中度提醒"; case 3 -> "高度关注"; default -> "—"; });
            m.put("windowDays", a.getWindowDays());
            m.put("recordDays", a.getRecordDays());
            m.put("negativeDays", a.getNegativeDays());
            m.put("maxDowncastDays", a.getMaxDowncastDays());
            m.put("negativeRatio", a.getNegativeRatio());
            m.put("avgIndex", a.getAvgIndex());
            m.put("trendSlope", a.getTrendSlope());
            m.put("triggerRule", a.getTriggerRule());
            // 面向人的展示用中文名，原枚举保留给检索
            m.put("triggerRuleName", com.seeyou.see_you.algorithm.RiskRuleEngine.ruleName(a.getTriggerRule()));
            // 危机通道标记：后台表格据此加「危机词」标签，并把窗口指标显示成「—」
            m.put("crisis", com.seeyou.see_you.service.impl.RiskAlertServiceImpl.isCrisisRule(a.getTriggerRule()));
            m.put("isRead", a.getIsRead());
            m.put("ackStatus", a.getAckStatus());
            m.put("disposition", a.getDisposition());
            m.put("dispositionTime", a.getDispositionTime());
            // 误报反馈：用户标了「这不是我」，列表里要能看到
            m.put("misreport", a.getMisreport() != null && a.getMisreport() == 1);
            m.put("createTime", a.getCreateTime());
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", p.getTotal());
        return Result.success(data);
    }

    /**
     * 预警报表导出（Excel / EasyExcel）。
     * 复用列表页的筛选条件（等级 / 已读 / 用户名关键字），不分页、导出全部匹配记录。
     * 列与后台列表一致，不含日记正文与行为明细（隐私红线同 {@link #alertList}）。
     */
    @GetMapping("/alert/export")
    public void exportAlerts(jakarta.servlet.http.HttpServletResponse response,
                             @RequestParam(required = false) Integer level,
                             @RequestParam(required = false) Integer isRead,
                             @RequestParam(required = false) String keyword) throws java.io.IOException {
        Map<Long, String> names = nameMap();
        LambdaQueryWrapper<RiskAlert> q = new LambdaQueryWrapper<>();
        if (level != null && level > 0) q.eq(RiskAlert::getAlertLevel, level);
        if (isRead != null && isRead >= 0) q.eq(RiskAlert::getIsRead, isRead);
        if (keyword != null && !keyword.isBlank()) {
            Set<Long> ids = new HashSet<>();
            names.forEach((id, n) -> { if (n.contains(keyword.trim())) ids.add(id); });
            q.in(RiskAlert::getUserId, ids);   // 无匹配用户时 in 空集 → 导出空表（前端提示）
        }
        q.orderByDesc(RiskAlert::getAlertDate).orderByDesc(RiskAlert::getAlertId);

        List<com.seeyou.see_you.dto.AlertExportDTO> rows = new ArrayList<>();
        for (RiskAlert a : riskAlertMapper.selectList(q)) {
            com.seeyou.see_you.dto.AlertExportDTO r = new com.seeyou.see_you.dto.AlertExportDTO();
            r.setAlertDate(a.getAlertDate() == null ? "" : a.getAlertDate().toString());
            r.setUserName(names.getOrDefault(a.getUserId(), "用户#" + a.getUserId()));
            r.setLevelName(a.getAlertLevel() == null ? "—" :
                    switch (a.getAlertLevel()) { case 1 -> "轻度关注"; case 2 -> "中度提醒"; case 3 -> "高度关注"; default -> "—"; });
            r.setTriggerRuleName(com.seeyou.see_you.algorithm.RiskRuleEngine.ruleName(a.getTriggerRule()));
            r.setTriggerDetail(a.getTriggerDetail());
            r.setMaxDowncastDays(a.getMaxDowncastDays() == null ? "—" : a.getMaxDowncastDays() + " 天");
            r.setNegativeRatio(a.getNegativeRatio() == null ? "—" :
                    a.getNegativeRatio().multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%");
            r.setAvgIndex(a.getAvgIndex() == null ? "—" : String.valueOf(a.getAvgIndex()));
            r.setIsRead(Integer.valueOf(1).equals(a.getIsRead()) ? "已读" : "未读");
            r.setAckStatus(Integer.valueOf(1).equals(a.getAckStatus()) ? "已知晓" : "未确认");
            r.setMisreport(Integer.valueOf(1).equals(a.getMisreport()) ? "误报" : "—");
            r.setDisposition(a.getDisposition() == null || a.getDisposition().isBlank() ? "未处置" : a.getDisposition());
            r.setDispositionNote(a.getDispositionNote());
            r.setDispositionTime(a.getDispositionTime() == null ? "" : a.getDispositionTime().withNano(0).toString());
            r.setCreateTime(a.getCreateTime() == null ? "" : a.getCreateTime().withNano(0).toString());
            rows.add(r);
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = java.net.URLEncoder.encode("情绪预警报表_" + LocalDate.now(), java.nio.charset.StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        com.alibaba.excel.EasyExcel.write(response.getOutputStream(), com.seeyou.see_you.dto.AlertExportDTO.class)
                .sheet("预警记录")
                .doWrite(rows);
    }

    /** 管理员查看判定依据 → 状态记为已读（「已读 = 被查看」：用户端弹窗或管理员查看，任一方都算） */
    @PostMapping("/alert/{id:\\d+}/read")
    public Result<Boolean> markAlertRead(@PathVariable Long id) {
        RiskAlert a = riskAlertMapper.selectById(id);
        if (a == null) return Result.error(404, "预警记录不存在");
        if (!Integer.valueOf(1).equals(a.getIsRead())) {
            a.setIsRead(1);
            a.setUpdateTime(java.time.LocalDateTime.now());
            riskAlertMapper.updateById(a);
        }
        return Result.success();
    }

    /** 预警详情：只给统计指标（不含日记正文与行为明细） */
    @GetMapping("/alert/{id:\\d+}")
    public Result<Map<String, Object>> alertDetail(@PathVariable Long id) {
        RiskAlert a = riskAlertMapper.selectById(id);
        if (a == null) return Result.error(404, "预警记录不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("alertId", a.getAlertId());
        m.put("userId", a.getUserId());
        m.put("userName", nameMap().getOrDefault(a.getUserId(), "用户#" + a.getUserId()));
        m.put("alertDate", a.getAlertDate());
        m.put("level", a.getAlertLevel());
        m.put("windowDays", a.getWindowDays());
        m.put("recordDays", a.getRecordDays());
        m.put("negativeDays", a.getNegativeDays());
        m.put("maxDowncastDays", a.getMaxDowncastDays());
        m.put("negativeRatio", a.getNegativeRatio());
        m.put("avgIndex", a.getAvgIndex());
        m.put("trendSlope", a.getTrendSlope());
        m.put("triggerRule", a.getTriggerRule());
        m.put("triggerRuleName", com.seeyou.see_you.algorithm.RiskRuleEngine.ruleName(a.getTriggerRule()));
        m.put("triggerDetail", a.getTriggerDetail());
        m.put("advice", a.getAdvice());
        m.put("isRead", a.getIsRead());
        m.put("ackStatus", a.getAckStatus());
        // 处置信息（轻量版闭环）：管理员做了什么、什么时候做的
        m.put("disposition", a.getDisposition());
        m.put("dispositionNote", a.getDispositionNote());
        m.put("dispositionTime", a.getDispositionTime());
        // 误报反馈：用户端「这不是我」标记
        m.put("misreport", a.getMisreport() != null && a.getMisreport() == 1);
        m.put("misreportTime", a.getMisreportTime());
        m.put("createTime", a.getCreateTime());
        m.put("disclaimer", settingsService.disclaimer());
        // 隐私说明：后台同样不提供日记正文，只呈现统计量
        m.put("privacyNote", "出于隐私保护，后台不展示该用户的日记正文与行为明细，仅呈现聚合指标。");
        return Result.success(m);
    }

    /** 处置方式的合法值：与前端下拉一致，防脏数据（备注自由填写不校验） */
    private static final Set<String> DISPOSITIONS = Set.of("已电话联系", "已通知辅导员", "已上门面谈");

    /**
     * 管理员标记处置（轻量闭环）：一条预警保留最新一次处置结果。
     * 只补「发现之后做了什么」这一环，不做多次跟进的时间线 —— 有需求再升级。
     */
    @PostMapping("/alert/{id:\\d+}/disposition")
    public Result<Boolean> disposition(@PathVariable Long id, @RequestBody Map<String, String> body) {
        RiskAlert a = riskAlertMapper.selectById(id);
        if (a == null) return Result.error(404, "预警记录不存在");
        String way = body == null ? null : body.get("disposition");
        if (way == null || !DISPOSITIONS.contains(way)) {
            return Result.error(400, "处置方式无效，请从下拉选项中选择");
        }
        String note = body.get("note");
        a.setDisposition(way);
        a.setDispositionNote(note == null || note.isBlank() ? null : note.trim());
        a.setDispositionTime(java.time.LocalDateTime.now());
        a.setUpdateTime(java.time.LocalDateTime.now());
        riskAlertMapper.updateById(a);
        return Result.success();
    }

    // ==================================================================
    // 二、无感记录管理
    // ==================================================================

    /** 无感记录统计：开启率、推测量、采纳率、平均置信度、近 14 天趋势、九类分布 */
    @GetMapping("/sense/stats")
    public Result<Map<String, Object>> senseStats() {
        Map<String, Object> data = new HashMap<>();
        try {
            long totalUsers = userService.count();
            long enabledUsers = userService.count(new LambdaQueryWrapper<SysUser>().eq(SysUser::getSenseEnabled, 1));
            data.put("totalUsers", totalUsers);
            data.put("enabledUsers", enabledUsers);
            data.put("enableRate", totalUsers == 0 ? 0 : round2(enabledUsers * 100.0 / totalUsers));

            List<EmotionPrediction> all = predictionMapper.selectList(null);
            long total = all.size();
            long adopted = all.stream().filter(p -> Integer.valueOf(1).equals(p.getConfirmStatus())).count();
            long ignored = all.stream().filter(p -> Integer.valueOf(2).equals(p.getConfirmStatus())).count();
            long expired = all.stream().filter(p -> Integer.valueOf(3).equals(p.getConfirmStatus())).count();
            long pending = all.stream().filter(p -> Integer.valueOf(0).equals(p.getConfirmStatus())).count();
            double avgConf = all.stream()
                    .filter(p -> p.getConfidence() != null)
                    .mapToDouble(p -> p.getConfidence().doubleValue()).average().orElse(0);
            data.put("total", total);
            data.put("adopted", adopted);
            data.put("ignored", ignored);
            data.put("expired", expired);
            data.put("pending", pending);
            data.put("adoptRate", total == 0 ? 0 : round2(adopted * 100.0 / total));
            data.put("avgConfidence", round2(avgConf));
            data.put("lexiconSize", senseService.lexiconSize());

            // 近 14 天趋势
            Map<String, long[]> acc = new TreeMap<>();
            for (int i = 13; i >= 0; i--) acc.put(LocalDate.now().minusDays(i).toString(), new long[2]);
            for (EmotionPrediction p : all) {
                if (p.getCreateTime() == null) continue;
                long[] cell = acc.get(p.getCreateTime().toLocalDate().toString());
                if (cell == null) continue;
                cell[0]++;
                if (Integer.valueOf(1).equals(p.getConfirmStatus())) cell[1]++;
            }
            List<Map<String, Object>> trend = new ArrayList<>();
            acc.forEach((d, c) -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", d); m.put("generated", c[0]); m.put("adopted", c[1]);
                trend.add(m);
            });
            data.put("trend", trend);

            // 九类推测情绪分布
            Map<String, Long> dist = new LinkedHashMap<>();
            for (int i = 1; i <= 9; i++) dist.put(EmotionConst.NAMES[i], 0L);
            for (EmotionPrediction p : all) {
                if (p.getSuggestEmotion() != null && p.getSuggestEmotion() >= 1 && p.getSuggestEmotion() <= 9) {
                    String n = EmotionConst.NAMES[p.getSuggestEmotion()];
                    dist.put(n, dist.get(n) + 1);
                }
            }
            data.put("emotionDist", dist);
        } catch (Exception e) {
            data.put("error", "无感统计失败：" + e.getMessage());
        }
        return Result.success(data);
    }

    /** 推测记录分页（剔除特征快照等明细字段） */
    @GetMapping("/sense/predictions")
    public Result<Map<String, Object>> sensePredictions(@RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "10") long size,
                                                        @RequestParam(required = false) Integer status) {
        Map<Long, String> names = nameMap();
        LambdaQueryWrapper<EmotionPrediction> q = new LambdaQueryWrapper<>();
        if (status != null && status >= 0) q.eq(EmotionPrediction::getConfirmStatus, status);
        q.orderByDesc(EmotionPrediction::getPredictionId);

        Page<EmotionPrediction> p = predictionMapper.selectPage(new Page<>(PageLimit.page(page), PageLimit.of(size)), q);
        List<Map<String, Object>> list = new ArrayList<>();
        for (EmotionPrediction e : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("predictionId", e.getPredictionId());
            m.put("userId", e.getUserId());
            m.put("userName", names.getOrDefault(e.getUserId(), "用户#" + e.getUserId()));
            m.put("targetDate", e.getTargetDate());
            m.put("triggerType", e.getTriggerType());
            m.put("suggestEmotion", e.getSuggestEmotion());
            m.put("suggestEmotionName", EmotionConst.nameOf(e.getSuggestEmotion()));
            m.put("suggestIntensity", e.getSuggestIntensity());
            m.put("confidence", e.getConfidence());
            m.put("reason", e.getReason());
            m.put("confirmStatus", e.getConfirmStatus());
            m.put("confirmStatusName", switch (e.getConfirmStatus() == null ? 0 : e.getConfirmStatus()) {
                case 1 -> "已采用"; case 2 -> "已忽略"; case 3 -> "已过期"; default -> "待确认";
            });
            m.put("confirmEmotion", e.getConfirmEmotion());
            m.put("confirmEmotionName", e.getConfirmEmotion() == null ? null : EmotionConst.nameOf(e.getConfirmEmotion()));
            m.put("hit", e.getConfirmEmotion() != null && e.getConfirmEmotion().equals(e.getSuggestEmotion()));
            m.put("createTime", e.getCreateTime());
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", p.getTotal());
        return Result.success(data);
    }

    // ==================================================================
    // 三、情绪词典维护
    // ==================================================================

    /** 词典统计：总数 + 按情绪分组 + 按类型分组 */
    @GetMapping("/lexicon/stats")
    public Result<Map<String, Object>> lexiconStats() {
        Map<String, Object> data = new HashMap<>();
        data.put("total", lexiconMapper.selectCount(null));
        Map<String, Long> byEmotion = new LinkedHashMap<>();
        for (int i = 1; i <= 9; i++) {
            byEmotion.put(EmotionConst.NAMES[i], lexiconMapper.selectCount(
                    new LambdaQueryWrapper<EmotionLexicon>().eq(EmotionLexicon::getEmotion, i)));
        }
        data.put("byEmotion", byEmotion);
        data.put("sentiment", lexiconMapper.selectCount(new LambdaQueryWrapper<EmotionLexicon>().eq(EmotionLexicon::getWordType, 0)));
        data.put("negation", lexiconMapper.selectCount(new LambdaQueryWrapper<EmotionLexicon>().eq(EmotionLexicon::getWordType, 1)));
        data.put("degree", lexiconMapper.selectCount(new LambdaQueryWrapper<EmotionLexicon>().eq(EmotionLexicon::getWordType, 2)));
        return Result.success(data);
    }

    /** 词典分页查询 */
    @GetMapping("/lexicon/list")
    public Result<Map<String, Object>> lexiconList(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) Integer emotion,
                                                   @RequestParam(required = false) Integer wordType) {
        LambdaQueryWrapper<EmotionLexicon> q = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) q.like(EmotionLexicon::getWord, keyword.trim());
        if (emotion != null && emotion > 0) q.eq(EmotionLexicon::getEmotion, emotion);
        if (wordType != null && wordType >= 0) q.eq(EmotionLexicon::getWordType, wordType);
        q.orderByAsc(EmotionLexicon::getEmotion).orderByAsc(EmotionLexicon::getLexiconId);

        Page<EmotionLexicon> p = lexiconMapper.selectPage(new Page<>(PageLimit.page(page), PageLimit.of(size)), q);
        List<Map<String, Object>> list = new ArrayList<>();
        for (EmotionLexicon l : p.getRecords()) {
            int wt = l.getWordType() == null ? 0 : l.getWordType();
            String wtName = switch (wt) { case 1 -> "否定词"; case 2 -> "程度副词"; default -> "情感词"; };
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lexiconId", l.getLexiconId());
            m.put("word", l.getWord());
            m.put("emotion", l.getEmotion());
            // emotion=0 是否定词/程度副词等功能词，本就不属于九类情绪，显示「未知」会让人误以为数据有问题
            m.put("emotionName", (l.getEmotion() == null || l.getEmotion() == 0) ? "—" : EmotionConst.nameOf(l.getEmotion()));
            m.put("weight", l.getWeight());
            m.put("wordType", l.getWordType());
            m.put("wordTypeName", wtName);
            m.put("sourceTag", l.getSourceTag());
            // 来源标记翻译成人话：manual=内置种子词 / admin=后台添加 / admin-import=批量导入
            m.put("sourceName", switch (String.valueOf(l.getSourceTag())) {
                case "manual" -> "内置词库"; case "admin" -> "后台添加"; case "admin-import" -> "批量导入";
                default -> String.valueOf(l.getSourceTag());
            });
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", p.getTotal());
        return Result.success(data);
    }

    /** 新增 / 修改词条（有 lexiconId 即更新） */
    @PostMapping("/lexicon/save")
    public Result<Boolean> lexiconSave(@RequestBody EmotionLexicon lex) {
        if (lex == null || lex.getWord() == null || lex.getWord().isBlank()) {
            return Result.error(400, "词条不能为空");
        }
        if (lex.getEmotion() == null || lex.getEmotion() < 1 || lex.getEmotion() > 9) {
            return Result.error(400, "情绪类别必须在 1~9 之间");
        }
        if (lex.getWordType() == null) lex.setWordType(0);
        if (lex.getWeight() == null) lex.setWeight(new BigDecimal("1.0"));
        if (lex.getSourceTag() == null || lex.getSourceTag().isBlank()) lex.setSourceTag("admin");
        lex.setWord(lex.getWord().trim());

        if (lex.getLexiconId() != null) {
            lexiconMapper.updateById(lex);
        } else {
            // 同词同类型视为重复，直接更新权重，避免词表膨胀
            EmotionLexicon exist = lexiconMapper.selectOne(new LambdaQueryWrapper<EmotionLexicon>()
                    .eq(EmotionLexicon::getWord, lex.getWord())
                    .eq(EmotionLexicon::getWordType, lex.getWordType()).last("LIMIT 1"));
            if (exist != null) {
                exist.setEmotion(lex.getEmotion());
                exist.setWeight(lex.getWeight());
                lexiconMapper.updateById(exist);
            } else {
                lexiconMapper.insert(lex);
            }
        }
        senseService.reloadLexicon();
        return Result.success(true);
    }

    /** 删除词条 */
    @DeleteMapping("/lexicon/{id:\\d+}")
    public Result<Boolean> lexiconDelete(@PathVariable Long id) {
        lexiconMapper.deleteById(id);
        senseService.reloadLexicon();
        return Result.success(true);
    }

    /** 批量导入：每行一条，支持「词,情绪,权重,类型」或「词 情绪」，# 开头为注释 */
    @PostMapping("/lexicon/import")
    public Result<Map<String, Object>> lexiconImport(@RequestBody Map<String, Object> body) {
        String text = body == null ? null : (String) body.get("text");
        if (text == null || text.isBlank()) return Result.error(400, "导入内容为空");
        int added = 0, updated = 0, skipped = 0;
        for (String rawLine : text.split("\r?\n")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("[,，\\s]+");
            if (parts.length < 2) { skipped++; continue; }
            String word = parts[0];
            int emotion;
            try {
                emotion = Integer.parseInt(parts[1]);
            } catch (Exception e) { skipped++; continue; }
            if (emotion < 1 || emotion > 9) { skipped++; continue; }
            double weight = 1.0;
            if (parts.length >= 3) {
                try { weight = Double.parseDouble(parts[2]); } catch (Exception ignored) { log.warn("可忽略的异常，已跳过: {}", ignored.toString()); }
            }
            int type = 0;
            if (parts.length >= 4) {
                try { type = Integer.parseInt(parts[3]); } catch (Exception ignored) { log.warn("可忽略的异常，已跳过: {}", ignored.toString()); }
            }
            EmotionLexicon exist = lexiconMapper.selectOne(new LambdaQueryWrapper<EmotionLexicon>()
                    .eq(EmotionLexicon::getWord, word).eq(EmotionLexicon::getWordType, type).last("LIMIT 1"));
            if (exist != null) {
                exist.setEmotion(emotion);
                exist.setWeight(BigDecimal.valueOf(weight));
                lexiconMapper.updateById(exist);
                updated++;
            } else {
                EmotionLexicon l = new EmotionLexicon();
                l.setWord(word);
                l.setEmotion(emotion);
                l.setWeight(BigDecimal.valueOf(weight));
                l.setWordType(type);
                l.setSourceTag("admin-import");
                lexiconMapper.insert(l);
                added++;
            }
        }
        senseService.reloadLexicon();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("added", added);
        res.put("updated", updated);
        res.put("skipped", skipped);
        res.put("total", senseService.lexiconSize());
        return Result.success(res);
    }

    // ==================================================================
    // 三、预警词库管理（危机词 / 预警词 / 例外短语）
    // ==================================================================

    /**
     * 词库落库方案：sys_config 三个键（alert.crisisWords / alert.warningWords / alert.crisisExcludes），
     * 逗号分隔。改的时候「内存 RiskProperties 单例立即生效 + 写库持久化」，
     * 与系统参数页同一套机制 —— 改完不重启，下一次日记保存就用新词库判定。
     */

    /** 词库列表 */
    @GetMapping("/alert/words")
    public Result<Map<String, Object>> alertWords() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("crisis", riskProps.getCrisisWords());
        d.put("warning", riskProps.getWarningWords());
        d.put("excludes", riskProps.getCrisisExcludes());
        return Result.success(d);
    }

    /** 新增词条 */
    @PostMapping("/alert/words")
    public Result<Boolean> alertWordAdd(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String type = body.getOrDefault("type", "");
        String word = body.getOrDefault("word", "").trim();
        String err = checkWord(type, word, WORD_TYPES);
        if (err != null) return Result.error(400, err);
        List<String> words = new ArrayList<>(wordsOf(type));
        if (words.contains(word)) return Result.error(400, "该词条已存在");
        words.add(word);
        persistWords(type, words, request);
        return Result.success(true);
    }

    /** 删除词条。用 POST + body 而不是 DELETE + 路径：词条是中文，拼进 URL 要转义，body 更干净 */
    @PostMapping("/alert/words/delete")
    public Result<Boolean> alertWordDelete(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String type = body.getOrDefault("type", "");
        String word = body.getOrDefault("word", "").trim();
        String err = checkWord(type, word, WORD_TYPES);
        if (err != null) return Result.error(400, err);
        List<String> words = new ArrayList<>(wordsOf(type));
        if (!words.contains(word)) return Result.error(400, "词条不存在，可能已被删除");
        words.remove(word);
        persistWords(type, words, request);
        return Result.success(true);
    }

    private static final List<String> WORD_TYPES = List.of("crisis", "warning", "excludes");

    private List<String> wordsOf(String type) {
        return switch (type) {
            case "crisis" -> riskProps.getCrisisWords();
            case "warning" -> riskProps.getWarningWords();
            case "excludes" -> riskProps.getCrisisExcludes();
            default -> null;
        };
    }

    /** 词条合法性校验：词库用逗号序列化，词条本身绝不能带逗号；长度限制防止把说明文字当词条贴进来 */
    private String checkWord(String type, String word, List<String> allowedTypes) {
        if (!allowedTypes.contains(type)) return "未知词库类型";
        if (word == null || word.isBlank()) return "词条不能为空";
        if (word.length() > 20) return "词条过长（最多 20 字）";
        if (word.contains(",") || word.contains("，")) return "词条不能包含逗号";
        return null;
    }

    /** 写内存（下一次判定立即生效）+ 写 sys_config（重启后仍生效），操作人记管理员 userId */
    private void persistWords(String type, List<String> words, HttpServletRequest request) {
        String key = switch (type) {
            case "crisis" -> "alert.crisisWords";
            case "warning" -> "alert.warningWords";
            default -> "alert.crisisExcludes";
        };
        Object uid = request.getAttribute("userId");
        settingsService.save(Map.of(key, String.join(",", words)), uid == null ? "system" : String.valueOf(uid));
    }

    // ==================================================================
    // 三点五、评论自动审核设置（可疑词库 + 总开关）
    // ==================================================================

    /**
     * 与预警词库同一套 sys_config 机制：键 comment.reviewWords（逗号分隔）+
     * comment.autoReview（开关）。改完立即生效，无需重启。
     * 机器审核只做「可疑 → 转人工」，不直接拒绝 —— 最终放行与否仍由管理员判断。
     */
    private static final List<String> COMMENT_WORD_TYPES = List.of("review");

    /** 评论审核设置：开关 + 可疑词库 */
    @GetMapping("/comment/words")
    public Result<Map<String, Object>> commentWords() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("autoReview", commentProps.isAutoReview());
        d.put("review", commentProps.getReviewWords());
        return Result.success(d);
    }

    /** 新增可疑词条（命中即转人工） */
    @PostMapping("/comment/words")
    public Result<Boolean> commentWordAdd(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String word = body.getOrDefault("word", "").trim();
        String err = checkWord("review", word, COMMENT_WORD_TYPES);
        if (err != null) return Result.error(400, err);
        List<String> words = new ArrayList<>(commentProps.getReviewWords());
        if (words.contains(word)) return Result.error(400, "该词条已存在");
        words.add(word);
        Object uid = request.getAttribute("userId");
        settingsService.save(Map.of("comment.reviewWords", String.join(",", words)),
                uid == null ? "system" : String.valueOf(uid));
        return Result.success(true);
    }

    /** 删除可疑词条（body 传词，避免中文拼 URL 转义） */
    @PostMapping("/comment/words/delete")
    public Result<Boolean> commentWordDelete(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String word = body.getOrDefault("word", "").trim();
        String err = checkWord("review", word, COMMENT_WORD_TYPES);
        if (err != null) return Result.error(400, err);
        List<String> words = new ArrayList<>(commentProps.getReviewWords());
        if (!words.contains(word)) return Result.error(400, "词条不存在，可能已被删除");
        words.remove(word);
        Object uid = request.getAttribute("userId");
        settingsService.save(Map.of("comment.reviewWords", String.join(",", words)),
                uid == null ? "system" : String.valueOf(uid));
        return Result.success(true);
    }

    // ==================================================================
    // 四、系统参数
    // ==================================================================

    @GetMapping("/settings")
    public Result<Map<String, Object>> settingsGet() {
        Map<String, Object> data = new HashMap<>();
        data.put("items", settingsService.list());
        data.put("disclaimer", settingsService.disclaimer());
        return Result.success(data);
    }

    @PutMapping("/settings")
    public Result<Boolean> settingsSave(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        settingsService.save(body, uid == null ? "system" : String.valueOf(uid));
        return Result.success(true);
    }

    /** 恢复默认：group 为空表示全部 */
    @PostMapping("/settings/reset")
    public Result<Boolean> settingsReset(@RequestParam(required = false) String group, HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        settingsService.reset(group, uid == null ? "system" : String.valueOf(uid));
        return Result.success(true);
    }

    // ==================================================================
    // 五、AI 对话记录
    // ==================================================================

    /** AI 对话总览：消息数、会话数、活跃用户、近 7 天趋势 */
    @GetMapping("/aiChat/stats")
    public Result<Map<String, Object>> aiChatStats() {
        Map<String, Object> data = new HashMap<>();
        List<AiChat> all = aiChatMapper.selectList(null);
        data.put("messages", all.size());
        Set<String> sessions = new HashSet<>();
        Set<Long> users = new HashSet<>();
        for (AiChat c : all) {
            if (c.getSessionId() != null) sessions.add(c.getSessionId());
            if (c.getUserId() != null) users.add(c.getUserId());
        }
        data.put("sessions", sessions.size());
        data.put("users", users.size());
        data.put("avgPerSession", sessions.isEmpty() ? 0 : round2(all.size() * 1.0 / sessions.size()));

        Map<String, Long> acc = new TreeMap<>();
        for (int i = 6; i >= 0; i--) acc.put(LocalDate.now().minusDays(i).toString(), 0L);
        for (AiChat c : all) {
            if (c.getCreateTime() == null) continue;
            String d = c.getCreateTime().toLocalDate().toString();
            if (acc.containsKey(d)) acc.put(d, acc.get(d) + 1);
        }
        List<Map<String, Object>> trend = new ArrayList<>();
        acc.forEach((d, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d); m.put("count", v);
            trend.add(m);
        });
        data.put("trend", trend);
        return Result.success(data);
    }

    /** 按用户聚合的对话列表 */
    @GetMapping("/aiChat/users")
    public Result<List<Map<String, Object>>> aiChatUsers(@RequestParam(required = false) String keyword) {
        Map<Long, String> names = nameMap();
        List<AiChat> all = aiChatMapper.selectList(new LambdaQueryWrapper<AiChat>().orderByDesc(AiChat::getChatId));
        Map<Long, int[]> agg = new LinkedHashMap<>();   // userId -> [消息数]
        Map<Long, Set<String>> userSessions = new HashMap<>();
        Map<Long, LocalDateTime> lastAt = new HashMap<>();
        for (AiChat c : all) {
            if (c.getUserId() == null) continue;
            int[] a = agg.computeIfAbsent(c.getUserId(), k -> new int[2]);
            a[0]++;
            userSessions.computeIfAbsent(c.getUserId(), k -> new HashSet<>());
            if (c.getSessionId() != null) userSessions.get(c.getUserId()).add(c.getSessionId());
            if (c.getCreateTime() != null) {
                LocalDateTime old = lastAt.get(c.getUserId());
                if (old == null || c.getCreateTime().isAfter(old)) lastAt.put(c.getUserId(), c.getCreateTime());
            }
        }
        List<Map<String, Object>> list = new ArrayList<>();
        agg.forEach((uid, a) -> {
            String name = names.getOrDefault(uid, "用户#" + uid);
            if (keyword != null && !keyword.isBlank() && !name.contains(keyword.trim())) return;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", uid);
            m.put("userName", name);
            m.put("messages", a[0]);
            m.put("sessions", userSessions.getOrDefault(uid, Collections.emptySet()).size());
            m.put("lastTime", lastAt.get(uid));
            list.add(m);
        });
        list.sort((x, y) -> {
            LocalDateTime a = (LocalDateTime) x.get("lastTime");
            LocalDateTime b = (LocalDateTime) y.get("lastTime");
            if (a == null && b == null) return 0;
            if (a == null) return 1;
            if (b == null) return -1;
            return b.compareTo(a);
        });
        return Result.success(list);
    }

    /** 某用户的会话列表 */
    @GetMapping("/aiChat/sessions")
    public Result<List<Map<String, Object>>> aiChatSessions(@RequestParam Long userId) {
        List<AiChat> list = aiChatMapper.selectList(new LambdaQueryWrapper<AiChat>()
                .eq(AiChat::getUserId, userId).orderByAsc(AiChat::getChatId));
        Map<String, List<AiChat>> grouped = new LinkedHashMap<>();
        for (AiChat c : list) {
            if (c.getSessionId() == null) continue;
            grouped.computeIfAbsent(c.getSessionId(), k -> new ArrayList<>()).add(c);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        grouped.forEach((sid, msgs) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sessionId", sid);
            m.put("count", msgs.size());
            m.put("startTime", msgs.get(0).getCreateTime());
            m.put("endTime", msgs.get(msgs.size() - 1).getCreateTime());
            m.put("preview", msgs.get(0).getUserMsg());
            out.add(m);
        });
        out.sort((a, b) -> {
            LocalDateTime x = (LocalDateTime) a.get("startTime");
            LocalDateTime y = (LocalDateTime) b.get("startTime");
            if (x == null || y == null) return 0;
            return y.compareTo(x);
        });
        return Result.success(out);
    }

    /** 某个会话的消息明细（内容安全审计用） */
    @GetMapping("/aiChat/messages")
    public Result<List<Map<String, Object>>> aiChatMessages(@RequestParam String sessionId) {
        List<AiChat> list = aiChatMapper.selectList(new LambdaQueryWrapper<AiChat>()
                .eq(AiChat::getSessionId, sessionId).orderByAsc(AiChat::getChatId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiChat c : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("chatId", c.getChatId());
            m.put("userMsg", c.getUserMsg());
            m.put("aiReply", c.getAiReply());
            m.put("createTime", c.getCreateTime());
            out.add(m);
        }
        return Result.success(out);
    }

    // ==================================================================
    // 六、测评答题记录
    // ==================================================================

    @GetMapping("/record/stats")
    public Result<Map<String, Object>> recordStats() {
        Map<String, Object> data = new HashMap<>();
        List<PsyRecord> all = recordMapper.selectList(null);
        data.put("total", all.size());
        data.put("users", all.stream().map(PsyRecord::getUserId).filter(Objects::nonNull).distinct().count());
        data.put("avgScore", all.stream().filter(r -> r.getScore() != null)
                .mapToInt(PsyRecord::getScore).average().orElse(0));

        Map<String, String> paperNames = new HashMap<>();
        try {
            testPaperService.list().forEach(t -> paperNames.put(String.valueOf(t.getTestId()), t.getTestName()));
        } catch (Exception ignored) { log.warn("可忽略的异常，已跳过: {}", ignored.toString()); }

        Map<String, Long> byPaper = new LinkedHashMap<>();
        for (PsyRecord r : all) {
            String name = paperNames.getOrDefault(String.valueOf(r.getTestId()), "量表#" + r.getTestId());
            byPaper.put(name, byPaper.getOrDefault(name, 0L) + 1);
        }
        // 只保留 TOP8，避免图表过于拥挤
        List<Map<String, Object>> dist = new ArrayList<>();
        byPaper.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(8)
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("value", e.getValue());
                    dist.add(m);
                });
        data.put("byPaper", dist);

        Map<String, Long> byLevel = new LinkedHashMap<>();
        for (PsyRecord r : all) {
            String lv = (r.getLevel() == null || r.getLevel().isBlank()) ? "未分级" : r.getLevel();
            byLevel.put(lv, byLevel.getOrDefault(lv, 0L) + 1);
        }
        data.put("byLevel", byLevel);
        return Result.success(data);
    }

    @GetMapping("/record/list")
    public Result<Map<String, Object>> recordList(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) Long testId,
                                                   @RequestParam(required = false) String keyword) {
        Map<Long, String> names = nameMap();
        Map<Long, String> paperNames = new HashMap<>();
        try {
            testPaperService.list().forEach(t -> paperNames.put(t.getTestId(), t.getTestName()));
        } catch (Exception ignored) { log.warn("可忽略的异常，已跳过: {}", ignored.toString()); }

        LambdaQueryWrapper<PsyRecord> q = new LambdaQueryWrapper<>();
        if (testId != null) q.eq(PsyRecord::getTestId, testId);
        if (keyword != null && !keyword.isBlank()) {
            Set<Long> ids = new HashSet<>();
            names.forEach((id, n) -> { if (n.contains(keyword.trim())) ids.add(id); });
            q.in(!ids.isEmpty(), PsyRecord::getUserId, ids);
            if (ids.isEmpty()) {
                Map<String, Object> empty = new HashMap<>();
                empty.put("list", new ArrayList<>());
                empty.put("total", 0L);
                return Result.success(empty);
            }
        }
        q.orderByDesc(PsyRecord::getCreateTime);

        Page<PsyRecord> p = recordMapper.selectPage(new Page<>(PageLimit.page(page), PageLimit.of(size)), q);
        List<Map<String, Object>> list = new ArrayList<>();
        for (PsyRecord r : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("recordId", r.getRecordId());
            m.put("userId", r.getUserId());
            m.put("userName", names.getOrDefault(r.getUserId(), "用户#" + r.getUserId()));
            m.put("testId", r.getTestId());
            m.put("testName", paperNames.getOrDefault(r.getTestId(), "量表#" + r.getTestId()));
            m.put("score", r.getScore());
            m.put("totalScore", r.getTotalScore());
            m.put("level", r.getLevel());
            m.put("tip", r.getTip());
            m.put("createTime", r.getCreateTime());
            list.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("list", list);
        data.put("total", p.getTotal());
        return Result.success(data);
    }

    // ==================================================================
    // 七、账号安全
    // ==================================================================

    @GetMapping("/security/overview")
    public Result<Map<String, Object>> securityOverview() {
        Map<String, Object> data = new HashMap<>();
        try {
            data.put("onlineSessions", sessionMapper.selectCount(new LambdaQueryWrapper<SysUserSession>().eq(SysUserSession::getStatus, 1)));
            data.put("totalSessions", sessionMapper.selectCount(null));
            data.put("locked", attemptMapper.selectCount(new LambdaQueryWrapper<SysLoginAttempt>()
                    .isNotNull(SysLoginAttempt::getLockUntil)));
            data.put("pendingResets", pwdResetMapper.selectCount(new LambdaQueryWrapper<SysPwdReset>().eq(SysPwdReset::getUsed, 0)));
            data.put("usedResets", pwdResetMapper.selectCount(new LambdaQueryWrapper<SysPwdReset>().eq(SysPwdReset::getUsed, 1)));
        } catch (Exception e) {
            data.put("error", "安全概览统计失败：" + e.getMessage());
        }
        return Result.success(data);
    }

    /** 在线会话（token 脱敏） */
    @GetMapping("/security/sessions")
    public Result<List<Map<String, Object>>> securitySessions(@RequestParam(required = false) Integer status) {
        Map<Long, String> names = nameMap();
        LambdaQueryWrapper<SysUserSession> q = new LambdaQueryWrapper<>();
        if (status != null && status >= 0) q.eq(SysUserSession::getStatus, status);
        q.orderByDesc(SysUserSession::getLastActiveTime);
        List<SysUserSession> list = sessionMapper.selectList(q);
        List<Map<String, Object>> out = new ArrayList<>();
        for (SysUserSession s : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("sessionId", s.getSessionId());
            m.put("userId", s.getUserId());
            m.put("userName", names.getOrDefault(s.getUserId(), "用户#" + s.getUserId()));
            m.put("device", s.getDevice());
            m.put("ip", s.getIp());
            m.put("userAgent", s.getUserAgent());
            m.put("status", s.getStatus());
            m.put("loginTime", s.getLoginTime());
            m.put("lastActiveTime", s.getLastActiveTime());
            m.put("tokenMasked", maskToken(s.getToken()));
            out.add(m);
        }
        return Result.success(out);
    }

    /** 强制下线 */
    @PostMapping("/security/kick/{id:\\d+}")
    public Result<Boolean> kickSession(@PathVariable Long id) {
        SysUserSession s = sessionMapper.selectById(id);
        if (s == null) return Result.error(404, "会话不存在");
        s.setStatus(0);
        sessionMapper.updateById(s);
        return Result.success(true);
    }

    /** 登录失败锁定记录 */
    @GetMapping("/security/attempts")
    public Result<List<Map<String, Object>>> securityAttempts() {
        List<SysLoginAttempt> list = attemptMapper.selectList(
                new LambdaQueryWrapper<SysLoginAttempt>().orderByDesc(SysLoginAttempt::getUpdateTime));
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (SysLoginAttempt a : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("username", a.getUsername());
            m.put("failCount", a.getFailCount());
            m.put("lockUntil", a.getLockUntil());
            m.put("updateTime", a.getUpdateTime());
            m.put("locked", a.getLockUntil() != null && now.isBefore(a.getLockUntil()));
            out.add(m);
        }
        return Result.success(out);
    }

    /** 解锁：清掉失败记录 */
    @PostMapping("/security/unlock/{id:\\d+}")
    public Result<Boolean> unlock(@PathVariable Long id) {
        attemptMapper.deleteById(id);
        return Result.success(true);
    }

    /** 密码找回申请（不返回验证码） */
    @GetMapping("/security/resets")
    public Result<List<Map<String, Object>>> securityResets() {
        Map<Long, String> names = nameMap();
        List<SysPwdReset> list = pwdResetMapper.selectList(
                new LambdaQueryWrapper<SysPwdReset>().orderByDesc(SysPwdReset::getCreateTime));
        List<Map<String, Object>> out = new ArrayList<>();
        for (SysPwdReset r : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("userId", r.getUserId());
            m.put("userName", names.getOrDefault(r.getUserId(), "用户#" + r.getUserId()));
            m.put("expireTime", r.getExpireTime());
            m.put("used", r.getUsed());
            m.put("expired", r.getExpireTime() != null && LocalDateTime.now().isAfter(r.getExpireTime()));
            m.put("createTime", r.getCreateTime());
            out.add(m);
        }
        return Result.success(out);
    }

    /** 删除一条找回申请（通常是用户找回成功后清理） */
    @DeleteMapping("/security/reset/{id:\\d+}")
    public Result<Boolean> deleteReset(@PathVariable Long id) {
        pwdResetMapper.deleteById(id);
        return Result.success(true);
    }
}
