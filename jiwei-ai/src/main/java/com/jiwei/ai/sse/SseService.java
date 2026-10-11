package com.jiwei.ai.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 推送服务：以 sessionId 为 key 管理连接，支持 Agent Loop 内部主动推送。
 */
public interface SseService {
    SseEmitter connect(String sessionId);

    void send(String sessionId, Object message);

    void close(String sessionId);
}
