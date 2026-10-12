package com.jiwei.market.seckill.ratelimit;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.jiwei.common.exception.OrderBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redisson 令牌桶限流切面：Redis 令牌桶 + Redis 故障时降级本地令牌桶。
 *
 * 降级说明（面试边界）：本地桶是单机内存态，多实例部署时全局限额会被放大成
 * 「实例数 × permits」；它只保证「Redis 挂掉时限流能力不消失」，不保证全局精确。
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    /** 本地降级桶的数量上限：SpEL key 按用户维度展开时防止内存无界增长 */
    private static final int LOCAL_BUCKET_MAX = 10_000;

    private final RedissonClient redisson;
    private final int degradeInstances;
    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final TemplateParserContext templateContext = new TemplateParserContext();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();
    private final Map<String, LocalTokenBucket> localBuckets = Caffeine.newBuilder()
            .maximumSize(LOCAL_BUCKET_MAX)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .<String, LocalTokenBucket>build()
            .asMap();

    public RateLimitAspect(RedissonClient redisson,
                           @Value("${seckill.ratelimit.degrade-instances:1}") int degradeInstances) {
        this.redisson = redisson;
        this.degradeInstances = Math.max(1, degradeInstances);
    }

    @Around("@annotation(com.jiwei.market.seckill.ratelimit.RateLimit)")
    public Object around(ProceedingJoinPoint point) throws Throwable {
        Method method = ((MethodSignature) point.getSignature()).getMethod();
        for (RateLimit rateLimit : method.getAnnotationsByType(RateLimit.class)) {
            acquire(point, rateLimit);
        }
        return point.proceed();
    }

    private void acquire(ProceedingJoinPoint point, RateLimit rateLimit) {
        String resolvedKey = resolveKey(point, rateLimit.key());
        String bucketKey = "seckill:rate:" + resolvedKey;
        try {
            RRateLimiter limiter = redisson.getRateLimiter(bucketKey);
            limiter.trySetRate(RateType.OVERALL, rateLimit.permits(),
                    rateLimit.intervalSeconds(), RateIntervalUnit.SECONDS);
            limiter.expireAsync(Math.max(rateLimit.intervalSeconds() * 10, 60), TimeUnit.SECONDS);
            if (!limiter.tryAcquire(rateLimit.permitsPerRequest())) {
                log.warn("令牌桶限流拒绝(Redis桶): key={}, rate={}/{}s", resolvedKey,
                        rateLimit.permits(), rateLimit.intervalSeconds());
                throw new OrderBusinessException("请求过于频繁，请稍后再试");
            }
        } catch (OrderBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Redis不可用，令牌桶降级为本地桶: key={}, cause={}", resolvedKey, e.getMessage());
            int localPermits = Math.max(1, rateLimit.permits() / degradeInstances);
            if (!localBucket(bucketKey, localPermits, rateLimit.intervalSeconds())
                    .tryAcquire(rateLimit.permitsPerRequest())) {
                log.warn("令牌桶限流拒绝(本地桶): key={}, rate={}/{}s（本机额度，degrade-instances={}）",
                        resolvedKey, localPermits, rateLimit.intervalSeconds(), degradeInstances);
                throw new OrderBusinessException("请求过于频繁，请稍后再试");
            }
        }
    }

    private LocalTokenBucket localBucket(String key, long permits, long intervalSeconds) {
        return localBuckets.computeIfAbsent(key, k -> new LocalTokenBucket(permits, intervalSeconds));
    }

    private String resolveKey(ProceedingJoinPoint point, String spelKey) {
        if (!spelKey.contains("{")) {
            return spelKey;
        }
        Expression expression = expressionCache.computeIfAbsent(spelKey,
                k -> parser.parseExpression(k, templateContext));
        MethodSignature signature = (MethodSignature) point.getSignature();
        EvaluationContext context = new StandardEvaluationContext();
        String[] names = parameterNameDiscoverer.getParameterNames(signature.getMethod());
        Object[] args = point.getArgs();
        if (names != null) {
            for (int i = 0; i < names.length; i++) {
                context.setVariable(names[i], args[i]);
            }
        }
        return expression.getValue(context, String.class);
    }

    /** 本地令牌桶降级实现（单机）。令牌以「毫令牌」为单位用 long 表示，避免浮点误差。 */
    static final class LocalTokenBucket {

        private final long capacityMillis;
        private final double tokensPerMillis;
        private final AtomicLong tokensMillis;
        private volatile long lastRefillNanos;

        LocalTokenBucket(long permits, long intervalSeconds) {
            this.capacityMillis = permits * 1000L;
            this.tokensPerMillis = (double) permits / (intervalSeconds * 1000.0);
            this.tokensMillis = new AtomicLong(permits * 1000L);
            this.lastRefillNanos = System.nanoTime();
        }

        synchronized boolean tryAcquire(long permits) {
            refill();
            long cost = permits * 1000L;
            if (tokensMillis.get() >= cost) {
                tokensMillis.addAndGet(-cost);
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            long elapsedMillis = (now - lastRefillNanos) / 1_000_000;
            if (elapsedMillis <= 0) {
                return;
            }
            long generatedMillis = (long) (elapsedMillis * tokensPerMillis * 1000);
            tokensMillis.set(Math.min(capacityMillis, tokensMillis.get() + generatedMillis));
            lastRefillNanos = now;
        }
    }
}
