package com.jiwei.market.seckill.breaker;

import com.jiwei.market.seckill.config.SeckillProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCircuitBreakerTest {

    /** 可控时钟：滑动窗口行为完全确定，不靠 sleep 碰运气 */
    private final AtomicLong now = new AtomicLong(1_700_000_000_000L);

    private RedisCircuitBreaker breaker(int windowMillis, int buckets, int minimumCalls,
                                        int failureRate, long openDurationMs,
                                        int halfOpenPermits, int halfOpenMaxConcurrent) {
        SeckillProperties props = new SeckillProperties();
        props.getBreaker().setWindowMillis(windowMillis);
        props.getBreaker().setBucketCount(buckets);
        props.getBreaker().setMinimumCalls(minimumCalls);
        props.getBreaker().setFailureRateThreshold(failureRate);
        props.getBreaker().setOpenDurationMs(openDurationMs);
        props.getBreaker().setHalfOpenPermits(halfOpenPermits);
        props.getBreaker().setHalfOpenMaxConcurrent(halfOpenMaxConcurrent);
        return RedisCircuitBreaker.withClock(props, now::get);
    }

    /** 默认配置：10s 窗口 10 片，最少 20 次样本，故障率 50% */
    private RedisCircuitBreaker defaultBreaker() {
        return breaker(10_000, 10, 20, 50, 30_000, 5, 3);
    }

    private String call(RedisCircuitBreaker breaker, boolean fail) {
        return breaker.execute("test", () -> {
            if (fail) {
                throw new RedisConnectionFailureException("down");
            }
            return "ok";
        }, t -> "fallback");
    }

    // ---------- 滑动窗口判定 ----------

    @Test
    void shouldNotTripWhenSamplesBelowMinimum() {
        RedisCircuitBreaker breaker = breaker(10_000, 10, 20, 50, 30_000, 5, 3);

        // 只有 5 次调用且全部失败：样本不足，不应熔断（低流量保护）
        for (int i = 0; i < 5; i++) {
            call(breaker, true);
        }
        assertThat(breaker.getState()).isEqualTo(RedisCircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldTripWhenFailureRateExceedsThreshold() {
        RedisCircuitBreaker breaker = defaultBreaker();

        for (int i = 0; i < 20; i++) {
            call(breaker, i % 2 == 0); // 50% 失败率
        }
        assertThat(breaker.getState()).isEqualTo(RedisCircuitBreaker.State.OPEN);
    }

    @Test
    void shouldNotTripWhenFailureRateBelowThreshold() {
        RedisCircuitBreaker breaker = defaultBreaker();

        for (int i = 0; i < 40; i++) {
            call(breaker, i % 4 == 0); // 25% 失败率
        }
        assertThat(breaker.getState()).isEqualTo(RedisCircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldAgeOutOldFailuresAsWindowSlides() {
        RedisCircuitBreaker breaker = breaker(10_000, 10, 20, 50, 30_000, 5, 3);

        // 20 次全失败 -> 样本刚够且 100%，熔断
        for (int i = 0; i < 19; i++) {
            call(breaker, true);
        }
        assertThat(breaker.getState()).isEqualTo(RedisCircuitBreaker.State.CLOSED); // 还差 1 次样本
        call(breaker, true);
        assertThat(breaker.getState()).isEqualTo(RedisCircuitBreaker.State.OPEN);

        // 时间前进 1 个窗口 + 打开时长 -> HALF_OPEN
        now.addAndGet(30_000);
        call(breaker, false);
        assertThat(breaker.getState()).isIn(RedisCircuitBreaker.State.HALF_OPEN,
                RedisCircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldForgetFailuresOlderThanOneWindow() {
        RedisCircuitBreaker breaker = breaker(10_000, 10, 20, 50, 30_000, 5, 3);

        // 先攒 19 次失败（窗口内），随后时间滑过一整个窗口
        for (int i = 0; i < 19; i++) {
            call(breaker, true);
        }
        now.addAndGet(11_000);
        // 旧的失败已经滑出窗口：再来 19 次失败 + 1 次成功，窗口内故障率 < 50% ?
        for (int i = 0; i < 10; i++) {
            call(breaker, true);
        }
        for (int i = 0; i < 10; i++) {
            call(breaker, false);
        }
        long[] snapshot = breaker.windowSnapshot();
        assertThat(snapshot[0]).isEqualTo(20);   // 只统计窗口内的 20 次
        assertThat(snapshot[1]).isEqualTo(10);   // 其中 10 次失败 -> 50%... 阈值 50% 会触发
        assertThat(breaker.getState()).isEqualTo(RedisCircuitBreaker.State.OPEN);
    }

    @Test
    void shouldIgnoreBusinessExceptionsForFailureRate() {
        // 业务异常（非基础设施异常）不得计入故障率
        RedisCircuitBreaker breaker = breaker(10_000, 10, 5, 50, 30_000, 2, 3);

        for (int i = 0; i < 20; i++) {
            try {
                breaker.execute("test", () -> {
                    throw new IllegalStateException("business failure");
                }, t -> "fallback");
            } catch (IllegalStateException ignored) {
                // 按约定业务异常直接抛出
            }
        }
        assertThat(breaker.getState()).isEqualTo(RedisCircuitBreaker.State.CLOSED);
        assertThat(breaker.windowSnapshot()[0]).isZero();
    }

    // ---------- OPEN / HALF_OPEN ----------

    @Test
    void shouldShortCircuitWhileOpen() {
        RedisCircuitBreaker breaker = breaker(10_000, 10, 4, 50, 30_000, 2, 3);
        for (int i = 0; i < 4; i++) {
            call(breaker, true);
        }
        assertThat(breaker.getState()).isEqualTo(RedisCircuitBreaker.State.OPEN);

        AtomicInteger invoked = new AtomicInteger();
        String result = breaker.execute("test", () -> {
            invoked.incrementAndGet();
            return "ok";
        }, t -> "fallback");

        assertThat(result).isEqualTo("fallback");
        assertThat(invoked.get()).isZero();
    }

    @Test
    void shouldCloseAfterEnoughSuccessfulProbes() {
        RedisCircuitBreaker breaker = breaker(10_000, 10, 4, 50, 30_000, 2, 2);
        for (int i = 0; i < 4; i++) {
            call(breaker, true);
        }
        assertThat(breaker.getState()).isEqualTo(RedisCircuitBreaker.State.OPEN);

        // openDurationMs 到期后进入 HALF_OPEN，连续 2 次成功探测恢复 CLOSED
        now.addAndGet(31_000);
        assertThat(call(breaker, false)).isEqualTo("ok");
        assertThat(call(breaker, false)).isEqualTo("ok");
        assertThat(breaker.getState()).isEqualTo(RedisCircuitBreaker.State.CLOSED);
    }

    @Test
    void shouldReopenImmediatelyWhenProbeFails() {
        RedisCircuitBreaker breaker = breaker(10_000, 10, 4, 50, 30_000, 3, 3);
        for (int i = 0; i < 4; i++) {
            call(breaker, true);
        }
        now.addAndGet(31_000);

        call(breaker, true);
        assertThat(breaker.getState()).isEqualTo(RedisCircuitBreaker.State.OPEN);
    }

    @Test
    void shouldLimitConcurrentProbesInHalfOpen() {
        RedisCircuitBreaker breaker = breaker(10_000, 10, 4, 50, 30_000, 5, 1);
        for (int i = 0; i < 4; i++) {
            call(breaker, true);
        }
        now.addAndGet(31_000);

        // 第一个探针占住额度（supplier 内不放行第二个）
        AtomicInteger admitted = new AtomicInteger();
        breaker.execute("probe-1", () -> {
            admitted.incrementAndGet();
            // 在探针内部再发起一次调用：额度已被占用，应被拒绝（不计入 admitted）
            breaker.execute("probe-inner", () -> {
                admitted.incrementAndGet();
                return "ok";
            }, t -> "rejected");
            return "ok";
        }, t -> "rejected");

        assertThat(admitted.get()).isEqualTo(1);
    }
}
