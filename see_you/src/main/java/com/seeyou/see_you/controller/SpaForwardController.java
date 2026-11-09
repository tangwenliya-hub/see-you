package com.seeyou.see_you.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端 history 路由回退（对应 Vue Router 的 createWebHistory 模式）。
 *
 * <p>作用：浏览器直接访问或刷新 /login、/main、/admin/dashboard 等前端页面路由时，
 * 服务端统一返回打包后的 index.html，交给前端路由接管，避免直接刷新页面出现 404。</p>
 *
 * <p>安全边界：
 * <ul>
 *   <li>只匹配「无扩展名」的 GET；.js/.css/图片/音视频由静态资源处理器负责；</li>
 *   <li>已存在的 REST 控制器方法（如 /auth/captcha、/user/info、/admin/**）映射优先级更高，不会被本转发吞掉；</li>
 *   <li>本控制器只做服务端 forward，不产生业务数据、不触碰数据库。</li>
 * </ul>
 */
@Controller
public class SpaForwardController {

    /**
     * 覆盖：根路径 /、单段路由（/main、/login、/profile、/feedback 等）、
     * 两段路由（/diary/write、/admin/dashboard 等）。现有前端路由均不超过两段。
     */
    @GetMapping({"/", "/{path:[^.]+}", "/{path:[^.]+}/{sub:[^.]+}"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
