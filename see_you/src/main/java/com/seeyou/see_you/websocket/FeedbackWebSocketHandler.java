package com.seeyou.see_you.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Component
public class FeedbackWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 用户连接：userId -> Set<session>（支持同一个账号多端/多页面） */
    private final Map<String, CopyOnWriteArraySet<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    /** 管理员连接集合 */
    private final CopyOnWriteArraySet<WebSocketSession> adminSessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        // 握手拦截器写入的是 Long userId / Integer role，必须做值转换而不是强转（强转 String 会 ClassCastException 导致连接即断）
        String userId = attrs.get("userId") == null ? null : String.valueOf(attrs.get("userId"));
        String role = String.valueOf(attrs.getOrDefault("role", "user"));

        // 所有带 userId 的连接都注册到 userSessions（支持同一账号多端/多页面）
        if (userId != null) {
            userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
            log.info("[WS] 用户已连接, userId={}, sessionId={}", userId, session.getId());
        }

        // 管理员额外注册到 adminSessions（用于群发通知）
        if ("admin".equals(role) || "1".equals(role)) {
            adminSessions.add(session);
            log.info("[WS] 管理员已连接, sessionId={}", session.getId());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 心跳或客户端消息，此处不处理
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Map<String, Object> attrs = session.getAttributes();
        // 与 afterConnectionEstablished 保持一致：值转换而非强转
        String userId = attrs.get("userId") == null ? null : String.valueOf(attrs.get("userId"));

        adminSessions.remove(session);

        if (userId != null) {
            CopyOnWriteArraySet<WebSocketSession> set = userSessions.get(userId);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) userSessions.remove(userId);
            }
            log.info("[WS] 用户已断开, userId={}", userId);
        }
    }

    /** 推送给所有管理员 */
    public void sendToAdmins(String type, Object data) {
        if (adminSessions.isEmpty()) {
            log.warn("[WS] sendToAdmins: adminSessions 是空的！没人能收通知！");
            return;
        }
        String json = buildMessage(type, data);
        int success = 0;
        for (WebSocketSession s : adminSessions) {
            boolean ok = sendJson(s, json);
            if (ok) success++;
        }
        log.info("[WS] sendToAdmins type={}, 在线管理员={}, 成功发送={}", type, adminSessions.size(), success);
    }

    /** 推送给指定用户（所有页面/设备） */
    public void sendToUser(Long userId, String type, Object data) {
        if (userId == null) return;
        CopyOnWriteArraySet<WebSocketSession> set = userSessions.get(String.valueOf(userId));
        if (set == null || set.isEmpty()) return;
        String json = buildMessage(type, data);
        for (WebSocketSession s : set) {
            sendJson(s, json);
        }
    }

    private String buildMessage(String type, Object data) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("type", type);
            map.put("data", data);
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.error("[WS] 消息序列化失败", e);
            return "{}";
        }
    }

    private boolean sendJson(WebSocketSession s, String json) {
        if (s == null || !s.isOpen()) return false;
        try {
            s.sendMessage(new TextMessage(json));
            return true;
        } catch (IOException e) {
            log.warn("[WS] 发送失败: {}", e.getMessage());
            return false;
        }
    }
}