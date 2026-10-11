package com.jiwei.social.like;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.jiwei.social.mapper.NoteMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 兜底对账：周期性把 Redis 计数整刷回 MySQL（幂等覆盖），修正最终一致偏差。
 */
@DS("social")
@Component
@Slf4j
public class SyncReconciler {

    private final NoteLikeStore store;
    private final NoteMapper noteMapper;

    public SyncReconciler(NoteLikeStore store, NoteMapper noteMapper) {
        this.store = store;
        this.noteMapper = noteMapper;
    }

    @Scheduled(fixedDelayString = "${like.reconcile-interval-ms:30000}", initialDelay = 15000)
    public void reconcile() {
        Map<Long, Long> counts;
        try {
            counts = store.allCounts();
        } catch (RuntimeException e) {
            log.warn("reconcile skipped: redis unavailable ({})", e.getMessage());
            return;
        }
        if (counts.isEmpty()) {
            return;
        }
        counts.forEach((noteId, count) ->
                noteMapper.reconcileLikeCount(noteId, (int) Math.min(count, Integer.MAX_VALUE)));
        log.info("对账完成: Redis 计数整刷 MySQL，覆盖 {} 篇笔记", counts.size());
    }
}
