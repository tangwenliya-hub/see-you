package com.seeyou.see_you.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seeyou.see_you.entity.SysLoginAttempt;
import com.seeyou.see_you.entity.SysPwdReset;
import com.seeyou.see_you.entity.SysUser;
import com.seeyou.see_you.entity.SysUserSession;
import com.seeyou.see_you.mapper.SysLoginAttemptMapper;
import com.seeyou.see_you.mapper.SysPwdResetMapper;
import com.seeyou.see_you.mapper.SysUserSessionMapper;
import com.seeyou.see_you.util.CaptchaUtil;
import com.seeyou.see_you.util.ClientIp;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账号安全增强：服务端验证码 / 登录限流锁定 / 多设备会话 / 邮箱找回密码
 */
@Service
public class AccountSecurityService {

    @Resource private SysLoginAttemptMapper attemptMapper;
    @Resource private SysUserSessionMapper sessionMapper;
    @Resource private SysPwdResetMapper pwdResetMapper;
    @Resource private SysUserService sysUserService;
    @Resource private MailAsyncService mailAsyncService;

    @Value("${spring.mail.username:}")
    private String mailFrom;
    /** 开发联调用：置 true 时验证码会打印到后端日志（默认关闭） */
    @Value("${app.dev-captcha-log:false}")
    private boolean devCaptchaLog;

    /**
     * 是否部署在反向代理/云边缘之后（nginx、Railway 等）。
     * 与限流器读同一个开关：开着才采信代理头，且只取不可伪造的那一端。
     * 生产（application-prod.yml）为 true，本地直跑为 false。
     */
    @Value("${app.rate-limit.trust-proxy:false}")
    private boolean trustProxy;

    private static final int MAX_FAIL = 5;
    private static final int LOCK_MINUTES = 10;
    /** 密码学安全随机数（验证码 / 重置码用，不可用 Math.random） */
    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();

    // ============ 1. 服务端图形验证码 ============
    private static class CaptchaEntry {
        final String code;
        final long expire;
        CaptchaEntry(String c) { this.code = c; this.expire = System.currentTimeMillis() + 5 * 60 * 1000L; }
        boolean alive() { return System.currentTimeMillis() < expire; }
    }
    private final Map<String, CaptchaEntry> captchaStore = new ConcurrentHashMap<>();
    /** 上次清理过期验证码的时间戳（避免每次请求都遍历） */
    private volatile long lastCaptchaSweep = 0L;

    /** 生成验证码：{captchaKey, captchaImage(base64)} */
    public Map<String, String> createCaptcha() {
        // 内存防泄漏：每 60 秒顺手清一次过期验证码（验证码 5 分钟过期，正常量级下这个遍历开销可忽略）
        long now = System.currentTimeMillis();
        if (now - lastCaptchaSweep > 60_000L) {
            lastCaptchaSweep = now;
            captchaStore.values().removeIf(e -> !e.alive());
        }
        String key = CaptchaUtil.newKey();
        String code = CaptchaUtil.randomCode();
        captchaStore.put(key, new CaptchaEntry(code));
        if (devCaptchaLog) {
            System.out.println("[Captcha] key=" + key + " code=" + code);
        }
        Map<String, String> data = new HashMap<>();
        data.put("captchaKey", key);
        data.put("captchaImage", CaptchaUtil.render(code));
        return data;
    }

    /** 开发模式查看验证码答案（app.dev-captcha-log=true 时可用） */
    public String peekCaptcha(String key) {
        if (!devCaptchaLog || key == null) return null;
        CaptchaEntry e = captchaStore.get(key);
        return (e != null && e.alive()) ? e.code : null;
    }

    /** 校验验证码；正确即销毁（一次性） */
    public boolean verifyCaptcha(String key, String code) {
        if (key == null || code == null) return false;
        CaptchaEntry e = captchaStore.remove(key);
        return e != null && e.alive() && e.code.equalsIgnoreCase(code.trim());
    }

    // ============ 2. 登录失败计数 / 锁定 ============
    /** 登录前检查；返回 null 表示未锁定，否则返回提示文案 */
    public String checkLock(String username) {
        SysLoginAttempt a = getAttempt(username);
        if (a == null || a.getLockUntil() == null) return null;
        if (LocalDateTime.now().isBefore(a.getLockUntil())) {
            long min = java.time.Duration.between(LocalDateTime.now(), a.getLockUntil()).toMinutes() + 1;
            return "失败次数过多，账号已锁定，请约 " + min + " 分钟后再试";
        }
        return null;
    }

    /** 登录成功：清空失败计数 */
    public void clearFail(String username) {
        SysLoginAttempt a = getAttempt(username);
        if (a != null) attemptMapper.deleteById(a.getId());
    }

