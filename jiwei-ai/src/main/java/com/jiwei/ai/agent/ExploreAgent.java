package com.jiwei.ai.agent;

import com.jiwei.ai.sse.AgentEventSink;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 探店助手：委托 AgentRuntimeFactory 组装 JChatMind 运行时实例，
 * 由 AgentController 异步执行 run()，SSE 推送式输出。
 */
@Component
public class ExploreAgent {

    private final AgentRuntimeFactory factory;

    public ExploreAgent(AgentRuntimeFactory factory) {
        this.factory = factory;
    }

    public JChatMind execute(String model, String sessionId, String message) {
        return factory.createExplore(model, sessionId, message);
    }

    public JChatMind execute(String model, String sessionId, String message,
                             List<Message> history, AgentEventSink sink) {
        return factory.createExplore(model, sessionId, message, history, sink);
    }
}
