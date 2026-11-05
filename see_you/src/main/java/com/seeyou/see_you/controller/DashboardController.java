package com.seeyou.see_you.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import com.seeyou.see_you.algorithm.EmotionConst;
import com.seeyou.see_you.entity.*;
import com.seeyou.see_you.mapper.EmotionPredictionMapper;
import com.seeyou.see_you.mapper.RiskAlertMapper;
import com.seeyou.see_you.service.*;
import com.seeyou.see_you.util.Result;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/admin/dashboard")
public class DashboardController {

    @Resource
    private SysUserService userService;
    @Resource
    private FeedbackService feedbackService;
    @Resource
    private CommentService commentService;
    @Resource
    private PsyTestPaperService testPaperService;
    @Resource
    private PsyRecordService recordService;
    @Resource
    private PsyVideoService videoService;
    @Resource
    private PsyAudioService audioService;
    @Resource
    private PsyDiaryService diaryService;
    @Resource
    private AiChatService aiChatService;
    @Resource
    private EmotionPredictionMapper predictionMapper;
    @Resource
    private RiskAlertMapper riskAlertMapper;

    /**
     * 数据大盘统一统计接口
     * 每个模块独立 try-catch，单个失败不影响整体
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> result = new HashMap<>();
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

        // ========== 1. 核心指标卡片 ==========
        try {
            Map<String, Object> metrics = new HashMap<>();

            // 用户总数
            metrics.put("totalUsers", userService.count());

            // 今日新增用户
            long newUsersToday = userService.count(new LambdaQueryWrapper<SysUser>()
                    .ge(SysUser::getCreateTime, todayStart)
                    .le(SysUser::getCreateTime, todayEnd));
            metrics.put("newUsersToday", newUsersToday);

            // 今日活跃用户（今日有评论/反馈/日记/测评记录/AI对话的不同用户数）
            Set<Long> activeUserIds = new HashSet<>();
            try {
                commentService.list(new LambdaQueryWrapper<Comment>()
                                .ge(Comment::getCreateTime, todayStart)
                                .le(Comment::getCreateTime, todayEnd))
                        .forEach(c -> activeUserIds.add(c.getUserId()));
            } catch (Exception ignored) {}
            try {
                feedbackService.list(new LambdaQueryWrapper<Feedback>()
                                .ge(Feedback::getCreateTime, todayStart)
                                .le(Feedback::getCreateTime, todayEnd))
                        .forEach(f -> activeUserIds.add(f.getUserId()));
            } catch (Exception ignored) {}
            try {
                diaryService.list(new LambdaQueryWrapper<PsyDiary>()
                                .ge(PsyDiary::getCreateTime, todayStart)
                                .le(PsyDiary::getCreateTime, todayEnd))
                        .forEach(d -> activeUserIds.add(d.getUserId()));
            } catch (Exception ignored) {}
            try {
                recordService.list(new LambdaQueryWrapper<PsyRecord>()
                                .ge(PsyRecord::getCreateTime, todayStart)
                                .le(PsyRecord::getCreateTime, todayEnd))
                        .forEach(r -> activeUserIds.add(r.getUserId()));
            } catch (Exception ignored) {}
            try {
                aiChatService.list(new LambdaQueryWrapper<AiChat>()
                                .ge(AiChat::getCreateTime, todayStart)
                                .le(AiChat::getCreateTime, todayEnd))
                        .forEach(a -> activeUserIds.add(a.getUserId()));
            } catch (Exception ignored) {}
            metrics.put("activeUsersToday", activeUserIds.size());

            // 反馈总数 & 待处理反馈数（status=0 未读，排除软删）
            long totalFeedbacks = feedbackService.count(new LambdaQueryWrapper<Feedback>()
                    .eq(Feedback::getDeleted, 0));
            metrics.put("totalFeedbacks", totalFeedbacks);
            long pendingFeedbacks = feedbackService.count(new LambdaQueryWrapper<Feedback>()
                    .eq(Feedback::getStatus, 0)
                    .eq(Feedback::getDeleted, 0));
            metrics.put("pendingFeedbacks", pendingFeedbacks);

            // 评论总数 & 待审核评论数（status=0）
            metrics.put("totalComments", commentService.count());
            long pendingComments = commentService.count(new LambdaQueryWrapper<Comment>()
                    .eq(Comment::getStatus, 0));
            metrics.put("pendingComments", pendingComments);

            // 测评总数（测评试卷数）
            metrics.put("totalTests", testPaperService.count());

            // 测评参与总数（测评记录数）
            metrics.put("totalTestRecords", recordService.count());

            // AI 对话总次数
            metrics.put("aiChatTotal", aiChatService.count());

            // 情绪日记：手记（source=1）vs 系统推测（source=2 用户确认 / source=3 自动代记）
            metrics.put("diaryTotal", diaryService.count());
            metrics.put("diaryManual", diaryService.count(new LambdaQueryWrapper<PsyDiary>()
                    .eq(PsyDiary::getSource, 1)));
            metrics.put("diaryInferred", diaryService.count(new LambdaQueryWrapper<PsyDiary>()
                    .ne(PsyDiary::getSource, 1)));

            result.put("metrics", metrics);
        } catch (Exception e) {
            result.put("metrics", new HashMap<>());
            log.warn("[Dashboard] 核心指标统计失败", e);
        }

        // ========== 2. 新增用户趋势（最近7天） ==========
        try {
            List<Map<String, Object>> userTrend = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate date = LocalDate.now().minusDays(i);
                LocalDateTime dayStart = LocalDateTime.of(date, LocalTime.MIN);
                LocalDateTime dayEnd = LocalDateTime.of(date, LocalTime.MAX);
                long count = userService.count(new LambdaQueryWrapper<SysUser>()
                        .ge(SysUser::getCreateTime, dayStart)
                        .le(SysUser::getCreateTime, dayEnd));
                Map<String, Object> item = new HashMap<>();
                item.put("date", date.toString());
                item.put("newUsers", count);
                userTrend.add(item);
            }
            result.put("userTrend", userTrend);
        } catch (Exception e) {
            result.put("userTrend", new ArrayList<>());
            log.warn("[Dashboard] 用户趋势统计失败", e);
        }

        // ========== 3. 功能使用占比 ==========
        try {
            Map<String, Object> usageRatio = new HashMap<>();
            // 视频播放总数
            int videoPlayCount = videoService.list().stream()
                    .mapToInt(v -> v.getPlayCount() == null ? 0 : v.getPlayCount()).sum();
            usageRatio.put("videoPlayCount", videoPlayCount);
            // 音频播放总数
            int audioPlayCount = audioService.list().stream()
                    .mapToInt(a -> a.getPlayCount() == null ? 0 : a.getPlayCount()).sum();
            usageRatio.put("audioPlayCount", audioPlayCount);
            // 测评参与数（测评记录数）
            usageRatio.put("testRecordCount", recordService.count());
            // AI对话数
            usageRatio.put("aiChatCount", aiChatService.count());
            // 日记数
            usageRatio.put("diaryCount", diaryService.count());
            // 无感推测数（新模块）
            try {
                usageRatio.put("senseCount", predictionMapper.selectCount(null));
            } catch (Exception e2) {
                usageRatio.put("senseCount", 0L);
            }
            result.put("usageRatio", usageRatio);
        } catch (Exception e) {
            result.put("usageRatio", new HashMap<>());
            log.warn("[Dashboard] 功能使用占比统计失败", e);
        }

        // ========== 4. 用户情绪分布（基于日记） ==========
        try {
            List<PsyDiary> allDiaries = diaryService.list();
            // 情绪字典与前端 9 项保持一致：1开心 2平静 3疲惫 4难过 5生气 6焦虑 7兴奋 8满足 9其他
            String[] emotionNames = {"", "开心", "平静", "疲惫", "难过", "生气", "焦虑", "兴奋", "满足", "其他"};
            int[] emotionCounts = new int[emotionNames.length];
            for (PsyDiary d : allDiaries) {
                if (d.getEmotion() != null && d.getEmotion() >= 1 && d.getEmotion() < emotionNames.length) {
                    emotionCounts[d.getEmotion()]++;
                }
            }
            List<String> emotionLabels = new ArrayList<>();
            List<Integer> emotionValues = new ArrayList<>();
            for (int i = 1; i < emotionNames.length; i++) {
                emotionLabels.add(emotionNames[i]);
                emotionValues.add(emotionCounts[i]);
            }
            Map<String, Object> emotionDist = new HashMap<>();
            emotionDist.put("labels", emotionLabels);
            emotionDist.put("values", emotionValues);
            emotionDist.put("total", allDiaries.size());
            result.put("emotionDist", emotionDist);
        } catch (Exception e) {
            result.put("emotionDist", new HashMap<>());
            log.warn("[Dashboard] 情绪分布统计失败", e);
        }

        // ========== 5. 视频播放排行 TOP5 ==========
        try {
            List<PsyVideo> videos = videoService.list();
            videos.sort((a, b) -> {
                int pa = a.getPlayCount() == null ? 0 : a.getPlayCount();
                int pb = b.getPlayCount() == null ? 0 : b.getPlayCount();
                return Integer.compare(pb, pa);
            });
            List<Map<String, Object>> videoRank = new ArrayList<>();
            int topN = Math.min(5, videos.size());
            for (int i = 0; i < topN; i++) {
                PsyVideo v = videos.get(i);
                Map<String, Object> item = new HashMap<>();
                item.put("name", v.getVideoName());
                item.put("value", v.getPlayCount() == null ? 0 : v.getPlayCount());
                item.put("category", v.getCategory());
                videoRank.add(item);
            }
            result.put("videoRank", videoRank);
        } catch (Exception e) {
            result.put("videoRank", new ArrayList<>());
            log.warn("[Dashboard] 视频排行统计失败", e);
        }

        // ========== 6. 最近活跃用户 ==========
        try {
            Map<Long, Long> userActivityCount = new HashMap<>();
            Map<Long, String> userActivityType = new HashMap<>();
            // 日记
            try {
                diaryService.list().forEach(d -> {
                    userActivityCount.merge(d.getUserId(), 1L, Long::sum);
                    userActivityType.putIfAbsent(d.getUserId(), "写日记");
                });
            } catch (Exception ignored) {}
            // 评论
            try {
                commentService.list().forEach(c -> {
                    userActivityCount.merge(c.getUserId(), 1L, Long::sum);
                    userActivityType.putIfAbsent(c.getUserId(), "发表评论");
                });
            } catch (Exception ignored) {}
            // 测评
            try {
                recordService.list().forEach(r -> {
                    userActivityCount.merge(r.getUserId(), 1L, Long::sum);
                    userActivityType.putIfAbsent(r.getUserId(), "做测评");
                });
            } catch (Exception ignored) {}
            // 反馈
            try {
                feedbackService.list(new LambdaQueryWrapper<Feedback>().eq(Feedback::getDeleted, 0)).forEach(f -> {
                    userActivityCount.merge(f.getUserId(), 1L, Long::sum);
                    userActivityType.putIfAbsent(f.getUserId(), "提交反馈");
                });
            } catch (Exception ignored) {}
            // AI对话
            try {
                aiChatService.list().forEach(a -> {
                    userActivityCount.merge(a.getUserId(), 1L, Long::sum);
                    userActivityType.putIfAbsent(a.getUserId(), "AI对话");
                });
            } catch (Exception ignored) {}

            // 按活动次数排序取前5
            List<Map.Entry<Long, Long>> sortedUsers = new ArrayList<>(userActivityCount.entrySet());
            sortedUsers.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

            List<Map<String, Object>> activeUsers = new ArrayList<>();
            int userTopN = Math.min(5, sortedUsers.size());
            for (int i = 0; i < userTopN; i++) {
                Long userId = sortedUsers.get(i).getKey();
                SysUser user = userService.getById(userId);
                if (user == null) continue;
                Map<String, Object> item = new HashMap<>();
                item.put("username", user.getNickname() != null ? user.getNickname() : user.getUsername());
                item.put("activity", userActivityType.getOrDefault(userId, "活跃"));
                item.put("count", sortedUsers.get(i).getValue());
                item.put("time", user.getCreateTime());
                activeUsers.add(item);
            }
            result.put("activeUsers", activeUsers);
        } catch (Exception e) {
            result.put("activeUsers", new ArrayList<>());
            log.warn("[Dashboard] 活跃用户统计失败", e);
        }

        // ========== 7. 无感记录统计（推测 / 采用 / 命中率） ==========
        try {
            Map<String, Object> sense = new HashMap<>();
            sense.put("enabledUsers", userService.count(new LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getSenseEnabled, 1)));
            long predictions = predictionMapper.selectCount(null);
            long adopted = predictionMapper.selectCount(new LambdaQueryWrapper<EmotionPrediction>()
                    .eq(EmotionPrediction::getConfirmStatus, 1));
            sense.put("predictions", predictions);
            sense.put("adopted", adopted);
            // 命中率 = 用户采用数 / 推测总数（保留一位小数）
            sense.put("hitRate", predictions == 0 ? 0 : Math.round(adopted * 1000.0 / predictions) / 10.0);
            result.put("senseStats", sense);
        } catch (Exception e) {
            result.put("senseStats", new HashMap<>());
            log.warn("[Dashboard] 无感记录统计失败", e);
        }

        // ========== 8. 无感推测近 7 天趋势（生成 vs 采用） ==========
        try {
            List<Map<String, Object>> senseTrend = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate date = LocalDate.now().minusDays(i);
                LocalDateTime dayStart = LocalDateTime.of(date, LocalTime.MIN);
                LocalDateTime dayEnd = LocalDateTime.of(date, LocalTime.MAX);
                long generated = predictionMapper.selectCount(new LambdaQueryWrapper<EmotionPrediction>()
                        .ge(EmotionPrediction::getCreateTime, dayStart)
                        .le(EmotionPrediction::getCreateTime, dayEnd));
                long adopted = predictionMapper.selectCount(new LambdaQueryWrapper<EmotionPrediction>()
                        .ge(EmotionPrediction::getCreateTime, dayStart)
                        .le(EmotionPrediction::getCreateTime, dayEnd)
                        .eq(EmotionPrediction::getConfirmStatus, 1));
                Map<String, Object> item = new HashMap<>();
                item.put("date", date.toString());
                item.put("generated", generated);
                item.put("adopted", adopted);
                senseTrend.add(item);
            }
            result.put("senseTrend", senseTrend);
        } catch (Exception e) {
            result.put("senseTrend", new ArrayList<>());
            log.warn("[Dashboard] 无感推测趋势统计失败", e);
        }

        // ========== 9. 情绪风险预警统计（三级分布 + 未读） ==========
        try {
            Map<String, Object> risk = new HashMap<>();
            risk.put("total", riskAlertMapper.selectCount(null));
            risk.put("unread", riskAlertMapper.selectCount(new LambdaQueryWrapper<RiskAlert>()
                    .eq(RiskAlert::getIsRead, 0)));
            risk.put("l1", riskAlertMapper.selectCount(new LambdaQueryWrapper<RiskAlert>()
                    .eq(RiskAlert::getAlertLevel, 1)));
            risk.put("l2", riskAlertMapper.selectCount(new LambdaQueryWrapper<RiskAlert>()
                    .eq(RiskAlert::getAlertLevel, 2)));
            risk.put("l3", riskAlertMapper.selectCount(new LambdaQueryWrapper<RiskAlert>()
                    .eq(RiskAlert::getAlertLevel, 3)));
            result.put("riskStats", risk);
        } catch (Exception e) {
            result.put("riskStats", new HashMap<>());
            log.warn("[Dashboard] 预警统计失败", e);
        }

        // ========== 10. 情绪指数 7 日趋势（手记 vs 推测确认 双线） ==========
        try {
            List<PsyDiary> weekDiaries = diaryService.list(new LambdaQueryWrapper<PsyDiary>()
                    .ge(PsyDiary::getDiaryDate, LocalDate.now().minusDays(6)));
            // date -> [手记指数和, 手记条数, 推测指数和, 推测条数]
            Map<LocalDate, double[]> acc = new TreeMap<>();
            for (PsyDiary d : weekDiaries) {
                int idx = EmotionConst.indexOf(d.getEmotion());
                if (idx <= 0 || d.getDiaryDate() == null) continue;
                double[] a = acc.computeIfAbsent(d.getDiaryDate(), k -> new double[4]);
                // 1=手记；2=确认推测、3=自动代记，统一归入「推测」这条线
                if (d.getSource() != null && d.getSource() != 1) { a[2] += idx; a[3]++; }
                else { a[0] += idx; a[1]++; }
            }
            List<Map<String, Object>> indexTrend = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate date = LocalDate.now().minusDays(i);
                double[] a = acc.getOrDefault(date, new double[4]);
                Map<String, Object> item = new HashMap<>();
                item.put("date", date.toString());
                // 当天没有该来源的数据时给 null，ECharts 会断开连线而不是画成 0
                item.put("manual", a[1] == 0 ? null : Math.round(a[0] / a[1]));
                item.put("inferred", a[3] == 0 ? null : Math.round(a[2] / a[3]));
                indexTrend.add(item);
            }
            result.put("emotionIndexTrend", indexTrend);
        } catch (Exception e) {
            result.put("emotionIndexTrend", new ArrayList<>());
            log.warn("[Dashboard] 情绪指数趋势统计失败", e);
        }

        return Result.success(result);
    }
}
