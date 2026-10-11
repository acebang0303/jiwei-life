package com.jiwei;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.jiwei.framework.properties.JwtProperties;
import com.jiwei.framework.properties.AliOssProperties;
import com.jiwei.framework.properties.WeChatProperties;
import com.jiwei.framework.properties.AiProperties;

/**
 * 吉味生活 · JiweiLife 主应用启动类
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@EnableTransactionManagement //开启注解方式的事务管理
@Slf4j
@EnableCaching                //开启Spring Cache缓存
@EnableScheduling             //开启定时任务
@EnableFeignClients(basePackages = "com.jiwei.merchant.client")
@EnableConfigurationProperties({JwtProperties.class, AliOssProperties.class, WeChatProperties.class, AiProperties.class})
public class JiweiApplication {
    public static void main(String[] args) {
        SpringApplication.run(JiweiApplication.class, args);
        log.info("=== 吉味生活 · JiweiLife 服务启动成功 ===");
    }
}
