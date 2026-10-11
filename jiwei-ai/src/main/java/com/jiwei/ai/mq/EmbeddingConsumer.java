package com.jiwei.ai.mq;

import com.jiwei.ai.embedding.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;

/**
 * 向量同步消息消费者
 * 订阅主应用发送的菜品/笔记变更消息，增量重建或删除 pgvector 向量
 */
@Component
@Slf4j
public class EmbeddingConsumer {

    private static final String DISH_TOPIC = "embedding-dish-topic";
    private static final String NOTE_TOPIC = "embedding-note-topic";

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${rocketmq.consumer.group:jiwei-ai-embedding-consumer}")
    private String consumerGroup;

    @Autowired
    private EmbeddingService embeddingService;

    private DefaultMQPushConsumer consumer;

    @PostConstruct
    public void start() throws MQClientException {
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(DISH_TOPIC, "*");
        consumer.subscribe(NOTE_TOPIC, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                try {
                    Long id = Long.valueOf(body.trim());
                    if (DISH_TOPIC.equals(msg.getTopic())) {
                        embeddingService.syncDishEmbedding(id);
                    } else if (NOTE_TOPIC.equals(msg.getTopic())) {
                        embeddingService.syncNoteEmbedding(id);
                    }
                } catch (Exception e) {
                    log.warn("消费向量同步消息失败 topic={}, body={}: {}", msg.getTopic(), body, e.getMessage());
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        log.info("EmbeddingConsumer 启动成功, topics={}, {}", DISH_TOPIC, NOTE_TOPIC);
    }

    @PreDestroy
    public void shutdown() {
        if (consumer != null) {
            consumer.shutdown();
            log.info("EmbeddingConsumer 已关闭");
        }
    }
}
