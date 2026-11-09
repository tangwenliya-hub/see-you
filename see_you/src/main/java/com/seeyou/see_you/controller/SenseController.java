package com.seeyou.see_you.controller;

import com.seeyou.see_you.dto.BehaviorReportDTO;
import com.seeyou.see_you.dto.SenseConfigDTO;
import com.seeyou.see_you.dto.SenseConfirmDTO;
import com.seeyou.see_you.entity.BehaviorFeature;
import com.seeyou.see_you.entity.SensePrivacyLog;
import com.seeyou.see_you.mapper.SensePrivacyLogMapper;
import com.seeyou.see_you.service.SenseService;
import com.seeyou.see_you.util.BizException;
import com.seeyou.see_you.util.CsvUtil;
import com.seeyou.see_you.util.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 无感记录模块控制器。
 *
 * <h2>隐私红线 · 修改本类前必读</h2>
 * <ol>
 *   <li><b>仅本人可见</b>：所有接口的 userId 一律取自 JWT（{@code request.getAttribute("userId")}），
 *       本类<b>不接受任何来自前端的用户标识入参</b>。行为特征与推测候选比普通内容更敏感，
 *       因此对它们施加<b>比内容更严格的访问控制</b>——<b>包括管理员在内，任何角色都无权查询他人的数据</b>。</li>
 *   <li><b>数据最小化</b>：BehaviorFeature 表只有整数与枚举，不含 URL、不含点击目标、不含任何用户输入文本。</li>
 *   <li><b>原文不落库</b>：{@code /sense/text/analyze} 的正文仅用于当次推理，不写表、不进日志。</li>
 *   <li><b>默认关闭</b>：开关未开启时，写接口直接丢弃数据返回 403，读接口返回空集合。</li>
 *   <li><b>可撤回</b>：支持一键清空、CSV 导出；数据保留期由 {@code app.sense.retention-days} 控制，
 *       到期由 {@code SenseScheduler} 物理删除。</li>
 * </ol>
 */
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/sense")
public class SenseController {

    @Resource
    private SenseService senseService;
    @Resource
    private SensePrivacyLogMapper privacyLogMapper;
    @Resource
    private HttpServletRequest request;

    /** 当前登录用户 ID：只认 JWT，永远不信任前端参数 */
    private Long viewerId() {
        Object v = request.getAttribute("userId");
        if (v == null) throw new BizException(401, "未登录");
        return Long.valueOf(String.valueOf(v));
    }

