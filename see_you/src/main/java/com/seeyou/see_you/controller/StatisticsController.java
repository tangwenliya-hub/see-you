package com.seeyou.see_you.controller;

import com.seeyou.see_you.algorithm.EmotionConst;
import com.seeyou.see_you.entity.*;
import com.seeyou.see_you.mapper.StatisticsMapper;
import com.seeyou.see_you.service.*;
import com.seeyou.see_you.util.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

/**
 * 统计接口（ECharts 数据源）
 *
 * 分两类：
 *  A. 全站统计 /statistics/xxx        —— 管理员看板用
 *  B. 个人统计 /statistics/my/xxx     —— 「我的数据看板」用，userId 一律从 token 取，不接受前端传参
 *
 * ⚠️ 情绪字典必须与前端保持一致（Diary.vue / Profile.vue 的 emotions 数组）：
 *    1开心 2平静 3疲惫 4难过 5生气 6焦虑 7兴奋 8满足 9其他
 *    旧版本写成 1~5（开心/平静/焦虑/低落/疲惫）与前端对不上，已修正。
 *
 * 【性能改造】旧实现是「全表 list() 拉进内存 + for 循环聚合」，数据量一大就会把
 * 日记正文、图片地址等无用字段一起查出来，接口耗时随数据量线性增长，且 A 类接口游客
 * 可访问，构成 DoS 放大面。现已改为：
 *   1. 聚合逻辑下沉到 MySQL（见 StatisticsMapper 的 GROUP BY / COUNT / AVG(CASE WHEN)）；
 *   2. A 类接口加 30 秒内存缓存，重复请求直接命中缓存，不落库。
 * 前后端返回的 JSON 结构保持完全一致，前端无需改动。
 */
@RestController
@RequestMapping("/statistics")
public class StatisticsController {

    /**
     * 情绪标签：下标即数据库里的 emotion 值。
     * 字典唯一真源在 {@link EmotionConst}，此处只做别名，不再维护第二份字面量
     * （历史问题：这里曾各存一份名称/指数/积极集，改一个数值要动 4 处）。
     */
    private static final String[] EMOTION_NAMES = EmotionConst.NAMES;
    /** 情绪指数（0~100）：同源于 EmotionConst.INDEX */
    private static final int[] EMOTION_INDEX = EmotionConst.INDEX;
    /** 积极情绪：开心 平静 兴奋 满足 */
    private static final Set<Integer> POSITIVE = EmotionConst.POSITIVE;
    /*
     * 注意：原先这里还有一个 NEGATIVE = {4,5} 的私有常量，与 EmotionConst.NEGATIVE={3,4,5,6}
     * 并存且从未被引用，是「同一个词两种定义」的物证，已删除。
     * 消极/负面的唯一定义请用 EmotionConst.NEGATIVE（疲惫/难过/生气/焦虑）。
     */

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM/dd");

    /** A 类（游客可访问的）统计结果缓存时长：30 秒 */
    private static final long CACHE_TTL_MS = 30_000L;
    /** key -> {数据, 过期时间戳} */
    private static final Map<String, Object[]> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    @Resource
    private StatisticsMapper statisticsMapper;
    @Resource
    private PsyDiaryService diaryService;
    @Resource
    private PsyRecordService recordService;
    @Resource
    private PsyAudioService audioService;
    @Resource
    private PsyVideoService videoService;
    @Resource
    private AiChatService aiChatService;
    @Resource
    private SysUserService userService;
    @Resource
    private CommentService commentService;
    @Resource
    private FeedbackService feedbackService;
    @Resource
    private PsyTestPaperService paperService;

    // ============ 公共小工具 ============

    // 说明：indexOf / nameOf / streakInfo / wordFreq 刻意不加 private —— 它们是纯函数（不依赖
    // Spring 容器与数据库），包内可见后单元测试可以直接调用，避免为了验证几行算法去启动整个上下文。

    static int indexOf(Integer emotion) {
        if (emotion == null || emotion < 1 || emotion >= EMOTION_INDEX.length) return 0;
        return EMOTION_INDEX[emotion];
    }

    static String nameOf(Integer emotion) {
        if (emotion == null || emotion < 1 || emotion >= EMOTION_NAMES.length) return "未知";
        return EMOTION_NAMES[emotion];
    }

