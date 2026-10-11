package com.jiwei.ai.agent;

import com.jiwei.ai.service.ConversationService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mongo 会话消息 ↔ Spring AI Message 转换。
 * JChatMind 续接时把历史消息转回模型可读的消息列表。
 */
public final class ConversationHistoryLoader {

    private ConversationHistoryLoader() {
    }

    @SuppressWarnings("unchecked")
    public static List<Message> toMessages(List<Map<String, Object>> rawMessages) {
        List<Message> messages = new ArrayList<>();
        for (Map<String, Object> m : rawMessages) {
            String role = m.get("role") == null ? "user" : m.get("role").toString();
            String content = m.get("content") == null ? "" : m.get("content").toString();
            //历史工具调用名回灌到 assistant 文本，供模型续接时感知上下文
            Object toolCalls = m.get("toolCalls");
            if ("assistant".equals(role) && toolCalls instanceof List && !((List<?>) toolCalls).isEmpty()) {
                String names = ((List<?>) toolCalls).stream()
                        .filter(c -> c instanceof Map)
                        .map(c -> String.valueOf(((Map<?, ?>) c).get("name")))
                        .filter(n -> !"null".equals(n) && n != null)
                        .collect(Collectors.joining("、"));
                if (!names.isEmpty()) {
                    content = content + "\n（本轮调用了工具：" + names + "）";
                }
            }
            if (content.isEmpty()) {
                continue;
            }
            if ("assistant".equals(role)) {
                messages.add(new AssistantMessage(content));
            } else {
                messages.add(new UserMessage(content));
            }
        }
        return messages;
    }

    public static List<Map<String, Object>> buildToolCallList(List<String> toolNames) {
        List<Map<String, Object>> calls = new ArrayList<>();
        for (String name : toolNames) {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("name", name);
            call.put("arguments", "");
            calls.add(call);
        }
        return calls;
    }

    /**
     * 一轮 Agent 完成后保存 user + assistant 消息（assistant 带工具调用名）。
     */
    public static void persistRound(ConversationService service, String conversationId,
                                    String userMsg, String assistantMsg, List<String> toolNames) {
        service.appendMessage(conversationId, "user", userMsg);
        service.appendMessage(conversationId, "assistant", assistantMsg,
                buildToolCallList(toolNames == null ? new ArrayList<>() : toolNames));
    }
}
