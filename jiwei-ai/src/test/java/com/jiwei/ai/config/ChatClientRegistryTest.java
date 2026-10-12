package com.jiwei.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ChatClientRegistryTest {

    private final ChatClient deepseek = mock(ChatClient.class);
    private final ChatClient kimi = mock(ChatClient.class);

    private ChatClientRegistry registry() {
        Map<String, ChatClient> clients = new HashMap<>();
        clients.put("deepseek", deepseek);
        clients.put("kimi", kimi);
        return new ChatClientRegistry(clients, "deepseek");
    }

    @Test
    void shouldReturnRegisteredModel() {
        assertThat(registry().get("kimi")).isSameAs(kimi);
    }

    @Test
    void shouldFallBackToDefaultModelForUnknownKey() {
        // 修复前：get() 直接返回 null，上层 think() 里 NPE，整轮对话 500
        assertThat(registry().get("gpt-9")).isSameAs(deepseek);
        assertThat(registry().get(null)).isSameAs(deepseek);
        assertThat(registry().get("")).isSameAs(deepseek);
    }

    @Test
    void shouldFallBackToAnyModelWhenDefaultIsMissing() {
        Map<String, ChatClient> clients = new HashMap<>();
        clients.put("kimi", kimi);
        ChatClientRegistry registry = new ChatClientRegistry(clients, "deepseek");

        assertThat(registry.get("whatever")).isSameAs(kimi);
    }

    @Test
    void shouldFailLoudlyWhenNoModelAvailable() {
        ChatClientRegistry registry = new ChatClientRegistry(new HashMap<>(), "deepseek");

        assertThatThrownBy(() -> registry.get("deepseek"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("没有可用的 ChatClient");
    }

    @Test
    void shouldReportAvailableModels() {
        assertThat(registry().availableModels()).containsExactlyInAnyOrder("deepseek", "kimi");
        assertThat(registry().supports("kimi")).isTrue();
        assertThat(registry().supports("gpt-9")).isFalse();
    }
}
