package com.jiwei.market.seckill.breaker;

import com.jiwei.market.seckill.config.SeckillProperties;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Redis 轻量熔断器（CLOSED -> OPEN -> HALF_OPEN）。
 * 只统计基础设施异常（连接失败/超时），业务异常直接抛出。
 */
@Slf4j
@Component
public class RedisCircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final SeckillProperties.Breaker config;

    @Getter
    private volatile State state = State.CLOSED;

    private final AtomicLong openUntil = new AtomicLong(0);
    private final AtomicInteger windowCalls = new AtomicInteger(0);
    private final AtomicInteger windowFailures = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccess = new AtomicInteger(0);

    public RedisCircuitBreaker(SeckillProperties properties) {
        this.config = properties.getBreaker();
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
            throw t;
        }
    }

    private boolean allowRequest() {
        State current = state;
        if (current == State.CLOSED) {
            return true;
        }
        if (current == State.OPEN) {
            if (System.currentTimeMillis() >= openUntil.get()) {
                if (tryTransition(State.OPEN, State.HALF_OPEN)) {
                    halfOpenSuccess.set(0);
                    log.info("熔断器进入HALF_OPEN，放行探测流量...");
                } else if (state == State.OPEN) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return state == State.HALF_OPEN || state == State.CLOSED;
    }

    private void onSuccess() {
        if (state == State.HALF_OPEN) {
            int success = halfOpenSuccess.incrementAndGet();
            if (success >= config.getHalfOpenPermits() && tryTransition(State.HALF_OPEN, State.CLOSED)) {
                resetWindow();
                log.info("熔断器CLOSED，探测成功已恢复");
            }
            return;
        }
        checkWindow();
    }

    private void onFailure() {
        if (state == State.HALF_OPEN) {
            trip();
            return;
        }
        windowFailures.incrementAndGet();
        checkWindow();
    }

    /** 每次调用（成功/失败）都计入窗口，窗口满时按失败率判定是否熔断 */
    private void checkWindow() {
        int calls = windowCalls.incrementAndGet();
        if (calls >= config.getWindowSize()) {
            int failures = windowFailures.get();
            int failureRate = failures * 100 / calls;
            if (failureRate >= config.getFailureRateThreshold()) {
                trip();
            }
            resetWindow();
        }
    }

    private void resetWindow() {
        windowCalls.set(0);
        windowFailures.set(0);
    }

    private void trip() {
        if (tryTransition(state, State.OPEN)) {
            openUntil.set(System.currentTimeMillis() + config.getOpenDurationMs());
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
}
