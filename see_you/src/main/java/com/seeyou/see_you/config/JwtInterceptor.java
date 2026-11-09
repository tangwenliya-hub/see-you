package com.seeyou.see_you.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seeyou.see_you.util.JwtUtil;
import com.seeyou.see_you.util.Result;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private com.seeyou.see_you.service.AccountSecurityService securityService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 获取 token
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || !jwtUtil.validateToken(token)) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            Result<?> r = Result.error(401, "未登录或token已过期");
            response.getWriter().write(new ObjectMapper().writeValueAsString(r));
            return false;
        }
        // 会话校验：被踢下线 / 主动退出后置为失效的 token 不再放行
        if (!securityService.isSessionActive(token)) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(401);
            Result<?> r = Result.error(401, "该登录已被移除，请重新登录");
            response.getWriter().write(new ObjectMapper().writeValueAsString(r));
            return false;
        }
        securityService.touch(token);

        // 解析 userId + role 存入 request
        Long userId = jwtUtil.getUserIdByToken(token);
        Integer role = jwtUtil.getRoleByToken(token);
        request.setAttribute("userId", userId);
        request.setAttribute("role", role);

        // 管理员权限校验
        // 优先看注解：@RequireAdmin 打在类或方法上即要求管理员，权限跟着代码走，新接口不会漏配
        boolean needAdmin = requireAdminByAnnotation(handler);
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 兜底：早期硬编码的 uri 规则（与注解是「或」关系）。
        // 等确认所有管理端接口都已标注 @RequireAdmin 后，整段可以删掉。
        if (!needAdmin) {
            // 1. /admin/ 开头的路径（纯后台接口）
            if (uri.contains("/admin/")) {
                needAdmin = true;
            }
            // 2. 用户管理相关
            else if (uri.startsWith("/user/list") || uri.startsWith("/user/status")) {
                needAdmin = true;
            }
            // 后台日记管理（全站日记的查看与删除）
            else if (uri.startsWith("/diary/admin/")) {
                needAdmin = true;
            }
            // 批量导入用户 / 下载导入模板：属于后台管理员功能
            else if (uri.startsWith("/user/import")) {
                needAdmin = true;
            }
            else if ("DELETE".equalsIgnoreCase(method) && uri.startsWith("/user/")) {
                needAdmin = true;
            }
            // 3. 评论管理相关
            else if (uri.startsWith("/comment/listAllWithUser")
                    || uri.startsWith("/comment/updateStatus")
                    || uri.startsWith("/comment/batchUpdateStatus")
                    || uri.startsWith("/comment/batchDelete")) {
                needAdmin = true;
            }
            else if ("DELETE".equalsIgnoreCase(method) && uri.startsWith("/comment/")) {
                needAdmin = true;
            }
            // 修改评论内容（/comment/update）同样必须是管理员，否则普通用户能改别人的评论
            else if ("PUT".equalsIgnoreCase(method) && uri.startsWith("/comment/update")) {
                needAdmin = true;
            }
            // 4. 反馈管理相关
            else if (uri.startsWith("/feedback/list")
                    || uri.startsWith("/feedback/update")
                    || uri.startsWith("/feedback/reply")) {
                needAdmin = true;
            }
            else if ("DELETE".equalsIgnoreCase(method) && uri.startsWith("/feedback/")) {
                needAdmin = true;
            }
            // 5. 测评试卷、题目、选项的写操作
            else if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
                if (uri.startsWith("/paper/") || uri.startsWith("/question/") || uri.startsWith("/option/")) {
                    needAdmin = true;
                }
                // 6. 视频、音频的写操作
                // 例外：/video/play/{id}、/audio/play/{id} 是普通用户播放时上报播放量，不能要求管理员
                if ((uri.startsWith("/video/") || uri.startsWith("/audio/")) && !uri.contains("/play/")) {
                    needAdmin = true;
                }
            }
        }

        if (needAdmin) {
            // 权限分级：role=1 完整管理员，role=2 只读管理员（只能看，不能改）。
            // 读接口（GET 且未标注 @AdminWrite）允许只读管理员；其余一律要求完整管理员。
            int requiredRole = ("GET".equalsIgnoreCase(method) && !hasAdminWrite(handler)) ? 2 : 1;
            boolean ok = role != null && role >= 1 && role <= requiredRole;
            if (!ok) {
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(403);
                String msg = (role != null && role == 2)
                        ? "当前账号为只读管理员，无修改权限"
                        : "无管理员权限";
                Result<?> r = Result.error(403, msg);
                response.getWriter().write(new ObjectMapper().writeValueAsString(r));
                return false;
            }
        }
        return true;
    }

    /**
     * 判断目标方法（或它所在的 Controller）是否标注了 {@link RequireAdmin}。
     * 类级注解覆盖整个 Controller，方法级只影响当前接口。
     */
    private boolean requireAdminByAnnotation(Object handler) {
        if (handler instanceof org.springframework.web.method.HandlerMethod hm) {
            return hm.hasMethodAnnotation(RequireAdmin.class)
                    || hm.getBeanType().isAnnotationPresent(RequireAdmin.class);
        }
        return false;
    }

    /**
     * 判断目标方法是否被 {@link AdminWrite} 标注：
     * 带该注解的 GET 接口视为写操作（强制下线、解锁、恢复默认参数等），只读管理员不可访问。
     */
    private boolean hasAdminWrite(Object handler) {
        if (handler instanceof org.springframework.web.method.HandlerMethod hm) {
            return hm.hasMethodAnnotation(AdminWrite.class)
                    || hm.getBeanType().isAnnotationPresent(AdminWrite.class);
        }
        return false;
    }
}