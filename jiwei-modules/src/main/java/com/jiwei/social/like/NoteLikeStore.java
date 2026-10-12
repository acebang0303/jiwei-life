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
 *
 * 写路径全部走 Lua（见 resources/lua）：
 *   - toggle：判断「是否已赞」与写入在同一段脚本里完成，消除 read-then-write 竞态；
 *   - flush ：按 SADD/SREM 的实际影响结果算增量，重放/多实例/跨路径都幂等。
 */
@Component
@Slf4j
public class NoteLikeStore {

    private static final String KEY_COUNT = "note:like:count";
    private static final String KEY_EVENTS = "note:like:events";
    private static final String KEY_USERS_PREFIX = "note:like:users:";

    private final StringRedisTemplate redis;
    private final NoteLikeScripts scripts;

    public NoteLikeStore(StringRedisTemplate redis, NoteLikeScripts scripts) {
        this.redis = redis;
        this.scripts = scripts;
    }

    public static String usersKey(long noteId) {
        return KEY_USERS_PREFIX + noteId;
    }

    /**
     * 原子点赞/取消（幂等切换）。
     * 判断与写入在同一段 Lua 里完成，同一用户并发请求不会把计数多加一次。
     */
    public ApplyResult toggle(long noteId, long userId) {
        String result = redis.execute(scripts.toggle(),
                List.of(KEY_COUNT, usersKey(noteId), KEY_EVENTS),
                String.valueOf(noteId), String.valueOf(userId), String.valueOf(System.currentTimeMillis()));
        return parseToggle(result);
    }

    /**
     * 热点笔记的本地攒批刷写：每篇笔记一次原子 Lua 调用。
     * 返回「实际净增量」——已存在的赞/已取消的赞不会被重复计数。
     */
    public FlushResult flushAggregated(List<AggregatedOps> batch) {
        if (batch == null || batch.isEmpty()) {
            return new FlushResult(0, 0);
        }
        long totalDelta = 0;
        long totalPushed = 0;
        long ts = System.currentTimeMillis();
        for (AggregatedOps ops : batch) {
            List<String> args = new ArrayList<>();
            args.add(String.valueOf(ops.noteId()));
            args.add(String.valueOf(ops.likeUsers().size()));
            for (Long uid : ops.likeUsers()) {
                args.add(String.valueOf(uid));
            }
            args.add(String.valueOf(ops.unlikeUsers().size()));
            for (Long uid : ops.unlikeUsers()) {
                args.add(String.valueOf(uid));
            }
            args.add(String.valueOf(ts));

            String result = redis.execute(scripts.flush(),
                    List.of(KEY_COUNT, usersKey(ops.noteId()), KEY_EVENTS),
                    args.toArray());
            FlushResult one = parseFlush(result);
            totalDelta += one.delta();
            totalPushed += one.pushedEvents();
        }
        return new FlushResult(totalDelta, totalPushed);
    }

    /** 每篇笔记一次聚合的操作集合。 */
    public record AggregatedOps(long noteId, List<Long> likeUsers, List<Long> unlikeUsers) {
        /** 意图条数净差（仅用于日志；真正落库的增量以 Lua 的实际影响为准） */
        public long intendedDelta() {
            return (long) likeUsers.size() - unlikeUsers.size();
        }
    }

    /** 单次切换的结果：切换后的状态 + 切换后的权威计数。 */
    public record ApplyResult(boolean liked, long count) {
    }

    /** 批量刷写结果：实际净增量 + 实际推入队列的事件条数。 */
    public record FlushResult(long delta, long pushedEvents) {
    }

    /** 启动预热：以 DB like_count 为权威基线覆盖 Redis 计数（残留增量会污染全量，故用覆盖而非 setIfAbsent）。 */
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
                // 脏数据跳过，不让一条坏事件卡住整条队列
            }
        }
        return events;
    }

    /** 消费端：提交前 n 条事件（LTRIM，与 peek 组成一次取批的至少一次语义）。 */
    public void commitEvents(int n) {
        redis.opsForList().trim(KEY_EVENTS, n, -1);
    }

    private ApplyResult parseToggle(String raw) {
        String[] parts = raw == null ? new String[0] : raw.split(",");
        if (parts.length != 2) {
            log.warn("点赞切换脚本返回异常: {}", raw);
            return new ApplyResult(false, 0);
        }
        return new ApplyResult("1".equals(parts[0].trim()), parseLong(parts[1].trim()));
    }

    private FlushResult parseFlush(String raw) {
        String[] parts = raw == null ? new String[0] : raw.split(",");
        if (parts.length != 2) {
            log.warn("点赞刷写脚本返回异常: {}", raw);
            return new FlushResult(0, 0);
        }
        return new FlushResult(parseLong(parts[0].trim()), parseLong(parts[1].trim()));
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
