package com.jiwei.social.like;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 热点笔记的本地大对象缓存。
 * 识别为热点的笔记，点赞操作不再直连 Redis，而是先打到 JVM 内存聚合，
 * 每隔 flushIntervalMs（默认 1.5s）通过一条 pipeline 批量刷回 Redis。
 */
@Component
@Slf4j
public class LocalHotLikeBuffer {

    static final class NoteBuffer {
        final ConcurrentHashMap<Long, Integer> pending = new ConcurrentHashMap<>();
        final AtomicLong delta = new AtomicLong(0);
    }

    private final NoteLikeStore store;
    private final long flushIntervalMs;

    private final Set<Long> hotNotes = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Long, NoteBuffer> buffers = new ConcurrentHashMap<>();

    public LocalHotLikeBuffer(NoteLikeStore store,
                              @Value("${like.hot.flush-interval-ms:1500}") long flushIntervalMs) {
        this.store = store;
        this.flushIntervalMs = flushIntervalMs;
    }

    public boolean isHot(long noteId) {
        return hotNotes.contains(noteId);
    }

    public void markHot(long noteId) {
        hotNotes.add(noteId);
        buffers.computeIfAbsent(noteId, k -> new NoteBuffer());
        log.info("热点模式开启: note={}，点赞将先在本地聚合，每 {}ms 刷一次 Redis", noteId, flushIntervalMs);
    }

    public void markCold(long noteId) {
        hotNotes.remove(noteId);
        log.info("热点模式关闭: note={}，恢复直连 Redis 写入", noteId);
    }

    /** 记录一次点赞操作，仅更新内存。 */
    public void record(long noteId, long userId, boolean like) {
        NoteBuffer buf = buffers.computeIfAbsent(noteId, k -> new NoteBuffer());
        Integer prev = buf.pending.put(userId, like ? 1 : 0);
        if (prev == null) {
            buf.delta.addAndGet(like ? 1 : -1);
        } else if (prev != (like ? 1 : 0)) {
            buf.delta.addAndGet(like ? 2 : -2);
        }
    }

    /** 用户对该笔记的本地未刷写意图，null 表示本地没有。 */
    public Integer pendingOp(long noteId, long userId) {
        NoteBuffer buf = buffers.get(noteId);
        return buf == null ? null : buf.pending.get(userId);
    }

    /** 笔记当前待刷写的净增量（读路径实时合并）。 */
    public long pendingDelta(long noteId) {
        NoteBuffer buf = buffers.get(noteId);
        return buf == null ? 0 : buf.delta.get();
    }

    /** 定时把本地聚合数据刷回 Redis：快照后单条 pipeline 批量写，成功才驱逐已持久化条目。 */
    @Scheduled(fixedDelayString = "${like.hot.flush-interval-ms:1500}",
               initialDelayString = "${like.hot.flush-interval-ms:1500}")
    public void flush() {
        List<NoteLikeStore.AggregatedOps> batch = new ArrayList<>();
        List<Map.Entry<Long, Map.Entry<Long, Integer>>> flushed = new ArrayList<>();

        for (Map.Entry<Long, NoteBuffer> entry : buffers.entrySet()) {
            Long noteId = entry.getKey();
            NoteBuffer buf = entry.getValue();
            if (buf.pending.isEmpty()) {
                continue;
            }
            List<Long> likeUsers = new ArrayList<>();
            List<Long> unlikeUsers = new ArrayList<>();
            List<Map.Entry<Long, Integer>> snapshot = new ArrayList<>(buf.pending.entrySet());
            for (Map.Entry<Long, Integer> op : snapshot) {
                flushed.add(Map.entry(noteId, op));
                if (op.getValue() == 1) {
                    likeUsers.add(op.getKey());
                } else {
                    unlikeUsers.add(op.getKey());
                }
            }
            batch.add(new NoteLikeStore.AggregatedOps(noteId, likeUsers, unlikeUsers));
        }

        if (batch.isEmpty()) {
            return;
        }
        try {
            store.flushAggregated(batch);
        } catch (RuntimeException e) {
            log.error("flush hot buffer to redis failed, keep pending for retry", e);
            return;
        }
        for (Map.Entry<Long, Map.Entry<Long, Integer>> item : flushed) {
            NoteBuffer buf = buffers.get(item.getKey());
            if (buf == null) {
                continue;
            }
            Map.Entry<Long, Integer> op = item.getValue();
            if (buf.pending.remove(op.getKey(), op.getValue())) {
                buf.delta.addAndGet(op.getValue() == 1 ? -1 : 1);
            }
        }
        log.info("本地缓冲刷写: 笔记数={} 操作数={} 聚合为 1 次 pipeline 写 Redis", batch.size(), flushed.size());
    }
}
