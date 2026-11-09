package com.seeyou.see_you.config;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Resource
    private JwtInterceptor jwtInterceptor;

    @Resource
    private RateLimitInterceptor rateLimitInterceptor;

    /**
     * 允许的来源清单，逗号分隔，支持通配符（如 https://*.example.com）。
     * 默认 * 保持本地开发（Vite 5173 / HBuilderX 任意端口）开箱即用；
     * 生产部署时在 application-prod.yml 覆盖为明确域名清单。
     */
    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins.split("\\s*,\\s*"))
                .allowCredentials(true)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 限流在最外层：未通过限流的请求不再进入鉴权与业务
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**");
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/auth/captcha/**",
                        "/auth/forgot/**",
                        // ⚠️ 上传接口【不放行】：/upload/file 可写 200MB 文件、/upload/list 可遍历目录，
                        //    免登录等于对外提供开放文件写入点（磁盘打满 / 往前端静态目录投毒）。
                        //    所有上传场景（头像、日记配图、封面、音视频）都在登录态下发生，无需白名单。
                        // "/upload/**",  ← 已移除，勿重新加入
                        // 全站统计游客可读；但 /statistics/my/** 涉及个人数据，必须登录（不放行）
                        "/statistics/emotion",
                        "/statistics/emotionTrend",
                        "/statistics/resource",
                        "/statistics/testResult",
                        "/statistics/overview",
                        "/statistics/videoRank",
                        "/statistics/activeUsers",
                        // /diary/whisper 涉及个人日记数据，必须登录（userId 由服务端从 token 取）
                        "/paper/list",
                        "/question/list",
                        "/option/list",
                        "/record/grade",   // 游客做测评也能看到后台配置的结果分级
                        // 👇 游客可浏览：心理FM / 绪见学堂 的只读接口（点赞收藏评论仍需登录）
                        "/audio/list",
                        "/video/list",
                        "/audio/play/*",
                        "/video/play/*",
                        "/comment/listByResource",
                        // 👇 游客也能看到点赞/收藏的数量（点赞收藏"动作"本身仍需登录）
                        "/like/count",
                        "/collect/count",
                        // 👇 首页轮播/公告：运营位内容，游客可读
                        "/banner/list",
                        // ⚠️ /risk/admin-stream：管理端 SSE 实时预警流。EventSource 无法携带
                        //    Authorization 头，token 只能走查询参数，JWT 拦截器在此放行；
                        //    鉴权由 RiskAlertController.adminStream 手动完成（token + 会话 + 管理员角色
                        //    三重校验），通道是只读推送，未校验通过拿不到任何事件。勿照抄此模式到写接口。
                        "/risk/admin-stream",
                        // 👇 网页托管放行：页面入口、前端构建产物、纯只读媒体（封面/头像/背景/图标）
                        //    安全边界：/upload/file、/upload/list 写接口仍刻意不放行；
                        //    与业务接口同名的 /video、/audio、/diary、/user、/banner 前缀也不放行，避免匿名触达业务接口。
                        "/index.html",
                        "/assets/**",
                        "/vite.svg",
                        "/cover/**",
                        "/avatar/**",
                        "/bg/**",
                        "/img/**",
                        "/error",
                        "/doc.html",
                        "/webjars/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html"
                );
    }
}