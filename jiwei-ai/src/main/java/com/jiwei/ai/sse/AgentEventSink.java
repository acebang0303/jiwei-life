package com.jiwei.ai.sse;

import com.jiwei.ai.dto.AgentEvent;

/**
 * Agent 事件输出通道。流式场景由 SseEventSink 推给前端，
 * 非流式场景由 ListEventSink 收集后一次性返回。
 */
public interface AgentEventSink {
    void send(String sessionId, AgentEvent event);
}
