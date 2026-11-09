package com.seeyou.see_you.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.seeyou.see_you.algorithm.EmotionConst;
import com.seeyou.see_you.config.AdminWrite;
import com.seeyou.see_you.entity.*;
import com.seeyou.see_you.mapper.*;
import com.seeyou.see_you.util.PageLimit;
import com.seeyou.see_you.util.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import com.seeyou.see_you.config.RequireAdmin;

/**
 * 后台第二批扩展接口：隐私审计 / 用户 360 画像 / 互动与内容消费 /
 * 操作日志 / 首页轮播公告。
 *
 * <h3>权限</h3>
 * 路径以 /admin/ 开头，JwtInterceptor 已做分级校验：
 * role=1 可读写，role=2（只读管理员）仅可读 GET，写操作返回 403。
 *
 * <h3>隐私红线（务必保持）</h3>
 * <ul>
 *   <li>隐私审计页<strong>只展示动作类型与时间</strong>，detail 超长一律截断，
 *       绝不展示任何日记正文、随笔原文或可反推内容的信息；</li>
 *   <li>用户 360 画像不返回日记正文，只返回统计值与情绪分布；</li>
 *   <li>IP 沿用建表时的脱敏值（末段置 0），本层不做还原。</li>
 * </ul>
 */
@RequireAdmin
@RestController
@RequestMapping("/admin")
@lombok.extern.slf4j.Slf4j
public class AdminOpsController {

    @Resource private SysUserMapper userMapper;
    @Resource private SensePrivacyLogMapper privacyLogMapper;
    @Resource private PsyDiaryMapper diaryMapper;
    @Resource private PsyRecordMapper recordMapper;
    @Resource private PsyTestPaperMapper testPaperMapper;
    @Resource private RiskAlertMapper riskAlertMapper;
    @Resource private EmotionPredictionMapper predictionMapper;
    @Resource private SysUserSessionMapper sessionMapper;
    @Resource private SysLoginAttemptMapper attemptMapper;
    @Resource private UserLikeMapper likeMapper;
    @Resource private UserCollectMapper collectMapper;
    @Resource private PsyHistoryMapper historyMapper;
    @Resource private PsyVideoMapper videoMapper;
    @Resource private PsyAudioMapper audioMapper;
    @Resource private SysLogMapper sysLogMapper;
    @Resource private PsyBannerMapper bannerMapper;

    /** 隐私动作中文名 */
    private static final Map<String, String> ACTION_NAME = new LinkedHashMap<>();
    static {
        ACTION_NAME.put("GRANT", "开启无感记录");
        ACTION_NAME.put("REVOKE", "关闭无感记录");
        ACTION_NAME.put("PREDICT", "生成情绪推测");
        ACTION_NAME.put("CONFIRM", "确认推测结果");
        ACTION_NAME.put("REJECT", "拒绝推测结果");
        ACTION_NAME.put("EXPORT", "导出本人数据");
        ACTION_NAME.put("DELETE", "删除本人数据");
        ACTION_NAME.put("VIEW", "查看本人推测");
        // 以下四种由 SenseServiceImpl.log 写入，之前漏配导致后台显示英文枚举值
        ACTION_NAME.put("GENERATE", "生成推测");
        ACTION_NAME.put("AUTO_FILL", "自动代记");
        ACTION_NAME.put("SWITCH", "开关变更");
        ACTION_NAME.put("DELETE_ALL", "清空行为数据");
    }

