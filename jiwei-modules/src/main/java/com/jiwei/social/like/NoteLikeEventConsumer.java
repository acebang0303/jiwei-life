package com.jiwei.social.like;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.jiwei.pojo.entity.NoteLike;
import com.jiwei.social.mapper.NoteLikeMapper;
import com.jiwei.social.mapper.NoteMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 点赞事件消费端：攒批写 MySQL。
 * 每 intervalMs（默认 1s）窥探一次事件队列：
 *   - 队列事件数 >= batchSize -> 立即批量写库
 *   - 不足一波但已等待 >= 2s -> 也写库，保证低峰时最终一致延迟有界
 * 赞批量 INSERT IGNORE，取消批量 DELETE，计数按 net delta 逐条 UPDATE。
 */
@DS("social")
@Component
@Slf4j
public class NoteLikeEventConsumer {

    private final NoteLikeStore store;
    private final NoteLikeMapper noteLikeMapper;
    private final NoteMapper noteMapper;
    private final int batchSize;
    private final int maxDrain;

    private volatile long firstSeenAt = -1;

    public NoteLikeEventConsumer(NoteLikeStore store,
                                 NoteLikeMapper noteLikeMapper,
                                 NoteMapper noteMapper,
                                 @Value("${like.consumer.batch-size:500}") int batchSize,
                                 @Value("${like.consumer.max-drain:5000}") int maxDrain) {
        this.store = store;
        this.noteLikeMapper = noteLikeMapper;
        this.noteMapper = noteMapper;
        this.batchSize = batchSize;
        this.maxDrain = maxDrain;
    }

    @Scheduled(fixedDelayString = "${like.consumer.interval-ms:1000}",
               initialDelayString = "${like.consumer.interval-ms:1000}")
    public void consume() {
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
        boolean waitedTooLong = System.currentTimeMillis() - firstSeenAt >= 2000;
        if (!full && !waitedTooLong) {
            return;
        }
        try {
            flushBatch(events);
            store.commitEvents(events.size());
            firstSeenAt = -1;
            log.info("攒批写库完成: 事件={}（{}）", events.size(), full ? "已满批" : "等待超时兜底");
        } catch (RuntimeException e) {
            log.error("persist like events failed, keep in queue for retry: {}", e.getMessage());
        }
    }

    private void flushBatch(List<NoteLikeEvent> events) {
        // 同一 (noteId, userId) 只保留最后一次意图
        Map<String, NoteLikeEvent> latest = new LinkedHashMap<>();
        for (NoteLikeEvent e : events) {
            latest.put(e.getNoteId() + ":" + e.getUserId(), e);
        }
        List<NoteLike> likes = new ArrayList<>();
        List<NoteLike> unlikes = new ArrayList<>();
        Map<Long, Integer> deltas = new LinkedHashMap<>();
        for (NoteLikeEvent e : latest.values()) {
            NoteLike r = NoteLike.builder().noteId(e.getNoteId()).userId(e.getUserId()).build();
            if (e.isLike()) {
                likes.add(r);
                deltas.merge(e.getNoteId(), 1, Integer::sum);
            } else {
                unlikes.add(r);
                deltas.merge(e.getNoteId(), -1, Integer::sum);
            }
        }
        if (!likes.isEmpty()) {
            noteLikeMapper.insertIgnoreBatch(likes);
        }
        if (!unlikes.isEmpty()) {
            noteLikeMapper.deleteBatch(unlikes);
        }
        deltas.forEach(noteMapper::incrLikeCount);
    }
}
