package com.jiwei.market.shortlink.component;

import com.jiwei.market.shortlink.mapper.ShortLinkMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 短链点击计数的本地攒批器。
 *
 * 重定向是读多写少的最高频链路：若每次跳转（哪怕 Caffeine 命中）都打一条
 * {@code UPDATE short_link SET click_count = click_count + 1}，DB 写会抵消本地缓存
 * 带来的收益，且把重定向拖进事务。这里把点击先在 JVM 内累加，定时一条
 * {@code CASE WHEN} 批量 UPDATE 落库，重定向路径退化为纯内存操作。
 *
 * 取舍（可被追问）：进程崩溃会丢失最多一个 flush 周期的点击数；点击量是
 * 「展示型统计指标」而非账务数据，允许近似，故选择吞吐优先。若要求精确，
 * 应改为 Redis INCR + 定时落库。
 */
@Component
@Slf4j
public class ClickCountBuffer {

    private final ShortLinkMapper mapper;
    private final int maxCodesPerFlush;

    private final ConcurrentHashMap<String, LongAdder> pending = new ConcurrentHashMap<>();

    public ClickCountBuffer(ShortLinkMapper mapper,
                            @Value("${shortlink.click.max-codes-per-flush:2000}") int maxCodesPerFlush) {
        this.mapper = mapper;
        this.maxCodesPerFlush = maxCodesPerFlush;
    }

    /** 重定向命中时调用：只累加内存，不触达 DB。 */
    public void increment(String code) {
        if (code == null || code.isEmpty()) {
            return;
        }
        pending.computeIfAbsent(code, k -> new LongAdder()).increment();
    }

    /** 定时把内存增量合并成一条批量 UPDATE 落库；失败则原样放回，下个周期重试。 */
    @Scheduled(fixedDelayString = "${shortlink.click.flush-interval-ms:5000}",
               initialDelayString = "${shortlink.click.flush-interval-ms:5000}")
    public void flush() {
        if (pending.isEmpty()) {
            return;
        }
        List<ShortLinkMapper.ClickDelta> batch = new ArrayList<>();
        int guard = 0;
        for (Map.Entry<String, LongAdder> entry : pending.entrySet()) {
            if (guard++ >= maxCodesPerFlush) {
                break;
            }
            if (!pending.remove(entry.getKey(), entry.getValue())) {
                continue;
            }
            long delta = entry.getValue().sum();
            if (delta != 0) {
                batch.add(new ShortLinkMapper.ClickDelta(entry.getKey(), delta));
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        try {
            mapper.batchIncrementClickCount(batch);
            log.info("点击计数落库: 短码数={} 总点击={}", batch.size(),
                    batch.stream().mapToLong(ShortLinkMapper.ClickDelta::delta).sum());
        } catch (RuntimeException e) {
            // 落库失败：把快照原样合并回内存，保证点击不丢（下个周期重试）
            for (ShortLinkMapper.ClickDelta d : batch) {
                pending.computeIfAbsent(d.code(), k -> new LongAdder()).add(d.delta());
            }
            log.warn("点击计数落库失败，已回滚到内存待下次重试: {}", e.getMessage());
        }
    }
}
