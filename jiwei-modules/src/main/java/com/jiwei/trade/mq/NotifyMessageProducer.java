package com.jiwei.trade.mq;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.jiwei.trade.websocket.NotifyMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * WebSocket 通知消息生产者：复用框架 DefaultMQProducer，发送到广播 topic。
 */
@Component
@Slf4j
public class NotifyMessageProducer {

    private static final String TOPIC = "ws-notify-topic";

    private final DefaultMQProducer rocketMQProducer;

    public NotifyMessageProducer(DefaultMQProducer rocketMQProducer) {
        this.rocketMQProducer = rocketMQProducer;
    }

    /** 定向推送 */
    public void sendToUser(Long userId, String type, String content) {
        NotifyMessage message = NotifyMessage.builder()
                .id(IdUtil.getSnowflakeNextIdStr())
                .userId(String.valueOf(userId))
                .broadcast(false)
                .type(type)
                .content(content)
                .build();
        send(message);
    }

    /** 广播 */
    public void broadcast(String type, String content) {
        NotifyMessage message = NotifyMessage.builder()
                .id(IdUtil.getSnowflakeNextIdStr())
                .broadcast(true)
                .type(type)
                .content(content)
                .build();
        send(message);
    }

    private void send(NotifyMessage message) {
        try {
            Message msg = new Message(TOPIC, JSON.toJSONString(message).getBytes(StandardCharsets.UTF_8));
            rocketMQProducer.send(msg);
        } catch (Exception e) {
            log.warn("发送 WebSocket 通知失败 type={}: {}", message.getType(), e.getMessage());
        }
    }
}
