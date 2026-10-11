package com.jiwei.social.like;

import com.jiwei.pojo.entity.Note;
import com.jiwei.social.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 点赞计数预热：以 DB note.like_count 为权威基线写入 Redis note:like:count。
 * SyncReconciler 以 Redis 为权威整刷 DB，若无基线则 Redis 从 0 累计的增量会被当作全量，
 * 覆盖掉 DB 里含历史 seed 的全量计数（点赞后被对账改成 1，取消后变 0）。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class LikeCountWarmUpRunner implements ApplicationRunner {

    private static final String HOT_NOTES_KEY = "hot_notes:weekly";

    private final NoteMapper noteMapper;
    private final NoteLikeStore store;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        List<Note> notes = noteMapper.selectList(null);
        int loaded = 0;
        int hotLoaded = 0;
        for (Note n : notes) {
            if (n.getId() == null || n.getLikeCount() == null) {
                continue;
            }
            store.overwriteCount(n.getId(), n.getLikeCount());
            loaded++;

            // 同步预热热门榜 ZSet，否则 hot_notes:weekly 只含被交互过的少量笔记
            double score = (n.getLikeCount() != null ? n.getLikeCount() : 0) * 2.0
                    + (n.getCommentCount() != null ? n.getCommentCount() : 0) * 3.0
                    + (n.getCollectCount() != null ? n.getCollectCount() : 0) * 5.0;
            redisTemplate.opsForZSet().add(HOT_NOTES_KEY, n.getId().toString(), score);
            hotLoaded++;
        }
        log.info("点赞计数预热完成: 从 DB 加载 {} 篇笔记的 like_count 到 Redis", loaded);
        log.info("热门榜预热完成: 写入 {} 篇笔记到 {}", hotLoaded, HOT_NOTES_KEY);
    }
}
