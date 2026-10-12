package com.jiwei.social.like;

import com.jiwei.pojo.entity.Note;
import com.jiwei.social.mapper.NoteMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 点赞计数预热：先重放队列里的滞留事件，再以 DB note.like_count 为权威基线覆盖 Redis。
 *
 * 顺序很关键：上一轮进程退出时，Redis 里可能已经加过计数、但对应事件还在队列里
 * 没落库。此时若直接用 DB 覆盖 Redis，这些「已加未落库」的增量会被抹掉，随后
 * 消费者把它们写进 DB，反而让 DB 高于 Redis，再被对账当成漂移压回来 —— 一个赞就
 * 这样凭空消失了。所以必须先 drain（把 DB 追平），再覆盖（此时 DB 才是真基线）。
 */
@Component
@Slf4j
public class LikeCountWarmUpRunner implements ApplicationRunner {

    private static final String HOT_NOTES_KEY = "hot_notes:weekly";

    private final NoteMapper noteMapper;
    private final NoteLikeStore store;
    private final NoteLikeEventConsumer consumer;
    private final RedisTemplate<String, Object> redisTemplate;

    public LikeCountWarmUpRunner(NoteMapper noteMapper,
                                 NoteLikeStore store,
                                 NoteLikeEventConsumer consumer,
                                 RedisTemplate<String, Object> redisTemplate) {
        this.noteMapper = noteMapper;
        this.store = store;
        this.consumer = consumer;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        //1、先把上次退出时滞留的点赞事件落库，保证 DB 已包含 Redis 里的全部计数
        try {
            consumer.drainAll();
        } catch (RuntimeException e) {
            log.warn("启动重放滞留点赞事件失败（可能 Redis 不可用），跳过：{}", e.getMessage());
        }

        //2、以 DB 为基线覆盖 Redis 计数
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
            double score = n.getLikeCount() * 2.0
                    + (n.getCommentCount() != null ? n.getCommentCount() : 0) * 3.0
                    + (n.getCollectCount() != null ? n.getCollectCount() : 0) * 5.0;
            redisTemplate.opsForZSet().add(HOT_NOTES_KEY, n.getId().toString(), score);
            hotLoaded++;
        }
        log.info("点赞计数预热完成: 从 DB 加载 {} 篇笔记的 like_count 到 Redis", loaded);
        log.info("热门榜预热完成: 写入 {} 篇笔记到 {}", hotLoaded, HOT_NOTES_KEY);
    }
}
