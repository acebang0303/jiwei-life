package com.jiwei.market.seckill.mq;

import com.alibaba.fastjson2.JSON;
import com.jiwei.common.exception.OrderBusinessException;
import com.jiwei.market.service.SeckillService;
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
 * 秒杀下单消费者：Redis 已预扣库存，这里做 MySQL 兜底扣减与建单（削峰落库）。
 * 确定性失败（库存不足/重复单）回滚 Redis 后确认；瞬时失败重试一次。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SeckillOrderCreateConsumer {

    private static final String TOPIC = "seckill-order-topic";

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${rocketmq.consumer.group:seckill-order-consumer}")
    private String consumerGroup;

    private final SeckillService seckillService;
    private final OrderService orderService;

    private DefaultMQPushConsumer consumer;

    @PostConstruct
    public void start() throws MQClientException {
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(TOPIC, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
            for (MessageExt msg : msgs) {
                SeckillMessage message = JSON.parseObject(
                        new String(msg.getBody(), StandardCharsets.UTF_8), SeckillMessage.class);
                try {
                    handleMessage(message);   // 0/-1/1 正常返回；失败已在此回补库存后抛出
                } catch (OrderBusinessException e) {
                    // 确定性失败：handleMessage 已回补库存并回滚 Redis 且已打 warn → 静默确认，不重试、不重复日志
                } catch (Exception e) {
                    // 瞬时失败：handleMessage 已回补库存并回滚 Redis；已重试一次转人工，否则重试
                    if (msg.getReconsumeTimes() >= 1) {
                        log.error("建单瞬时失败且已重试，转人工: orderNo={}", message.orderNo(), e);
                    } else {
                        log.warn("建单瞬时失败，进入重试: orderNo={}", message.orderNo(), e);
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        log.info("SeckillOrderCreateConsumer 启动成功, topic={}", TOPIC);
    }

    /**
     * 处理单条秒杀下单消息（包内可见，供单测直接调用；生产仍由 MQ listener 驱动）。
     * 返回 0=重复跳过, -1=库存不足, 1=成功建单；
     * 确定性失败(OrderBusinessException)与瞬时失败(Exception)统一在此完成库存回补后向上抛出，
     * 由 listener 决定确认还是重试。
     */
    long handleMessage(SeckillMessage message) {
        boolean deducted = false;
        try {
            // 0. 幂等：orderNo 已建单（重复投递）→ 跳过；须在 try 内，若查询抛异常同样走下方回补+重试
            if (orderService.seckillOrderExists(message.orderNo())) {
                log.info("重复投递已处理，跳过: orderNo={}", message.orderNo());
                return 0L;
            }
            // deductStock 须在 try 内：若它抛异常(Redis/DB 故障)也走下方回补+重试，与原逻辑等价
            deducted = seckillService.deductStock(message.activityId(), message.quantity());
            if (!deducted) {
                seckillService.revertRedisStock(message.activityId(), message.dishId(),
                        message.userId(), message.quantity());
                log.warn("库存不足，回滚Redis: orderNo={}", message.orderNo());
                return -1L;
            }
            orderService.createSeckillOrder(message);
            return 1L;
        } catch (OrderBusinessException e) {
            if (deducted) {
                seckillService.restoreStock(message.activityId(), message.quantity());
            }
            seckillService.revertRedisStock(message.activityId(), message.dishId(),
                    message.userId(), message.quantity());
            log.warn("建单确定性失败，回补库存并回滚Redis: orderNo={}, reason={}",
                    message.orderNo(), e.getMessage());
            throw e;
        } catch (Exception e) {
            if (deducted) {
                seckillService.restoreStock(message.activityId(), message.quantity());
            }
            seckillService.revertRedisStock(message.activityId(), message.dishId(),
                    message.userId(), message.quantity());
            throw e;
        }
    }

    @PreDestroy
    public void shutdown() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
