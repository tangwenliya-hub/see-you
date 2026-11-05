package com.seeyou.see_you.config;

import com.seeyou.see_you.service.AccountSecurityService;
import com.seeyou.see_you.util.JwtUtil;
import com.seeyou.see_you.websocket.FeedbackWebSocketHandler;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 配置（反馈实时推送）
 * 安全约定：握手必须携带有效 token（/ws/feedback?token=xxx），
 * userId / role 一律由服务端从 JWT 解析，前端传参一律不信任。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private FeedbackWebSocketHandler feedbackWebSocketHandler;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private AccountSecurityService securityService;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler((WebSocketHandler) feedbackWebSocketHandler, "/ws/feedback")
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(ServerHttpRequest request,
                                                   ServerHttpResponse response,
                                                   WebSocketHandler wsHandler,
                                                   Map<String, Object> attributes) {
                        if (request instanceof ServletServerHttpRequest servletReq) {
                            String token = servletReq.getServletRequest().getParameter("token");
                            // 鉴权：token 缺失 / 签名无效 / 会话已被踢或注销 → 拒绝握手
                            if (token == null || !jwtUtil.validateToken(token)
                                    || !securityService.isSessionActive(token)) {
                                return false;
                            }
                            // 身份只认 token，不认 URL 参数（防止伪造 role=admin / 冒用他人 userId）
                            attributes.put("userId", jwtUtil.getUserIdByToken(token));
                            attributes.put("role", jwtUtil.getRoleByToken(token) == null ? "user" : jwtUtil.getRoleByToken(token));
                            return true;
                        }
                        return false;
                    }
                    @Override
                    public void afterHandshake(ServerHttpRequest request,
                                               ServerHttpResponse response,
                                               WebSocketHandler wsHandler, Exception e) {}
                })
                .setAllowedOrigins("*");
    }
}
