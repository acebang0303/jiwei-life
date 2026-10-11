package com.jiwei.trade.mq;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jiwei.merchant.mapper.MerchantInfoMapper;
import com.jiwei.pojo.entity.MerchantInfo;
import com.jiwei.pojo.entity.Orders;
import com.jiwei.trade.mapper.OrderMapper;
import com.jiwei.trade.mq.NotifyMessageProducer;
import com.jiwei.user.service.GrowthService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付成功消息消费者：消费 order-paid-topic，回写 orders.pay_status/status。
 */
@Component
@Slf4j
public class OrderPaidConsumer {

    private static final String TOPIC = "order-paid-topic";

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${rocketmq.consumer.group:order-paid-consumer}")
    private String consumerGroup;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private MerchantInfoMapper merchantInfoMapper;

    @Autowired
    private NotifyMessageProducer notifyMessageProducer;

    @Autowired
    private GrowthService growthService;

    /** 每笔支付成功订单获得的成长值 */
    private static final int ORDER_GROWTH = 10;

    private DefaultMQPushConsumer consumer;

    @PostConstruct
    public void start() throws MQClientException {
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(TOPIC, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
            for (MessageExt msg : msgs) {
                String orderNo = new String(msg.getBody(), StandardCharsets.UTF_8);
                try {
                    Orders order = orderMapper.selectOne(
                            new LambdaQueryWrapper<Orders>().eq(Orders::getNumber, orderNo));
                    if (order != null && order.getPayStatus().equals(Orders.UN_PAID)) {
                        order.setPayStatus(Orders.PAID);
                        order.setStatus(Orders.TO_BE_CONFIRMED);
                        order.setPayTime(LocalDateTime.now());
                        orderMapper.updateById(order);
                        // 支付成功 → 发放成长值（下单行为）
                        try {
                            growthService.addGrowth(order.getUserId(), ORDER_GROWTH);
                        } catch (Exception e) {
                            log.warn("发放订单成长值失败 orderNo={}: {}", orderNo, e.getMessage());
                        }
                        // 支付成功 → 推送商家端新订单提醒（merchantId → empId）
                        pushNewOrderToMerchant(order);
                    }
                } catch (Exception e) {
                    log.warn("回写订单支付状态失败 orderNo={}: {}", orderNo, e.getMessage());
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        log.info("OrderPaidConsumer 启动成功, topic={}", TOPIC);
    }

    @PreDestroy
    public void shutdown() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }

    /**
     * 推送新订单提醒给商家（商家 WebSocket 用 empId 连接）
     */
    private void pushNewOrderToMerchant(Orders order) {
        try {
            MerchantInfo merchant = merchantInfoMapper.selectById(order.getMerchantId());
            if (merchant == null || merchant.getEmpId() == null) {
                return;
            }
            String content = JSON.toJSONString(Map.of(
                    "orderId", order.getId(),
                    "orderNo", order.getNumber(),
                    "amount", order.getPayAmount(),
                    "message", "您有新的订单，请及时接单"
            ));
            notifyMessageProducer.sendToUser(merchant.getEmpId(), "newOrder", content);
            log.info("推送新订单提醒: merchantId={}, empId={}, orderId={}",
                    order.getMerchantId(), merchant.getEmpId(), order.getId());
        } catch (Exception e) {
            log.warn("推送新订单提醒失败 orderId={}: {}", order.getId(), e.getMessage());
        }
    }
}
