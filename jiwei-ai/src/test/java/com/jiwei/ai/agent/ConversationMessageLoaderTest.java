package com.jiwei.ai.agent;

import com.jiwei.ai.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationMessageLoaderTest {

    private static Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    @Test
    void convertsMongoMessagesToSpringAiMessages() {
        List<Map<String, Object>> raw = List.of(
                msg("user", "你好"),
                msg("assistant", "你好！有什么可以帮你？"));
        List<Message> messages = ConversationHistoryLoader.toMessages(raw);
        assertEquals(2, messages.size());
        assertTrue(messages.get(0) instanceof UserMessage);
        assertTrue(messages.get(1) instanceof AssistantMessage);
        assertEquals("你好", messages.get(0).getText());
    }

    @Test
    void buildToolCallList() {
        List<Map<String, Object>> calls = ConversationHistoryLoader.buildToolCallList(
                List.of("queryPlatformData", "searchKnowledgeBase"));
        assertEquals(2, calls.size());
        assertEquals("queryPlatformData", calls.get(0).get("name"));
    }
}
