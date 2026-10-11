package com.jiwei.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册三个模型的 ChatClient bean（deepseek/qwen/kimi），
 * 由 ChatClientRegistry 统一收集，运行时按 key 切换。
 *
 * deepseek 走官方 starter；kimi 用 openai starter 自动配置（moonshot base-url）；
 * qwen 复用 openai starter，手动构建 OpenAiChatModel 指向 DashScope 兼容模式。
 */
@Configuration
public class MultiChatClientConfig {

    @Bean("deepseek")
    public ChatClient deepSeekChatClient(DeepSeekChatModel model) {
        return ChatClient.create(model);
    }

    @Bean("kimi")
    public ChatClient kimiChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.create(openAiChatModel);
    }

    @Bean("qwen")
    public ChatClient qwenChatClient(
            @Value("${spring.ai.qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
            @Value("${spring.ai.qwen.api-key:}") String apiKey,
            @Value("${spring.ai.qwen.model:qwen3.6-flash}") String model) {
        OpenAiApi qwenApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath("/chat/completions") // base-url 已含 /v1，避免重复 /v1
                .build();
        OpenAiChatModel qwenModel = OpenAiChatModel.builder()
                .openAiApi(qwenApi)
                .defaultOptions(OpenAiChatOptions.builder().model(model).build())
                .build();
        return ChatClient.create(qwenModel);
    }
}
