package com.seeyou.see_you.config;

import com.seeyou.see_you.util.ClientIp;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 高成本 / 敏感接口的滑动窗口限流。
 *
 * <p>覆盖三类风险面：
 * <ul>
 *   <li>爆破面：登录、注册、验证码、找回密码（账号侧另有 5 次锁定，这里按 IP 收口）</li>
 *   <li>烧钱面：AI 对话（每次调用都是真实的大模型 API 费用）</li>
 *   <li>重查询面：日记导出（一次拉全表）</li>
 * </ul>
 *
 * <p>实现按「IP + 接口组」滑动窗口计数，窗口内的请求时间戳放在 Deque 里，
 * 命中时把窗口外的旧时间戳弹出。数据结构按接口组分桶（固定 6 组），不存在
 * 无界增长；每分钟有请求时顺手清理过期条目。
 *
 * <p><b>开关</b>：{@code app.rate-limit.enabled=false} 可整体关闭（功能演示、压测场景用），
 * 阈值可用 {@code app.rate-limit.rules} 逐条覆盖，窗口固定 60 秒。
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    /** 默认规则：接口前缀 -> 窗口内最大次数（窗口固定 60 秒） */
    private static final Map<String, Long> DEFAULT_RULES = new LinkedHashMap<>();

    static {
        DEFAULT_RULES.put("/user/login", 10L);     // 10 次/分钟/IP
        DEFAULT_RULES.put("/user/register", 5L);
        DEFAULT_RULES.put("/auth/captcha", 20L);
        DEFAULT_RULES.put("/auth/forgot", 5L);     // 每次都可能触发邮件，从严
        DEFAULT_RULES.put("/aiChat", 30L);         // 大模型调用成本
        DEFAULT_RULES.put("/diary/export", 5L);    // 全表导出
    }

    /** 窗口长度：60 秒 */
    private static final long WINDOW_MS = 60_000L;

    /** 生效规则（默认值 + 配置覆盖） */
    private final Map<String, Long> rules = new LinkedHashMap<>(DEFAULT_RULES);

    /** 总开关：false 时直接放行，不做任何计数 */
    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    /**
     * 是否信任代理头（部署在 nginx 反代后面时置 true）。
     * 只信任 nginx 强制覆盖的 {@code X-Real-IP}（$remote_addr，客户端伪造不了）；
     * 不信任 {@code X-Forwarded-For} 的第一个值——$proxy_add_x_forwarded_for 是"追加"模式，
     * 客户端自带一个假 XFF 时第一个值就是假的，攻击者可以借此无限伪造 IP 绕过限流。
     * 本地直跑后端（不经过 nginx）时保持 false，直接用 TCP 连接的真实地址。
     */
    @Value("${app.rate-limit.trust-proxy:false}")
    private boolean trustProxy;

    /**
     * 阈值覆盖，格式：{@code /user/login=20,/aiChat=60}。
     * 只写需要调整的项，未写的沿用默认值；窗口固定 60 秒。
     */
    @Value("${app.rate-limit.rules:}")
    private String rulesConfig;

    /** key = ip|prefix -> 窗口内请求时间戳队列 */
    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();

    @PostConstruct
    public void initRules() {
        if (rulesConfig == null || rulesConfig.isBlank()) return;
        for (String part : rulesConfig.split(",")) {
            String seg = part.trim();
            if (seg.isEmpty()) continue;
            int eq = seg.indexOf('=');
            if (eq <= 0) continue;
            String prefix = seg.substring(0, eq).trim();
            try {
                long limit = Long.parseLong(seg.substring(eq + 1).trim());
                // 只覆盖已登记的接口组，避免配错路径造出一个永远不生效的规则
                if (DEFAULT_RULES.containsKey(prefix) && limit > 0) {
                    rules.put(prefix, limit);
                }
            } catch (NumberFormatException ignored) {
                // 配置写错就沿用默认值，不让启动失败
            }
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!enabled) return true;

        String path = request.getRequestURI();
        String prefix = matchPrefix(path);
        if (prefix == null) return true;

        long now = System.currentTimeMillis();
        long limit = rules.get(prefix);
        String key = clientIp(request) + "|" + prefix;

        Deque<Long> dq = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (dq) {
            while (!dq.isEmpty() && now - dq.peekFirst() > WINDOW_MS) dq.pollFirst();
            if (dq.size() >= limit) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":429,\"msg\":\"操作过于频繁，请稍后再试\",\"data\":null}");
                return false;
            }
            dq.addLast(now);
        }
        return true;
    }

    private String matchPrefix(String path) {
        for (String prefix : rules.keySet()) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) return prefix;
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        // 统一走 ClientIp：未部署在反代后时不信任任何请求头；
        // 反代后取 X-Forwarded-For 的最后一个值（代理追加、客户端改不了），其次 X-Real-IP。
        // 与 AccountSecurityService 记录会话 IP 的逻辑保持同一真源，避免两处口径不一致。
        return ClientIp.resolve(request, trustProxy);
    }
}
