package com.jiwei.framework.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI服务配置属性
 */
@Component
@ConfigurationProperties(prefix = "jiwei.ai")
@Data
public class AiProperties {
    //DeepSeek API Base URL
    private String baseUrl;
    //API Key
    private String apiKey;
    //使用的模型名称
    private String model;
    //AI服务地址（主应用调用 ai-service）
    private String serviceUrl;
}
