package com.jiwei.ai.agent;

import com.jiwei.ai.dto.AgentEvent;
import com.jiwei.ai.sse.AgentEventSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 手写 ReAct Agent 运行时（移植自 JChatMind）。
 *
 * 设计要点：
 * 1. 状态机 IDLE → THINKING/EXECUTING → FINISHED/ERROR，maxSteps 硬上限 + 整体超时防死循环
 * 2. 关闭 Spring AI 内部工具自动执行（internalToolExecutionEnabled=false），
 *    改由 ToolCallingManager 手动执行，便于在循环中观察/干预每一步
 * 3. 决策阶段走真正的流式：token 增量通过 AgentEventSink 实时推送（SSE 前端逐字出字），
 *    同时用 ChatClientMessageAggregator 聚合出完整响应供工具执行；
 *    流式链路异常时自动降级为阻塞调用，保证可用性
 * 4. 角色提示词只作为一条 system message 存在于会话记忆里（不再用 .system() 追加
 *    第二条 system 覆盖角色设定），窗口裁剪后也会自动补回
 */
@Slf4j
public class JChatMind {

    private static final Integer DEFAULT_MAX_STEPS = 8;
    private static final Integer DEFAULT_MAX_MESSAGES = 20;
    private static final Integer DEFAULT_TIMEOUT_SECONDS = 30;

    /** 决策阶段的附加指令，拼在角色提示词之后（而不是另起一条 system message） */
    private static final String DECISION_RULE = """

            现在你是「决策模块」：根据当前对话上下文决定下一步动作 ——
            调用工具获取信息，还是直接给出最终回答。需要调用工具时只发工具调用，不要编造工具结果。
            """;

    private final ChatClient chatClient;
    private final List<ToolCallback> availableTools;
    private final AgentEventSink eventSink;
    private final String chatSessionId;
    private final String systemPrompt;
    private final int maxSteps;
    private final long timeoutMillis;

    private AgentState agentState;
    private final ToolCallingManager toolCallingManager;
    private final ChatMemory chatMemory;
    private final ChatOptions chatOptions;
    private ChatResponse lastChatResponse;

    public JChatMind(ChatClient chatClient,
                     String systemPrompt,
                     List<ToolCallback> availableTools,
                     AgentEventSink eventSink,
                     String chatSessionId) {
        this(chatClient, systemPrompt, availableTools, eventSink, chatSessionId,
                List.of(), DEFAULT_MAX_STEPS, DEFAULT_TIMEOUT_SECONDS);
    }

    public JChatMind(ChatClient chatClient,
                     String systemPrompt,
                     List<ToolCallback> availableTools,
                     AgentEventSink eventSink,
                     String chatSessionId,
                     List<Message> memory) {
        this(chatClient, systemPrompt, availableTools, eventSink, chatSessionId,
                memory, DEFAULT_MAX_STEPS, DEFAULT_TIMEOUT_SECONDS);
    }

    public JChatMind(ChatClient chatClient,
                     String systemPrompt,
                     List<ToolCallback> availableTools,
                     AgentEventSink eventSink,
                     String chatSessionId,
                     List<Message> memory,
                     int maxSteps,
                     int timeoutSeconds) {
        this.chatClient = chatClient;
        this.systemPrompt = systemPrompt;
        this.availableTools = availableTools;
        this.eventSink = eventSink;
        this.chatSessionId = chatSessionId;
        this.maxSteps = maxSteps;
        this.timeoutMillis = timeoutSeconds * 1000L;

        this.agentState = AgentState.IDLE;

        this.chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(DEFAULT_MAX_MESSAGES)
                .build();
        if (memory != null && !memory.isEmpty()) {
            this.chatMemory.add(chatSessionId, memory);
        }

        // 关闭 Spring AI 内部工具自动执行，改由 ToolCallingManager 手动执行
        this.chatOptions = DefaultToolCallingChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .build();

        this.toolCallingManager = ToolCallingManager.builder().build();
    }

