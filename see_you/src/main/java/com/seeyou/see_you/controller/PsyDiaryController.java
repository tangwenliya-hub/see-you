package com.seeyou.see_you.controller;

import com.seeyou.see_you.entity.PsyDiary;
import com.seeyou.see_you.service.PsyDiaryService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeyou.see_you.util.Result;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.seeyou.see_you.util.PageLimit;
import lombok.extern.slf4j.Slf4j;
import com.seeyou.see_you.config.RequireAdmin;

@Slf4j
@RestController
@RequestMapping("/diary")
public class PsyDiaryController {
    @Resource
    private PsyDiaryService psyDiaryService;
    @Resource
    private com.seeyou.see_you.service.RiskAlertService riskAlertService;
    @Resource
    private jakarta.servlet.http.HttpServletRequest request;

    /**
     * 危机通道：正文命中危机词就立刻生成关怀提示（不等 7 天统计窗口）。
     *
     * <p>为什么挂在写日记这里而不是定时任务里：危机表达的价值在「当下」，
     * 隔天扫描等于延迟处理。这里是同步调用，但只做字符串匹配 + 一次主键查询，开销可忽略；
     * 任何异常都必须吞掉 —— 关怀提示失败不能连累用户存日记。
     */
    private void scanCrisis(Long userId, String content) {
        if (userId == null || content == null || content.isBlank()) return;
        try {
            riskAlertService.evaluateCrisis(userId, content);
        } catch (Exception ignored) {
            // 静默：预警是附加能力，绝不能因为它的异常让日记保存失败（只打 debug 便于排查）
            log.debug("[日记] 危机词扫描失败，已忽略", ignored);
        }
    }

    /** 当前登录用户（拦截器已把 JWT 解析结果放进 request）；管理员 role=1 */
    private Long viewerId() {
        Object v = request.getAttribute("userId");
        return v != null ? Long.valueOf(String.valueOf(v)) : null;
    }
    private boolean isAdmin() {
        return Integer.valueOf(1).equals(request.getAttribute("role"));
    }

