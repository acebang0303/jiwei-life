package com.jiwei.social.like;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 点赞事件管道的互斥闸门：本 JVM 可重入锁 + Redisson 分布式锁两层。
 *
 * 解决两个问题：
 * 1. 同一 JVM 内：消费端与对账都要「先落库、再比快照、再修 Redis」，中间不能被彼此插入。
 *    （默认 @Scheduled 是单线程，天然串行；但把线程池调大就会同时跑，所以用显式锁固定下来。）
 * 2. 跨实例：队列消费是 peek(LRANGE) + 成功后 LTRIM 的「至少一次」语义，
 *    两个实例同时 peek 会拿到同一批事件（明细靠唯一键幂等，但事件会被处理两遍）。
 *    加分布式锁后，同一时刻只有一个实例在消费。
 *
 * 取舍：锁粒度 = 整条队列，吞吐上限受限于单实例（本路径是 1s 一次的低频攒批，够用）；
 * 若要并行消费，应改成 Redis Stream + consumer group，或「两阶段队列」（把待处理批次
 * 原子搬到 processing 列表，成功后再删），代价是复杂度显著上升。
 *
 * 拿不到锁时直接返回 false（不阻塞调度线程），下一轮再试。
 */
@Component
@Slf4j
public class NoteLikeSyncLock {

    public static final String LOCK_KEY = "lock:note:like:sync";
    /** 锁租约：持有者崩溃后最多 30s 自动释放，避免死锁 */
    private static final long LEASE_MILLIS = 30_000;
    /** 不等待，抢不到就算了，交给下一轮 */
    private static final long WAIT_MILLIS = 0;

    private final RedissonClient redisson;
    private final ReentrantLock local = new ReentrantLock();
    private final ThreadLocal<Boolean> distributedHeld = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public NoteLikeSyncLock(RedissonClient redisson) {
        this.redisson = redisson;
    }

    public boolean tryLock() {
        if (!local.tryLock()) {
            return false;
        }
        try {
            RLock lock = redisson.getLock(LOCK_KEY);
            if (lock.tryLock(WAIT_MILLIS, LEASE_MILLIS, TimeUnit.MILLISECONDS)) {
                distributedHeld.set(Boolean.TRUE);
                return true;
            }
            local.unlock();
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            local.unlock();
            return false;
        } catch (Exception e) {
            log.warn("获取点赞管道分布式锁失败（Redis 不可用？），本轮跳过: {}", e.getMessage());
            local.unlock();
            return false;
        }
    }

    public void unlock() {
        try {
            if (Boolean.TRUE.equals(distributedHeld.get())) {
                RLock lock = redisson.getLock(LOCK_KEY);
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
                distributedHeld.set(Boolean.FALSE);
            }
        } catch (Exception e) {
            log.warn("释放点赞管道分布式锁异常（租约到期会自动释放）: {}", e.getMessage());
        } finally {
            if (local.isHeldByCurrentThread()) {
                local.unlock();
            }
        }
    }

    public boolean isHeldByCurrentThread() {
        return local.isHeldByCurrentThread();
    }
}
