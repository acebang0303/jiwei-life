package com.jiwei.market.seckill.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 秒杀配置（熔断/对账）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "seckill")
public class SeckillProperties {

    private Breaker breaker = new Breaker();
    private Reconcile reconcile = new Reconcile();

    @Data
    public static class Breaker {
        private int windowSize = 100;            // 滑动窗口大小
        private int failureRateThreshold = 50;   // 失败率阈值(%)
        private long openDurationMs = 30_000;    // 熔断持续时长
        private int halfOpenPermits = 5;         // 半开探测放行数
    }

    @Data
    public static class Reconcile {
        private boolean enabled = true;
        private boolean autoFix = true;
        private long intervalMs = 60_000;        // 对账周期
    }
}