    /** 登录失败：记录一次；达到上限则锁定。返回 null 表示无需提示 */
    public String recordFail(String username) {
        SysLoginAttempt a = getAttempt(username);
        int next = (a == null || a.getFailCount() == null ? 0 : a.getFailCount()) + 1;
        if (a == null) {
            a = new SysLoginAttempt();
            a.setUsername(username);
        }
        a.setFailCount(next);
        a.setUpdateTime(LocalDateTime.now());
        if (next >= MAX_FAIL) {
            a.setLockUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            a.setFailCount(0);
        }
        if (a.getId() == null) attemptMapper.insert(a);
        else attemptMapper.updateById(a);
        if (a.getLockUntil() != null && LocalDateTime.now().isBefore(a.getLockUntil())) {
            return "连续失败 " + MAX_FAIL + " 次，账号锁定 " + LOCK_MINUTES + " 分钟";
        }
        return null;
    }

    private SysLoginAttempt getAttempt(String username) {
        return attemptMapper.selectOne(new LambdaQueryWrapper<SysLoginAttempt>()
                .eq(SysLoginAttempt::getUsername, username).last("LIMIT 1"));
    }

    // ============ 3. 多设备会话 ============
    /** 登录成功后记录一条会话 */
    public void createSession(Long userId, String token, HttpServletRequest request) {
        SysUserSession s = new SysUserSession();
        s.setUserId(userId);
        s.setToken(token);
        s.setIp(getIp(request));
        s.setUserAgent(request.getHeader("User-Agent"));
        s.setDevice(parseDevice(request.getHeader("User-Agent")));
        s.setStatus(1);
        s.setLoginTime(LocalDateTime.now());
        s.setLastActiveTime(LocalDateTime.now());
        // 同一设备重复登录时顶替旧会话（置为下线），否则「登录设备」列表会被
        // 同一条「电脑 · 浏览器」刷屏——微信/QQ 也是这个语义：同设备只保留最新登录
        sessionMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysUserSession>()
                .eq(SysUserSession::getUserId, userId)
                .eq(SysUserSession::getDevice, s.getDevice())
                .eq(SysUserSession::getIp, s.getIp())
                .eq(SysUserSession::getStatus, 1)
                .set(SysUserSession::getStatus, 0));
        sessionMapper.insert(s);
    }

    /** token 对应会话是否仍在线（被踢后 false） */
    public boolean isSessionActive(String token) {
        if (token == null) return false;
        SysUserSession s = sessionMapper.selectOne(new LambdaQueryWrapper<SysUserSession>()
                .eq(SysUserSession::getToken, token).eq(SysUserSession::getStatus, 1).last("LIMIT 1"));
        return s != null;
    }

    /** 活跃心跳（按 token 做 60 秒节流：每个请求都 UPDATE 会话表太浪费） */
    private final Map<String, Long> lastTouchAt = new ConcurrentHashMap<>();

    public void touch(String token) {
        if (token == null) return;
        long now = System.currentTimeMillis();
        Long last = lastTouchAt.get(token);
        if (last != null && now - last < 60_000L) return;   // 一分钟内已同步过，跳过
        lastTouchAt.put(token, now);
        // 顺手清理退出的 token 记录，防止 Map 无限膨胀
        if (lastTouchAt.size() > 5000) {
            lastTouchAt.entrySet().removeIf(en -> now - en.getValue() > 10 * 60_000L);
        }
        sessionMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysUserSession>()
                .eq(SysUserSession::getToken, token)
                .set(SysUserSession::getLastActiveTime, LocalDateTime.now()));
    }

    /** 某用户的会话列表（current 标记当前 token） */
    public List<Map<String, Object>> listSessions(Long userId, String currentToken) {
        List<SysUserSession> list = sessionMapper.selectList(new LambdaQueryWrapper<SysUserSession>()
                .eq(SysUserSession::getUserId, userId).orderByDesc(SysUserSession::getLoginTime));
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUserSession s : list) {
            Map<String, Object> m = new HashMap<>();
            m.put("sessionId", s.getSessionId());
            m.put("device", s.getDevice());
            m.put("ip", s.getIp());
            m.put("status", s.getStatus());
            m.put("loginTime", s.getLoginTime());
            m.put("lastActiveTime", s.getLastActiveTime());
            m.put("current", tokenEquals(s.getToken(), currentToken));
            result.add(m);
        }
        return result;
    }

    private boolean tokenEquals(String a, String b) {
        return a != null && a.equals(b);
    }

    /** 踢掉指定会话（仅本人） */
    public boolean kickSession(Long userId, Long sessionId) {
        SysUserSession s = sessionMapper.selectById(sessionId);
        if (s == null || !s.getUserId().equals(userId)) return false;
        s.setStatus(0);
        sessionMapper.updateById(s);
        return true;
    }

    /** 一键踢掉除当前设备外的所有会话 */
    public int kickOthers(Long userId, String currentToken) {
        List<SysUserSession> list = sessionMapper.selectList(new LambdaQueryWrapper<SysUserSession>()
                .eq(SysUserSession::getUserId, userId).eq(SysUserSession::getStatus, 1));
        int n = 0;
        for (SysUserSession s : list) {
            if (tokenEquals(s.getToken(), currentToken)) continue;
            s.setStatus(0);
            sessionMapper.updateById(s);
            n++;
        }
        return n;
    }

    /** 注销账号：清空该用户全部会话（注销后所有设备立即下线） */
    public int deleteAllSessions(Long userId) {
        return sessionMapper.delete(new LambdaQueryWrapper<SysUserSession>()
                .eq(SysUserSession::getUserId, userId));
    }

    /**
     * 客户端 IP：统一走 {@link ClientIp}，与限流器同一套抗伪造逻辑。
     *
     * <p>历史实现取的是 {@code X-Forwarded-For} 的<b>第一个</b>值——那是客户端可以自己填的位置，
     * 结果是会话列表里能出现任意伪造 IP（排查入侵时完全不可信），按 IP 的统计也会被污染。
     * 现在 trustProxy=true 时取 XFF 最后一个值（边缘代理追加、客户端改不了）。
     */
    private String getIp(HttpServletRequest request) {
        return ClientIp.resolve(request, trustProxy);
    }

    private String parseDevice(String ua) {
        if (ua == null) return "未知设备";
        String s = ua.toLowerCase();
        if (s.contains("iphone") || s.contains("ipad")) return "iOS · 浏览器";
        if (s.contains("android")) return "Android · 浏览器";
        if (s.contains("mac os")) return "Mac · 浏览器";
        return "电脑 · 浏览器";
    }

    // ============ 4. 邮箱找回密码 ============
    /** 第一步：发送重置码。返回 {token, sent, devCode, email} */
    public Map<String, Object> sendResetCode(String account) {
        Map<String, Object> res = new HashMap<>();
        SysUser user = sysUserService.lambdaQuery()
                .eq(SysUser::getUsername, account)
                .or().eq(SysUser::getEmail, account)
                .one();
        if (user == null) {
            res.put("ok", false);
            res.put("msg", "未找到该用户名或邮箱对应的账号");
            return res;
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            res.put("ok", false);
            res.put("msg", "该账号未绑定邮箱，请联系管理员重置密码");
            return res;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        // 密码学安全随机数：重置码可预测 = 可被遍历利用
        String code = String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));
        SysPwdReset r = new SysPwdReset();
        r.setUserId(user.getUserId());
        r.setToken(token);
        r.setCode(code);
        r.setExpireTime(LocalDateTime.now().plusMinutes(10));
        r.setUsed(0);
        r.setCreateTime(LocalDateTime.now());
        pwdResetMapper.insert(r);

        // 邮件发送异步化：请求线程不等 SMTP（外部网络调用可能阻塞数秒）。
        // 「sent」语义变为「已委托发送」；devCode 的判定保持原样——只有未配置 SMTP
        // 且显式开启 --app.dev-captcha-log=true 时才返回验证码，安全语义不变。
        boolean mailConfigured = mailFrom != null && !mailFrom.isBlank();
        if (mailConfigured) {
            mailAsyncService.sendResetCode(user.getEmail(), code);
        }
        res.put("ok", true);
        res.put("sent", mailConfigured);
        res.put("token", token);
        // 安全：验证码绝不能直接下发。仅在显式开启开发开关（--app.dev-captcha-log=true）且未配置 SMTP 时返回，方便本地调试
        res.put("devCode", (!mailConfigured && devCaptchaLog) ? code : null);
        res.put("email", maskEmail(user.getEmail()));
        return res;
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(0, at));
        return email.charAt(0) + "***" + email.substring(at);
    }

    /** 第二步：校验验证码 + 设置新密码 */
    public Map<String, Object> resetPassword(String token, String code, String newPassword) {
        Map<String, Object> res = new HashMap<>();
        if (newPassword == null || newPassword.length() < 6) {
            res.put("ok", false); res.put("msg", "新密码至少 6 位"); return res;
        }
        SysPwdReset r = pwdResetMapper.selectOne(new LambdaQueryWrapper<SysPwdReset>()
                .eq(SysPwdReset::getToken, token).eq(SysPwdReset::getUsed, 0)
                .orderByDesc(SysPwdReset::getId).last("LIMIT 1"));
        if (r == null) { res.put("ok", false); res.put("msg", "重置凭证无效"); return res; }
        if (r.getExpireTime() != null && LocalDateTime.now().isAfter(r.getExpireTime())) {
            res.put("ok", false); res.put("msg", "验证码已过期，请重新获取"); return res;
        }
        if (!r.getCode().equalsIgnoreCase(code == null ? "" : code.trim())) {
            res.put("ok", false); res.put("msg", "验证码不正确"); return res;
        }
        SysUser user = sysUserService.getById(r.getUserId());
        if (user == null) { res.put("ok", false); res.put("msg", "账号不存在"); return res; }
        user.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(newPassword));
        sysUserService.updateById(user);
        r.setUsed(1);
        pwdResetMapper.updateById(r);
        res.put("ok", true);
        res.put("username", user.getUsername());
        return res;
    }
}
