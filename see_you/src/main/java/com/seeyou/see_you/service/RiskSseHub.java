package com.seeyou.see_you.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 情绪预警 SSE 推送中心（管理端实时提醒）。
 *
 * <h3>为什么用 SSE 而不是复用反馈的 WebSocket</h3>
 * 预警推送是<b>纯服务端单向广播</b>：管理员不需要通过这条通道发任何消息回去，
 * SSE 的单向模型刚好匹配，且浏览器 EventSource 自带断线重连，比 WS 更轻。
 * 反馈通道需要双向收发（管理员即时回复），继续留在 WebSocket —— 两个通道各司其职。
 *
 * <h3>鉴权说明（⚠️ 安全边界，勿改动）</h3>
 * EventSource 无法携带 Authorization 头，token 只能走查询参数；
 * 因此 {@code /risk/admin-stream} 必须在 CorsConfig 放行 JWT 拦截器，
 * 由控制器手动完成「token 有效 + 会话在线 + 管理员角色」三重校验后才允许注册，
 * 未登录 / 普通用户连上来拿不到任何事件（register 只在控制器校验通过后被调用）。
 *
 * <h3>容错原则</h3>
 * 推送是锦上添花，<b>任何 SSE 失败都不允许影响预警落库主流程</b>：
 * 序列化失败、发送失败一律吞掉并清理死连接，管理员下次刷新页面仍能看到数据。
 */
@Component
public class RiskSseHub {

    private static final Logger log = LoggerFactory.getLogger(RiskSseHub.class);

    /** 在线管理员通道：userId -> emitter（同一管理员开多个标签页时后者覆盖前者，防泄漏） */
    private final Map<Long, SseEmitter> admins = new ConcurrentHashMap<>();

    @Resource
    private ObjectMapper objectMapper;

    private ScheduledExecutorService heartbeat;

    @PostConstruct
    public void start() {
        // 25s 心跳：防止浏览器/代理把空闲连接掐掉。SSE 注释行不会触发前端 message 事件，零噪音。
        heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "risk-sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeat.scheduleAtFixedRate(this::pingAll, 25, 25, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() {
        if (heartbeat != null) heartbeat.shutdownNow();
    }

    /** 管理员注册一条实时通道（调用前必须已完成鉴权） */
    public SseEmitter register(Long userId) {
        SseEmitter em = new SseEmitter(0L);   // 0 = 不超时；靠心跳保活 + 前端 EventSource 自动重连
        // 同一管理员开第二个标签页 → 旧连接必须显式关闭，否则它既收不到推送（已被移出 map）
        // 又不会被 GC（Spring 仍持着），白白挂到浏览器关闭为止。
        SseEmitter old = admins.put(userId, em);
        if (old != null) {
            try { old.complete(); } catch (Exception ignored) { /* 旧连接可能已断 */ }
        }
        Runnable clean = () -> admins.remove(userId, em);
        em.onCompletion(clean);
        em.onTimeout(clean);
        em.onError(e -> clean.run());
        // 立刻回一个 connected 事件：前端用它确认通道就绪，也顺带冲掉代理的缓冲
        try {
            em.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            admins.remove(userId, em);
        }
        return em;
    }

    /** 向所有在线管理员广播预警事件 */
    public void pushToAdmins(String event, Object payload) {
        if (admins.isEmpty()) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[预警SSE] payload 序列化失败，本次广播跳过", e);
            return;
        }
        admins.forEach((uid, em) -> {
            try {
                em.send(SseEmitter.event().name(event).data(json, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                // 发送失败 = 连接已死，清掉等前端 EventSource 重连
                admins.remove(uid, em);
            }
        });
    }

    private void pingAll() {
        admins.forEach((uid, em) -> {
            try {
                em.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                admins.remove(uid, em);
            }
        });
    }
}
