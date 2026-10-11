package com.jiwei.market.seckill.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级限流：基于 Redisson RRateLimiter 的 Redis 令牌桶算法。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Repeatable(RateLimit.RateLimits.class)
public @interface RateLimit {

    /** 限流 key（支持 SpEL） */
    String key();

    /** 每秒令牌数 */
    int permitsPerSecond() default 100;

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
