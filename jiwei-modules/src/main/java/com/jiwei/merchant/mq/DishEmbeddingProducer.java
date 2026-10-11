package com.jiwei.merchant.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 菜品向量同步消息生产者
 * 菜品新增/上下架后发消息，由 AI 服务消费后重建/删除 pgvector 向量
 */
@Component
@Slf4j
public class DishEmbeddingProducer {

    private static final String TOPIC = "embedding-dish-topic";

    @Autowired
    private DefaultMQProducer rocketMQProducer;

    public void send(Long dishId) {
        try {
            Message message = new Message(TOPIC, String.valueOf(dishId).getBytes(StandardCharsets.UTF_8));
            rocketMQProducer.send(message);
            log.info("发送菜品向量同步消息: dishId={}", dishId);
        } catch (Exception e) {
            log.warn("发送菜品向量同步消息失败 dishId={}: {}", dishId, e.getMessage());
        }
    }
}
