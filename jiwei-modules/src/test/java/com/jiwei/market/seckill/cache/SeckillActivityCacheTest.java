package com.jiwei.market.seckill.cache;

import com.alibaba.fastjson2.JSON;
import com.jiwei.market.mapper.SeckillActivityMapper;
import com.jiwei.pojo.entity.SeckillActivity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SeckillActivityCacheTest {

    private final SeckillActivityMapper activityMapper = mock(SeckillActivityMapper.class);
    private final RedisExecutor redisExecutor = mock(RedisExecutor.class);

    private SeckillActivityCache newCache() {
        return new SeckillActivityCache(activityMapper, redisExecutor);
    }

    private SeckillActivity activity() {
        SeckillActivity act = new SeckillActivity();
        act.setId(5L);
        act.setDishId(5L);
        act.setStock(50);
        act.setLimitPerUser(1);
        act.setSeckillPrice(new BigDecimal("9.90"));
        act.setStartTime(LocalDateTime.now().minusHours(1));
        act.setEndTime(LocalDateTime.now().plusHours(1));
        return act;
    }

    @SuppressWarnings("unchecked")
    private void stubRedisGet(String value) {
        when(redisExecutor.get(anyString(), any(Function.class))).thenReturn(value);
    }

    @Test
    void dbHitShouldPopulateBothLevels() {
        // L1/L2 均 miss → 查 DB → 写 L1（下次命中）+ 写 L2（带 TTL）
        stubRedisGet(null);
        when(activityMapper.selectById(5L)).thenReturn(activity());
        SeckillActivityCache cache = newCache();

        Optional<SeckillActivity> first = cache.get(5L);

        assertThat(first).isPresent();
        verify(activityMapper).selectById(5L);
        verify(redisExecutor).set(eq("seckill:activity:5"), anyString(), any(Duration.class));

        // 第二次读：L1 命中，不再访问 DB/Redis
        clearInvocations(activityMapper, redisExecutor);
        Optional<SeckillActivity> second = cache.get(5L);
        assertThat(second).isPresent();
        verifyNoInteractions(activityMapper);
        verify(redisExecutor, never()).get(anyString(), any(Function.class));
    }

    @Test
    void redisJsonHitShouldNotTouchDb() {
        SeckillActivity act = activity();
        stubRedisGet(JSON.toJSONString(act));
        SeckillActivityCache cache = newCache();

        Optional<SeckillActivity> result = cache.get(5L);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(5L);
        assertThat(result.get().getLimitPerUser()).isEqualTo(1);
        verifyNoInteractions(activityMapper);
        verify(redisExecutor, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void nullMarkerShouldShortCircuitAndCacheNegative() {
        // L2 命中 NULL 标记 → 返回 empty 且不查 DB、不回写 L2
        stubRedisGet(SeckillActivityCache.NULL_MARKER);
        SeckillActivityCache cache = newCache();

        assertThat(cache.get(5L)).isEmpty();
        verifyNoInteractions(activityMapper);
        verify(redisExecutor, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void dbMissShouldWriteNullMarkerToRedis() {
        // DB 也不存在 → L2 写 NULL 标记（60s 短 TTL），后续恶意 id 不再打 DB
        stubRedisGet(null);
        when(activityMapper.selectById(5L)).thenReturn(null);
        SeckillActivityCache cache = newCache();

        assertThat(cache.get(5L)).isEmpty();

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisExecutor).set(eq("seckill:activity:5"), valueCaptor.capture(), any(Duration.class));
        assertThat(valueCaptor.getValue()).isEqualTo(SeckillActivityCache.NULL_MARKER);
    }

    @Test
    void redisFailureShouldFallBackToDb() {
        // Redis 熔断降级（get fallback 返回 null）→ 直接查 DB，正确性优先
        stubRedisGet(null);
        when(activityMapper.selectById(5L)).thenReturn(activity());
        SeckillActivityCache cache = newCache();

        assertThat(cache.get(5L)).isPresent();
        verify(activityMapper).selectById(5L);
    }

    @Test
    void putShouldPopulateBothLevels() {
        SeckillActivity act = activity();
        SeckillActivityCache cache = newCache();

        cache.put(act);

        verify(redisExecutor).set(eq("seckill:activity:5"), anyString(), any(Duration.class));
        // put 后立即读：L1 命中
        assertThat(cache.get(5L)).isPresent();
        verifyNoInteractions(activityMapper);
    }

    @Test
    void illegalIdShouldReturnEmptyWithoutAnyAccess() {
        SeckillActivityCache cache = newCache();
        assertThat(cache.get(null)).isEmpty();
        assertThat(cache.get(0L)).isEmpty();
        assertThat(cache.get(-1L)).isEmpty();
        verifyNoInteractions(activityMapper, redisExecutor);
    }
}
