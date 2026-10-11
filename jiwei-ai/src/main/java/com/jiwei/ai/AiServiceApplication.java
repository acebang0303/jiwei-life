package com.jiwei.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 吉味生活 · JiweiAI 服务启动类
 * 独立部署的AI智能服务，提供 Agent 对话、RAG 检索、NL2SQL、内容审核等功能
 */
@SpringBootApplication
@Slf4j
public class AiServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
        log.info("=== 吉味生活 · JiweiAI 智能服务启动成功 ===");
    }
}
