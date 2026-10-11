package com.jiwei.trade.websocket;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 通知 WebSocket 处理器：本机过滤推送。
 * 广播消费模式下每个实例都收到消息，dispatch 时查本地连接表，未命中跳过（连在别的实例）。
 */
@Component
@Slf4j
public class NotifyWebSocketHandler extends TextWebSocketHandler {

    private final RedisSessionRegistry sessionRegistry;
    private final Map<String, Set<WebSocketSession>> localSessions = new ConcurrentHashMap<>();

    public NotifyWebSocketHandler(RedisSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = resolveUserId(session);
        session.getAttributes().put("userId", userId);
        localSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionRegistry.register(session.getId(), userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            Set<WebSocketSession> sessions = localSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    localSessions.remove(userId, sessions);
                }
            }
        }
        sessionRegistry.unregister(session.getId());
    }

    /** 本机过滤推送 */
    public void dispatch(NotifyMessage msg) {
        String payload = JSON.toJSONString(msg);
        if (msg.isBroadcast()) {
            localSessions.values().forEach(sessions -> sendAll(sessions, payload));
        } else {
            Set<WebSocketSession> sessions = localSessions.getOrDefault(msg.getUserId(), Set.of());
            sendAll(sessions, payload);
        }
    }

    private void sendAll(Set<WebSocketSession> sessions, String payload) {
        for (WebSocketSession s : sessions) {
            try {
                if (s.isOpen()) {
                    s.sendMessage(new TextMessage(payload));
                }
            } catch (IOException e) {
                log.warn("WS 推送失败 session={}: {}", s.getId(), e.getMessage());
            }
        }
    }

    private String resolveUserId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri != null) {
            String path = uri.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        }
        return "anonymous-" + session.getId();
    }
}
