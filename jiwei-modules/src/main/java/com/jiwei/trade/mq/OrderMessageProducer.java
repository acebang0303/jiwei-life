package com.jiwei.trade.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * RocketMQ 消息生产者。
 */
@Component
@Slf4j
public class OrderMessageProducer {

    private static final String ORDER_DELAY_TOPIC = "order-delay-topic";

    /** RocketMQ 默认延迟级别 15 = 20 分钟（内置 18 档无 15 分钟档，取最接近且不早于 15 分钟） */
    private static final int DELAY_LEVEL_20M = 15;

    private final DefaultMQProducer rocketMQProducer;

    public OrderMessageProducer(DefaultMQProducer rocketMQProducer) {
        this.rocketMQProducer = rocketMQProducer;
    }

    /**
     * 发送订单支付超时检查消息（延迟 20 分钟，RocketMQ 内置档位）
     */
    public void sendOrderDelayCheck(Long orderId) {
        try {
            Message msg = new Message(ORDER_DELAY_TOPIC, String.valueOf(orderId).getBytes(StandardCharsets.UTF_8));
            msg.setDelayTimeLevel(DELAY_LEVEL_20M);
            rocketMQProducer.send(msg);
            log.info("发送订单延迟检查消息: orderId={}, delayLevel={}(20min)", orderId, DELAY_LEVEL_20M);
        } catch (Exception e) {
            log.warn("发送订单延迟检查消息失败 orderId={}: {}", orderId, e.getMessage());
        }
    }
}
