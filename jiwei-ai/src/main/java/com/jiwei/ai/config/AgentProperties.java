package com.jiwei.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Agent 收敛配置（对应 application.yml 的 agent.* 配置）
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {
    /** ReAct 最大循环轮数 */
    private int maxIterations = 8;
    /** Agent 超时时间（秒） */
    private int timeoutSeconds = 30;
}
