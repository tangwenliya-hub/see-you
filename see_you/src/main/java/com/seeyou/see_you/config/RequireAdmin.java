package com.seeyou.see_you.config;

import java.lang.annotation.*;

/**
 * 标记「必须是管理员」的接口。
 *
 * <p>可打在类上（该类全部方法都要求管理员）或方法上（仅该接口要求）。
 * 读/写分级沿用 {@link AdminWrite}：GET 且未标注 AdminWrite 时允许只读管理员（role=2），
 * 其余一律要求完整管理员（role=1）。</p>
 *
 * <p>为什么要有它：管理员判定原来全部依赖 JwtInterceptor 里 17 个 {@code uri.startsWith}
 * 硬编码，新增接口时很容易忘记登记，一旦漏配就是越权漏洞。改成注解后权限跟着方法走，
 * 看一眼方法签名就知道这个接口谁能调。旧规则暂时保留做兜底，两者是「或」的关系，
 * 等确认所有管理端接口都标注后即可删除旧规则。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAdmin {
}
