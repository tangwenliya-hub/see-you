package com.seeyou.see_you.controller;

import com.seeyou.see_you.service.AccountSecurityService;
import com.seeyou.see_you.util.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 账号安全相关：服务端验证码 / 找回密码 / 多设备会话
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource private AccountSecurityService securityService;

    /** 获取图形验证码 */
    @GetMapping("/captcha")
    public Result<Map<String, String>> captcha() {
        return Result.success(securityService.createCaptcha());
    }

    /** 开发模式查看验证码答案（app.dev-captcha-log=true 时可用） */
    @GetMapping("/captcha/dev-code")
    public Result<Map<String, Object>> captchaDevCode(@RequestParam String key) {
        String code = securityService.peekCaptcha(key);
        if (code == null) return Result.error(404, "未开启开发模式或验证码已失效");
        Map<String, Object> data = new HashMap<>();
        data.put("code", code);
        return Result.success(data);
    }

    // ============ 找回密码 ============
    @PostMapping("/forgot/send")
    public Result<Map<String, Object>> forgotSend(@RequestBody Map<String, String> body) {
        String account = body.getOrDefault("account", "").trim();
        if (account.isEmpty()) return Result.error(400, "请输入用户名或邮箱");
        Map<String, Object> data = securityService.sendResetCode(account);
        if (!Boolean.TRUE.equals(data.get("ok"))) {
            return Result.error(400, String.valueOf(data.get("msg")));
        }
        return Result.success(data);
    }

    @PostMapping("/forgot/reset")
    public Result<Map<String, Object>> forgotReset(@RequestBody Map<String, String> body) {
        String token = body.get("token");
        String code = body.get("code");
        String pwd = body.get("password");
        if (token == null || code == null || pwd == null) return Result.error(400, "参数不完整");
        Map<String, Object> data = securityService.resetPassword(token, code, pwd);
        if (!Boolean.TRUE.equals(data.get("ok"))) {
            return Result.error(400, String.valueOf(data.get("msg")));
        }
        return Result.success(data);
    }

    // ============ 多设备会话（需登录） ============
    @GetMapping("/sessions")
    public Result<Map<String, Object>> sessions(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Map<String, Object> data = new HashMap<>();
        data.put("list", securityService.listSessions(userId, resolveToken(request)));
        return Result.success(data);
    }

    @DeleteMapping("/sessions/{sessionId:\\d+}")
    public Result<Boolean> kickSession(HttpServletRequest request, @PathVariable Long sessionId) {
        Long userId = (Long) request.getAttribute("userId");
        boolean ok = securityService.kickSession(userId, sessionId);
        return ok ? Result.success(true) : Result.error(400, "会话不存在或已被移除");
    }

    @DeleteMapping("/sessions/others")
    public Result<Map<String, Object>> kickOthers(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        int n = securityService.kickOthers(userId, resolveToken(request));
        Map<String, Object> data = new HashMap<>();
        data.put("count", n);
        return Result.success(data);
    }

    private String resolveToken(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) return token.substring(7);
        return null;
    }
}
