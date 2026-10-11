package com.jiwei.market.seckill.breaker;

import com.jiwei.market.seckill.config.SeckillProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCircuitBreakerTest {

    @Test
    void shouldOpenAfterFailureRateExceedsThreshold() {
        SeckillProperties props = new SeckillProperties();
        props.getBreaker().setWindowSize(10);
        props.getBreaker().setFailureRateThreshold(50);
        RedisCircuitBreaker breaker = new RedisCircuitBreaker(props);

        AtomicInteger fallbacks = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            final boolean fail = i % 2 == 0; // 50% 失败率
            breaker.execute("test", () -> {
                if (fail) {
                    throw new RedisConnectionFailureException("down");
                }
                return "ok";
            }, t -> {
                fallbacks.incrementAndGet();
                return "fallback";
            });
        }
        // 达到阈值后应进入 OPEN 或至少触发过熔断降级
        assertThat(breaker.getState()).isIn(RedisCircuitBreaker.State.OPEN, RedisCircuitBreaker.State.HALF_OPEN);
    }
}
