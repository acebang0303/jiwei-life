package com.jiwei.trade.mq;

import com.jiwei.trade.service.OrderService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 订单延迟消息消费者：消费 order-delay-topic，处理超时未支付订单（取消 + 秒杀库存回补）。
 * handleTimeoutOrder 幂等（仅处理 PENDING_PAYMENT），消费异常确认不重试，OrderTask 定时扫描兜底。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderDelayConsumer {

    private static final String TOPIC = "order-delay-topic";

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${rocketmq.consumer.group:order-delay-consumer}")
    private String consumerGroup;

    private final OrderService orderService;

    private DefaultMQPushConsumer consumer;

    @PostConstruct
    public void start() throws MQClientException {
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(TOPIC, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
            for (MessageExt msg : msgs) {
                String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                try {
                    orderService.handleTimeoutOrder(Long.valueOf(body));
                } catch (Exception e) {
                    log.warn("处理超时订单失败 body={}: {}", body, e.getMessage());
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        log.info("OrderDelayConsumer 启动成功, topic={}", TOPIC);
    }

    @PreDestroy
    public void shutdown() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
