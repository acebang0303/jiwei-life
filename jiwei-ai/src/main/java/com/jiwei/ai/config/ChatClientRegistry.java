package com.jiwei.ai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 多模型 ChatClient 注册表。
 * 由 MultiChatClientConfig 注册的 deepseek/qwen/kimi 三个 ChatClient bean
 * 通过构造器自动收集进 Map，运行时按 key 切换模型。
 *
 * 降级策略（对应「大模型故障时自动降级」）：
 *   客户端传了不认识的 model（拼错、下线了某家模型）时，不再返回 null
 *   —— 那会让上层拿到 null 后在 think() 里 NPE，整轮对话直接 500。
 *   这里统一回退到 agent.default-model（默认 deepseek）；该模型也不可用时，
 *   取注册表里任意一个可用模型，保证对话能力不中断。
 */
@Slf4j
@Component
public class ChatClientRegistry {

    private final Map<String, ChatClient> chatClients;
    private final String defaultModel;

    public ChatClientRegistry(Map<String, ChatClient> chatClients,
                              @Value("${agent.default-model:deepseek}") String defaultModel) {
        this.chatClients = chatClients;
        this.defaultModel = defaultModel;
        if (chatClients.isEmpty()) {
            log.error("没有任何 ChatClient 注册成功，AI 对话能力不可用");
        } else {
            log.info("ChatClient 注册表初始化完成: models={}, default={}", chatClients.keySet(), defaultModel);
        }
    }

    /** 按 key 取模型；未命中时回退默认模型，避免返回 null 导致上层 NPE。 */
    public ChatClient get(String key) {
        ChatClient client = key == null ? null : chatClients.get(key);
        if (client != null) {
            return client;
        }
        ChatClient fallback = chatClients.get(defaultModel);
        if (fallback == null) {
            fallback = chatClients.values().stream().findFirst().orElse(null);
        }
        if (fallback == null) {
            throw new IllegalStateException("没有可用的 ChatClient，请检查模型 API Key 配置");
        }
        log.warn("模型 [{}] 不可用，本次请求回退到默认模型 [{}]", key, defaultModel);
        return fallback;
    }

    /** 注册表里可用的模型 key，供接口做参数校验/前端下拉。 */
    public Set<String> availableModels() {
        return Set.copyOf(chatClients.keySet());
    }

    public boolean supports(String key) {
        return key != null && chatClients.containsKey(key);
    }

    public String defaultModel() {
        return defaultModel;
    }
}
