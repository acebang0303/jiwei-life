package com.jiwei.market.seckill.breaker;

import com.jiwei.market.seckill.config.SeckillProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Redis 轻量三态熔断器（CLOSED → OPEN → HALF_OPEN → CLOSED）。
 *
 * 设计取舍（面试可讲）：
 * 1. 只统计「基础设施异常」（连接失败/超时/系统异常），业务异常直接抛出。
 *    否则一次正常的业务失败（如库存不足）会污染故障率，把熔断器误触发。
 * 2. 统计窗口是**真滑动窗口**：把 windowMillis 切成 bucketCount 个时间片环形轮转，
 *    判定时汇总「最近一个窗口内」的所有时间片。相比固定窗口（累计 N 次就清零），
 *    它在窗口边界不会出现「故障率突然跳变」的假象。
 *    另外设 minimumCalls 下限：低流量时样本太少不判定，避免 1 次失败就 100% 熔断。
 * 3. HALF_OPEN 不是「放行全部流量」，而是限制并发探针数（halfOpenMaxConcurrent），
 *    避免下游刚恢复就被瞬间打满；连续 halfOpenPermits 次成功才回到 CLOSED，
 *    期间任一失败立刻重新 OPEN。
 */
@Slf4j
@Component
public class RedisCircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final SeckillProperties.Breaker config;
    private final LongSupplier clock;

    /** 滑动窗口：环形时间片（calls/failures 按片统计，epochs 记录该片属于哪个时间片编号） */
    private final int bucketCount;
    private final long bucketMillis;
    private final long[] bucketCalls;
    private final long[] bucketFailures;
    private final long[] bucketEpochs;

    @Getter
    private volatile State state = State.CLOSED;

    private final AtomicLong openUntil = new AtomicLong(0);
    /** HALF_OPEN 期间累计的成功探测数 */
    private final AtomicInteger halfOpenSuccess = new AtomicInteger(0);
    /** HALF_OPEN 期间在飞的探针数 */
    private final AtomicInteger halfOpenInflight = new AtomicInteger(0);

    @Autowired
    public RedisCircuitBreaker(SeckillProperties properties) {
        this(properties, System::currentTimeMillis);
    }

    /**
     * 可注入时钟的构造器：仅供测试通过 {@link #withClock} 使用。
     * 注意：Spring 会把「声明的构造器个数」都算进来（private 也算），
     * 所以只要存在第二个构造器，就必须给要用的那个显式标注 @Autowired，
     * 否则启动报 "No default constructor found"。
     */
    private RedisCircuitBreaker(SeckillProperties properties, LongSupplier clock) {
        this.config = properties.getBreaker();
        this.clock = clock;
        this.bucketCount = Math.max(2, config.getBucketCount());
        this.bucketMillis = Math.max(1, config.getWindowMillis() / this.bucketCount);
        this.bucketCalls = new long[this.bucketCount];
        this.bucketFailures = new long[this.bucketCount];
        this.bucketEpochs = new long[this.bucketCount];
        // 初始标记为「远古时间片」，第一次写入时会被重置
        java.util.Arrays.fill(this.bucketEpochs, Long.MIN_VALUE);
    }

    /**
     * 测试工厂：用可控时钟构造熔断器。
     * 之所以用静态工厂而不是第二个可注入构造器：见上面的构造器注释。
     */
    static RedisCircuitBreaker withClock(SeckillProperties properties, LongSupplier clock) {
        return new RedisCircuitBreaker(properties, clock);
    }

    public <T> T execute(String name, Supplier<T> call, Function<Throwable, T> fallback) {
        if (!allowRequest()) {
            log.warn("熔断器OPEN，快速失败走降级: {}", name);
            return fallback.apply(new RedisConnectionFailureException("redis circuit breaker open"));
        }
        try {
            T result = call.get();
            onSuccess();
            return result;
        } catch (Throwable t) {
            if (isInfraFailure(t)) {
                onFailure();
                log.error("Redis基础设施异常(计入熔断统计): [{}], cause: {}", name, t.getMessage());
                return fallback.apply(t);
            }
            // 业务异常不计入熔断；HALF_OPEN 下探针已占用，需归还额度
            releaseProbe();
            throw t;
        }
    }

    private boolean allowRequest() {
        State current = state;
        if (current == State.CLOSED) {
            return true;
        }
        if (current == State.OPEN) {
            if (clock.getAsLong() < openUntil.get()) {
                return false;
            }
            if (tryTransition(State.OPEN, State.HALF_OPEN)) {
                halfOpenSuccess.set(0);
                halfOpenInflight.set(0);
                log.info("熔断器进入HALF_OPEN，最多放行 {} 个并发探针...", config.getHalfOpenMaxConcurrent());
            } else if (state == State.OPEN) {
                // 其他线程已经抢到状态迁移权，本线程这一轮不参与探测
                return false;
            }
        }
        if (state != State.HALF_OPEN) {
            return state == State.CLOSED;
        }
        if (halfOpenInflight.incrementAndGet() > config.getHalfOpenMaxConcurrent()) {
            halfOpenInflight.decrementAndGet();
            return false;
        }
        return true;
    }

    private void onSuccess() {
        if (state == State.HALF_OPEN) {
            try {
                int success = halfOpenSuccess.incrementAndGet();
                if (success >= config.getHalfOpenPermits() && tryTransition(State.HALF_OPEN, State.CLOSED)) {
                    // 恢复后清空窗口：否则刚恢复就会被熔断前的历史失败再次拉闸，造成抖动
                    synchronized (this) {
                        java.util.Arrays.fill(bucketEpochs, Long.MIN_VALUE);
                        java.util.Arrays.fill(bucketCalls, 0L);
                        java.util.Arrays.fill(bucketFailures, 0L);
                    }
                    log.info("熔断器CLOSED，探测成功已恢复");
                }
            } finally {
                releaseProbe();
            }
            return;
        }
        record(false);
    }

    private void onFailure() {
        if (state == State.HALF_OPEN) {
            try {
                trip();
            } finally {
                releaseProbe();
            }
            return;
        }
        record(true);
    }

    /** 归还 HALF_OPEN 探针额度。 */
    private void releaseProbe() {
        if (state == State.HALF_OPEN) {
            halfOpenInflight.updateAndGet(v -> v > 0 ? v - 1 : 0);
        }
    }

    /** 记一次调用到当前时间片，并按滑动窗口判定是否跳闸。 */
    private void record(boolean failure) {
        boolean trip;
        synchronized (this) {
            long nowBucket = clock.getAsLong() / bucketMillis;
            // 清掉已经滑出窗口的时间片
            long minValid = nowBucket - bucketCount + 1;
            for (int i = 0; i < bucketCount; i++) {
                if (bucketEpochs[i] < minValid) {
                    bucketEpochs[i] = nowBucket;
                    bucketCalls[i] = 0;
                    bucketFailures[i] = 0;
                }
            }
            int idx = (int) Math.floorMod(nowBucket, bucketCount);
            if (bucketEpochs[idx] != nowBucket) {
                bucketEpochs[idx] = nowBucket;
                bucketCalls[idx] = 0;
                bucketFailures[idx] = 0;
            }
            bucketCalls[idx]++;
            if (failure) {
                bucketFailures[idx]++;
            }

            long calls = 0;
            long failures = 0;
            for (int i = 0; i < bucketCount; i++) {
                if (bucketEpochs[i] >= minValid && bucketEpochs[i] <= nowBucket) {
                    calls += bucketCalls[i];
                    failures += bucketFailures[i];
                }
            }
            trip = calls >= config.getMinimumCalls()
                    && failures * 100 / calls >= config.getFailureRateThreshold();
        }
        if (trip) {
            trip();
        }
    }

    private void trip() {
        if (tryTransition(state, State.OPEN)) {
            openUntil.set(clock.getAsLong() + config.getOpenDurationMs());
            log.error("熔断器跳闸OPEN，持续{}ms", config.getOpenDurationMs());
        }
    }

    private synchronized boolean tryTransition(State from, State to) {
        if (state == from) {
            state = to;
            return true;
        }
        return false;
    }

    private boolean isInfraFailure(Throwable t) {
        return t instanceof RedisConnectionFailureException
                || t instanceof QueryTimeoutException
                || t instanceof RedisSystemException
                || (t.getCause() != null && t.getCause() instanceof java.net.SocketException);
    }

    /** 仅供测试/监控观察窗口内样本（包级可见）。 */
    synchronized long[] windowSnapshot() {
        long nowBucket = clock.getAsLong() / bucketMillis;
        long minValid = nowBucket - bucketCount + 1;
        long calls = 0;
        long failures = 0;
        for (int i = 0; i < bucketCount; i++) {
            if (bucketEpochs[i] >= minValid && bucketEpochs[i] <= nowBucket) {
                calls += bucketCalls[i];
                failures += bucketFailures[i];
            }
        }
        return new long[]{calls, failures};
    }
}