    /** JDBC 返回的数值类型不固定（Long / BigInteger / BigDecimal），统一转 long */
    private static long num(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return 0L; }
    }

    private static int intNum(Object v) {
        return (int) num(v);
    }

    /** emotion 列在 JDBC 里可能是 Integer / Byte / String，统一转 Integer */
    private static Integer emotionOf(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.valueOf(v.toString()); } catch (Exception e) { return null; }
    }

    /** 取当前登录用户 ID（来自 JwtInterceptor） */
    private Long currentUid(HttpServletRequest request) {
        Object uid = request.getAttribute("userId");
        return uid == null ? null : ((Number) uid).longValue();
    }

    /** 「emotion -> 条数」的 Map，缺失补 0 */
    private static int[] toCountArray(List<Map<String, Object>> rows, String key) {
        int[] counts = new int[EMOTION_NAMES.length];
        if (rows == null) return counts;
        for (Map<String, Object> row : rows) {
            Integer e = emotionOf(row.get(key));
            if (e != null && e >= 1 && e < EMOTION_NAMES.length) counts[e] = intNum(row.get("cnt"));
        }
        return counts;
    }

    /** diaryDate 可能是 java.sql.Date / LocalDate / String，统一转 LocalDate */
    private static LocalDate toDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate) return (LocalDate) v;
        if (v instanceof java.sql.Date) return ((java.sql.Date) v).toLocalDate();
        String s = v.toString();
        if (s.length() > 10) s = s.substring(0, 10);
        try { return LocalDate.parse(s); } catch (Exception e) { return null; }
    }

    /** 连续打卡天数：截至 today（若最后一条不是今天/昨天，则算到最后一条）。纯函数，单元测试直接调用 */
    static Map<String, Object> streakInfo(List<LocalDate> dates) {
        Map<String, Object> m = new HashMap<>();
        if (dates.isEmpty()) { m.put("current", 0); m.put("longest", 0); return m; }
        List<LocalDate> sorted = new ArrayList<>(dates);
        Collections.sort(sorted);
        int longest = 1, cur = 1;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).minusDays(1).equals(sorted.get(i - 1))) cur++;
            else cur = 1;
            longest = Math.max(longest, cur);
        }
        // 连续天数（从最后一天往前推）
        int current = 1;
        for (int i = sorted.size() - 1; i > 0; i--) {
            if (sorted.get(i - 1).plusDays(1).equals(sorted.get(i))) current++;
            else break;
        }
        LocalDate last = sorted.get(sorted.size() - 1);
        LocalDate today = LocalDate.now();
        // 最后一次记录距今超过 1 天，视为「已断签」
        if (last.isBefore(today.minusDays(1))) current = 0;
        m.put("current", current);
        m.put("longest", longest);
        return m;
    }

    /**
     * 读取缓存；没有或已过期才真正查库。
     * A 类接口游客可访问，缓存可以把「反复刷新 / 恶意刷接口」挡在数据库之外。
     * 后台增删改后最多 30 秒即可看到最新统计，如需即时生效调用 {@link #clearCache()}。
     */
    @SuppressWarnings("unchecked")
    private static <T> T cached(String key, Supplier<T> loader) {
        long now = System.currentTimeMillis();
        Object[] hit = CACHE.get(key);
        if (hit != null && (long) hit[1] > now) return (T) hit[0];
        T value = loader.get();
        CACHE.put(key, new Object[]{value, now + CACHE_TTL_MS});
        return value;
    }

    /** 手动清空统计缓存（后台做了增删改、想立刻看到最新统计时调用） */
    public static void clearCache() {
        CACHE.clear();
    }

    // =========================================================
    // A. 全站统计（管理员）
    // =========================================================

    /** 1. 全站情绪分布（柱状图） */
    @GetMapping("/emotion")
    public Result<Map<String, Object>> emotionStats() {
        return Result.success(cached("emotion", () -> {
            int[] counts = toCountArray(statisticsMapper.emotionCount(), "e");
            List<String> labels = new ArrayList<>();
            List<Integer> values = new ArrayList<>();
            for (int i = 1; i < EMOTION_NAMES.length; i++) { labels.add(EMOTION_NAMES[i]); values.add(counts[i]); }
            Map<String, Object> result = new HashMap<>();
            result.put("labels", labels);
            result.put("values", values);
            result.put("total", diaryService.count());
            return result;
        }));
    }

    /**
     * 2. 月度情绪趋势（折线图）
     * 旧实现返回的是「每月日记条数」，名不副实；改为返回每月平均情绪指数 + 每月记录数。
     */
    @GetMapping("/emotionTrend")
    public Result<Map<String, Object>> emotionTrend() {
        return Result.success(cached("emotionTrend", () -> {
            List<String> labels = new ArrayList<>();
            List<Integer> values = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            for (Map<String, Object> row : statisticsMapper.monthlyEmotionTrend()) {
                Object ym = row.get("ym");
                if (ym == null) continue;
                labels.add(ym.toString());
                values.add(intNum(row.get("avg_index")));
                counts.add(intNum(row.get("cnt")));
            }
            Map<String, Object> result = new HashMap<>();
            result.put("labels", labels);
            result.put("values", values);
            result.put("counts", counts);
            return result;
        }));
    }

    /** 3. 资源分类统计（饼图） */
    @GetMapping("/resource")
    public Result<Map<String, Object>> resourceStats() {
        return Result.success(cached("resource", () -> {
            List<Map<String, Object>> audioData = new ArrayList<>();
            for (Map<String, Object> row : statisticsMapper.audioCategoryCount()) {
                audioData.add(mapOf(String.valueOf(row.get("name")), num(row.get("cnt"))));
            }
            List<Map<String, Object>> videoData = new ArrayList<>();
            for (Map<String, Object> row : statisticsMapper.videoCategoryCount()) {
                videoData.add(mapOf(String.valueOf(row.get("name")), num(row.get("cnt"))));
            }
            Map<String, Object> result = new HashMap<>();
            result.put("audio", audioData);
            result.put("video", videoData);
            result.put("audioTotal", audioService.count());
            result.put("videoTotal", videoService.count());
            return result;
        }));
    }

    private static Map<String, Object> mapOf(String name, Object value) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", name);
        item.put("value", value);
        return item;
    }

    /** 4. 测评结果情绪分布（饼图） */
    @GetMapping("/testResult")
    public Result<Map<String, Object>> testResultStats() {
        return Result.success(cached("testResult", () -> {
            int[] counts = toCountArray(statisticsMapper.testResultEmotionCount(), "e");
            List<String> labels = new ArrayList<>();
            List<Integer> values = new ArrayList<>();
            for (int i = 1; i < EMOTION_NAMES.length; i++) { labels.add(EMOTION_NAMES[i]); values.add(counts[i]); }
            Map<String, Object> result = new HashMap<>();
            result.put("labels", labels);
            result.put("values", values);
            result.put("total", recordService.count());
            return result;
        }));
    }

    /** 5. 全站概览（数字卡片）—— 本来就是 SQL COUNT(*)，保持原样 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(cached("overview", () -> {
            Map<String, Object> result = new HashMap<>();
            result.put("userTotal", userService.count());
            result.put("diaryTotal", diaryService.count());
            result.put("audioTotal", audioService.count());
            result.put("videoTotal", videoService.count());
            result.put("chatTotal", aiChatService.count());
            result.put("testRecordTotal", recordService.count());
            result.put("commentTotal", commentService.count());
            result.put("feedbackTotal", feedbackService.count());
            result.put("comments", commentService.count() + feedbackService.count());
            return result;
        }));
    }

    /** 6. 视频播放排行（横向条形图） */
    @GetMapping("/videoRank")
    public Result<List<Map<String, Object>>> videoRank() {
        return Result.success(cached("videoRank", () -> {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> row : statisticsMapper.videoPlayRank(8)) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", row.get("name"));
                item.put("value", intNum(row.get("value")));
                item.put("category", row.get("category"));
                result.add(item);
            }
            return result;
        }));
    }

    /** 7. 最近活跃用户（写日记最多的前 5 人） */
    @GetMapping("/activeUsers")
    public Result<List<Map<String, Object>>> activeUsers() {
        return Result.success(cached("activeUsers", () -> {
            List<Map<String, Object>> top = statisticsMapper.topDiaryUsers(5);
            List<Map<String, Object>> result = new ArrayList<>();
            if (top.isEmpty()) return result;
            // 第二步只查这 5 个用户的基础信息，避免 sys_user × psy_diary 大表 JOIN
            List<Long> ids = new ArrayList<>();
            for (Map<String, Object> row : top) ids.add(num(row.get("uid")));
            Map<Long, Map<String, Object>> userMap = new HashMap<>();
            for (Map<String, Object> u : statisticsMapper.usersByIds(ids)) {
                userMap.put(num(u.get("uid")), u);
            }
            for (Map<String, Object> row : top) {
                long uid = num(row.get("uid"));
                Map<String, Object> u = userMap.get(uid);
                if (u == null) continue;
                Object nick = u.get("nickname");
                String name = (nick != null && !nick.toString().isBlank()) ? nick.toString() : String.valueOf(u.get("username"));
                Map<String, Object> item = new HashMap<>();
                item.put("username", name);
                item.put("activity", "写日记");
                item.put("count", num(row.get("cnt")));
                item.put("time", u.get("create_time"));
                result.add(item);
            }
            return result;
        }));
    }

    // =========================================================
    // B. 个人统计（「我的数据看板」，userId 只从 token 取）
    // =========================================================

    /**
     * B1. 我的概览：总天数 / 连续打卡 / 最长连续 / 本月天数 / 平均情绪指数 / 积极占比 / 测评次数
     * 聚合交给 SQL；只有「连续打卡」需要逐日推算，因此只查去重后的日期列（不再拉正文/图片）。
     */
    @GetMapping("/my/overview")
    public Result<Map<String, Object>> myOverview(HttpServletRequest request) {
        Long uid = currentUid(request);
        if (uid == null) return Result.error(401, "未登录");

        LocalDate now = LocalDate.now();
        Map<String, Object> agg = statisticsMapper.myDiaryAgg(uid, now.getYear(), now.getMonthValue());
        // myDiaryAgg 已按「每天取最后一篇」聚合，total_days 就等于打卡天数（含无感代记）。
        // 但 pos / neg / avg 只统计 source=1（手动），所以占比的分母必须用 manual_days，
        // 否则「代记占多数」的用户积极占比会被稀释成一个没有意义的数字。
        long totalDays = num(agg.get("total_days"));
        long manualDays = num(agg.get("manual_days"));
        long positive = num(agg.get("pos"));
        long negative = num(agg.get("neg"));

        List<LocalDate> dates = new ArrayList<>();
        for (Map<String, Object> row : statisticsMapper.myDiaryDates(uid)) {
            LocalDate d = toDate(row.get("d"));
            if (d != null) dates.add(d);
        }
        Map<String, Object> streak = streakInfo(dates);

        Map<String, Object> latest = statisticsMapper.myLatestDiary(uid);
        Integer latestEmotion = latest == null ? null : emotionOf(latest.get("e"));

        // 按来源计数：manual 用于经验值（只有亲笔才算），auto 用于页面上的「系统帮你记了 N 天」提示
        Map<String, Object> src = statisticsMapper.myDiarySourceCount(uid);

        Map<String, Object> result = new HashMap<>();
        result.put("totalDays", totalDays);
        result.put("manualDays", manualDays);
        result.put("manualDiaries", num(src.get("manual_cnt")));
        result.put("confirmedDiaries", num(src.get("confirmed_cnt")));
        result.put("autoDiaries", num(src.get("auto_cnt")));
        result.put("currentStreak", streak.get("current"));
        result.put("longestStreak", streak.get("longest"));
        result.put("monthDays", num(agg.get("month_days")));
        result.put("avgIndex", intNum(agg.get("avg_index")));
        result.put("positiveDays", positive);
        result.put("negativeDays", negative);
        result.put("positiveRatio", manualDays == 0 ? 0 : (int) Math.round(positive * 100.0 / manualDays));
        result.put("negativeRatio", manualDays == 0 ? 0 : (int) Math.round(negative * 100.0 / manualDays));
        result.put("latestEmotion", latestEmotion);
        result.put("latestEmotionName", latestEmotion == null ? null : nameOf(latestEmotion));
        result.put("latestDate", latest == null ? null : toDate(latest.get("d")));
        result.put("testCount", recordService.count(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PsyRecord>()
                        .eq(PsyRecord::getUserId, uid)));
        return Result.success(result);
    }

    /**
     * B2. 我的情绪指数趋势（折线图）：按天补全，无记录的日期为 null
     * 只查 [今天-days+1, 今天] 这个区间，不再把历史全部日记捞出来。
     */
    @GetMapping("/my/trend")
    public Result<Map<String, Object>> myTrend(@RequestParam(defaultValue = "30") Integer days,
                                               HttpServletRequest request) {
        Long uid = currentUid(request);
        if (uid == null) return Result.error(401, "未登录");
        if (days == null || days <= 0) days = 30;
        days = Math.min(days, 365);

        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1);

        Map<LocalDate, Integer> map = new HashMap<>();
        for (Map<String, Object> row : statisticsMapper.myDiaryBetween(uid, start.toString(), end.toString())) {
            LocalDate date = toDate(row.get("d"));
            if (date != null) map.put(date, emotionOf(row.get("e")));
        }

        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        List<Integer> emotions = new ArrayList<>();
        List<String> dates = new ArrayList<>();
        for (LocalDate cur = start; !cur.isAfter(end); cur = cur.plusDays(1)) {
            labels.add(cur.format(FMT));
            dates.add(cur.toString());
            Integer e = map.get(cur);
            if (e == null) { values.add(null); emotions.add(null); }
            else { values.add(indexOf(e)); emotions.add(e); }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("dates", dates);
        result.put("values", values);
        result.put("emotions", emotions);
        result.put("days", days);
        return Result.success(result);
    }

    /**
     * B3. 我的打卡热力图数据（日历坐标系）：[[date, index], ...]
     * 只查该年度区间，而不是把全部历史日记查出来。
     */
    @GetMapping("/my/heatmap")
    public Result<Map<String, Object>> myHeatmap(@RequestParam(required = false) Integer year,
                                                 HttpServletRequest request) {
        Long uid = currentUid(request);
        if (uid == null) return Result.error(401, "未登录");
        if (year == null) year = LocalDate.now().getYear();
        if (year < 1970 || year > LocalDate.now().getYear() + 1) year = LocalDate.now().getYear();

        Map<LocalDate, Integer> map = new HashMap<>();
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        for (Map<String, Object> row : statisticsMapper.myDiaryBetween(uid, from.toString(), to.toString())) {
            LocalDate date = toDate(row.get("d"));
            if (date != null) map.put(date, emotionOf(row.get("e")));
        }
        List<List<Object>> data = new ArrayList<>();
        int filled = 0;
        LocalDate cur = from;
        while (!cur.isAfter(to)) {
            Integer e = map.get(cur);
            if (e != null) {
                data.add(Arrays.asList(cur.toString(), indexOf(e)));
                filled++;
            }
            cur = cur.plusDays(1);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("year", year);
        result.put("data", data);
        result.put("filledDays", filled);
        return Result.success(result);
    }

    /** B4. 我的情绪分布（饼图 / 柱状图） */
    @GetMapping("/my/emotion")
    public Result<Map<String, Object>> myEmotion(HttpServletRequest request) {
        Long uid = currentUid(request);
        if (uid == null) return Result.error(401, "未登录");
        int[] counts = toCountArray(statisticsMapper.myEmotionCount(uid), "e");

        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        List<Map<String, Object>> pie = new ArrayList<>();
        long total = 0, pos = 0;
        for (int i = 1; i < EMOTION_NAMES.length; i++) {
            labels.add(EMOTION_NAMES[i]);
            values.add(counts[i]);
            total += counts[i];
            if (POSITIVE.contains(i)) pos += counts[i];
            if (counts[i] > 0) pie.add(mapOf(EMOTION_NAMES[i], counts[i]));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("values", values);
        result.put("pie", pie);
        result.put("total", total);
        result.put("positiveRatio", total == 0 ? 0 : (int) Math.round(pos * 100.0 / total));
        return Result.success(result);
    }

    /**
     * B5. 我的测评分数纵向对比：每个量表一条折线（含量表满分，便于归一化对比）
     */
    @GetMapping("/my/testTrend")
    public Result<Map<String, Object>> myTestTrend(HttpServletRequest request) {
        Long uid = currentUid(request);
        if (uid == null) return Result.error(401, "未登录");
        List<PsyRecord> records = recordService.list(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PsyRecord>()
                        .eq(PsyRecord::getUserId, uid)
                        .orderByAsc(PsyRecord::getCreateTime)
        );
        Map<Long, String> nameMap = new HashMap<>();
        Map<Long, Integer> maxMap = new HashMap<>();
        if (paperService != null) {
            for (PsyTestPaper p : paperService.list()) {
                nameMap.put(p.getTestId(), p.getTestName());
                maxMap.put(p.getTestId(), p.getMaxScore());
            }
        }
        Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (PsyRecord r : records) {
            int score = r.getTotalScore() != null ? r.getTotalScore() : (r.getScore() == null ? 0 : r.getScore());
            Map<String, Object> point = new HashMap<>();
            point.put("time", r.getCreateTime() == null ? null : r.getCreateTime().toString());
            point.put("date", r.getCreateTime() == null ? null : r.getCreateTime().toLocalDate().toString());
            point.put("score", score);
            point.put("level", r.getLevel());
            grouped.computeIfAbsent(r.getTestId(), k -> new ArrayList<>()).add(point);
        }
        List<Map<String, Object>> series = new ArrayList<>();
        grouped.forEach((testId, points) -> {
            Map<String, Object> s = new HashMap<>();
            s.put("testId", testId);
            s.put("testName", nameMap.getOrDefault(testId, "量表" + testId));
            Integer max = maxMap.get(testId);
            s.put("maxScore", max == null ? 0 : max);
            s.put("points", points);
            series.add(s);
        });
        Map<String, Object> result = new HashMap<>();
        result.put("series", series);
        result.put("total", records.size());
        return Result.success(result);
    }

    /** B6. 我的月度统计（近 N 个月平均情绪指数 + 记录数） */
    @GetMapping("/my/monthly")
    public Result<Map<String, Object>> myMonthly(@RequestParam(defaultValue = "6") Integer months,
                                                 HttpServletRequest request) {
        Long uid = currentUid(request);
        if (uid == null) return Result.error(401, "未登录");
        if (months == null || months <= 0) months = 6;
        months = Math.min(months, 24);

        List<String> allLabels = new ArrayList<>();
        List<Integer> allValues = new ArrayList<>();
        List<Integer> allCounts = new ArrayList<>();
        for (Map<String, Object> row : statisticsMapper.myMonthlyEmotionTrend(uid)) {
            Object ym = row.get("ym");
            if (ym == null) continue;
            allLabels.add(ym.toString());
            allValues.add(intNum(row.get("avg_index")));
            allCounts.add(intNum(row.get("cnt")));
        }
        // 只保留最近 N 个月
        int from = Math.max(0, allLabels.size() - months);

        Map<String, Object> result = new HashMap<>();
        result.put("labels", allLabels.subList(from, allLabels.size()));
        result.put("values", allValues.subList(from, allValues.size()));
        result.put("counts", allCounts.subList(from, allCounts.size()));
        return Result.success(result);
    }

    // ============ C. 进阶图表（Step 3） ============

    /** C1. 我的「睡眠时长 x 情绪指数」散点样本（近 90 天，最多 500 条；回归线由前端最小二乘计算） */
    @GetMapping("/my/scatter")
    public Result<List<Map<String, Object>>> myScatter(@RequestParam(defaultValue = "90") Integer days,
                                                       HttpServletRequest request) {
        Long uid = currentUid(request);
        if (uid == null) return Result.error(401, "未登录");
        if (days == null || days <= 0) days = 90;
        days = Math.min(days, 365);
        String from = LocalDate.now().minusDays(days).toString();
        List<Map<String, Object>> rows = statisticsMapper.myScatter(uid, from);
        List<Map<String, Object>> points = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object s = r.get("s");
            Integer e = intNum(r.get("e"));
            if (s == null || e == null || e < 1 || e >= EMOTION_INDEX.length) continue;
            Map<String, Object> p = new HashMap<>();
            p.put("sleep", Double.parseDouble(s.toString()));
            p.put("score", EMOTION_INDEX[e]);
            p.put("emotion", e);
            points.add(p);
        }
        return Result.success(points);
    }

    /** C2. 我的「星期 x 情绪」桑基流向（近 90 天）：node=周一~周日 + 9 种情绪，link=出现次数 */
    @GetMapping("/my/sankey")
    public Result<Map<String, Object>> mySankey(@RequestParam(defaultValue = "90") Integer days,
                                                HttpServletRequest request) {
        Long uid = currentUid(request);
        if (uid == null) return Result.error(401, "未登录");
        if (days == null || days <= 0) days = 90;
        days = Math.min(days, 365);
        String from = LocalDate.now().minusDays(days).toString();
        // DAYOFWEEK: 1=周日 2=周一 ... 7=周六
        String[] weekdayNames = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> links = new ArrayList<>();
        for (Map<String, Object> r : statisticsMapper.myWeekdayEmotion(uid, from)) {
            Integer wd = intNum(r.get("wd")), e = intNum(r.get("e"));
            Integer cnt = intNum(r.get("cnt"));
            if (wd == null || wd < 1 || wd > 7 || e == null || e < 1 || e >= EMOTION_NAMES.length || cnt == null) continue;
            String w = weekdayNames[wd - 1], m = EMOTION_NAMES[e];
            if (seen.add(w)) {
                Map<String, Object> n = new HashMap<>();
                n.put("name", w);
                nodes.add(n);
            }
            if (seen.add(m)) {
                Map<String, Object> n = new HashMap<>();
                n.put("name", m);
                nodes.add(n);
            }
            Map<String, Object> link = new HashMap<>();
            link.put("source", w);
            link.put("target", m);
            link.put("value", cnt);
            links.add(link);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("links", links);
        return Result.success(result);
    }

    /**
     * 停用字：二元组里只要任一端出现这些字，就认为它跨词了（如「的天」「去了」），直接丢弃。
     *
     * <p>⚠️ 这里必须是「无实义虚词」的<b>单字</b>集合。早期版本把「今天/觉得/因为/开始……」
     * 这类停用<b>词</b>串在一起当字符表用，结果「今」「天」「心」「情」「觉」等大量常用字被误杀，
     * 词云里看不到「今天」「心情」这类日记里最高频的词。单元测试 {@code StatisticsLogicTest}
     * 对这张表做了回归保护。
     */
    static final String STOP_CHARS =
            "的了是在我你他她它们这那就都也还又再才只很太更最把被让给和与或而及其之于以所但因若则吗呢吧啊呀哦嗯嘛啦哇咯呗着过个些么什怎";

    /** C3. 我的日记高频词（词云用）：近 180 天正文做中文二元切分 + 停用字过滤，返回前 40 个 */
    @GetMapping("/my/keywords")
    public Result<List<Map<String, Object>>> myKeywords(@RequestParam(defaultValue = "180") Integer days,
                                                        HttpServletRequest request) {
        Long uid = currentUid(request);
        if (uid == null) return Result.error(401, "未登录");
        if (days == null || days <= 0) days = 180;
        days = Math.min(days, 365);
        String from = LocalDate.now().minusDays(days).toString();
        List<String> contents = statisticsMapper.myDiaryContents(uid, from, 300);
        Map<String, Integer> freq = wordFreq(contents);
        // 出现太少（<5 次）的噪声词直接丢掉，再按频率取前 40
        List<Map<String, Object>> words = new ArrayList<>();
        freq.entrySet().stream()
                .filter(en -> en.getValue() >= 5)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(40)
                .forEach(en -> {
                    Map<String, Object> w = new HashMap<>();
                    w.put("name", en.getKey());
                    w.put("value", en.getValue());
                    words.add(w);
                });
        return Result.success(words);
    }

    /**
     * 词频统计：无词典的轻量中文分词 —— 统计相邻两个汉字组成的二元组频率。
     * 对「今天 / 心情 / 工作 / 压力」这类双音节词效果很好，且不需要引入分词库。
     * 跳过规则：①非汉字字符；②任一端是停用字（的、了、是……），避免出现「的时」「我了」这类噪声。
     */
    static Map<String, Integer> wordFreq(List<String> contents) {
        Map<String, Integer> freq = new HashMap<>();
        if (contents == null) return freq;
        for (String content : contents) {
            if (content == null || content.length() < 2) continue;
            for (int i = 0; i < content.length() - 1; i++) {
                char a = content.charAt(i), b = content.charAt(i + 1);
                if (!isCjk(a) || !isCjk(b)) continue;
                if (STOP_CHARS.indexOf(a) >= 0 || STOP_CHARS.indexOf(b) >= 0) continue;
                freq.merge("" + a + b, 1, Integer::sum);
            }
        }
        return freq;
    }

    private static boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }
}
