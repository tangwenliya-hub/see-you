package com.seeyou.see_you.config;

import java.lang.annotation.*;

/**
 * 标记「必须是完整管理员（role=1）才能执行」的接口。
 *
 * <p>默认规则（见 JwtInterceptor）：/admin/ 下的 GET 允许 role=1 与 role=2（只读管理员）访问，
 * 非 GET 请求一律要求 role=1。但少数 GET 接口本身带有副作用（例如强制下线、解锁账号、
 * 恢复默认参数），这些接口必须显式标注本注解，把只读管理员挡在外面。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AdminWrite {
    /** 日志模块名，供操作日志切面使用 */
    String value() default "";
}
