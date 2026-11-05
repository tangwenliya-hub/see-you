package com.seeyou.see_you.controller;

import com.seeyou.see_you.entity.RiskAlert;
import com.seeyou.see_you.service.AccountSecurityService;
import com.seeyou.see_you.service.RiskAlertService;
import com.seeyou.see_you.service.RiskSseHub;
import com.seeyou.see_you.util.BizException;
import com.seeyou.see_you.util.JwtUtil;
import com.seeyou.see_you.util.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 情绪风险预警控制器。
 *
 * <h2>⚠️ 非医疗声明（务必保留）</h2>
 * 本模块的全部输出都是基于用户<b>自行记录</b>的情绪数据做出的<b>统计层面的状态提示</b>，
 * <b>不是抑郁障碍筛查工具，不构成任何形式的医疗诊断、治疗建议或用药指导</b>。
 * 所有响应携带 {@code disclaimer} 字段，前端必须在醒目位置展示。
 *
 * <h2>权限</h2>
 * 与无感记录模块一样：userId 只取自 JWT，任何角色都无法查询他人的预警记录。
 */
@RestController
@RequestMapping("/risk")
public class RiskAlertController {

    @Resource
    private RiskAlertService riskAlertService;
    @Resource
    private HttpServletRequest request;
    @Resource
    private RiskSseHub sseHub;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private AccountSecurityService securityService;

    private Long viewerId() {
        Object v = request.getAttribute("userId");
        if (v == null) return null;
        return Long.valueOf(String.valueOf(v));
    }

    /**
     * 管理端 SSE 实时预警流：有用户触发/升级预警时，在线管理员立刻收到事件。
     *
     * <p>⚠️ 鉴权说明：EventSource 无法携带 Authorization 头，token 只能走查询参数；
     * 本端点已在 CorsConfig 放行 JWT 拦截器，鉴权由本方法手动完成——
     * <b>token 有效 + 会话在线 + 管理员角色</b>，三者缺一不可，普通用户连上拿不到任何事件。</p>
     */
    @GetMapping("/admin-stream")
    public SseEmitter adminStream(@RequestParam(required = false) String token) {
        if (token == null || token.isBlank() || !jwtUtil.validateToken(token)) {
            throw new BizException(401, "未登录或token已过期");
        }
        if (!securityService.isSessionActive(token)) {
            throw new BizException(401, "该登录已被移除，请重新登录");
        }
        Integer role = jwtUtil.getRoleByToken(token);
        if (role == null || role < 1) throw new BizException(403, "无管理员权限");
        return sseHub.register(jwtUtil.getUserIdByToken(token));
    }

    /** 最近一条未确认预警（登录后弹窗用） */
    @GetMapping("/latest")
    public Result<Map<String, Object>> latest() {
        return Result.success(riskAlertService.latest(viewerId()));
    }

    /** 历史预警记录（分页） */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(riskAlertService.list(viewerId(),
                page == null ? 1 : page, size == null ? 10 : size));
    }

    /** 概览：未读数 / 累计次数 / 最高等级 / 当前阈值 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.success(riskAlertService.overview(viewerId()));
    }

    /** 用户点「我知道了」 */
    @PostMapping("/{id:\\d+}/ack")
    public Result<Boolean> ack(@PathVariable Long id) {
        riskAlertService.ack(viewerId(), id);
        return Result.success();
    }

    /** 标记已读 */
    @PostMapping("/{id:\\d+}/read")
    public Result<Boolean> markRead(@PathVariable Long id) {
        riskAlertService.markRead(viewerId(), id);
        return Result.success();
    }

    /**
     * 用户标记「这不是我」（误报反馈）。
     * 用途：预警弹出的内容与用户真实状态明显不符时，让用户一键反馈；
     * 后台据此统计准确率 = (触发总数 - 误报数) / 触发总数（论文量化指标）。
     * 危机词通道不支持标记，安全方向宁可错弹不可漏接。
     */
    @PostMapping("/{id:\\d+}/misreport")
    public Result<Boolean> markMisreport(@PathVariable Long id) {
        riskAlertService.markMisreport(viewerId(), id);
        return Result.success();
    }

    /**
     * 手动触发一次判定（调试用）。
     * 正常流程由定时任务每日扫描，这个接口方便现场改完阈值立刻看到效果。
     */
    @PostMapping("/evaluate")
    public Result<Map<String, Object>> evaluate() {
        RiskAlert a = riskAlertService.evaluate(viewerId());
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("triggered", a != null);
        m.put("level", a == null ? 0 : a.getAlertLevel());
        m.put("levelName", a == null ? "正常" : (Integer.valueOf(3).equals(a.getAlertLevel()) ? "高度关注"
                : Integer.valueOf(2).equals(a.getAlertLevel()) ? "中度提醒" : "轻度关注"));
        m.put("detail", a == null ? "当前窗口未达到任何预警阈值。" : a.getTriggerDetail());
        return Result.success(m);
    }
}
