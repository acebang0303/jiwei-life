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
        /** 滑动窗口总长度(ms)：统计「最近这段时间」的调用，而不是固定批次的窗口 */
        private long windowMillis = 10_000;
        /** 窗口分片数：窗口被切成这么多时间片轮转统计，分片越细越平滑 */
        private int bucketCount = 10;
        /** 窗口内最少样本数：低于该数量不做判定，避免低流量下 1 次失败就 100% 熔断 */
        private int minimumCalls = 20;
        /** 故障率阈值(%)，窗口内达到即跳闸 */
        private int failureRateThreshold = 50;
        /** 熔断持续时长(ms)，到期后进入 HALF_OPEN 放探针 */
        private long openDurationMs = 30_000;
        /** HALF_OPEN 期间连续成功多少次才恢复 CLOSED */
        private int halfOpenPermits = 5;
        /** HALF_OPEN 期间允许同时在飞的探针数上限，防止下游刚恢复就被打满 */
        private int halfOpenMaxConcurrent = 3;
    }

    @Data
    public static class Reconcile {
        private boolean enabled = true;
        private boolean autoFix = true;
        private long intervalMs = 60_000;        // 对账周期
    }
}
