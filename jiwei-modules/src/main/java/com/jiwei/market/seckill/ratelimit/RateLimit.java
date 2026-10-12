package com.jiwei.market.seckill.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级限流：基于 Redisson RRateLimiter 的 Redis 令牌桶算法。
 *
 * 注意语义：额度是「permits 个令牌 / intervalSeconds 秒」，不是「每秒 permits 个」。
 * （Redisson 的 trySetRate(RateType, rate, interval, unit) 就是 rate/interval，
 * 旧字段名 permitsPerSecond 在 intervalSeconds != 1 时会误导，故改名。）
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(RateLimit.RateLimits.class)
public @interface RateLimit {

    /** 限流 key（支持 SpEL，形如 "seckill:user:{#userId}"） */
    String key();

    /** 每个速率周期内可用的令牌数 */
    int permits() default 100;

    /** 速率周期（秒） */
    int intervalSeconds() default 1;

    /** 每次请求消耗令牌 */
    int permitsPerRequest() default 1;

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface RateLimits {
        RateLimit[] value();
    }
}
