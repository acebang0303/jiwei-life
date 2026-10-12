package com.jiwei.market.shortlink.component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 短链生成接口限流：按客户端 IP 限流。
 *
 * 两级：
 *   1. 主通道：Redisson RRateLimiter（Redis 令牌桶，按 IP 建 key + TTL 自动回收），
 *      额度是**全局**的 —— 多实例部署时不会被放大成「实例数 × 额度」。
 *   2. 降级通道：Redis 不可用时退化为本机 Caffeine 固定窗口。
 *      此时额度会被实例数放大，所以提供 degrade-instances：把它设成实际实例数，
 *      每台机器的本地额度就按比例摊薄（默认 1 = 单机部署，不做摊薄）。
 *
 * 关于 X-Forwarded-For：该头可被客户端伪造，直接采信等于把限流开关交给调用方。
 * 默认只取 remoteAddr；仅当部署确实在可信反向代理（Nginx）之后才打开 trust-forwarded-for。
 */
@Component
@Slf4j
public class CreateRateLimiter {

    private static final String KEY_PREFIX = "shortlink:rate:create:";

    private final RedissonClient redisson;
    private final int limitPerWindow;
    private final int windowSeconds;
    private final boolean trustForwardedFor;
    private final int degradeInstances;

    /** Redis 降级时的本机固定窗口计数器 */
    private final Cache<String, AtomicInteger> localCounters;

    public CreateRateLimiter(RedissonClient redisson,
                             @Value("${shortlink.rate-limit.limit-per-window:100}") int limitPerWindow,
                             @Value("${shortlink.rate-limit.window-seconds:60}") int windowSeconds,
                             @Value("${shortlink.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor,
                             @Value("${shortlink.rate-limit.degrade-instances:1}") int degradeInstances) {
        this.redisson = redisson;
        this.limitPerWindow = limitPerWindow;
        this.windowSeconds = windowSeconds;
        this.trustForwardedFor = trustForwardedFor;
        this.degradeInstances = Math.max(1, degradeInstances);
        // expireAfterWrite 只对 put 生效（计数器只 put 一次后自增），所以是真正的固定窗口
        this.localCounters = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(windowSeconds))
                .maximumSize(100_000)
                .build();
    }

    public boolean tryAcquire(String clientKey) {
        if (!StringUtils.hasText(clientKey)) {
            return true;
        }
        try {
            RRateLimiter limiter = redisson.getRateLimiter(KEY_PREFIX + clientKey);
            // 额度是全局的（不按实例摊薄），所以多实例下总限额仍然正确
            limiter.trySetRate(RateType.OVERALL, limitPerWindow, windowSeconds, RateIntervalUnit.SECONDS);
            // TTL 让 IP 维度 key 自动回收，避免无限增长
            limiter.expireAsync(Math.max(windowSeconds * 2L, 120L), TimeUnit.SECONDS);
            boolean acquired = limiter.tryAcquire();
            if (!acquired) {
                log.warn("短链生成被限流(Redis全局桶): ip={}, 限额={}/{}s", clientKey, limitPerWindow, windowSeconds);
            }
            return acquired;
        } catch (Exception e) {
            log.warn("Redis 限流不可用，降级为本机固定窗口: cause={}", e.getMessage());
            return tryAcquireLocal(clientKey);
        }
    }

    /** 降级通道：本机固定窗口，额度按 degradeInstances 摊薄，尽量贴近全局限额。 */
    private boolean tryAcquireLocal(String clientKey) {
        int localLimit = Math.max(1, limitPerWindow / degradeInstances);
        AtomicInteger counter = localCounters.get(clientKey, k -> new AtomicInteger());
        boolean acquired = counter.incrementAndGet() <= localLimit;
        if (!acquired) {
            log.warn("短链生成被限流(本机降级桶): ip={}, 本机限额={}/{}s（degrade-instances={}）",
                    clientKey, localLimit, windowSeconds, degradeInstances);
        }
        return acquired;
    }

    /** 解析限流维度：默认 remoteAddr，可信代理后才采信 X-Forwarded-For 首段。 */
    public String clientKey(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
