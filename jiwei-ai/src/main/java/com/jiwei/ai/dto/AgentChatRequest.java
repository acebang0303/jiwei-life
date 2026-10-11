package com.jiwei.ai.dto;

import lombok.Data;

@Data
public class AgentChatRequest {
    private String agentType;   // EXPLORE / MERCHANT / ADMIN
    private String message;
    private String model = "deepseek";
    private Long userId;
    private Long empId;
    private Long merchantId;
    private String conversationId;
}
