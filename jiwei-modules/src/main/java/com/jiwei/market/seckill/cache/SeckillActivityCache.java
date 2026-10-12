package com.jiwei.market.seckill.cache;

import com.alibaba.fastjson2.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jiwei.market.mapper.SeckillActivityMapper;
import com.jiwei.pojo.entity.SeckillActivity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 秒杀活动元数据二级缓存：Caffeine 本地 → Redis → DB。
 *
 * 目标：seckillBuy 热路径完全去 DB（存在性 + 时间窗口 + 限购配置均来自缓存）。
 *
 * - L1 Caffeine：秒级短 TTL，单机扛洪峰，允许各实例秒级不一致；
 * - L2 Redis：分钟级 TTL + 随机抖动防雪崩，跨实例共享；
 * - 防穿透：不存在的活动在 L1/L2 缓存 NULL 标记（短 TTL），恶意 id 不打到 DB；
 * - 一致性：活动元数据（价格/时间/限购）创建后不可变（管理端无编辑入口），
 *   读路径无需失效；createActivity 在 afterCommit 主动 put。
 * - Redis 故障：经 RedisExecutor 熔断降级，L1 miss 时直接查 DB（正确性优先）。
 */
@Slf4j
@Component
public class SeckillActivityCache {

    static final String KEY_PREFIX = "seckill:activity:";
    /** 不存在标记：L2 用空串表示「确认不存在」 */
    static final String NULL_MARKER = "";

    private final Cache<Long, Optional<SeckillActivity>> localCache = Caffeine.newBuilder()
            .maximumSize(1024)
            .expireAfterWrite(Duration.ofSeconds(5))
            .build();

    private final SeckillActivityMapper activityMapper;
    private final RedisExecutor redisExecutor;

    public SeckillActivityCache(SeckillActivityMapper activityMapper, RedisExecutor redisExecutor) {
        this.activityMapper = activityMapper;
        this.redisExecutor = redisExecutor;
    }

    /**
     * 查询活动元数据，三级链路：L1 → L2 → DB。
     * 返回 Optional.empty 表示活动不存在（已缓存 NULL 标记防穿透）。
     */
    public Optional<SeckillActivity> get(Long activityId) {
        if (activityId == null || activityId <= 0) {
            return Optional.empty();
        }
        Optional<SeckillActivity> local = localCache.getIfPresent(activityId);
        if (local != null) {
            return local;
        }
        // L2 Redis：经熔断器；故障时 fallback 为 null（未命中），继续查 DB
        String json = redisExecutor.get(KEY_PREFIX + activityId, t -> null);
        if (json != null) {
            Optional<SeckillActivity> parsed = NULL_MARKER.equals(json)
                    ? Optional.empty()
                    : Optional.ofNullable(JSON.parseObject(json, SeckillActivity.class));
            localCache.put(activityId, parsed);
            return parsed;
        }
        // L3 DB 兜底（含 Redis 故障降级场景）
        SeckillActivity activity = activityMapper.selectById(activityId);
        Optional<SeckillActivity> result = Optional.ofNullable(activity);
        localCache.put(activityId, result);
        putRedis(activityId, activity);
        return result;
    }

    /** 活动创建后主动写缓存（afterCommit 调用），避免首个请求穿透到 DB */
    public void put(SeckillActivity activity) {
        if (activity == null || activity.getId() == null) {
            return;
        }
        localCache.put(activity.getId(), Optional.of(activity));
        putRedis(activity.getId(), activity);
    }

    private void putRedis(Long activityId, SeckillActivity activity) {
        // 存在：5min 基础 TTL + [0,60s) 抖动；不存在：NULL 标记 60s 短 TTL 防穿透
        if (activity != null) {
            long ttlSeconds = 300 + ThreadLocalRandom.current().nextLong(60);
            redisExecutor.set(KEY_PREFIX + activityId, JSON.toJSONString(activity),
                    Duration.ofSeconds(ttlSeconds));
        } else {
            redisExecutor.set(KEY_PREFIX + activityId, NULL_MARKER, Duration.ofSeconds(60));
        }
    }
}
