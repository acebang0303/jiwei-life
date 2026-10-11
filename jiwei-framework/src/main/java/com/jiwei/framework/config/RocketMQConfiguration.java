package com.jiwei.framework.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 配置类
 * 使用传统 rocketmq-client（remoting 协议），与 Docker apache/rocketmq:5.3.0 兼容
 */
@Configuration
@Slf4j
public class RocketMQConfiguration {

    @Value("${rocketmq.name-server:localhost:9876}")
    private String nameServer;

    @Value("${rocketmq.producer.group:jiwei-producer-group}")
    private String producerGroup;

    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public DefaultMQProducer rocketMQProducer() throws MQClientException {
        DefaultMQProducer producer = new DefaultMQProducer(producerGroup);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(5000);
        producer.setRetryTimesWhenSendFailed(2);
        log.info("RocketMQ Producer 初始化成功, NameServer: {}", nameServer);
        return producer;
    }

    /**
     * 事务消息 Producer：与普通 producer 用不同 group，避免消息路由冲突。
     * 全局 TransactionListener 由各事务监听器（如 SeckillTransactionListener）在 @PostConstruct
     * 中通过 setTransactionListener 注册；新增事务主题需独立 listener 或独立 producer，避免互相覆盖。
     */
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    public TransactionMQProducer rocketMQTransactionProducer() throws MQClientException {
        TransactionMQProducer producer = new TransactionMQProducer(
                producerGroup + "-tx");
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(5000);
        log.info("RocketMQ TransactionProducer 初始化成功, NameServer: {}", nameServer);
        return producer;
    }
}
