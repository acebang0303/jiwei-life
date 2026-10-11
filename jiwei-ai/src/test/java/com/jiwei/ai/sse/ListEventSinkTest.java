package com.jiwei.ai.sse;

import com.jiwei.ai.dto.AgentEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListEventSinkTest {

    @Test
    void collectEventsInOrder() {
        ListEventSink sink = new ListEventSink();
        sink.send("s1", new AgentEvent("action", "工具A 返回"));
        sink.send("s1", new AgentEvent("message", "结论"));
        List<AgentEvent> events = sink.getEvents();
        assertEquals(2, events.size());
        assertEquals("action", events.get(0).type());
        assertEquals("结论", events.get(1).content());
    }
}
