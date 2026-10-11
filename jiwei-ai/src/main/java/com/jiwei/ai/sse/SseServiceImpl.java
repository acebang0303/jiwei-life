package com.jiwei.ai.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 推送实现：ConcurrentHashMap 管理连接（单实例内存态）。
 */
@Service
@Slf4j
public class SseServiceImpl implements SseService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter connect(String sessionId) {
        SseEmitter emitter = new SseEmitter(0L); // 0 = 不超时
        emitters.put(sessionId, emitter);
        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> emitters.remove(sessionId));
        emitter.onError(e -> emitters.remove(sessionId));
        return emitter;
    }

    @Override
    public void send(String sessionId, Object message) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(message);
        } catch (IOException e) {
            emitters.remove(sessionId);
            log.warn("SSE 发送失败，session={}: {}", sessionId, e.getMessage());
        }
    }

    @Override
    public void close(String sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) {
            emitter.complete();
        }
    }
}
