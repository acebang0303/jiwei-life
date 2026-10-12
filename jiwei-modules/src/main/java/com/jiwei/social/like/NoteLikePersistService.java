package com.jiwei.social.like;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.jiwei.pojo.entity.NoteLike;
import com.jiwei.social.mapper.NoteLikeMapper;
import com.jiwei.social.mapper.NoteMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 点赞明细与计数的幂等落库。
 *
 * 关键设计：计数增量取自「SQL 实际影响行数」，而不是事件里的意图条数。
 *   消费端是 at-least-once 语义（先落库、成功后才 LTRIM 提交），进程崩溃或
 *   LTRIM 失败都会导致同一批事件被重放。若按意图条数累加计数
 *   （like_count + 事件数），重放就会把计数重复加上去，形成永久性虚高；
 *   改为「INSERT IGNORE 实际插入数 - DELETE 实际删除数」后，重放时两个影响
 *   行数都是 0，增量即 0，天然幂等。
 *
 * 注意顺序：明细与计数必须在同一事务内，否则明细写了、计数没写会留下漂移；
 * 事务提交后再由调用方 LTRIM，保证「至少一次」而不是「至多一次」。
 */
@DS("social")
@Service
@Slf4j
public class NoteLikePersistService {

    private final NoteLikeMapper noteLikeMapper;
    private final NoteMapper noteMapper;

    public NoteLikePersistService(NoteLikeMapper noteLikeMapper, NoteMapper noteMapper) {
        this.noteLikeMapper = noteLikeMapper;
        this.noteMapper = noteMapper;
    }

    @Transactional
    public Result persist(List<NoteLikeEvent> events) {
        if (events == null || events.isEmpty()) {
            return new Result(0, 0, 0);
        }
        // 同一 (noteId, userId) 只保留最后一次意图，避免同批内互相抵消
        Map<String, NoteLikeEvent> latest = new LinkedHashMap<>();
        for (NoteLikeEvent e : events) {
            latest.put(e.getNoteId() + ":" + e.getUserId(), e);
        }

        Map<Long, List<NoteLike>> likes = new LinkedHashMap<>();
        Map<Long, List<NoteLike>> unlikes = new LinkedHashMap<>();
        for (NoteLikeEvent e : latest.values()) {
            NoteLike row = NoteLike.builder().noteId(e.getNoteId()).userId(e.getUserId()).build();
            (e.isLike() ? likes : unlikes)
                    .computeIfAbsent(e.getNoteId(), k -> new ArrayList<>())
                    .add(row);
        }

        Set<Long> noteIds = new LinkedHashSet<>(likes.keySet());
        noteIds.addAll(unlikes.keySet());

        int appliedLikes = 0;
        int appliedUnlikes = 0;
        int adjustedNotes = 0;
        for (Long noteId : noteIds) {
            List<NoteLike> likeRows = likes.getOrDefault(noteId, List.of());
            List<NoteLike> unlikeRows = unlikes.getOrDefault(noteId, List.of());

            int inserted = likeRows.isEmpty() ? 0 : noteLikeMapper.insertIgnoreBatch(likeRows);
            int deleted = unlikeRows.isEmpty() ? 0 : noteLikeMapper.deleteBatch(unlikeRows);

            appliedLikes += inserted;
            appliedUnlikes += deleted;

            int delta = inserted - deleted;
            if (delta != 0) {
                noteMapper.incrLikeCount(noteId, delta);
                adjustedNotes++;
            }
        }
        return new Result(appliedLikes, appliedUnlikes, adjustedNotes);
    }

    /** appliedLikes/appliedUnlikes 为实际影响行数；adjustedNotes 为计数真正变动的笔记数。 */
    public record Result(int appliedLikes, int appliedUnlikes, int adjustedNotes) {
    }
}
