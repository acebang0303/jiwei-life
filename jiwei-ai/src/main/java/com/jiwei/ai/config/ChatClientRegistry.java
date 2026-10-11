package com.jiwei.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 多模型 ChatClient 注册表。
 * 由 MultiChatClientConfig 注册的 deepseek/qwen/kimi 三个 ChatClient bean
 * 通过构造器自动收集进 Map，运行时按 key 切换模型。
 */
@Component
public class ChatClientRegistry {

    private final Map<String, ChatClient> chatClients;

    public ChatClientRegistry(Map<String, ChatClient> chatClients) {
        this.chatClients = chatClients;
    }

    public ChatClient get(String key) {
        return chatClients.get(key);
    }
}
