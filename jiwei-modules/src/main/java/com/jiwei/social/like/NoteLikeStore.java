package com.jiwei.social.like;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 点赞数据的 Redis 存取层。
 * 数据结构：
 *   note:like:count         HASH   field=noteId, value=点赞数（读路径 O(1)）
 *   note:like:users:{id}    SET    已点赞用户 id 集合
 *   note:like:events        LIST   点赞事件队列（JSON），供消费端攒批持久化
 */
@Component
@Slf4j
public class NoteLikeStore {

    private static final String KEY_COUNT = "note:like:count";
    private static final String KEY_EVENTS = "note:like:events";
    private static final String KEY_USERS_PREFIX = "note:like:users:";

    private final StringRedisTemplate redis;

    public NoteLikeStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public static String usersKey(long noteId) {
        return KEY_USERS_PREFIX + noteId;
    }

    /** 普通笔记：直接写 Redis（SADD/HINCRBY + 推事件）。 */
    public void applyDirect(long noteId, long userId, boolean like) {
        String usersKey = usersKey(noteId);
        String member = String.valueOf(userId);
        String field = String.valueOf(noteId);
        if (like) {
            redis.opsForSet().add(usersKey, member);
            redis.opsForHash().increment(KEY_COUNT, field, 1);
        } else {
            redis.opsForSet().remove(usersKey, member);
            redis.opsForHash().increment(KEY_COUNT, field, -1);
        }
        pushEvent(NoteLikeEvent.of(like, noteId, userId));
        log.debug("直连Redis完成: note={} user={} like={}", noteId, userId, like);
    }

    /** 热点笔记：本地攒批后一次性聚合刷写（单条 pipeline 一次网络往返）。 */
    public void flushAggregated(List<AggregatedOps> batch) {
        if (batch.isEmpty()) {
            return;
        }
        RedisSerializer<String> ser = redis.getStringSerializer();
        redis.executePipelined((RedisCallback<Object>) connection -> {
            for (AggregatedOps ops : batch) {
                byte[] usersKey = ser.serialize(usersKey(ops.noteId()));
                byte[] field = ser.serialize(String.valueOf(ops.noteId()));
                for (Long uid : ops.likeUsers()) {
                    connection.setCommands().sAdd(usersKey, ser.serialize(String.valueOf(uid)));
                }
                for (Long uid : ops.unlikeUsers()) {
                    connection.setCommands().sRem(usersKey, ser.serialize(String.valueOf(uid)));
                }
                if (ops.delta() != 0) {
                    connection.hashCommands().hIncrBy(ser.serialize(KEY_COUNT), field, ops.delta());
                }
                byte[] queue = ser.serialize(KEY_EVENTS);
                for (Long uid : ops.likeUsers()) {
                    connection.listCommands().rPush(queue,
                            ser.serialize(JSON.toJSONString(NoteLikeEvent.of(true, ops.noteId(), uid))));
                }
                for (Long uid : ops.unlikeUsers()) {
                    connection.listCommands().rPush(queue,
                            ser.serialize(JSON.toJSONString(NoteLikeEvent.of(false, ops.noteId(), uid))));
                }
            }
            return null;
        });
        long likeOps = batch.stream().mapToLong(o -> o.likeUsers().size()).sum();
        long unlikeOps = batch.stream().mapToLong(o -> o.unlikeUsers().size()).sum();
        log.info("聚合刷写Redis: 笔记数={} LIKE={} UNLIKE={}（仅 1 次 pipeline 往返）",
                batch.size(), likeOps, unlikeOps);
    }

    /** 每篇笔记一次聚合的操作集合。 */
    public record AggregatedOps(long noteId, List<Long> likeUsers, List<Long> unlikeUsers) {
        public long delta() {
            return likeUsers.size() - unlikeUsers.size();
        }
    }

    /** 启动预热：以 DB like_count 为权威基线覆盖 Redis 计数（setIfAbsent 不适用，残留增量会污染全量）。 */
    public void overwriteCount(long noteId, long count) {
        redis.opsForHash().put(KEY_COUNT, String.valueOf(noteId), String.valueOf(count));
    }

    /** 查询用户是否已赞（SISMEMBER）。 */
    public boolean hasLiked(long noteId, long userId) {
        Boolean member = redis.opsForSet().isMember(usersKey(noteId), String.valueOf(userId));
        return Boolean.TRUE.equals(member);
    }

    /** 批量查询用户对多篇笔记的点赞状态（一次 pipeline）。 */
    public List<Boolean> likedFlags(long userId, List<Long> noteIds) {
        if (noteIds.isEmpty()) {
            return List.of();
        }
        RedisSerializer<String> ser = redis.getStringSerializer();
        byte[] member = ser.serialize(String.valueOf(userId));
        List<Object> raw = redis.executePipelined((RedisCallback<Object>) connection -> {
            for (Long id : noteIds) {
                connection.setCommands().sIsMember(ser.serialize(usersKey(id)), member);
            }
            return null;
        });
        List<Boolean> flags = new ArrayList<>(raw.size());
        for (Object o : raw) {
            flags.add(Boolean.TRUE.equals(o));
        }
        return flags;
    }

    /** 读取某笔记实时计数（HGET）。 */
    public long getCount(long noteId) {
        Object val = redis.opsForHash().get(KEY_COUNT, String.valueOf(noteId));
        return parseLong(val);
    }

    /** 一次 HGETALL 拿全量计数，供对账使用。 */
    public Map<Long, Long> allCounts() {
        Map<Object, Object> entries = redis.opsForHash().entries(KEY_COUNT);
        Map<Long, Long> result = new HashMap<>();
        entries.forEach((k, v) -> result.put(parseLong(k), parseLong(v)));
        return result;
    }

    /** 消费端：窥探队列头部（不移除），处理成功后才 commit。 */
    public List<NoteLikeEvent> peekEvents(int max) {
        List<String> raw = redis.opsForList().range(KEY_EVENTS, 0, max - 1L);
        List<NoteLikeEvent> events = new ArrayList<>();
        if (raw == null) {
            return events;
        }
        for (String item : raw) {
            try {
                events.add(JSON.parseObject(item, NoteLikeEvent.class));
            } catch (Exception ignored) {
            }
        }
        return events;
    }

    /** 消费端：提交前 n 条事件（LTRIM，与 peek 组成一次取批的至少一次语义）。 */
    public void commitEvents(int n) {
        redis.opsForList().trim(KEY_EVENTS, n, -1);
    }

    /** 把一条点赞事件写入队列（JSON 序列化）。 */
    public void pushEvent(NoteLikeEvent event) {
        redis.opsForList().rightPush(KEY_EVENTS, JSON.toJSONString(event));
    }

    private long parseLong(Object val) {
        if (val == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(val));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
