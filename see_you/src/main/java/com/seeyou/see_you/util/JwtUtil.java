package com.seeyou.see_you.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {
    // 密钥必须由外部注入（环境变量 JWT_SECRET / application-local.yml 的 jwt.secret）。
    // ⚠️ 这里【不再提供任何内置默认值】：源码里留一个固定密钥，等于把「自签 token 冒充任意用户」
    //    的能力公开给所有能看到仓库的人。缺失时启动失败，比带后门上线安全。
    @Value("${jwt.secret:}")
    private String secret;
    private SecretKey key;

    /** HS256 要求密钥长度 ≥ 256 bit = 32 字节 */
    private static final int MIN_SECRET_BYTES = 32;

    @PostConstruct
    void initKey() {
        if (secret == null || secret.isBlank() || secret.startsWith("__SET_")) {
            throw new IllegalStateException(
                    "缺少 JWT 密钥：请设置环境变量 JWT_SECRET，或在 application-local.yml 中配置 jwt.secret"
                            + "（长度至少 32 个字符）。参考 application-local.yml.example。");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        // 提前给出人能看懂的报错：否则 jjwt 只会抛 WeakKeyException，看不出是「密钥太短」还是「没配置」
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "jwt.secret 长度不足：HS256 要求至少 " + MIN_SECRET_BYTES + " 字节，当前 "
                            + bytes.length + " 字节。请换成一段更长的随机字符串。");
        }
        key = Keys.hmacShaKeyFor(bytes);
    }
    //过期时间 2小时
    private static final long EXPIRATION = 2 * 60 * 60 * 1000;

    //生成token（带 role）
    public String generateToken(Long userId, Integer role) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + EXPIRATION);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    //生成token（不带 role，兼容旧调用）
    public String generateToken(Long userId) {
        return generateToken(userId, null);
    }

    //解析token获取userId
    public Long getUserIdByToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.parseLong(claims.getSubject());
    }

    //解析token获取role
    public Integer getRoleByToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Object role = claims.get("role");
        return role != null ? ((Number) role).intValue() : null;
    }

    //校验token
    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}