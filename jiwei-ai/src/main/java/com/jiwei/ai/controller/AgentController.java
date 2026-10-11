package com.jiwei.ai.controller;

import com.jiwei.ai.agent.AgentRuntimeFactory;
import com.jiwei.ai.agent.AuditAgent;
import com.jiwei.ai.agent.ConversationHistoryLoader;
import com.jiwei.ai.agent.ExploreAgent;
import com.jiwei.ai.agent.JChatMind;
import com.jiwei.ai.agent.MerchantAgent;
import com.jiwei.ai.dto.AgentChatRequest;
import com.jiwei.ai.dto.AgentChatResponse;
import com.jiwei.ai.dto.AgentEvent;
import com.jiwei.ai.service.AgentChatService;
import com.jiwei.ai.service.ConversationService;
import com.jiwei.ai.sse.AgentEventSink;
import com.jiwei.ai.sse.ListEventSink;
import com.jiwei.ai.sse.SseEventSink;
import com.jiwei.ai.sse.SseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * AI Agent 对话接口（SSE 推送式）。
 * 建立连接后异步执行 Agent Loop，运行时内部通过 SseService 主动推送。
 */
@RestController
@RequestMapping("/ai")
@Slf4j
public class AgentController {

    private static final Executor EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ExploreAgent exploreAgent;
    private final MerchantAgent merchantAgent;
    private final AuditAgent auditAgent;
    private final SseService sseService;
    private final AgentChatService agentChatService;
    private final AgentRuntimeFactory agentRuntimeFactory;
    private final ConversationService conversationService;
    private final SseEventSink sseEventSink;

    public AgentController(ExploreAgent exploreAgent,
                           MerchantAgent merchantAgent,
                           AuditAgent auditAgent,
                           SseService sseService,
                           AgentChatService agentChatService,
                           AgentRuntimeFactory agentRuntimeFactory,
                           ConversationService conversationService,
                           SseEventSink sseEventSink) {
        this.exploreAgent = exploreAgent;
        this.merchantAgent = merchantAgent;
        this.auditAgent = auditAgent;
        this.sseService = sseService;
        this.agentChatService = agentChatService;
        this.agentRuntimeFactory = agentRuntimeFactory;
        this.conversationService = conversationService;
        this.sseEventSink = sseEventSink;
    }

    @GetMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter exploreChat(@RequestParam String message,
                                  @RequestParam Long userId,
                                  @RequestParam(defaultValue = "deepseek") String model,
                                  @RequestParam(required = false) String conversationId) {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = sseService.connect(sessionId);
        EXECUTOR.execute(() -> {
            String convId = conversationId;
            if (convId == null || convId.isBlank()) {
                convId = conversationService.createConversation(userId, "EXPLORE");
            }
            final String finalConvId = convId;
            ListEventSink buffer = new ListEventSink();
            AgentEventSink sink = (sid, event) -> {
                buffer.send(sid, event);
                sseEventSink.send(sid, event);
            };
            try {
                List<Message> history = loadHistory(finalConvId);
                exploreAgent.execute(model, sessionId, message, history, sink).run();
                persistRound(finalConvId, message, buffer);
            } catch (Exception e) {
                log.error("探店助手执行失败", e);
                sseService.send(sessionId, new AgentEvent("error", e.getMessage()));
            } finally {
                sseService.send(sessionId, new AgentEvent("done", String.valueOf(finalConvId)));
                sseService.close(sessionId);
            }
        });
        return emitter;
    }

    @GetMapping(value = "/merchant/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter merchantChat(@RequestParam String question,
                                   @RequestParam Long empId,
                                   @RequestParam(defaultValue = "deepseek") String model,
                                   @RequestParam(required = false) String conversationId) {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = sseService.connect(sessionId);
        EXECUTOR.execute(() -> {
            String convId = conversationId;
            if (convId == null || convId.isBlank()) {
                convId = conversationService.createConversation(empId, "MERCHANT");
            }
            final String finalConvId = convId;
            ListEventSink buffer = new ListEventSink();
            AgentEventSink sink = (sid, event) -> {
                buffer.send(sid, event);
                sseEventSink.send(sid, event);
            };
            try {
                List<Message> history = loadHistory(finalConvId);
                JChatMind runtime = merchantAgent.execute(model, sessionId, question, empId, history, sink);
                if (runtime == null) {
                    sink.send(sessionId, new AgentEvent("message",
                            "未找到对应的商户信息，请联系管理员确认账号绑定。"));
                } else {
                    runtime.run();
                }
                persistRound(finalConvId, question, buffer);
            } catch (Exception e) {
                log.error("商家助手执行失败", e);
                sseService.send(sessionId, new AgentEvent("error", e.getMessage()));
            } finally {
                sseService.send(sessionId, new AgentEvent("done", String.valueOf(finalConvId)));
                sseService.close(sessionId);
            }
        });
        return emitter;
    }

    @GetMapping(value = "/admin/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter adminChat(@RequestParam String message,
                                @RequestParam Long empId,
                                @RequestParam(defaultValue = "deepseek") String model,
                                @RequestParam(required = false) String conversationId) {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = sseService.connect(sessionId);
        EXECUTOR.execute(() -> {
            String convId = conversationId;
            if (convId == null || convId.isBlank()) {
                convId = conversationService.createConversation(empId, "ADMIN");
            }
            final String finalConvId = convId;
            ListEventSink buffer = new ListEventSink();
            AgentEventSink sink = (sid, event) -> {
                buffer.send(sid, event);
                sseEventSink.send(sid, event);
            };
            try {
                List<Message> history = loadHistory(finalConvId);
                JChatMind runtime = agentRuntimeFactory.createAdmin(model, sessionId, empId, message, history, sink);
                runtime.run();
                persistRound(finalConvId, message, buffer);
            } catch (Exception e) {
                log.error("管理端助手执行失败", e);
                sseService.send(sessionId, new AgentEvent("error", e.getMessage()));
            } finally {
                sseService.send(sessionId, new AgentEvent("done", String.valueOf(finalConvId)));
                sseService.close(sessionId);
            }
        });
        return emitter;
    }

    @PostMapping("/audit/content")
    public Object contentAudit(@RequestParam String content,
                               @RequestParam(defaultValue = "note") String contentType) {
        log.info("AI内容审核: type={}", contentType);
        return auditAgent.audit(content, contentType);
    }

    @PostMapping(value = "/agent/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public AgentChatResponse chat(@RequestBody AgentChatRequest request) {
        log.info("Agent非流式对话: type={}, model={}", request.getAgentType(), request.getModel());
        return agentChatService.chat(request);
    }

    private List<Message> loadHistory(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        return ConversationHistoryLoader.toMessages(
                conversationService.getRecentMessages(conversationId, 10));
    }

    private void persistRound(String convId, String userMsg, ListEventSink buffer) {
        String answer = buffer.getEvents().stream()
                .filter(e -> "message".equals(e.type()))
                .map(AgentEvent::content)
                .reduce("", (a, b) -> a + b);
        List<String> toolNames = buffer.getEvents().stream()
                .filter(e -> "action".equals(e.type()))
                .map(e -> {
                    String c = e.content() == null ? "" : e.content();
                    String[] p = c.trim().split("\\s+");
                    return p.length > 1 ? p[1] : c.trim();
                })
                .distinct()
                .toList();
        ConversationHistoryLoader.persistRound(conversationService, convId, userMsg, answer, toolNames);
    }
}
