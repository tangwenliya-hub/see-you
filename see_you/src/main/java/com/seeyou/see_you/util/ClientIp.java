package com.seeyou.see_you.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端真实 IP 解析（全项目唯一真源）。
 *
 * <p>为什么需要单独一个类：限流与会话记录各写过一份取值逻辑，而且取法不一致——
 * 限流器取 {@code X-Real-IP}，会话表取 {@code X-Forwarded-For} 的<b>第一个</b>值。
 * 后者是典型的可伪造点：{@code X-Forwarded-For} 是「追加」语义
 * （nginx 的 {@code $proxy_add_x_forwarded_for}、Railway/Fly 等边缘代理都是把自己看到的
 * TCP 源地址<b>追加到末尾</b>），客户端自带一个假值时就排在第一位。
 * 于是攻击者可以给每次登录塞不同的假 IP：会话列表里全是陌生 IP、按 IP 的限流也能被绕过。
 *
 * <p>取值规则（trustProxy = 部署在反向代理/云边缘之后）：
 * <ol>
 *   <li>false：只认 {@code getRemoteAddr()}（TCP 层来源，请求头一律不采信）；</li>
 *   <li>true ：取 {@code X-Forwarded-For} 的<b>最后一个</b>值（代理追加的真实来源，客户端改不了），
 *       其次 {@code X-Real-IP}（nginx 用 {@code $remote_addr} 强制覆盖时可信），
 *       最后退回 {@code getRemoteAddr()}。</li>
 * </ol>
 * 注意：只有「最外层代理会覆盖或追加」这一前提成立时 trustProxy=true 才安全，
 * 直连公网且没配 nginx 时应保持 false。
 */
public final class ClientIp {

    private ClientIp() {
    }

    /** 解析客户端 IP；任何异常都降级为 remoteAddr，绝不因取值失败影响登录/限流主流程 */
    public static String resolve(HttpServletRequest request, boolean trustProxy) {
        if (request == null) return "";
        String ip;
        if (!trustProxy) {
            ip = request.getRemoteAddr();
        } else {
            ip = lastOfForwardedFor(request.getHeader("X-Forwarded-For"));
            if (ip == null) ip = clean(request.getHeader("X-Real-IP"));
            if (ip == null) ip = request.getRemoteAddr();
        }
        return normalize(ip);
    }

    /**
     * 取 X-Forwarded-For 的最后一个值。
     * 链式转发形如 {@code 伪造值, 真实客户端, 边缘节点}，只有最右侧（代理自己写入的）不可伪造。
     */
    private static String lastOfForwardedFor(String header) {
        if (header == null || header.isBlank()) return null;
        String[] parts = header.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String v = clean(parts[i]);
            if (v != null) return v;
        }
        return null;
    }

    private static String clean(String v) {
        if (v == null) return null;
        String t = v.trim();
        if (t.isEmpty() || "unknown".equalsIgnoreCase(t)) return null;
        // 防御：超长或含空白/换行的头直接丢弃，避免被写进日志/数据库
        if (t.length() > 64 || t.contains(" ") || t.contains("\n") || t.contains("\r")) return null;
        return t;
    }

    /** IPv6 回环统一显示为 127.0.0.1，否则「登录设备」里会出现一串看不懂的东西 */
    private static String normalize(String ip) {
        if (ip == null) return "";
        String t = ip.split("%")[0].trim(); // 去掉 IPv6 zone 后缀（如 %0）
        if (t.equals("::1") || t.equals("0:0:0:0:0:0:0:1")) return "127.0.0.1";
        return t;
    }
}