    /**
     * 决策阶段：流式调用模型，token 增量实时推送，返回是否需要调用工具。
     */
    private boolean think() {
        this.agentState = AgentState.THINKING;
        Prompt prompt = Prompt.builder()
                .chatOptions(this.chatOptions)
                .messages(messagesWithSystemPrompt())
                .build();

        boolean streamedIncrementally = false;
        try {
            this.lastChatResponse = streamDecision(prompt);
            streamedIncrementally = true;
        } catch (PartialStreamException e) {
            // 已经往客户端推过增量：不能再重跑一次，否则前端会看到重复内容
            log.warn("流式输出中途中断，已推送部分内容，本轮直接收敛: {}", e.getMessage());
            this.agentState = AgentState.FINISHED;
            return false;
        } catch (Exception e) {
            log.warn("流式决策不可用，降级为阻塞调用: {}", e.getMessage());
            this.lastChatResponse = blockingDecision(prompt);
        }

        AssistantMessage output = this.lastChatResponse.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();

        // 阻塞降级路径下内容还没有推给前端，这里整段补发；流式路径已经逐 token 推过了
        if (!streamedIncrementally && StringUtils.hasLength(output.getText())) {
            eventSink.send(chatSessionId, new AgentEvent("message", output.getText()));
        }

        logToolCalls(toolCalls);
        return !toolCalls.isEmpty();
    }

    /**
     * 流式决策：对原始分片流做旁路观察（推送增量），再用聚合器拿到完整响应供工具执行。
     *
     * 注意聚合器语义：ChatClientMessageAggregator 的回调只会被调用一次，
     * 且收到的是「合并后的完整响应」，不是逐个原始分片。因此增量推送必须挂在
     * 原始 Flux 的 doOnNext 上，聚合器只负责产出可用于 ToolCallingManager 的完整响应。
     */
    private ChatResponse streamDecision(Prompt prompt) {
        AtomicReference<ChatClientResponse> aggregated = new AtomicReference<>();
        AtomicBoolean pushedAny = new AtomicBoolean(false);

        Flux<ChatClientResponse> raw = chatClient.prompt(prompt)
                .toolCallbacks(availableTools.toArray(new ToolCallback[0]))
                .stream()
                .chatClientResponse();

        try {
            new ChatClientMessageAggregator()
                    .aggregateChatClientResponse(raw.doOnNext(chunk -> {
                        String delta = deltaText(chunk);
                        if (StringUtils.hasLength(delta)) {
                            pushedAny.set(true);
                            eventSink.send(chatSessionId, new AgentEvent("message", delta));
                        }
                    }), aggregated::set)
                    .then()
                    .block(Duration.ofMillis(timeoutMillis));
        } catch (Exception e) {
            if (pushedAny.get()) {
                throw new PartialStreamException(e.getMessage());
            }
            throw new IllegalStateException("流式决策失败: " + e.getMessage(), e);
        }

        ChatClientResponse response = aggregated.get();
        if (response == null || response.chatResponse() == null
                || response.chatResponse().getResult() == null) {
            throw new IllegalStateException("模型未返回任何内容");
        }
        return response.chatResponse();
    }

    /** 阻塞兜底：流式链路不可用时的退路，语义与改造前一致。 */
    private ChatResponse blockingDecision(Prompt prompt) {
        return this.chatClient.prompt(prompt)
                .toolCallbacks(availableTools.toArray(new ToolCallback[0]))
                .call()
                .chatClientResponse()
                .chatResponse();
    }

