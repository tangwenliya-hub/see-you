package com.seeyou.see_you.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 客户端 IP 解析的防伪造测试。
 * 背景：会话表里的登录 IP 曾被写成 X-Forwarded-For 的第一个值，客户端可随意伪造，
 * 导致「谁登了我的账号」这件事查不出真相。这里锁定正确的取值口径。
 */
class ClientIpTest {

    @Test
    @DisplayName("未部署在代理后：任何请求头都不采信，只认 TCP 来源")
    void ignoresHeadersWhenNotBehindProxy() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("192.168.1.10");
        req.addHeader("X-Forwarded-For", "1.2.3.4");
        req.addHeader("X-Real-IP", "5.6.7.8");
        assertEquals("192.168.1.10", ClientIp.resolve(req, false));
    }

    @Test
    @DisplayName("代理后：取 XFF 最后一个值，攻击者伪造的前缀无效")
    void takesLastForwardedForValue() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        // 攻击者在最左边塞了假 IP，边缘代理把真实来源追加在末尾
        req.addHeader("X-Forwarded-For", "200.180.162.15, 112.92.78.189");
        assertEquals("112.92.78.189", ClientIp.resolve(req, true));
    }

    @Test
    @DisplayName("代理后：没有 XFF 时退回 X-Real-IP（nginx 强制覆盖场景）")
    void fallsBackToRealIp() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Real-IP", "139.162.26.99");
        assertEquals("139.162.26.99", ClientIp.resolve(req, true));
    }

    @Test
    @DisplayName("XFF 末尾是 unknown 时继续往前找可用值")
    void skipsUnknownTail() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        req.addHeader("X-Forwarded-For", "112.92.78.195, unknown");
        assertEquals("112.92.78.195", ClientIp.resolve(req, true));
    }

    @Test
    @DisplayName("畸形请求头（超长 / 含空格换行）直接丢弃，降级为 remoteAddr")
    void dropsMalformedHeaders() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.9");
        req.addHeader("X-Forwarded-For", "1.2.3.4 5.6.7.8");
        req.addHeader("X-Real-IP", "x".repeat(80));
        assertEquals("10.0.0.9", ClientIp.resolve(req, true));
    }

    @Test
    @DisplayName("IPv6 回环统一显示为 127.0.0.1")
    void normalizesIpv6Loopback() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("0:0:0:0:0:0:0:1");
        assertEquals("127.0.0.1", ClientIp.resolve(req, false));
    }
}
