package com.jiwei.ai.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.jiwei.ai.config.ChatClientRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 内容审核 Agent（同步调用，不走 Agent Loop）。
 * 通过 ChatClientRegistry 选择模型（默认 deepseek）。
 */
@Component
@Slf4j
public class AuditAgent {

    private final ChatClientRegistry registry;
    private final String model;

    public AuditAgent(ChatClientRegistry registry,
                      @Value("${agent.audit-model:deepseek}") String model) {
        this.registry = registry;
        this.model = model;
    }

    private static final String AUDIT_PROMPT = """
            你是内容审核助手，负责审核用户提交的评价和笔记内容。

            审核规则：
            1. 禁止内容: 政治敏感、色情、暴力、赌博、毒品相关
            2. 风险内容: 人身攻击、恶意差评、虚假宣传、广告引流
            3. 低质量内容: 纯表情/无意义文字、明显复制粘贴

            请以JSON格式返回审核结果：
            {
                "approved": true/false,
                "reason": "通过原因或驳回原因",
                "riskLevel": "safe/low/medium/high"
            }

            只返回JSON，不要有任何其他内容。
            """;

    public JSONObject audit(String content, String contentType) {
        log.info("内容审核Agent处理: type={}, content长度={}", contentType,
                content != null ? content.length() : 0);

        String prompt = String.format("请审核以下%s内容:\n\n%s", contentType, content);

        ChatClient client = registry.get(model);
        String response = client.prompt()
                .system(AUDIT_PROMPT)
                .user(prompt)
                .call()
                .content();

        if (response != null) {
            try {
                return JSON.parseObject(response.trim());
            } catch (Exception e) {
                log.error("审核结果解析失败，默认放行: {}", e.getMessage());
                JSONObject fallback = new JSONObject();
                fallback.put("approved", true);
                fallback.put("reason", "AI审核结果解析异常，默认放行");
                fallback.put("riskLevel", "low");
                return fallback;
            }
        }

        JSONObject fallback = new JSONObject();
        fallback.put("approved", true);
        fallback.put("reason", "AI未返回结果，默认放行");
        fallback.put("riskLevel", "low");
        return fallback;
    }
}
