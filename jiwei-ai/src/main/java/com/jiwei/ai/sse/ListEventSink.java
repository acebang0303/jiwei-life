package com.jiwei.ai.sse;

import com.jiwei.ai.dto.AgentEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 非流式事件通道：把 Agent 执行过程收集到内存列表，供一次性返回。
 */
public class ListEventSink implements AgentEventSink {

    private final List<AgentEvent> events = new ArrayList<>();

    @Override
    public void send(String sessionId, AgentEvent event) {
        events.add(event);
    }

    public List<AgentEvent> getEvents() {
        return events;
    }
}
