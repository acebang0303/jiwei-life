package com.jiwei.market.shortlink.component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 短链生成接口限流：按 IP 固定窗口计数，窗口内超过阈值拒绝。
 */
@Component
public class CreateRateLimiter {

    private static final int LIMIT = 100;
    private static final long WINDOW_SECONDS = 60;

    private final Cache<String, AtomicInteger> counters;

    public CreateRateLimiter() {
        this.counters = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(WINDOW_SECONDS))
                .build();
    }

    public boolean tryAcquire(String clientIp) {
        AtomicInteger counter = counters.get(clientIp, key -> new AtomicInteger());
        return counter.incrementAndGet() <= LIMIT;
    }
}
