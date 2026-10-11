package com.jiwei.trade.mq;

import com.alibaba.fastjson2.JSON;
import com.jiwei.trade.websocket.NotifyMessage;
import com.jiwei.trade.websocket.NotifyWebSocketHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * WebSocket 通知消费者（广播消费）：每个实例都收到消息，经本机过滤后推送。
 */
@Component
@Slf4j
public class NotifyMessageConsumer {

    private static final String TOPIC = "ws-notify-topic";

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${rocketmq.consumer.group:ws-notify-consumer}")
    private String consumerGroup;

    private final NotifyWebSocketHandler handler;

    private DefaultMQPushConsumer consumer;

    public NotifyMessageConsumer(NotifyWebSocketHandler handler) {
        this.handler = handler;
    }

    @PostConstruct
    public void start() throws MQClientException {
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.setMessageModel(MessageModel.BROADCASTING);
        consumer.subscribe(TOPIC, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
            for (MessageExt msg : msgs) {
                NotifyMessage notify = JSON.parseObject(
                        new String(msg.getBody(), StandardCharsets.UTF_8), NotifyMessage.class);
                handler.dispatch(notify);
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        log.info("NotifyMessageConsumer 启动成功（广播消费）: {}", TOPIC);
    }

    @PreDestroy
    public void shutdown() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