    private String deltaText(ChatClientResponse chunk) {
        if (chunk == null || chunk.chatResponse() == null
                || chunk.chatResponse().getResult() == null
                || chunk.chatResponse().getResult().getOutput() == null) {
            return null;
        }
        try {
            return chunk.chatResponse().getResult().getOutput().getText();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 会话消息 + 必要的角色 system 提示。
     * 窗口裁剪（maxMessages）后 system 消息可能被挤掉，这里兜底补回，
     * 避免步数一多角色设定/工具说明就丢失。（包级可见，便于单测直接校验）
     */
    List<Message> messagesWithSystemPrompt() {
        List<Message> messages = new ArrayList<>(chatMemory.get(chatSessionId));
        boolean hasSystem = messages.stream().anyMatch(m -> m.getMessageType() == MessageType.SYSTEM);
        if (!hasSystem && StringUtils.hasLength(systemPrompt)) {
            messages.add(0, new SystemMessage(systemPrompt + DECISION_RULE));
        }
        return messages;
    }

    /**
     * 执行阶段：手动执行工具调用，更新对话记忆，检查 terminate 工具。
     */
    private void execute() {
        this.agentState = AgentState.EXECUTING;

        Prompt prompt = Prompt.builder()
                .messages(messagesWithSystemPrompt())
                .chatOptions(this.chatOptions)
                .build();

        ToolExecutionResult toolExecutionResult =
                toolCallingManager.executeToolCalls(prompt, this.lastChatResponse);

        List<Message> history = toolExecutionResult.conversationHistory();
        this.chatMemory.clear(this.chatSessionId);
        this.chatMemory.add(this.chatSessionId, history);

        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) history.get(history.size() - 1);

        String collect = toolResponseMessage.getResponses()
                .stream()
                .map(resp -> "工具 " + resp.name() + " 的返回结果为：" + resp.responseData())
                .collect(Collectors.joining("\n"));

        log.info("工具调用结果：{}", collect);
        eventSink.send(chatSessionId, new AgentEvent("action", collect));

        if (toolResponseMessage.getResponses()
                .stream()
                .anyMatch(resp -> resp.name().equals("terminate"))) {
            this.agentState = AgentState.FINISHED;
            log.info("任务结束");
        }
    }

    private void step() {
        if (think()) {
            execute();
        } else {
            agentState = AgentState.FINISHED;
        }
    }

    /**
     * 运行 Agent Loop：最多 maxSteps 轮，直到 FINISHED、超时或异常。
     */
    public void run() {
        if (agentState != AgentState.IDLE) {
            throw new IllegalStateException("Agent is not idle");
        }

        long deadline = System.currentTimeMillis() + timeoutMillis;
        try {
            int currentStep = 0;
            while (currentStep < maxSteps && agentState != AgentState.FINISHED) {
                if (System.currentTimeMillis() > deadline) {
                    log.warn("Agent 超时（{}s），停止执行", timeoutMillis / 1000);
                    break;
                }
                currentStep++;
                step();
            }
            if (currentStep >= maxSteps && agentState != AgentState.FINISHED) {
                log.warn("达到最大步数 {}，停止执行", maxSteps);
            }
            agentState = AgentState.FINISHED;
        } catch (Exception e) {
            agentState = AgentState.ERROR;
            log.error("Error running agent", e);
            throw new RuntimeException("Error running agent", e);
        }
    }

    public AgentState getAgentState() {
        return agentState;
    }

    private void logToolCalls(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            log.info("[ToolCalling] 无工具调用");
            return;
        }
        String logMessage = IntStream.range(0, toolCalls.size())
                .mapToObj(i -> {
                    AssistantMessage.ToolCall call = toolCalls.get(i);
                    return String.format("[ToolCalling #%d] name=%s, arguments=%s",
                            i + 1, call.name(), call.arguments());
                })
                .collect(Collectors.joining("\n"));
        log.info("========== Tool Calling ==========\n{}\n=============================", logMessage);
    }

    /** 流式已经输出过一部分内容后中断：不能再重跑，避免前端重复收到内容。 */
    private static final class PartialStreamException extends RuntimeException {
        PartialStreamException(String message) {
            super(message);
        }
    }
}
