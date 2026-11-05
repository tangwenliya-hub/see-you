package com.seeyou.see_you.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JWT 工具类测试（签发 / 解析 / 防篡改）。
 *
 * <p>密钥从配置注入，测试里用反射塞一个固定的测试密钥 —— HS256 要求密钥 ≥ 256 bit（32 字节）。
 */
class JwtUtilTest {

    private static final String TEST_SECRET = "unit-test-secret-for-see-you-project-2026-abcdefg";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", TEST_SECRET);
        jwtUtil.initKey();
    }

    @Test
    @DisplayName("签发后能原样解析出 userId 与 role")
    void roundTrip() {
        String token = jwtUtil.generateToken(1001L, 1);
        assertEquals(1001L, jwtUtil.getUserIdByToken(token));
        assertEquals(1, jwtUtil.getRoleByToken(token));
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("不传 role 的旧签名重载不会抛异常，role 解析为 null")
    void legacyOverload() {
        String token = jwtUtil.generateToken(7L);
        assertEquals(7L, jwtUtil.getUserIdByToken(token));
        assertNull(jwtUtil.getRoleByToken(token));
    }

    @Test
    @DisplayName("签名被篡改后校验失败（防止改 role 提权）")
    void tamperedTokenRejected() {
        String token = jwtUtil.generateToken(1L, 0);
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + parts[1] + "." + flipLastChar(parts[2]);
        assertFalse(jwtUtil.validateToken(forged), "篡改签名后仍然通过了校验");
    }

    @Test
    @DisplayName("换一个密钥签发的 token 无法通过校验")
    void foreignKeyRejected() {
        JwtUtil other = new JwtUtil();
        ReflectionTestUtils.setField(other, "secret", "another-secret-that-is-long-enough-1234567890");
        other.initKey();
        String foreign = other.generateToken(1L, 1);
        assertFalse(jwtUtil.validateToken(foreign));
    }

    @Test
    @DisplayName("空串 / 乱码 / 截断的 token 返回 false 而不是抛异常")
    void garbageTokenRejected() {
        assertFalse(jwtUtil.validateToken(""));
        assertFalse(jwtUtil.validateToken(null));
        assertFalse(jwtUtil.validateToken("abc.def.ghi"));
        String token = jwtUtil.generateToken(1L, 1);
        assertFalse(jwtUtil.validateToken(token.substring(0, token.length() - 4)));
    }

    private String flipLastChar(String s) {
        char last = s.charAt(s.length() - 1);
        char flipped = last == 'A' ? 'B' : 'A';
        return s.substring(0, s.length() - 1) + flipped;
    }
}