    // ==================== 1. 开关 ====================

    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        return Result.success(senseService.getConfig(viewerId()));
    }

    @PostMapping("/config")
    public Result<Boolean> updateConfig(@RequestBody(required = false) SenseConfigDTO dto) {
        if (dto == null) throw new BizException(400, "参数不能为空");
        senseService.updateConfig(viewerId(), dto);
        return Result.success();
    }

    // ==================== 2. 行为上报 ====================

    /**
     * 批量上报脱敏行为特征。
     * 前端应在内存里先聚合好，每 30 秒或离开页面时上报一次。
     */
    @PostMapping("/behavior/report")
    public Result<Integer> report(@RequestBody Map<String, Object> body) {
        Object items = body == null ? null : body.get("items");
        if (!(items instanceof List<?> raw) || raw.isEmpty()) throw new BizException(400, "上报内容为空");
        java.util.List<BehaviorReportDTO> list = new java.util.ArrayList<>();
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> m)) continue;
            BehaviorReportDTO d = new BehaviorReportDTO();
            d.setSceneCode(str(m.get("sceneCode")));
            d.setStayMs(lng(m.get("stayMs")));
            d.setVisitCnt(integer(m.get("visitCnt")));
            d.setClickCnt(integer(m.get("clickCnt")));
            d.setHourMask(lng(m.get("hourMask")));
            Object date = m.get("date");
            if (date != null) {
                try { d.setDate(LocalDate.parse(String.valueOf(date))); } catch (Exception ignored) { log.warn("可忽略的异常，已跳过: {}", ignored.toString()); }
            }
            list.add(d);
        }
        return Result.success(senseService.report(viewerId(), list));
    }

    /** 我的行为数据（自查用） */
    @GetMapping("/behavior/my")
    public Result<List<BehaviorFeature>> myBehavior(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long uid = viewerId();
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(30) : from;
        if (start.isAfter(end)) { LocalDate t = start; start = end; end = t; }
        return Result.success(senseService.myBehavior(uid, start, end));
    }

    /** 一键清空我的全部行为数据（数据可撤回权） */
    @DeleteMapping("/behavior")
    public Result<Integer> clearBehavior() {
        return Result.success(senseService.clearBehavior(viewerId()));
    }

    /** 导出我的行为数据 CSV（数据可携带权） */
    @GetMapping("/behavior/export")
    public org.springframework.http.ResponseEntity<byte[]> exportBehavior() throws IOException {
        Long uid = viewerId();
        java.util.List<BehaviorFeature> rows = senseService.myBehavior(uid,
                LocalDate.now().minusDays(365), LocalDate.now());
        StringBuilder sb = new StringBuilder();
        sb.append(CsvUtil.line("日期", "场景", "停留(分钟)", "进入次数", "点击次数", "活跃时段"));
        for (BehaviorFeature f : rows) {
            sb.append(CsvUtil.line(
                    CsvUtil.cell(f.getStatDate()),
                    CsvUtil.cell(f.getSceneCode()),
                    CsvUtil.cell(f.getStayMs() == null ? 0 : Math.round(f.getStayMs() / 60000.0)),
                    CsvUtil.cell(f.getVisitCnt()),
                    CsvUtil.cell(f.getClickCnt()),
                    CsvUtil.cell(hoursText(f.getHourMask()))));
        }
        byte[] body = (CsvUtil.BOM + sb).getBytes(StandardCharsets.UTF_8);
        String filename = URLEncoder.encode("我的无感行为数据.csv", StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
                .header("Content-Type", "text/csv;charset=UTF-8")
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + filename)
                .body(body);
    }

    /** 我的隐私操作日志（证明"我的数据我可控"） */
    @GetMapping("/privacy/logs")
    public Result<List<SensePrivacyLog>> myPrivacyLogs() {
        Long uid = viewerId();
        return Result.success(privacyLogMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SensePrivacyLog>()
                        .eq(SensePrivacyLog::getUserId, uid)
                        .orderByDesc(SensePrivacyLog::getCreateTime)
                        .last("LIMIT 50")));
    }

    // ==================== 3. 推测 ====================

    /** 今日待确认推测 */
    @GetMapping("/predict/today")
    public Result<Map<String, Object>> today() {
        return Result.success(senseService.today(viewerId()));
    }

    /** 主动触发一次推测（用户点「帮我猜一下」） */
    @PostMapping("/predict/generate")
    public Result<Map<String, Object>> generate(@RequestBody(required = false) Map<String, Object> body) {
        String text = body == null ? null : str(body.get("text"));
        return Result.success(senseService.generate(viewerId(), 2, text));
    }

    /** 采用推测 → 生成正式日记(source=2) */
    @PostMapping("/predict/{id:\\d+}/confirm")
    public Result<Long> confirm(@PathVariable Long id,
                                @RequestBody(required = false) SenseConfirmDTO dto) {
        if (dto == null) dto = new SenseConfirmDTO();
        return Result.success(senseService.confirm(viewerId(), id, dto));
    }

    /** 忽略该推测 */
    @PostMapping("/predict/{id:\\d+}/reject")
    public Result<Boolean> reject(@PathVariable Long id) {
        senseService.reject(viewerId(), id);
        return Result.success();
    }

    /**
     * 立刻自动代记一次（本人操作）。
     * 定时任务是每天 23:30 跑，白天想演示/想补当天记录时用这个接口。
     */
    @PostMapping("/auto-fill")
    public Result<Map<String, Object>> autoFill() {
        String skipReason = senseService.autoFill(viewerId(), true);
        boolean done = skipReason == null;
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("filled", done);
        m.put("message", done ? "已根据你今天的使用痕迹自动生成一条日记" : skipReason);
        return Result.success(m);
    }

    @GetMapping("/predict/history")
    public Result<Map<String, Object>> history(@RequestParam(defaultValue = "1") Integer page,
                                               @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(senseService.history(viewerId(), page == null ? 1 : page, size == null ? 10 : size));
    }

    /** 命中率统计（论文实验数据来源） */
    @GetMapping("/accuracy")
    public Result<Map<String, Object>> accuracy() {
        return Result.success(senseService.accuracy(viewerId()));
    }

    // ==================== 4. 文本即时分析 ====================

    /**
     * 短文本情感分析。
     * 请求结束即销毁，<b>原文不会写入任何表，也不会打印进日志</b>。
     */
    @PostMapping("/text/analyze")
    public Result<Map<String, Object>> analyzeText(@RequestBody Map<String, Object> body) {
        String text = body == null ? null : str(body.get("text"));
        return Result.success(senseService.analyzeText(viewerId(), text));
    }

    // ==================== 5. 可视化数据 ====================

    /** 特征-情绪关联（散点 / 热力 / 雷达） */
    @GetMapping("/correlation")
    public Result<Map<String, Object>> correlation(@RequestParam(defaultValue = "90") Integer days) {
        return Result.success(senseService.correlation(viewerId(), days == null ? 90 : days));
    }

    /** 手动录入 vs 系统推测 双序列 */
    @GetMapping("/timeline")
    public Result<Map<String, Object>> timeline(@RequestParam(defaultValue = "30") Integer days) {
        return Result.success(senseService.timeline(viewerId(), days == null ? 30 : days));
    }

    // ==================== 6. 维护 ====================

    /** 重新加载词典（后台改词之后调用） */
    @PostMapping("/lexicon/reload")
    public Result<Integer> reloadLexicon() {
        senseService.reloadLexicon();
        return Result.success(senseService.lexiconSize());
    }

    // ==================== 小工具 ====================

    /** 把 24 位活跃掩码翻译成 "9,14,22时" 这种人能读的形式 */
    private String hoursText(Long mask) {
        if (mask == null || mask == 0) return "-";
        StringBuilder sb = new StringBuilder();
        for (int h = 0; h < 24; h++) {
            if ((mask & (1L << h)) != 0) {
                if (sb.length() > 0) sb.append('/');
                sb.append(h).append("时");
            }
        }
        return sb.toString();
    }

    private String str(Object o) { return o == null ? null : String.valueOf(o); }
    private Long lng(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception e) { return 0L; }
    }
    private Integer integer(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception e) { return 0; }
    }
}