    private Map<Long, String> nameMap() {
        Map<Long, String> map = new HashMap<>();
        try {
            userMapper.selectList(null).forEach(u -> map.put(u.getUserId(),
                    (u.getNickname() != null && !u.getNickname().isBlank()) ? u.getNickname() : u.getUsername()));
        } catch (Exception ignored) { log.warn("可忽略的异常，已跳过: {}", ignored.toString()); }
        return map;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** detail 超过阈值就截断——防止任何长文本（含特征权重 JSON）被完整展示 */
    private static String safeDetail(String d) {
        if (d == null || d.isBlank()) return "—";
        String s = d.trim();
        return s.length() > 120 ? s.substring(0, 120) + "…" : s;
    }

    private static List<String> last14Days() {
        List<String> days = new ArrayList<>();
        LocalDate d = LocalDate.now().minusDays(13);
        for (int i = 0; i < 14; i++) {
            days.add(d.plusDays(i).toString());
        }
        return days;
    }

    /** 按日期补齐成完整序列，缺的填 0 */
    private static List<Map<String, Object>> fillSeries(List<String> days, Map<String, Long> counts) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String d : days) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d);
            m.put("count", counts.getOrDefault(d, 0L));
            list.add(m);
        }
        return list;
    }

    // ==================================================================
    // 一、隐私审计日志
    // ==================================================================

    /** 隐私审计统计：总量 / 今日 / 涉及用户 / 动作分布 / 近 14 天趋势 */
    @GetMapping("/privacy/stats")
    public Result<Map<String, Object>> privacyStats() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("total", privacyLogMapper.selectCount(null));
            LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
            data.put("today", privacyLogMapper.selectCount(
                    new LambdaQueryWrapper<SensePrivacyLog>().ge(SensePrivacyLog::getCreateTime, todayStart)));
            data.put("grant", privacyLogMapper.selectCount(
                    new LambdaQueryWrapper<SensePrivacyLog>().eq(SensePrivacyLog::getAction, "GRANT")));
            data.put("revoke", privacyLogMapper.selectCount(
                    new LambdaQueryWrapper<SensePrivacyLog>().eq(SensePrivacyLog::getAction, "REVOKE")));

            List<SensePrivacyLog> all = privacyLogMapper.selectList(null);
            Set<Long> users = new HashSet<>();
            Map<String, Long> actionCount = new LinkedHashMap<>();
            Map<String, Long> dayCount = new LinkedHashMap<>();
            Map<Long, Long> userCount = new HashMap<>();
            for (SensePrivacyLog l : all) {
                users.add(l.getUserId());
                String act = l.getAction() == null ? "OTHER" : l.getAction();
                actionCount.merge(act, 1L, Long::sum);
                userCount.merge(l.getUserId(), 1L, Long::sum);
                if (l.getCreateTime() != null) {
                    dayCount.merge(l.getCreateTime().toLocalDate().toString(), 1L, Long::sum);
                }
            }
            data.put("userCount", users.size());
            // 开启后又有过撤回的用户数：说明用户真的能「随时收回授权」
            data.put("revokeUserCount", countRevokeUsers(all));

            Map<Long, String> nm = nameMap();
            List<Map<String, Object>> actionDist = new ArrayList<>();
            actionCount.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .forEach(e -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("action", e.getKey());
                        m.put("name", ACTION_NAME.getOrDefault(e.getKey(), e.getKey()));
                        m.put("value", e.getValue());
                        actionDist.add(m);
                    });
            data.put("actionDist", actionDist);

            List<Map<String, Object>> topUsers = new ArrayList<>();
            userCount.entrySet().stream()
                    .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .forEach(e -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("userId", e.getKey());
                        m.put("name", nm.getOrDefault(e.getKey(), "用户" + e.getKey()));
                        m.put("count", e.getValue());
                        topUsers.add(m);
                    });
            data.put("topUsers", topUsers);
            data.put("trend", fillSeries(last14Days(), dayCount));
            data.put("note",
                    "本页只记录「用户对本人无感数据做了什么操作」，不含日记正文、随笔原文与行为明细，"
                            + "IP 为脱敏值。它是系统未滥用用户数据的可验证凭据。");
        } catch (Exception e) {
            data.put("error", e.getMessage());
        }
        return Result.success(data);
    }

    /** 既开启过也撤回过的用户数 */
    private long countRevokeUsers(List<SensePrivacyLog> logs) {
        Map<Long, Set<String>> m = new HashMap<>();
        for (SensePrivacyLog l : logs) {
            m.computeIfAbsent(l.getUserId(), k -> new HashSet<>()).add(String.valueOf(l.getAction()));
        }
        return m.values().stream().filter(s -> s.contains("GRANT") && s.contains("REVOKE")).count();
    }

    /** 隐私审计日志列表（支持按动作、用户筛选） */
    @GetMapping("/privacy/list")
    public Result<Map<String, Object>> privacyList(@RequestParam(defaultValue = "1") long page,
                                                   @RequestParam(defaultValue = "20") long size,
                                                   @RequestParam(required = false) String action,
                                                   @RequestParam(required = false) Long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        LambdaQueryWrapper<SensePrivacyLog> w = new LambdaQueryWrapper<>();
        if (action != null && !action.isBlank()) w.eq(SensePrivacyLog::getAction, action);
        if (userId != null) w.eq(SensePrivacyLog::getUserId, userId);
        w.orderByDesc(SensePrivacyLog::getCreateTime);

        Page<SensePrivacyLog> p = privacyLogMapper.selectPage(new Page<>(PageLimit.page(page), PageLimit.of(size)), w);
        Map<Long, String> nm = nameMap();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SensePrivacyLog l : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("logId", l.getLogId());
            m.put("userId", l.getUserId());
            m.put("userName", nm.getOrDefault(l.getUserId(), "用户" + l.getUserId()));
            m.put("action", l.getAction());
            m.put("actionName", ACTION_NAME.getOrDefault(String.valueOf(l.getAction()), String.valueOf(l.getAction())));
            m.put("detail", safeDetail(l.getDetail()));
            m.put("ipMasked", l.getIpMasked() == null || l.getIpMasked().isBlank() ? "—" : l.getIpMasked());
            m.put("createTime", l.getCreateTime());
            rows.add(m);
        }
        data.put("total", p.getTotal());
        data.put("rows", rows);
        return Result.success(data);
    }

    // ==================================================================
    // 二、用户 360 画像
    // ==================================================================

    /** 单个用户的完整画像：日记 / 测评 / 预警 / 无感 / 安全 / 互动 六维汇总 */
    @GetMapping("/user/{id:\\d+}/profile")
    public Result<Map<String, Object>> userProfile(@PathVariable Long id) {
        Map<String, Object> data = new LinkedHashMap<>();
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            return Result.error(404, "用户不存在");
        }

        Map<String, Object> base = new LinkedHashMap<>();
        base.put("userId", u.getUserId());
        base.put("username", u.getUsername());
        base.put("nickname", u.getNickname());
        base.put("avatar", u.getAvatar());
        base.put("bio", u.getBio());
        base.put("role", u.getRole());
        base.put("roleName", Integer.valueOf(1).equals(u.getRole()) ? "管理员"
                : Integer.valueOf(2).equals(u.getRole()) ? "只读管理员" : "普通用户");
        base.put("status", u.getStatus());
        base.put("createTime", u.getCreateTime());
        data.put("user", base);

        // —— 日记维度
        Map<String, Object> diary = new LinkedHashMap<>();
        List<PsyDiary> diaries = diaryMapper.selectList(
                new LambdaQueryWrapper<PsyDiary>().eq(PsyDiary::getUserId, id));
        diary.put("total", diaries.size());
        diary.put("manual", diaries.stream().filter(d -> Integer.valueOf(1).equals(d.getSource())).count());
        diary.put("inferred", diaries.stream().filter(d -> !Integer.valueOf(1).equals(d.getSource())).count());
        diary.put("lastDate", diaries.stream()
                .map(PsyDiary::getCreateTime).filter(Objects::nonNull)
                .max(LocalDateTime::compareTo).map(t -> t.toLocalDate().toString()).orElse("—"));
        Map<Integer, Long> emoCount = diaries.stream()
                .filter(d -> d.getEmotion() != null)
                .collect(Collectors.groupingBy(PsyDiary::getEmotion, Collectors.counting()));
        List<Map<String, Object>> emoDist = new ArrayList<>();
        emoCount.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("emotion", e.getKey());
                    m.put("name", EmotionConst.nameOf(e.getKey()));
                    m.put("value", e.getValue());
                    emoDist.add(m);
                });
        diary.put("emotionDist", emoDist);
        diary.put("avgIndex", diaries.isEmpty() ? 0 : round2(diaries.stream()
                .filter(d -> d.getEmotion() != null)
                .mapToInt(d -> EmotionConst.indexOf(d.getEmotion()))
                .average().orElse(0)));
        data.put("diary", diary);

        // —— 测评维度
        Map<String, Object> test = new LinkedHashMap<>();
        List<PsyRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<PsyRecord>().eq(PsyRecord::getUserId, id)
                        .orderByDesc(PsyRecord::getCreateTime));
        test.put("total", records.size());
        if (!records.isEmpty()) {
            PsyRecord r = records.get(0);
            Map<String, Object> last = new LinkedHashMap<>();
            String paperName = "—";
            try {
                PsyTestPaper tp = testPaperMapper.selectById(r.getTestId());
                if (tp != null && tp.getTestName() != null) paperName = tp.getTestName();
            } catch (Exception ignored) { log.warn("可忽略的异常，已跳过: {}", ignored.toString()); }
            last.put("testId", r.getTestId());
            last.put("paperName", paperName);
            last.put("score", r.getScore() == null ? r.getTotalScore() : r.getScore());
            last.put("level", r.getLevel());
            last.put("date", r.getCreateTime() == null ? "—" : r.getCreateTime().toLocalDate().toString());
            test.put("last", last);
        }
        data.put("test", test);

        // —— 预警维度
        Map<String, Object> alert = new LinkedHashMap<>();
        List<RiskAlert> alerts = riskAlertMapper.selectList(
                new LambdaQueryWrapper<RiskAlert>().eq(RiskAlert::getUserId, id)
                        .orderByDesc(RiskAlert::getCreateTime));
        alert.put("total", alerts.size());
        alert.put("unread", alerts.stream().filter(a -> !Integer.valueOf(1).equals(a.getIsRead())).count());
        alert.put("maxLevel", alerts.stream().map(a -> a.getAlertLevel() == null ? 0 : a.getAlertLevel())
                .max(Integer::compareTo).orElse(0));
        if (!alerts.isEmpty()) {
            RiskAlert a = alerts.get(0);
            Map<String, Object> last = new LinkedHashMap<>();
            last.put("level", a.getAlertLevel());
            last.put("date", a.getAlertDate() == null ? "—" : a.getAlertDate().toString());
            last.put("avgIndex", a.getAvgIndex());
            last.put("maxDowncastDays", a.getMaxDowncastDays());
            alert.put("last", last);
        }
        data.put("alert", alert);

        // —— 无感记录维度
        Map<String, Object> sense = new LinkedHashMap<>();
        sense.put("enabled", u.getSenseEnabled());
        sense.put("behavior", u.getSenseBehavior());
        sense.put("text", u.getSenseText());
        sense.put("grantAt", u.getSenseGrantAt());
        sense.put("revokeAt", u.getSenseRevokeAt());
        sense.put("predictions", predictionMapper.selectCount(
                new LambdaQueryWrapper<EmotionPrediction>().eq(EmotionPrediction::getUserId, id)));
        // confirmStatus：0待确认 1已确认采纳 2已拒绝 3已过期
        sense.put("adopted", predictionMapper.selectCount(
                new LambdaQueryWrapper<EmotionPrediction>().eq(EmotionPrediction::getUserId, id)
                        .eq(EmotionPrediction::getConfirmStatus, 1)));
        data.put("sense", sense);

        // —— 账号安全维度
        Map<String, Object> security = new LinkedHashMap<>();
        List<SysUserSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<SysUserSession>().eq(SysUserSession::getUserId, id)
                        .orderByDesc(SysUserSession::getLoginTime));
        security.put("sessionTotal", sessions.size());
        security.put("activeSession", sessions.stream().filter(s -> Integer.valueOf(1).equals(s.getStatus())).count());
        security.put("lastLogin", sessions.isEmpty() || sessions.get(0).getLoginTime() == null
                ? "—" : sessions.get(0).getLoginTime());
        // 登录失败表按 username 记录（登录时还没拿到 userId），这里用账号匹配
        security.put("loginFail", attemptMapper.selectCount(
                new LambdaQueryWrapper<SysLoginAttempt>().eq(SysLoginAttempt::getUsername, u.getUsername())));
        data.put("security", security);

        // —— 互动维度
        Map<String, Object> inter = new LinkedHashMap<>();
        inter.put("likes", likeMapper.selectCount(new LambdaQueryWrapper<UserLike>().eq(UserLike::getUserId, id)));
        inter.put("collects", collectMapper.selectCount(new LambdaQueryWrapper<UserCollect>().eq(UserCollect::getUserId, id)));
        inter.put("history", historyMapper.selectCount(new LambdaQueryWrapper<PsyHistory>().eq(PsyHistory::getUserId, id)));
        data.put("interaction", inter);

        data.put("privacyNote", "本页面只展示统计指标，不含日记正文与随笔原文。");
        return Result.success(data);
    }

    // ==================================================================
    // 三、互动与内容消费统计
    // ==================================================================

    /** 互动总览：点赞 / 收藏 / 浏览 / 完播率 / 活跃用户 */
    @GetMapping("/interaction/stats")
    public Result<Map<String, Object>> interactionStats() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            List<UserLike> likes = likeMapper.selectList(null);
            List<UserCollect> collects = collectMapper.selectList(null);
            List<PsyHistory> histories = historyMapper.selectList(null);

            data.put("likeTotal", likes.size());
            data.put("collectTotal", collects.size());
            data.put("historyTotal", histories.size());
            long finished = histories.stream().filter(h -> Integer.valueOf(1).equals(h.getIsFinished())).count();
            data.put("finished", finished);
            data.put("finishRate", histories.isEmpty() ? 0 : round2(finished * 100.0 / histories.size()));

            Set<Long> actives = new HashSet<>();
            likes.forEach(l -> actives.add(l.getUserId()));
            collects.forEach(c -> actives.add(c.getUserId()));
            histories.forEach(h -> actives.add(h.getUserId()));
            data.put("activeUsers", actives.size());

            // 音视频占比
            long audioCnt = histories.stream().filter(h -> Integer.valueOf(1).equals(h.getResourceType())).count();
            long videoCnt = histories.stream().filter(h -> Integer.valueOf(2).equals(h.getResourceType())).count();
            data.put("audioPlay", audioCnt);
            data.put("videoPlay", videoCnt);

            // 近 14 天趋势
            Map<String, Long> likeDay = new LinkedHashMap<>();
            Map<String, Long> collectDay = new LinkedHashMap<>();
            likes.forEach(l -> { if (l.getCreateTime() != null) likeDay.merge(l.getCreateTime().toLocalDate().toString(), 1L, Long::sum); });
            collects.forEach(c -> { if (c.getCreateTime() != null) collectDay.merge(c.getCreateTime().toLocalDate().toString(), 1L, Long::sum); });
            List<String> days = last14Days();
            List<Map<String, Object>> trend = new ArrayList<>();
            for (String d : days) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", d);
                m.put("like", likeDay.getOrDefault(d, 0L));
                m.put("collect", collectDay.getOrDefault(d, 0L));
                trend.add(m);
            }
            data.put("trend", trend);
        } catch (Exception e) {
            data.put("error", e.getMessage());
        }
        return Result.success(data);
    }

    /** 热门内容 Top10：按「点赞数 + 收藏数」加权排序 */
    @GetMapping("/interaction/hot")
    public Result<List<Map<String, Object>>> interactionHot(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) Integer resourceType) {
        Map<String, long[]> agg = new LinkedHashMap<>();   // key=type:id -> [likes, collects]
        if (resourceType == null || resourceType == 2) {
            likeMapper.selectList(new LambdaQueryWrapper<UserLike>().eq(UserLike::getResourceType, 2))
                    .forEach(l -> agg.computeIfAbsent("2:" + l.getResourceId(), k -> new long[2])[0]++);
            collectMapper.selectList(new LambdaQueryWrapper<UserCollect>().eq(UserCollect::getResourceType, 2))
                    .forEach(c -> agg.computeIfAbsent("2:" + c.getResourceId(), k -> new long[2])[1]++);
        }
        if (resourceType == null || resourceType == 1) {
            likeMapper.selectList(new LambdaQueryWrapper<UserLike>().eq(UserLike::getResourceType, 1))
                    .forEach(l -> agg.computeIfAbsent("1:" + l.getResourceId(), k -> new long[2])[0]++);
            collectMapper.selectList(new LambdaQueryWrapper<UserCollect>().eq(UserCollect::getResourceType, 1))
                    .forEach(c -> agg.computeIfAbsent("1:" + c.getResourceId(), k -> new long[2])[1]++);
        }

        Map<Long, String> videoName = new HashMap<>();
        Map<Long, String> audioName = new HashMap<>();
        try {
            videoMapper.selectList(null).forEach(v -> videoName.put(v.getVideoId(), v.getVideoName()));
        } catch (Exception ignored) { log.warn("可忽略的异常，已跳过: {}", ignored.toString()); }
        try {
            audioMapper.selectList(null).forEach(a -> audioName.put(a.getAudioId(), a.getAudioName()));
        } catch (Exception ignored) { log.warn("可忽略的异常，已跳过: {}", ignored.toString()); }

        List<Map<String, Object>> list = new ArrayList<>();
        agg.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue()[0] + b.getValue()[1], a.getValue()[0] + a.getValue()[1]))
                .limit(limit)
                .forEach(e -> {
                    String[] kv = e.getKey().split(":");
                    int type = Integer.parseInt(kv[0]);
                    Long rid = Long.valueOf(kv[1]);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("resourceType", type);
                    m.put("resourceId", rid);
                    m.put("typeName", type == 1 ? "音频" : "视频");
                    String title = type == 1 ? audioName.get(rid) : videoName.get(rid);
                    m.put("title", title == null ? "（内容已删除）" : title);
                    m.put("likes", e.getValue()[0]);
                    m.put("collects", e.getValue()[1]);
                    m.put("score", e.getValue()[0] + e.getValue()[1]);
                    list.add(m);
                });
        return Result.success(list);
    }

    /** 浏览/播放记录分页 */
    @GetMapping("/interaction/history")
    public Result<Map<String, Object>> interactionHistory(@RequestParam(defaultValue = "1") long page,
                                                          @RequestParam(defaultValue = "20") long size,
                                                          @RequestParam(required = false) Integer resourceType,
                                                          @RequestParam(required = false) Integer finished) {
        LambdaQueryWrapper<PsyHistory> w = new LambdaQueryWrapper<>();
        if (resourceType != null) w.eq(PsyHistory::getResourceType, resourceType);
        if (finished != null) w.eq(PsyHistory::getIsFinished, finished);
        w.orderByDesc(PsyHistory::getCreateTime);
        Page<PsyHistory> p = historyMapper.selectPage(new Page<>(PageLimit.page(page), PageLimit.of(size)), w);

        Map<Long, String> nm = nameMap();
        Map<Long, String> videoName = new HashMap<>();
        Map<Long, String> audioName = new HashMap<>();
        try { videoMapper.selectList(null).forEach(v -> videoName.put(v.getVideoId(), v.getVideoName())); } catch (Exception ignored) { log.warn("可忽略的异常，已跳过: {}", ignored.toString()); }
        try { audioMapper.selectList(null).forEach(a -> audioName.put(a.getAudioId(), a.getAudioName())); } catch (Exception ignored) { log.warn("可忽略的异常，已跳过: {}", ignored.toString()); }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (PsyHistory h : p.getRecords()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("historyId", h.getHistoryId());
            m.put("userId", h.getUserId());
            m.put("userName", nm.getOrDefault(h.getUserId(), "用户" + h.getUserId()));
            m.put("resourceType", h.getResourceType());
            m.put("typeName", Integer.valueOf(1).equals(h.getResourceType()) ? "音频" : "视频");
            String title = Integer.valueOf(1).equals(h.getResourceType())
                    ? audioName.get(h.getResourceId()) : videoName.get(h.getResourceId());
            m.put("title", title == null ? "（内容已删除）" : title);
            m.put("progress", h.getPlayProgress());
            m.put("duration", h.getDuration());
            m.put("percent", (h.getDuration() == null || h.getDuration() == 0 || h.getPlayProgress() == null)
                    ? 0 : Math.min(100, Math.round(h.getPlayProgress() * 100.0 / h.getDuration())));
            m.put("finished", Integer.valueOf(1).equals(h.getIsFinished()) ? "是" : "否");
            m.put("createTime", h.getCreateTime());
            rows.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", p.getTotal());
        data.put("rows", rows);
        return Result.success(data);
    }

    // ==================================================================
    // 四、系统操作日志
    // ==================================================================

    @GetMapping("/log/stats")
    public Result<Map<String, Object>> logStats() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            List<SysLog> all = sysLogMapper.selectList(
                    new LambdaQueryWrapper<SysLog>().orderByDesc(SysLog::getCreateTime).last("limit 2000"));
            data.put("total", sysLogMapper.selectCount(null));
            LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
            data.put("today", sysLogMapper.selectCount(
                    new LambdaQueryWrapper<SysLog>().ge(SysLog::getCreateTime, todayStart)));
            data.put("fail", sysLogMapper.selectCount(new LambdaQueryWrapper<SysLog>().eq(SysLog::getStatus, 0)));
            data.put("avgCost", all.isEmpty() ? 0
                    : Math.round(all.stream().filter(l -> l.getCostTime() != null)
                    .mapToLong(SysLog::getCostTime).average().orElse(0)));

            Map<String, Long> moduleCount = new LinkedHashMap<>();
            Map<String, Long> dayCount = new LinkedHashMap<>();
            for (SysLog l : all) {
                moduleCount.merge(l.getModule() == null ? "其他" : l.getModule(), 1L, Long::sum);
                if (l.getCreateTime() != null) {
                    dayCount.merge(l.getCreateTime().toLocalDate().toString(), 1L, Long::sum);
                }
            }
            List<Map<String, Object>> moduleDist = new ArrayList<>();
            moduleCount.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                    .forEach(e -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("name", e.getKey());
                        m.put("value", e.getValue());
                        moduleDist.add(m);
                    });
            data.put("moduleDist", moduleDist);
            data.put("trend", fillSeries(last14Days(), dayCount));
        } catch (Exception e) {
            data.put("error", e.getMessage());
        }
        return Result.success(data);
    }

    @GetMapping("/log/list")
    public Result<Map<String, Object>> logList(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "20") long size,
                                               @RequestParam(required = false) String module,
                                               @RequestParam(required = false) Integer status,
                                               @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysLog> w = new LambdaQueryWrapper<>();
        if (module != null && !module.isBlank()) w.eq(SysLog::getModule, module);
        if (status != null) w.eq(SysLog::getStatus, status);
        if (keyword != null && !keyword.isBlank()) {
            w.and(x -> x.like(SysLog::getUsername, keyword)
                    .or().like(SysLog::getNickname, keyword)
                    .or().like(SysLog::getDescription, keyword));
        }
        w.orderByDesc(SysLog::getCreateTime);
        Page<SysLog> p = sysLogMapper.selectPage(new Page<>(PageLimit.page(page), PageLimit.of(size)), w);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", p.getTotal());
        data.put("rows", p.getRecords());
        return Result.success(data);
    }

    /** 清理 N 天前的操作日志（写操作，只读管理员不可执行） */
    @AdminWrite
    @PostMapping("/log/clear")
    public Result<Map<String, Object>> logClear(@RequestParam(defaultValue = "90") int days) {
        LocalDateTime before = LocalDateTime.now().minusDays(Math.max(1, days));
        int n = sysLogMapper.delete(new LambdaQueryWrapper<SysLog>().lt(SysLog::getCreateTime, before));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", n);
        data.put("before", before.toLocalDate().toString());
        return Result.success(data);
    }

    // ==================================================================
    // 五、首页轮播 / 公告位
    // ==================================================================

    @GetMapping("/banner/list")
    public Result<List<PsyBanner>> bannerList(@RequestParam(required = false) String position) {
        LambdaQueryWrapper<PsyBanner> w = new LambdaQueryWrapper<>();
        if (position != null && !position.isBlank()) w.eq(PsyBanner::getPosition, position);
        w.orderByAsc(PsyBanner::getSort).orderByDesc(PsyBanner::getBannerId);
        return Result.success(bannerMapper.selectList(w));
    }

    @PostMapping("/banner/save")
    public Result<Boolean> bannerSave(@RequestBody PsyBanner b) {
        if (b.getBannerId() != null) {
            b.setUpdateTime(LocalDateTime.now());
            return Result.success(bannerMapper.updateById(b) > 0);
        }
        if (b.getSort() == null) b.setSort(0);
        if (b.getStatus() == null) b.setStatus(1);
        if (b.getPosition() == null || b.getPosition().isBlank()) b.setPosition("home");
        if (b.getLinkType() == null || b.getLinkType().isBlank()) b.setLinkType("none");
        return Result.success(bannerMapper.insert(b) > 0);
    }

    @PostMapping("/banner/status/{id:\\d+}")
    public Result<Boolean> bannerStatus(@PathVariable Long id, @RequestParam Integer status) {
        PsyBanner b = new PsyBanner();
        b.setBannerId(id);
        b.setStatus(status);
        return Result.success(bannerMapper.updateById(b) > 0);
    }

    @DeleteMapping("/banner/{id:\\d+}")
    public Result<Boolean> bannerDelete(@PathVariable Long id) {
        return Result.success(bannerMapper.deleteById(id) > 0);
    }

    /* ============================================================
     * 头像审核：管理员对「用户自己上传的头像」进行复核，
     * 发现违规图片可一键重置为系统默认头像（不必禁用整个账号）。
     * 系统内置头像（/img/...）不在此列，用户自选不做干预。
     * ============================================================ */

    /** 系统默认头像（重置目标） */
    private static final String DEFAULT_AVATAR = "/img/1 (1).png";

    /** 是否为用户自行上传的头像（内置头像统一放在 /img/ 下） */
    private boolean isCustomAvatar(String avatar) {
        return avatar != null && !avatar.isBlank() && !avatar.startsWith("/img/");
    }

    /** 头像审核概览：用户总数 / 自定义头像数 / 内置头像数 / 累计重置次数 */
    @GetMapping("/avatar/stats")
    public Result<Map<String, Object>> avatarStats() {
        List<SysUser> users = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().select(SysUser::getUserId, SysUser::getAvatar));
        long custom = users.stream().filter(u -> isCustomAvatar(u.getAvatar())).count();
        long resetCnt = sysLogMapper.selectCount(
                new LambdaQueryWrapper<SysLog>().eq(SysLog::getModule, "头像审核"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userTotal", users.size());
        m.put("customCount", custom);
        m.put("builtinCount", users.size() - custom);
        m.put("resetCount", resetCnt);
        return Result.success(m);
    }

    /** 自定义头像用户列表（支持用户名/昵称模糊搜索，分页） */
    @GetMapping("/avatar/list")
    public Result<Map<String, Object>> avatarList(@RequestParam(required = false) String keyword,
                                                  @RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "12") long size) {
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getUserId, SysUser::getUsername, SysUser::getNickname,
                        SysUser::getAvatar, SysUser::getRole, SysUser::getStatus, SysUser::getCreateTime)
                .isNotNull(SysUser::getAvatar).ne(SysUser::getAvatar, "")
                .notLike(SysUser::getAvatar, "/img/%");
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            w.and(x -> x.like(SysUser::getUsername, kw).or().like(SysUser::getNickname, kw));
        }
        w.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> p = userMapper.selectPage(new Page<>(PageLimit.page(page), PageLimit.of(size)), w);
        List<Map<String, Object>> rows = p.getRecords().stream().map(u -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("userId", u.getUserId());
            r.put("username", u.getUsername());
            r.put("nickname", u.getNickname());
            r.put("avatar", u.getAvatar());
            r.put("role", u.getRole());
            r.put("status", u.getStatus());
            r.put("createTime", u.getCreateTime());
            return r;
        }).collect(Collectors.toList());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("list", rows);
        res.put("total", p.getTotal());
        res.put("page", p.getCurrent());
        res.put("size", p.getSize());
        return Result.success(res);
    }

    /** 重置为系统默认头像（写操作，AOP 自动记录） */
    @AdminWrite
    @PostMapping("/avatar/reset")
    public Result<Boolean> avatarReset(@RequestBody Map<String, Object> body) {
        Object uid = body.get("userId");
        if (uid == null) return Result.error(400, "userId不能为空");
        Long userId = Long.valueOf(String.valueOf(uid));
        SysUser u = userMapper.selectById(userId);
        if (u == null) return Result.error(404, "用户不存在");
        if (!isCustomAvatar(u.getAvatar())) return Result.error(400, "该用户使用的是系统内置头像，无需重置");
        String old = u.getAvatar();
        u.setAvatar(DEFAULT_AVATAR);
        boolean ok = userMapper.updateById(u) > 0;
        if (ok) log.info("[头像审核] 管理员重置 userId={} 的头像：{} -> {}", userId, old, DEFAULT_AVATAR);
        return ok ? Result.success(true) : Result.error(500, "重置失败");
    }
}
