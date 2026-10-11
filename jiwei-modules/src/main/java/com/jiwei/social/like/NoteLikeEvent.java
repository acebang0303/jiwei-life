package com.jiwei.social.like;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 点赞事件：写入 Redis 队列 note:like:events，由消费端攒批持久化到 MySQL。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteLikeEvent {

    public static final String TYPE_LIKE = "LIKE";
    public static final String TYPE_UNLIKE = "UNLIKE";

    private String type;      // LIKE / UNLIKE
    private Long noteId;
    private Long userId;
    private long ts;

    public static NoteLikeEvent of(boolean like, Long noteId, Long userId) {
        return NoteLikeEvent.builder()
                .type(like ? TYPE_LIKE : TYPE_UNLIKE)
                .noteId(noteId)
                .userId(userId)
                .ts(System.currentTimeMillis())
                .build();
    }

    public boolean isLike() {
        return TYPE_LIKE.equals(type);
    }
}
