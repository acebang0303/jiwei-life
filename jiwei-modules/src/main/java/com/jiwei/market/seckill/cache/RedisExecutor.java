package com.jiwei.market.seckill.cache;

import com.jiwei.market.seckill.breaker.RedisCircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Redis API 统一入口：所有读写都经过熔断器，故障时快速降级。
 */
@Slf4j
@Component
public class RedisExecutor {

    private final StringRedisTemplate redis;
    private final RedisCircuitBreaker breaker;

    public RedisExecutor(StringRedisTemplate redis, RedisCircuitBreaker breaker) {
        this.redis = redis;
        this.breaker = breaker;
    }

    public <T> T execute(String name, Supplier<T> call, Function<Throwable, T> fallback) {
        return breaker.execute(name, call, fallback);
    }

    /** 当前 Redis 是否处于熔断/降级状态（OPEN 或 HALF_OPEN） */
    public boolean isDegraded() {
        return breaker.getState() != RedisCircuitBreaker.State.CLOSED;
    }

    public String get(String key, Function<Throwable, String> fallback) {
        return breaker.execute("redis:get", () -> redis.opsForValue().get(key), fallback);
    }

    public void set(String key, String value, Duration ttl) {
        breaker.execute("redis:set", () -> {
            redis.opsForValue().set(key, value, ttl);
            return null;
        }, t -> {
            log.warn("cache write degraded, key={}, cause={}", key, t.getMessage());
            return null;
        });
    }

    public void setIfAbsent(String key, String value, Duration ttl) {
        breaker.execute("redis:setIfAbsent", () -> redis.opsForValue().setIfAbsent(key, value, ttl),
                t -> {
                    log.warn("cache setIfAbsent degraded, key={}, cause={}", key, t.getMessage());
                    return false;
                });
    }

    public void del(String... keys) {
        breaker.execute("redis:del", () -> {
            redis.delete(List.of(keys));
            return null;
        }, t -> {
            log.warn("cache delete degraded, keys={}, cause={}", keys, t.getMessage());
            return null;
        });
    }

    public Long eval(DefaultRedisScript<Long> script, List<String> keys,
                     Function<Throwable, Long> fallback, Object... args) {
        return breaker.execute("redis:lua:" + script.hashCode(), () -> redis.execute(script, keys, args), fallback);
    }

    public void expire(String key, long timeout, TimeUnit unit) {
        breaker.execute("redis:expire", () -> {
            redis.expire(key, timeout, unit);
            return null;
        }, t -> {
            log.warn("expire degraded, key={}", key);
            return null;
        });
    }
}
