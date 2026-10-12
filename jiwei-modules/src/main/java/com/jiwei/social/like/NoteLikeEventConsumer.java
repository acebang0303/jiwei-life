package com.jiwei.social.like;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 点赞事件消费端：攒批把 Redis 事件队列落库（DB 为唯一账本）。
 *
 * 每 intervalMs（默认 1s）窥探一次队列：
 *   - 事件数 >= batchSize -> 立即落库
 *   - 不足一波但已等待 >= 2s -> 也落库，保证低峰期最终一致延迟有界
 *   - 处理成功后才 LTRIM 提交（至少一次语义，重放由 NoteLikePersistService 幂等吸收）
 */
@DS("social")
@Component
@Slf4j
public class NoteLikeEventConsumer {

    /** 单次 drainAll 最多循环多少轮，防止极端情况下长时间占用调度线程 */
    private static final int MAX_DRAIN_ROUNDS = 100;
    private static final long MAX_WAIT_MS = 2000;

    private final NoteLikeStore store;
    private final NoteLikePersistService persistService;
    private final NoteLikeSyncLock syncLock;
    private final int batchSize;
    private final int maxDrain;

    private volatile long firstSeenAt = -1;

    public NoteLikeEventConsumer(NoteLikeStore store,
                                 NoteLikePersistService persistService,
                                 NoteLikeSyncLock syncLock,
                                 @Value("${like.consumer.batch-size:500}") int batchSize,
                                 @Value("${like.consumer.max-drain:5000}") int maxDrain) {
        this.store = store;
        this.persistService = persistService;
        this.syncLock = syncLock;
        this.batchSize = batchSize;
        this.maxDrain = maxDrain;
    }

    @Scheduled(fixedDelayString = "${like.consumer.interval-ms:1000}",
               initialDelayString = "${like.consumer.interval-ms:1000}")
    public void consume() {
        // 跨实例互斥：同一时刻只有一个实例在 peek+消费，避免同一批事件被两个实例各处理一遍
        if (!syncLock.tryLock()) {
            log.debug("未抢到点赞消费锁，本轮跳过（其它实例正在消费）");
            return;
        }
        try {
            consumeLocked();
        } finally {
            syncLock.unlock();
        }
    }

    private void consumeLocked() {
        List<NoteLikeEvent> events;
        try {
            events = store.peekEvents(maxDrain);
        } catch (RuntimeException e) {
            log.warn("peek like events failed: {}", e.getMessage());
            return;
        }
        if (events.isEmpty()) {
            firstSeenAt = -1;
            return;
        }
        if (firstSeenAt < 0) {
            firstSeenAt = System.currentTimeMillis();
        }
        boolean full = events.size() >= batchSize;
        boolean waitedTooLong = System.currentTimeMillis() - firstSeenAt >= MAX_WAIT_MS;
        if (!full && !waitedTooLong) {
            return;
        }
        try {
            NoteLikePersistService.Result r = persistService.persist(events);
            store.commitEvents(events.size());
            firstSeenAt = -1;
            log.info("攒批写库完成: 事件={} 实际LIKE={} 实际UNLIKE={}（{}）",
                    events.size(), r.appliedLikes(), r.appliedUnlikes(), full ? "已满批" : "等待超时兜底");
        } catch (RuntimeException e) {
            log.error("persist like events failed, keep in queue for retry: {}", e.getMessage());
        }
    }

    /**
     * 一次性把队列里的滞留事件全部落库，返回处理条数。
     * 供启动预热与周期性对账复用：只有先把队列清空，Redis 计数与 DB 计数才可比较。
     * 已被外层持锁时不再重复抢锁（同一线程重入）。
     */
    public int drainAll() {
        if (syncLock.isHeldByCurrentThread()) {
            return drainAllLocked();
        }
        if (!syncLock.tryLock()) {
            log.info("未抢到点赞消费锁，跳过本轮滞留事件重放");
            return 0;
        }
        try {
            return drainAllLocked();
        } finally {
            syncLock.unlock();
        }
    }

    private int drainAllLocked() {
        int total = 0;
        for (int round = 0; round < MAX_DRAIN_ROUNDS; round++) {
            List<NoteLikeEvent> events;
            try {
                events = store.peekEvents(maxDrain);
            } catch (RuntimeException e) {
                log.warn("drainAll peek failed: {}", e.getMessage());
                break;
            }
            if (events.isEmpty()) {
                break;
            }
            try {
                persistService.persist(events);
                store.commitEvents(events.size());
                total += events.size();
            } catch (RuntimeException e) {
                log.error("drainAll persist failed, stop draining: {}", e.getMessage());
                break;
            }
        }
        if (total > 0) {
            log.info("滞留点赞事件重放完成: {} 条", total);
        }
        return total;
    }
}
