package com.seeyou.see_you.util;

/**
 * IP 脱敏工具：用于隐私审计日志。
 *
 * 为什么要脱敏：完整的 IP 属于个人信息，而无感模块的日志只需要回答
 * 「这次授权是不是从异常地点来的」，不需要精确地址。因此 IPv4 末段置 0，IPv6 保留前 3 段。
 */
public final class IpMaskUtil {

    private IpMaskUtil() {}

    public static String mask(String ip) {
        if (ip == null || ip.isBlank()) return null;
        String s = ip.trim();
        // 反向代理场景：X-Forwarded-For 可能是 "a, b, c"，取第一个
        int comma = s.indexOf(',');
        if (comma > 0) s = s.substring(0, comma).trim();
        if (s.isBlank()) return null;

        if (s.contains(":")) {          // IPv6：保留前 3 段
            String[] parts = s.split(":");
            int keep = Math.min(3, parts.length);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < keep; i++) sb.append(parts[i]).append(":");
            return sb + "0:0:0:0:0:0:0";
        }
        String[] parts = s.split("\\.");
        if (parts.length != 4) return null;
        return parts[0] + "." + parts[1] + "." + parts[2] + ".0";
    }
}
