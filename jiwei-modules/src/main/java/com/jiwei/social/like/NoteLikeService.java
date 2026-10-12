package com.jiwei.social.like;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 点赞写路径编排：
 *   普通笔记 -> Redis 侧 Lua 原子切换（判断+写入同一段脚本）
 *   热点笔记 -> 本地大对象缓存聚合，每 1.5 秒用 Lua 原子批量刷回 Redis
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
        detector.hit(noteId);
        if (buffer.isHot(noteId)) {
            // 热点路径：先看本地未刷写意图，再落到本地聚合（同用户的重复记录不会重复计 delta）
            boolean likedNow = currentLiked(noteId, userId);
            boolean like = !likedNow;
            buffer.record(noteId, userId, like);
            long count = realTimeCount(noteId);
            log.info("点赞操作(热点本地聚合): user={} note={} -> {}（实时计数={}）",
                    userId, noteId, like ? "LIKE" : "UNLIKE", count);
            return new ToggleResult(like, count);
        }
        // 非热点路径：整个「查是否已赞 -> 翻转 -> 改计数 -> 推事件」在 Redis 单线程内原子完成，
        // 同一用户并发双击不会再出现「都读到未赞、各加一次」的问题
        NoteLikeStore.ApplyResult result = store.toggle(noteId, userId);
        log.info("点赞操作(Redis原子切换): user={} note={} -> {}（计数={}）",
                userId, noteId, result.liked() ? "LIKE" : "UNLIKE", result.count());
        return new ToggleResult(result.liked(), result.count());
    }

    /** 查询用户当前是否已赞：本地缓冲未刷写意图优先，否则查 Redis。 */
    public boolean currentLiked(long noteId, long userId) {
        Integer pending = buffer.pendingOp(noteId, userId);
        if (pending != null) {
            return pending == 1;
        }
        return store.hasLiked(noteId, userId);
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
