package com.jiwei.social.like;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.jiwei.social.mapper.NoteMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 点赞计数对账：以 MySQL 的 note.like_count 为唯一账本，校验并修复 Redis 缓存漂移。
 *
 * 为什么方向是「修 Redis」而不是「Redis 覆盖 DB」：
 *   写路径先把计数加到 Redis，再把事件推入队列由消费者异步落库，中间存在一个
 *   「Redis 已加、DB 未加」的窗口。若此时用 Redis 覆盖 DB，等消费者再把这批事件
 *   的增量应用一次，DB 就被重复加了——在高频点赞下这个窗口几乎一直存在，
 *   漂移会持续放大。因此正确做法是：先把队列落库（DB 追平 Redis），
 *   再以 DB 为基线校正 Redis。Redis 丢数据（重启/被清）时也能靠这一步自愈。
 */
@DS("social")
@Component
@Slf4j
public class SyncReconciler {

    private final NoteLikeStore store;
    private final NoteMapper noteMapper;
    private final NoteLikeEventConsumer consumer;
    private final NoteLikeSyncLock syncLock;

    public SyncReconciler(NoteLikeStore store,
                          NoteMapper noteMapper,
                          NoteLikeEventConsumer consumer,
                          NoteLikeSyncLock syncLock) {
        this.store = store;
        this.noteMapper = noteMapper;
        this.consumer = consumer;
        this.syncLock = syncLock;
    }

    @Scheduled(fixedDelayString = "${like.reconcile-interval-ms:30000}", initialDelay = 15000)
    public void reconcile() {
        // 整段「落库 → 读快照 → 修 Redis」必须与消费端（含其它实例）互斥，
        // 否则中间被插入一次落库就会误判漂移
        if (!syncLock.tryLock()) {
            log.info("未抢到点赞对账锁，跳过本轮（其它实例正在消费/对账）");
            return;
        }
        try {
            reconcileLocked();
        } finally {
            syncLock.unlock();
        }
    }

    private void reconcileLocked() {
        //1、先把滞留事件落库：只有队列空了，Redis 计数与 DB 计数才是可比状态
        int replayed = consumer.drainAll();

        //2、读快照（Redis 不可用则本轮跳过，不影响主流程）
        Map<Long, Long> redisCounts;
        try {
            redisCounts = store.allCounts();
        } catch (RuntimeException e) {
            log.warn("对账跳过: Redis 不可用 ({})", e.getMessage());
            return;
        }
        Map<Long, Long> dbCounts = loadDbCounts();
        if (dbCounts.isEmpty() && redisCounts.isEmpty()) {
            return;
        }

        Set<Long> noteIds = new LinkedHashSet<>(dbCounts.keySet());
        noteIds.addAll(redisCounts.keySet());

        int drifted = 0;
        for (Long noteId : noteIds) {
            long expected = dbCounts.getOrDefault(noteId, 0L);   // DB 为唯一账本
            long actual = redisCounts.getOrDefault(noteId, 0L);
            if (expected != actual) {
                store.overwriteCount(noteId, expected);
                drifted++;
                log.warn("点赞计数漂移已修正: note={} Redis={} -> DB基线={}", noteId, actual, expected);
            }
        }
        log.info("点赞对账完成: 重放滞留事件={} 检查笔记={} 修正漂移={}", replayed, noteIds.size(), drifted);
    }

    private Map<Long, Long> loadDbCounts() {
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : noteMapper.selectLikeCounts()) {
            Object id = row.get("id");
            if (id == null) {
                continue;
            }
            result.put(parseLong(id), parseLong(row.get("likeCount")));
        }
        return result;
    }

    private long parseLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
