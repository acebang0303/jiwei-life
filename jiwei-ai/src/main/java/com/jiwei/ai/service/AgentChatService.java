package com.jiwei.ai.service;

import com.jiwei.ai.agent.AgentRuntimeFactory;
import com.jiwei.ai.agent.ConversationHistoryLoader;
import com.jiwei.ai.agent.JChatMind;
import com.jiwei.ai.dto.AgentChatRequest;
import com.jiwei.ai.dto.AgentChatResponse;
import com.jiwei.ai.dto.AgentEvent;
import com.jiwei.ai.sse.ListEventSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 非流式 Agent 调用服务：一次请求跑完 Agent 循环，
 * 收集全部事件后一次性返回（供小程序等无 SSE 能力的前端）。
 */
@Service
@Slf4j
public class AgentChatService {

    private static final int HISTORY_ROUNDS = 10;

    private final AgentRuntimeFactory factory;
    private final ConversationService conversationService;

    public AgentChatService(AgentRuntimeFactory factory, ConversationService conversationService) {
        this.factory = factory;
        this.conversationService = conversationService;
    }

    public AgentChatResponse chat(AgentChatRequest req) {
        //1、解析/创建会话
        Long ownerId = req.getUserId() != null ? req.getUserId()
                : (req.getEmpId() != null ? req.getEmpId() : 0L);
        String agentType = req.getAgentType() == null ? "EXPLORE" : req.getAgentType().toUpperCase();
        String conversationId = req.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = conversationService.createConversation(ownerId, agentType);
        }

        //2、加载历史
        List<Message> history = ConversationHistoryLoader.toMessages(
                conversationService.getRecentMessages(conversationId, HISTORY_ROUNDS));

        //3、组装 Agent
        String sessionId = UUID.randomUUID().toString();
        ListEventSink sink = new ListEventSink();
        JChatMind runtime;
        switch (agentType) {
            case "MERCHANT":
                runtime = factory.createMerchant(req.getModel(), sessionId, req.getMerchantId(), req.getMessage(), history, sink);
                break;
            case "ADMIN":
                runtime = factory.createAdmin(req.getModel(), sessionId, req.getEmpId(), req.getMessage(), history, sink);
                break;
            case "EXPLORE":
            default:
                runtime = factory.createExplore(req.getModel(), sessionId, req.getMessage(), history, sink);
                break;
        }
        if (runtime == null) {
            return new AgentChatResponse(conversationId, List.of(
                    new AgentEvent("message", "无法初始化助手，请检查参数")), "无法初始化助手");
        }

        //4、执行并收集
        runtime.run();
        List<AgentEvent> events = sink.getEvents();
        String finalAnswer = events.stream()
                .filter(e -> "message".equals(e.type()))
                .map(AgentEvent::content)
                .reduce("", (a, b) -> a + b);

        //5、持久化本轮
        List<String> toolNames = events.stream()
                .filter(e -> "action".equals(e.type()))
                .map(e -> extractToolName(e.content()))
                .distinct()
                .toList();
        ConversationHistoryLoader.persistRound(conversationService, conversationId,
                req.getMessage(), finalAnswer, toolNames);

        return new AgentChatResponse(conversationId, events, finalAnswer);
    }

    private static String extractToolName(String actionContent) {
        if (actionContent == null) {
            return "unknown";
        }
        String[] parts = actionContent.trim().split("\\s+");
        return parts.length > 1 ? parts[1] : actionContent.trim();
    }
}
