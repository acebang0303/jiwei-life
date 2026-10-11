package com.jiwei.social.like;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 点赞写路径编排：
 *   普通笔记 -> 直接写 Redis（少量命令）
 *   热点笔记 -> 本地大对象缓存聚合，1~2 秒刷一次 Redis
 * 读路径：Redis 计数 + 本地未刷写增量实时合并。
 */
@Service
@Slf4j
public class NoteLikeService {

    private final NoteLikeStore store;
    private final LocalHotLikeBuffer buffer;
    private final HotKeyDetector detector;

    public NoteLikeService(NoteLikeStore store,
                           LocalHotLikeBuffer buffer,
                           HotKeyDetector detector) {
        this.store = store;
        this.buffer = buffer;
        this.detector = detector;
    }

    /** 点赞/取消点赞（幂等切换）。返回切换后的状态与实时计数。 */
    public ToggleResult toggle(long noteId, long userId) {
        boolean likedNow = currentLiked(noteId, userId);
        boolean like = !likedNow;
        applyOp(noteId, userId, like);
        long count = realTimeCount(noteId);
        log.info("点赞操作: user={} note={} -> {}（实时计数={}）", userId, noteId, like ? "LIKE" : "UNLIKE", count);
        return new ToggleResult(like, count);
    }

    /** 查询用户当前是否已赞：本地缓冲未刷写意图优先，否则查 Redis。 */
    public boolean currentLiked(long noteId, long userId) {
        Integer pending = buffer.pendingOp(noteId, userId);
        if (pending != null) {
            return pending == 1;
        }
        return store.hasLiked(noteId, userId);
    }

    /** 写入路径分流：热点笔记走本地大对象缓存，普通笔记直连 Redis。 */
    private void applyOp(long noteId, long userId, boolean like) {
        detector.hit(noteId);
        if (buffer.isHot(noteId)) {
            buffer.record(noteId, userId, like);
        } else {
            store.applyDirect(noteId, userId, like);
        }
    }

    /** 实时计数 = Redis 计数 + 本地待刷写净增量（读路径实时合并）。 */
    public long realTimeCount(long noteId) {
        return Math.max(0, store.getCount(noteId) + buffer.pendingDelta(noteId));
    }

    public boolean isHot(long noteId) {
        return buffer.isHot(noteId);
    }

    public record ToggleResult(boolean liked, long count) {
    }
}
