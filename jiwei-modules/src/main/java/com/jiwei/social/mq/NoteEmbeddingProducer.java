package com.jiwei.social.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 笔记向量同步消息生产者
 * 笔记发布后发消息，由 AI 服务消费后按审核状态重建 pgvector 向量
 */
@Component
@Slf4j
public class NoteEmbeddingProducer {

    private static final String TOPIC = "embedding-note-topic";

    @Autowired
    private DefaultMQProducer rocketMQProducer;

    public void send(Long noteId) {
        try {
            Message message = new Message(TOPIC, String.valueOf(noteId).getBytes(StandardCharsets.UTF_8));
            rocketMQProducer.send(message);
            log.info("发送笔记向量同步消息: noteId={}", noteId);
        } catch (Exception e) {
            log.warn("发送笔记向量同步消息失败 noteId={}: {}", noteId, e.getMessage());
        }
    }
}