    @GetMapping("/{id:\\d+}")
    public Result<PsyDiary> getById(@PathVariable Long id){
        PsyDiary d = psyDiaryService.getById(id);
        if (d == null) return Result.error(404, "日记不存在");
        // 越权防护：普通用户只能看自己的日记
        if (!isAdmin() && !d.getUserId().equals(viewerId())) return Result.error(403, "只能查看自己的日记");
        return Result.success(d);
    }
    @GetMapping("/list")
    public Result<Page<PsyDiary>> list(@RequestParam(defaultValue = "1") Integer page,
                                       @RequestParam(defaultValue = "10") Integer size,
                                       @RequestParam(required = false) Long userId,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LambdaQueryWrapper<PsyDiary> wrapper = new LambdaQueryWrapper<>();
        // 越权防护：普通用户忽略前端传的 userId，强制只查自己的；管理员可查任意/全部
        if (!isAdmin()) userId = viewerId();
        if (userId != null) wrapper.eq(PsyDiary::getUserId, userId);
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(PsyDiary::getDiaryContent, keyword);
        }
        if (startDate != null) wrapper.ge(PsyDiary::getDiaryDate, startDate);
        if (endDate != null) wrapper.le(PsyDiary::getDiaryDate, endDate);
        wrapper.orderByDesc(PsyDiary::getDiaryDate);
        return Result.success(psyDiaryService.page(new Page<>(PageLimit.page(page), PageLimit.of(size, 10)), wrapper));
    }

    @PostMapping("/save")
    public Result<Boolean> save(@RequestBody PsyDiary diary){
        // userId 以后端 token 为准，防止改参替别人写日记
        if (!isAdmin() || diary.getUserId() == null) diary.setUserId(viewerId());
        // 来源由后端判定：用户主动保存的一律是亲笔（1），不允许前端伪造 2/3 绕过统计口径
        if (!isAdmin()) diary.setSource(1);
        boolean b = psyDiaryService.save(diary);
        // 统计接口有 30 秒手写缓存，写操作后必须主动失效，否则「刚写完看板还是旧数字」
        if (b) {
            StatisticsController.clearCache();
            scanCrisis(diary.getUserId(), diary.getDiaryContent());
        }
        return b ? Result.success() : Result.error(500, "保存失败");
    }
    @PutMapping("/update")
    public Result<Boolean> update(@RequestBody PsyDiary diary){
        PsyDiary old = psyDiaryService.getById(diary.getDiaryId());
        if (old == null) return Result.error(404, "日记不存在");
        if (!isAdmin() && !old.getUserId().equals(viewerId())) return Result.error(403, "只能修改自己的日记");
        // 不允许借 update 改归属
        diary.setUserId(old.getUserId());
        // 来源同样不可由前端改：编辑系统代记请走 /{id}/claim，避免「改一下就把推测洗成亲笔」
        if (!isAdmin()) diary.setSource(null);
        boolean b = psyDiaryService.updateById(diary);
        if (b) {
            StatisticsController.clearCache();
            // 编辑后的正文同样要过一遍危机词（用户可能当时没写，改的时候才写出来）
            scanCrisis(old.getUserId(), diary.getDiaryContent());
        }
        return b ? Result.success() : Result.error(500, "更新失败");
    }

    // ==================== 睡眠补录（近 7 天批量） ====================

    /** 近 7 天每天的日记数与现有 sleep_hours（供补录弹窗预填；预填取该天最后一篇的非空值） */
    @GetMapping("/sleep/week")
    public Result<List<Map<String, Object>>> sleepWeek() {
        Long uid = viewerId();
        if (uid == null) return Result.error(401, "未登录");
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6);
        List<PsyDiary> rows = psyDiaryService.lambdaQuery()
                .select(PsyDiary::getDiaryDate, PsyDiary::getSleepHours)
                .eq(PsyDiary::getUserId, uid)
                .ge(PsyDiary::getDiaryDate, from)
                .list();
        Map<LocalDate, List<PsyDiary>> byDate = new TreeMap<>();
        for (PsyDiary d : rows) {
            if (d.getDiaryDate() != null) byDate.computeIfAbsent(d.getDiaryDate(), k -> new ArrayList<>()).add(d);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = from.plusDays(i);
            List<PsyDiary> list = byDate.get(day);
            java.math.BigDecimal hours = null;
            if (list != null) {
                for (int j = list.size() - 1; j >= 0; j--) {
                    if (list.get(j).getSleepHours() != null) { hours = list.get(j).getSleepHours(); break; }
                }
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", day.toString());
            m.put("count", list == null ? 0 : list.size());
            m.put("sleepHours", hours);
            out.add(m);
        }
        return Result.success(out);
    }

    /**
     * 批量补录睡眠：按日期更新该用户当天所有日记的 sleep_hours。
     * 入参 [{date:"2026-10-24", hours:7.5}]，hours 为 null/空 视为清除该天睡眠值；
     * 只允许补录近 30 天内的日期，越界/非法项静默跳过。
     */
    @PostMapping("/sleep/batch")
    public Result<Integer> sleepBatch(@RequestBody List<Map<String, Object>> items) {
        Long uid = viewerId();
        if (uid == null) return Result.error(401, "未登录");
        if (items == null || items.isEmpty()) return Result.error(400, "没有要补录的日期");
        if (items.size() > 31) return Result.error(400, "一次最多补录 31 天");
        LocalDate today = LocalDate.now();
        LocalDate earliest = today.minusDays(30);
        int total = 0;
        for (Map<String, Object> it : items) {
            Object dateObj = it.get("date");
            if (dateObj == null) continue;
            LocalDate date;
            try { date = LocalDate.parse(String.valueOf(dateObj)); } catch (Exception e) { continue; }
            if (date.isBefore(earliest) || date.isAfter(today)) continue;
            java.math.BigDecimal hours = null;
            Object hObj = it.get("hours");
            if (hObj != null && !"".equals(String.valueOf(hObj).trim()) && !"null".equalsIgnoreCase(String.valueOf(hObj).trim())) {
                try { hours = new java.math.BigDecimal(String.valueOf(hObj).trim()); } catch (Exception e) { continue; }
                if (hours.signum() < 0 || hours.compareTo(java.math.BigDecimal.valueOf(24)) > 0) continue;
                hours = hours.setScale(1, java.math.RoundingMode.HALF_UP);
            }
            boolean updated = psyDiaryService.lambdaUpdate()
                    .eq(PsyDiary::getUserId, uid)
                    .eq(PsyDiary::getDiaryDate, date)
                    .set(PsyDiary::getSleepHours, hours)
                    .update();
            if (updated) total++;
        }
        if (total > 0) StatisticsController.clearCache();
        return Result.success(total);
    }
    /**
     * 把「系统代记」认领成亲笔日记：source 3 → 1。
     *
     * <p>代记的价值是「保底打卡」，但不能永远是机器写的。用户补一句话就能把它转正，
     * 转正后才开始计入情绪画像与经验值 —— 这样既不影响连续记录，也不会让经验值被机器刷上去。
     */
    @PutMapping("/{id:\\d+}/claim")
    public Result<Boolean> claim(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        PsyDiary old = psyDiaryService.getById(id);
        if (old == null) return Result.error(404, "日记不存在");
        if (!isAdmin() && !old.getUserId().equals(viewerId())) return Result.error(403, "只能修改自己的日记");
        if (!Integer.valueOf(3).equals(old.getSource())) return Result.error(400, "这条不是系统代记，直接编辑即可");

        Object c = body == null ? null : body.get("diaryContent");
        String content = c == null ? "" : String.valueOf(c).trim();
        if (content.isEmpty()) return Result.error(400, "补一句你的感受，这条才会变成你的日记");
        if (content.length() > 2000) return Result.error(400, "日记正文最多 2000 字");

        PsyDiary upd = new PsyDiary();
        upd.setDiaryId(id);
        upd.setDiaryContent(content);
        upd.setSource(1);
        Object emo = body.get("emotion");
        if (emo instanceof Number) {
            int e = ((Number) emo).intValue();
            if (e >= 1 && e <= 9) upd.setEmotion(e);
        }
        // 认领时顺手补的睡眠也要带上，否则用户填了却没保存
        Object sh = body.get("sleepHours");
        if (sh instanceof Number) {
            java.math.BigDecimal h = java.math.BigDecimal.valueOf(((Number) sh).doubleValue())
                    .setScale(1, java.math.RoundingMode.HALF_UP);
            if (h.signum() >= 0 && h.compareTo(java.math.BigDecimal.valueOf(24)) <= 0) upd.setSleepHours(h);
        }
        boolean b = psyDiaryService.updateById(upd);
        if (b) {
            StatisticsController.clearCache();
            scanCrisis(old.getUserId(), content);
        }
        return b ? Result.success() : Result.error(500, "保存失败");
    }

    @DeleteMapping("/{id:\\d+}")
    public Result<Boolean> remove(@PathVariable Long id){
        PsyDiary old = psyDiaryService.getById(id);
        if (old == null) return Result.success();
        if (!isAdmin() && !old.getUserId().equals(viewerId())) return Result.error(403, "只能删除自己的日记");
        boolean b = psyDiaryService.removeWithPrediction(id);
        if (b) StatisticsController.clearCache();
        return b ? Result.success() : Result.error(500, "删除失败");
    }

    // ========== AI 智能寄语 ==========
    @Value("${ai.api-url:}")
    private String aiApiUrl;
    @Value("${ai.api-key:}")
    private String aiApiKey;
    @Value("${ai.model:Qwen/Qwen2.5-7B-Instruct}")
    private String aiModel;

    /** AI 请求客户端：必须设超时，否则 AI 接口挂起会拖死 Tomcat 线程池 */
    private final RestTemplate restTemplate = new RestTemplate(
        new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
            setConnectTimeout(5_000);
            setReadTimeout(30_000);
        }}
    );

    private static final Map<Integer, String> EMOTION_MAP = Map.of(
        1, "开心", 2, "平静", 3, "疲惫", 4, "难过", 5, "生气",
        6, "焦虑", 7, "兴奋", 8, "满足", 9, "其他"
    );

    @GetMapping("/whisper")
    public Result<String> whisper(@RequestParam(required = false) Long userId) {
        // 安全：userId 一律以登录态为准，前端传参不信任（管理员可代查他人）
        Long uid = isAdmin() && userId != null ? userId : viewerId();
        if (uid == null) return Result.error(401, "未登录");
        // 1. 查最近 7 天日记
        LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);
        List<PsyDiary> recent = psyDiaryService.lambdaQuery()
            .eq(PsyDiary::getUserId, uid)
            .ge(PsyDiary::getDiaryDate, sevenDaysAgo)
            .orderByDesc(PsyDiary::getDiaryDate)
            .list();

        // 2. 如果没数据，返回默认话
        if (recent.isEmpty()) {
            return Result.success("记录每一天的心情，让 AI 陪你温柔成长 🌿");
        }

        // 3. 组装日记摘要
        StringBuilder diarySummary = new StringBuilder();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (PsyDiary d : recent) {
            String emo = EMOTION_MAP.getOrDefault(d.getEmotion(), "未知");
            String content = d.getDiaryContent() == null ? "" : d.getDiaryContent();
            if (content.length() > 80) content = content.substring(0, 80) + "...";
            diarySummary.append(String.format("%s【%s】%s%n",
                d.getDiaryDate().format(fmt), emo, content));
        }

        // 4. 组 prompt
        String systemPrompt = "你是一位温暖的心理咨询师，名叫『绪见』。"
            + "请根据用户最近的情绪日记，生成一句简短（不超过30字）的鼓励/寄语。"
            + "要温暖、有共情力、像朋友一样，不要说教，不要用emoji堆砌。";
        String userPrompt = "以下是我最近 7 天的日记：\n" + diarySummary
            + "\n请给我一句最触动的寄语：";

        // 5. 调 AI
        try {
            if (aiApiKey == null || aiApiKey.isBlank() || aiApiUrl.isBlank()) {
                return Result.success(needleDefault(recent));
            }
            Map<String, Object> body = new HashMap<>();
            body.put("model", aiModel);
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.add(Map.of("role", "user", "content", userPrompt));
            body.put("messages", messages);
            body.put("max_tokens", 120);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + aiApiKey);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<Map<String, Object>> entity =
                new org.springframework.http.HttpEntity<>(body, headers);

            org.springframework.http.ResponseEntity<Map> resp = restTemplate.postForEntity(aiApiUrl, entity, Map.class);
            Map<?, ?> data = resp.getBody();
            if (data != null && data.containsKey("choices")) {
                List<?> choices = (List<?>) data.get("choices");
                if (!choices.isEmpty()) {
                    Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                    Map<?, ?> msg = (Map<?, ?>) choice.get("message");
                    String content = (String) msg.get("content");
                    if (content != null && !content.isBlank()) {
                        return Result.success(content.trim());
                    }
                }
            }
            return Result.success(needleDefault(recent));
        } catch (Exception e) {
            return Result.success(needleDefault(recent));
        }
    }

    // AI 调用失败时的兜底：用本地模板但根据情绪调整
    private String needleDefault(List<PsyDiary> recent) {
        Map<Integer, Long> emoCount = new HashMap<>();
        recent.forEach(d -> emoCount.merge(d.getEmotion(), 1L, Long::sum));
        int top = emoCount.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey).orElse(1);
        return switch (top) {
            case 4 -> "难过的时候，别忘记自己值得被温柔接住 🌿";
            case 5 -> "生气是正常的，深呼吸，世界会柔软下来";
            case 6 -> "焦虑时，请记得：你已经做得很好了 ✨";
            case 3 -> "累了就休息，爱自己是第一优先级";
            case 1, 7, 8 -> "愿这份美好的心情，能延续一整天 ☀️";
            default -> "慢慢来也没关系，你在努力已经很棒了";
        };
    }

    // ============ 后台日记管理（管理员）============

    /** 管理员分页查看全站日记（keyword 搜正文，userId 过滤某用户） */
    @RequireAdmin
    @GetMapping("/admin/list")
    public Result<Page<PsyDiary>> adminList(@RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "10") Integer size,
                                            @RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) String keyword) {
        if (!isAdmin()) return Result.error(403, "无管理员权限");
        // 注意：MP 的 like(condition, col, val) 的 val 是立即求值的，必须先判空再取 trim
        String kw = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PsyDiary> p =
            psyDiaryService.page(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                    com.seeyou.see_you.util.PageLimit.page(page),
                    com.seeyou.see_you.util.PageLimit.of(size, 10)),
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PsyDiary>()
                    .eq(userId != null, PsyDiary::getUserId, userId)
                    .like(kw != null, PsyDiary::getDiaryContent, kw)
                    .orderByDesc(PsyDiary::getDiaryDate).orderByDesc(PsyDiary::getDiaryId));
        // 列表页不回传正文全文，只截断，减小响应体
        p.getRecords().forEach(d -> {
            if (d.getDiaryContent() != null && d.getDiaryContent().length() > 120) {
                d.setDiaryContent(d.getDiaryContent().substring(0, 120) + "…");
            }
        });
        return Result.success(p);
    }

    /** 管理员删除违规日记 */
    @RequireAdmin
    @DeleteMapping("/admin/{id:\\d+}")
    public Result<Boolean> adminRemove(@PathVariable Long id) {
        if (!isAdmin()) return Result.error(403, "无管理员权限");
        boolean b = psyDiaryService.removeWithPrediction(id);
        if (b) StatisticsController.clearCache();
        return b ? Result.success() : Result.error(500, "删除失败");
    }

    // ============ 数据导出（本人日记 CSV，Excel 友好）============

    /** 导出我的全部日记为 CSV（带 BOM，Excel 直接打开不乱码） */
    @GetMapping("/export")
    public org.springframework.http.ResponseEntity<byte[]> exportMine() throws java.io.IOException {
        Long uid = viewerId();
        if (uid == null) return org.springframework.http.ResponseEntity.status(401).build();
        List<PsyDiary> diaries = psyDiaryService.lambdaQuery()
            .eq(PsyDiary::getUserId, uid)
            .orderByAsc(PsyDiary::getDiaryDate).list();
        Map<Integer, String> names = Map.of(1, "开心", 2, "平静", 3, "疲惫", 4, "难过", 5, "生气",
            6, "焦虑", 7, "兴奋", 8, "满足", 9, "其他");
        // 转义 / BOM / 公式注入防护统一交给 CsvUtil（原来手写拼接没有 BOM，Excel 打开中文是乱码）
        StringBuilder sb = new StringBuilder();
        sb.append(com.seeyou.see_you.util.CsvUtil.line(
            "日期", "情绪", "强度", "睡眠时长(小时)", "日记内容"));
        for (PsyDiary d : diaries) {
            sb.append(com.seeyou.see_you.util.CsvUtil.line(
                com.seeyou.see_you.util.CsvUtil.cell(d.getDiaryDate()),
                com.seeyou.see_you.util.CsvUtil.cell(names.getOrDefault(d.getEmotion() == null ? 0 : d.getEmotion(), "其他")),
                com.seeyou.see_you.util.CsvUtil.cell(d.getIntensity()),
                com.seeyou.see_you.util.CsvUtil.cell(d.getSleepHours()),
                com.seeyou.see_you.util.CsvUtil.text(d.getDiaryContent())));
        }
        byte[] body = (com.seeyou.see_you.util.CsvUtil.BOM + sb)
            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String filename = java.net.URLEncoder.encode("我的日记导出.csv", java.nio.charset.StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
            .header("Content-Type", "text/csv;charset=UTF-8")
            .header("Content-Disposition", "attachment; filename*=UTF-8''" + filename)
            .body(body);
    }
}