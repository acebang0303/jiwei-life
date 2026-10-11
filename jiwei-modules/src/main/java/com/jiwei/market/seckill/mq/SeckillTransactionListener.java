package com.jiwei.market.seckill.mq;

import com.alibaba.fastjson2.JSON;
import com.jiwei.market.service.SeckillService;
import com.jiwei.trade.service.OrderService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 秒杀事务消息监听器：将「Redis Lua 预扣库存」作为本地事务，
 * 与 RocketMQ 半消息的投递原子绑定，解决「库存扣了但消息没发出」的资损问题。
 *
 * 循环依赖规避：本类依赖 SeckillService / OrderService（均不注入回 SeckillServiceImpl），
 * 在 @PostConstruct 中把自己注册到注入的 TransactionMQProducer 上，
 * 从而避免 SeckillServiceImpl → listener → SeckillService → SeckillServiceImpl 成环。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SeckillTransactionListener implements TransactionListener {

    private final SeckillService seckillService;

    private final OrderService orderService;

    @Autowired
    private TransactionMQProducer transactionProducer;

    @PostConstruct
    public void register() {
        transactionProducer.setTransactionListener(this);
        log.info("SeckillTransactionListener 已注册到 TransactionMQProducer");
    }

    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            SeckillMessage message = JSON.parseObject(
                    new String(msg.getBody(), StandardCharsets.UTF_8), SeckillMessage.class);
            boolean ok = seckillService.preDeductSeckillStock(message);
            return ok ? LocalTransactionState.COMMIT_MESSAGE : LocalTransactionState.ROLLBACK_MESSAGE;
        } catch (Exception e) {
            log.error("秒杀本地事务执行异常: {}", e.getMessage(), e);
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
    }

    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        try {
            SeckillMessage message = JSON.parseObject(
                    new String(msg.getBody(), StandardCharsets.UTF_8), SeckillMessage.class);
            // 1) 预扣标记仍在 → 库存已扣但消息未投递，应提交让其建单
            if (seckillService.isPreDeducted(message.orderNo())) {
                return LocalTransactionState.COMMIT_MESSAGE;
            }
            // 2) 标记已过期，但订单已建（消费者已处理过本消息）→ 提交，避免丢弃已建单的消息
            if (orderService.seckillOrderExists(message.orderNo())) {
                return LocalTransactionState.COMMIT_MESSAGE;
            }
            // 3) 标记不在且订单未建 → 预扣未生效（或已回滚），丢弃半消息
            return LocalTransactionState.ROLLBACK_MESSAGE;
        } catch (Exception e) {
            log.error("秒杀事务回查异常: {}", e.getMessage(), e);
            return LocalTransactionState.UNKNOW;
        }
    }
}
