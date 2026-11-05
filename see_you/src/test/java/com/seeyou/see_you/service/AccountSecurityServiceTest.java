package com.seeyou.see_you.service;

import com.seeyou.see_you.entity.SysLoginAttempt;
import com.seeyou.see_you.entity.SysPwdReset;
import com.seeyou.see_you.entity.SysUser;
import com.seeyou.see_you.entity.SysUserSession;
import com.seeyou.see_you.mapper.SysLoginAttemptMapper;
import com.seeyou.see_you.mapper.SysPwdResetMapper;
import com.seeyou.see_you.mapper.SysUserSessionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 账号安全服务测试：验证码 / 登录锁定 / 找回密码 / 会话归属。
 *
 * <p>用 Mockito 把 Mapper 全部打桩，不连数据库、不启动 Spring，跑得快且结果稳定。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountSecurityServiceTest {

    @Mock private SysLoginAttemptMapper attemptMapper;
    @Mock private SysUserSessionMapper sessionMapper;
    @Mock private SysPwdResetMapper pwdResetMapper;
    @Mock private SysUserService sysUserService;
    @Mock private JavaMailSender mailSender;

    @InjectMocks private AccountSecurityService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "devCaptchaLog", true);
        ReflectionTestUtils.setField(service, "mailFrom", "");
    }

    // ==================== 1. 图形验证码 ====================

    @Test
    @DisplayName("验证码一次性：校验成功后立即销毁，不能重复使用")
    void captchaIsOneTime() {
        Map<String, String> c = service.createCaptcha();
        String key = c.get("captchaKey");
        assertNotNull(key);
        assertTrue(c.get("captchaImage").startsWith("data:image/png;base64,"));

        String code = service.peekCaptcha(key);
        assertNotNull(code);

        assertTrue(service.verifyCaptcha(key, code), "正确验证码应通过");
        assertFalse(service.verifyCaptcha(key, code), "验证码被重复使用");
    }

    @Test
    @DisplayName("验证码忽略大小写，但错误验证码一律拒绝")
    void captchaCaseInsensitive() {
        Map<String, String> c = service.createCaptcha();
        String key = c.get("captchaKey");
        String code = service.peekCaptcha(key);

        assertTrue(service.verifyCaptcha(key, code.toLowerCase()), "验证码应忽略大小写");
        assertFalse(service.verifyCaptcha(key, "ZZZZ"));
        assertFalse(service.verifyCaptcha(key, null));
        assertFalse(service.verifyCaptcha(null, code));
    }

    @Test
    @DisplayName("安全：开发开关关闭时 peekCaptcha 必须返回 null（防止生产泄露验证码）")
    void peekDisabledWhenDevFlagOff() {
        Map<String, String> c = service.createCaptcha();
        ReflectionTestUtils.setField(service, "devCaptchaLog", false);
        assertNull(service.peekCaptcha(c.get("captchaKey")));
    }

    // ==================== 2. 登录失败锁定 ====================

    @Test
    @DisplayName("连续失败 5 次才锁定，前 4 次只计数不锁定")
    void lockAfterFiveFails() {
        // 用数组持有「库里那条记录」：真实 MyBatis insert 会把自增 id 回填到实体上，
        // 这里用 Answer 模拟，才能验证「只有第一次 insert、之后都是 update」这条路径。
        SysLoginAttempt[] stored = new SysLoginAttempt[1];
        when(attemptMapper.selectOne(any())).thenAnswer(inv -> stored[0]);
        doAnswer(inv -> {
            stored[0] = inv.getArgument(0);
            ReflectionTestUtils.setField(stored[0], "id", 1L);
            return 1;
        }).when(attemptMapper).insert(any(SysLoginAttempt.class));
        doAnswer(inv -> {
            stored[0] = inv.getArgument(0);
            return 1;
        }).when(attemptMapper).updateById(any(SysLoginAttempt.class));

        for (int i = 1; i <= 4; i++) {
            assertNull(service.recordFail("test"), "第 " + i + " 次失败不应锁定");
        }
        String msg = service.recordFail("test");
        assertNotNull(msg, "第 5 次失败应锁定");
        assertTrue(msg.contains("锁定 10 分钟"), msg);

        assertNotNull(stored[0].getLockUntil());
        assertEquals(0, stored[0].getFailCount(), "锁定后失败计数应清零");
        verify(attemptMapper, times(1)).insert(any(SysLoginAttempt.class));
        verify(attemptMapper, times(4)).updateById(any(SysLoginAttempt.class));
    }

    @Test
    @DisplayName("锁定期间 checkLock 给出提示，锁定期过后自动解除")
    void checkLockWindow() {
        SysLoginAttempt stored = new SysLoginAttempt();
        stored.setUsername("test");
        stored.setLockUntil(LocalDateTime.now().plusMinutes(10));
        when(attemptMapper.selectOne(any())).thenReturn(stored);

        String msg = service.checkLock("test");
        assertNotNull(msg);
        assertTrue(msg.contains("账号已锁定"), msg);

        stored.setLockUntil(LocalDateTime.now().minusMinutes(1));
        assertNull(service.checkLock("test"), "锁定期过后应自动解除");
    }

    @Test
    @DisplayName("登录成功后清空失败计数")
    void clearFailOnSuccess() {
        SysLoginAttempt stored = new SysLoginAttempt();
        stored.setId(9L);
        stored.setFailCount(3);
        when(attemptMapper.selectOne(any())).thenReturn(stored);

        service.clearFail("test");
        verify(attemptMapper).deleteById(9L);
    }

    // ==================== 3. 找回密码 ====================

    @Test
    @DisplayName("新密码不足 6 位直接拒绝，且不查库")
    void resetRejectsShortPassword() {
        Map<String, Object> res = service.resetPassword("t", "123456", "123");
        assertFalse((Boolean) res.get("ok"));
        assertEquals("新密码至少 6 位", res.get("msg"));
        verifyNoInteractions(pwdResetMapper);
    }

    @Test
    @DisplayName("凭证不存在 / 已被使用过 → 提示无效")
    void resetRejectsInvalidToken() {
        when(pwdResetMapper.selectOne(any())).thenReturn(null);
        Map<String, Object> res = service.resetPassword("bad-token", "123456", "abc123456");
        assertFalse((Boolean) res.get("ok"));
        assertEquals("重置凭证无效", res.get("msg"));
    }

    @Test
    @DisplayName("验证码超过 10 分钟有效期 → 提示过期")
    void resetRejectsExpiredCode() {
        when(pwdResetMapper.selectOne(any())).thenReturn(record(LocalDateTime.now().minusMinutes(1), "123456"));
        Map<String, Object> res = service.resetPassword("t", "123456", "abc123456");
        assertFalse((Boolean) res.get("ok"));
        assertEquals("验证码已过期，请重新获取", res.get("msg"));
        verify(pwdResetMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("验证码不正确 → 提示错误，且凭证不被消耗")
    void resetRejectsWrongCode() {
        when(pwdResetMapper.selectOne(any())).thenReturn(record(LocalDateTime.now().plusMinutes(5), "123456"));
        Map<String, Object> res = service.resetPassword("t", "000000", "abc123456");
        assertFalse((Boolean) res.get("ok"));
        assertEquals("验证码不正确", res.get("msg"));
        verify(pwdResetMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("凭证有效 → 重置成功、凭证置为已用、密码以 BCrypt 存储（不存明文）")
    void resetSucceeds() {
        when(pwdResetMapper.selectOne(any())).thenReturn(record(LocalDateTime.now().plusMinutes(5), "123456"));
        SysUser user = new SysUser();
        user.setUserId(5L);
        user.setUsername("test");
        when(sysUserService.getById(5L)).thenReturn(user);
        when(sysUserService.updateById(any(SysUser.class))).thenReturn(true);

        Map<String, Object> res = service.resetPassword("t", "123456", "newpass123");
        assertTrue((Boolean) res.get("ok"), String.valueOf(res.get("msg")));
        assertEquals("test", res.get("username"));

        ArgumentCaptor<SysUser> userCap = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserService).updateById(userCap.capture());
        assertNotEquals("newpass123", userCap.getValue().getPassword(), "密码必须加密后存储");
        assertTrue(userCap.getValue().getPassword().startsWith("$2"), "应为 BCrypt 散列");

        ArgumentCaptor<SysPwdReset> resetCap = ArgumentCaptor.forClass(SysPwdReset.class);
        verify(pwdResetMapper).updateById(resetCap.capture());
        assertEquals(1, resetCap.getValue().getUsed(), "凭证用后必须置为已用，防止二次使用");
    }

    // ==================== 4. 会话归属 ====================

    @Test
    @DisplayName("不能踢掉别人的会话（IDOR 防护）")
    void cannotKickOthersSession() {
        SysUserSession other = new SysUserSession();
        other.setSessionId(1L);
        other.setUserId(999L);
        when(sessionMapper.selectById(1L)).thenReturn(other);

        assertFalse(service.kickSession(1L, 1L));
        verify(sessionMapper, never()).updateById(any());
    }

    @Test
    @DisplayName("可以踢掉自己的会话")
    void canKickOwnSession() {
        SysUserSession mine = new SysUserSession();
        mine.setSessionId(1L);
        mine.setUserId(1L);
        when(sessionMapper.selectById(1L)).thenReturn(mine);

        assertTrue(service.kickSession(1L, 1L));
        verify(sessionMapper).updateById(mine);
        assertEquals(0, mine.getStatus());
    }

    @Test
    @DisplayName("邮箱脱敏：只保留首字符与域名")
    void emailMasking() {
        assertEquals("t***@example.com",
                ReflectionTestUtils.invokeMethod(service, "maskEmail", "test@example.com"));
        assertEquals("***@b.com",
                ReflectionTestUtils.invokeMethod(service, "maskEmail", "a@b.com"));
    }

    @Test
    @DisplayName("设备类型按 UA 识别，未知时给出兜底文案")
    void deviceParsing() {
        assertEquals("iOS · 浏览器", ReflectionTestUtils.invokeMethod(service, "parseDevice", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)"));
        assertEquals("Android · 浏览器", ReflectionTestUtils.invokeMethod(service, "parseDevice", "Mozilla/5.0 (Linux; Android 14)"));
        assertEquals("未知设备", ReflectionTestUtils.invokeMethod(service, "parseDevice", (String) null));
    }

    private SysPwdReset record(LocalDateTime expire, String code) {
        SysPwdReset r = new SysPwdReset();
        r.setId(1L);
        r.setUserId(5L);
        r.setToken("t");
        r.setCode(code);
        r.setExpireTime(expire);
        r.setUsed(0);
        return r;
    }
}
