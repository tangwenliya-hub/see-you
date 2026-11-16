package com.seeyou.see_you.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeyou.see_you.entity.PsyAiWeeklyReport;
import com.seeyou.see_you.entity.PsyDiary;
import com.seeyou.see_you.mapper.PsyAiWeeklyReportMapper;
import com.seeyou.see_you.service.AiAssistantService;
import com.seeyou.see_you.service.PsyDiaryService;
import com.seeyou.see_you.util.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * AI 生活助手三件套（H5/PC 端）：
 * <ul>
 *   <li>GET  /ai/report/weekly —— 情绪周报：汇总近 7 天日记与生活因子（睡眠/压力/运动），LLM 生成温暖周报；
 *       按 (user_id, week_start) 缓存，同一天重复打开不重复调大模型，点「刷新」才重新生成。</li>
 *   <li>POST /ai/diary/polish  —— 日记润色：流水账 → 通顺日记草稿，只在编辑器里替换文本，不落库不记来源。</li>
 *   <li>POST /ai/diary/voice   —— 语音写日记：前端录音转 WAV 上传，ASR 转文字回填编辑框；转写内容不落库。</li>
 * </ul>
 * 隐私红线（与预警模块同标准）：发给大模型的只有内容本身与统计摘要，不携带任何身份信息；
 * userId 一律从 token 取，接口不收 userId 入参。
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class AiAssistantController {

    @Resource
    private AiAssistantService aiAssistantService;
    @Resource
    private PsyDiaryService diaryService;
    @Resource
    private PsyAiWeeklyReportMapper weeklyReportMapper;

    /** 情绪字典真源（与前端 utils/constants.js 一致：1开心 2平静 3疲惫 4难过 5生气 6焦虑 7兴奋 8满足 9其他） */
    private static final Map<Integer, String> EMOTION_NAMES = Map.of(
            1, "开心", 2, "平静", 3, "疲惫", 4, "难过", 5, "生气",
            6, "焦虑", 7, "兴奋", 8, "满足", 9, "其他");

    /** 语音文件上限：30 秒 16kHz 单声道 WAV 约 1MB，给足余量即可 */
    private static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;
    /** 周报纳入的单篇日记正文截断长度（控制 prompt 体量） */
    private static final int SNIPPET_LEN = 100;

    // ==================== 情绪周报 ====================

    /**
     * 获取（或生成）本周情绪周报。报告覆盖「近 7 天含今天」。
     *
     * @param refresh true = 忽略缓存强制重新生成（前端「刷新」按钮）
     */
    @GetMapping("/report/weekly")
    public Result<Map<String, Object>> weeklyReport(@RequestParam(defaultValue = "false") boolean refresh,
                                                    HttpServletRequest request) {
        Long uid = currentUserId(request);
        if (uid == null) return Result.error(401, "未登录");

        LocalDate weekEnd = LocalDate.now();
        LocalDate weekStart = weekEnd.minusDays(6);

        // 1. 缓存命中：同一天内重复打开看板不重复调大模型
        if (!refresh) {
            PsyAiWeeklyReport cached = weeklyReportMapper.selectOne(new LambdaQueryWrapper<PsyAiWeeklyReport>()
                    .eq(PsyAiWeeklyReport::getUserId, uid)
                    .eq(PsyAiWeeklyReport::getWeekStart, weekStart));
            if (cached != null && cached.getContent() != null && !cached.getContent().isBlank()) {
                Map<String, Object> data = new HashMap<>();
                data.put("content", cached.getContent());
                data.put("weekStart", weekStart);
                data.put("weekEnd", weekEnd);
                data.put("diaryCount", cached.getDiaryCount());
                data.put("cached", true);
                return Result.success(data);
            }
        }

        // 2. 组装数据摘要（只含内容与数字，不含身份信息）
        List<PsyDiary> diaries = diaryService.list(new LambdaQueryWrapper<PsyDiary>()
                .eq(PsyDiary::getUserId, uid)
                .between(PsyDiary::getDiaryDate, weekStart, weekEnd)
                .orderByAsc(PsyDiary::getDiaryDate)
                .orderByAsc(PsyDiary::getDiaryId));
        if (diaries.isEmpty()) {
            return Result.error(400, "最近一周还没有日记记录，先去写一篇，周报才会好看哦");
        }

        String digest = buildDigest(diaries, weekStart, weekEnd);

        // 3. 调大模型生成（失败时抛 AiUnavailableException → 全局处理器 / 本地 catch 转友好提示）
        String content;
        try {
            content = aiAssistantService.weeklyReport(digest);
        } catch (AiAssistantService.AiUnavailableException e) {
            return Result.error(400, e.getMessage());
        }

        // 4. 落库缓存（存在则覆盖）
        PsyAiWeeklyReport report = new PsyAiWeeklyReport();
        report.setUserId(uid);
        report.setWeekStart(weekStart);
        report.setWeekEnd(weekEnd);
        report.setContent(content);
        report.setDiaryCount(diaries.size());
        report.setCreateTime(java.time.LocalDateTime.now());
        PsyAiWeeklyReport exist = weeklyReportMapper.selectOne(new LambdaQueryWrapper<PsyAiWeeklyReport>()
                .eq(PsyAiWeeklyReport::getUserId, uid)
                .eq(PsyAiWeeklyReport::getWeekStart, weekStart));
        if (exist != null) {
            report.setReportId(exist.getReportId());
            weeklyReportMapper.updateById(report);
        } else {
            weeklyReportMapper.insert(report);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("content", content);
        data.put("weekStart", weekStart);
        data.put("weekEnd", weekEnd);
        data.put("diaryCount", diaries.size());
        data.put("cached", false);
        return Result.success(data);
    }

    /**
     * 把一周日记压成给大模型看的纯文本摘要：逐日一行（情绪/强度/睡眠/压力/运动/正文截断），
     * 结尾附一周聚合统计。不含 userId、昵称等任何身份信息（隐私红线）。
     */
    private String buildDigest(List<PsyDiary> diaries, LocalDate weekStart, LocalDate weekEnd) {
        StringBuilder sb = new StringBuilder();
        sb.append("以下是用户 ").append(weekStart).append(" 至 ").append(weekEnd).append("（近7天）的日记与生活数据：\n\n");

        Map<Integer, Integer> emotionCount = new TreeMap<>();
        BigDecimal sleepSum = BigDecimal.ZERO;
        int sleepDays = 0, stressSum = 0, stressDays = 0, exerciseSum = 0;

        for (PsyDiary d : diaries) {
            sb.append(d.getDiaryDate()).append("｜情绪：").append(EMOTION_NAMES.getOrDefault(d.getEmotion(), "未知"));
            if (d.getIntensity() != null) sb.append("（强度").append(d.getIntensity()).append("）");
            if (d.getSleepHours() != null) sb.append("｜睡眠").append(d.getSleepHours()).append("小时");
            if (d.getStressLevel() != null) sb.append("｜压力自评").append(d.getStressLevel()).append("/5");
            if (d.getExerciseMin() != null && d.getExerciseMin() > 0) sb.append("｜运动").append(d.getExerciseMin()).append("分钟");
            sb.append("\n日记：").append(truncate(d.getDiaryContent())).append("\n\n");

            emotionCount.merge(d.getEmotion(), 1, Integer::sum);
            if (d.getSleepHours() != null) { sleepSum = sleepSum.add(d.getSleepHours()); sleepDays++; }
            if (d.getStressLevel() != null) { stressSum += d.getStressLevel(); stressDays++; }
            if (d.getExerciseMin() != null) exerciseSum += d.getExerciseMin();
        }

        sb.append("—— 一周聚合 ——\n");
        sb.append("记录天数：").append(diaries.size()).append(" 天\n");
        sb.append("情绪分布：");
        emotionCount.forEach((k, v) -> sb.append(EMOTION_NAMES.getOrDefault(k, "未知")).append("×").append(v).append(" "));
        sb.append("\n");
        if (sleepDays > 0) sb.append("平均睡眠：").append(sleepSum.divide(BigDecimal.valueOf(sleepDays), 1, RoundingMode.HALF_UP)).append("小时（").append(sleepDays).append("天有记录）\n");
        if (stressDays > 0) sb.append("平均压力自评：").append(BigDecimal.valueOf(stressSum).divide(BigDecimal.valueOf(stressDays), 1, RoundingMode.HALF_UP)).append("/5（").append(stressDays).append("天有记录）\n");
        if (exerciseSum > 0) sb.append("总运动时长：").append(exerciseSum).append("分钟\n");
        return sb.toString();
    }

    private String truncate(String s) {
        if (s == null) return "（无正文）";
        String t = s.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) return "（无正文）";
        return t.length() <= SNIPPET_LEN ? t : t.substring(0, SNIPPET_LEN) + "…";
    }

    // ==================== 日记润色 ====================

    /** 流水账 → 通顺日记草稿。只返回文本，不落库：用户点「采用」后仍在编辑器里自行修改、自行保存。 */
    @PostMapping("/diary/polish")
    public Result<Map<String, Object>> polishDiary(@RequestBody Map<String, String> body, HttpServletRequest request) {
        Long uid = currentUserId(request);
        if (uid == null) return Result.error(401, "未登录");

        String content = body == null ? null : body.get("content");
        if (content == null || content.isBlank()) {
            return Result.error(400, "先写点什么，AI 才有的润色哦");
        }
        if (content.length() > 2000) {
            return Result.error(400, "内容太长啦，润色支持最多 2000 字");
        }
        try {
            String polished = aiAssistantService.polishDiary(content);
            return Result.success(Map.of("content", polished));
        } catch (AiAssistantService.AiUnavailableException e) {
            return Result.error(400, e.getMessage());
        }
    }

    // ==================== 语音写日记 ====================

    /**
     * 语音转文字：前端录音（MediaRecorder）→ 转成 16kHz 单声道 WAV → 上传 → ASR → 返回文字。
     * 转写结果只在响应里返回，不落库；用户确认后作为普通日记正文保存（与手动输入完全同权）。
     */
    @PostMapping("/diary/voice")
    public Result<Map<String, Object>> voiceToText(@RequestParam("file") MultipartFile file,
                                                   HttpServletRequest request) {
        Long uid = currentUserId(request);
        if (uid == null) return Result.error(401, "未登录");

        if (file == null || file.isEmpty()) {
            return Result.error(400, "没有收到录音，请再试一次");
        }
        if (file.getSize() > MAX_AUDIO_BYTES) {
            return Result.error(400, "录音太长啦，请控制在 1 分钟以内");
        }
        try {
            String text = aiAssistantService.transcribe(file.getBytes(), file.getOriginalFilename());
            return Result.success(Map.of("text", text));
        } catch (AiAssistantService.AiUnavailableException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.warn("[AI助手] 语音上传处理失败: {}", e.getMessage());
            return Result.error(400, "语音识别暂时不可用，请稍后再试或直接手动输入");
        }
    }

    // ==================== 工具 ====================

    /** userId 只从 token 解析结果里取（JwtInterceptor 注入），绝不接收前端入参 */
    private Long currentUserId(HttpServletRequest request) {
        Object v = request.getAttribute("userId");
        if (v instanceof Long l) return l;
        if (v instanceof Integer i) return i.longValue();
        if (v instanceof String s && s.matches("\\d+")) return Long.valueOf(s);
        return null;
    }
}
