# 社交域（social）实现计划：点赞攒批 + 热点聚合 + 幂等 upsert + 对账

> ⚠️ **实现现状对照（2026-10-12）**：本文是当时的实施记录，**对账方向已反转**——本文写的是
> 「以 Redis 为权威覆盖 DB」，当前实现是**以 DB 为唯一账本校正 Redis**（写路径先加 Redis、后异步落库，
> 用 Redis 覆盖 DB 会在在途窗口把计数永久推高）。另：点赞切换已改 Redis Lua 原子；
> 批量刷写按 SADD/SREM **实际结果**算增量；消费端改 Redisson 分布式锁；
> `NoteLikeStore.applyDirect(...)` 已被 `toggle(...)` / `flushAggregated(...)` 取代。
> 完整清单见 [`docs/实现现状对照.md`](../实现现状对照.md) §4。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用高并发点赞系统的「Redis 挡写 → 攒批 → 热点聚合 → 幂等 upsert → 兜底对账」五层链路，替换 JiweiLife 社交域里 `NoteServiceImpl.like()` 的「裸 `selectOne` + `deleteById/insert` + `updateById`」浅实现。改造后点赞写路径不再同步打 DB，读路径用「Redis 计数 + 本地待刷增量」实时合并，最终一致由攒批落库 + 30s 兜底对账保证。

**Architecture:** 点赞操作先按「热点 / 普通」分流——热点笔记进 JVM 本地大对象缓存（`LocalHotLikeBuffer`，1.5s 一次 pipeline 聚合刷 Redis），普通笔记直连 Redis（`NoteLikeStore.applyDirect`）。所有操作同步打点到 `HotKeyDetector`（按窗口速率识别热点），同时把事件写入 Redis LIST（`note:like:events`）。消费端 `NoteLikeEventConsumer` 每 1s 攒批窥探（peek），攒够 `batch-size` 或等待超 2s 即批量落库（幂等 INSERT/DELETE + 计数 UPDATE），成功后才 `commitEvents`（LTRIM，至少一次语义）。`SyncReconciler` 每 30s 把 Redis 全量计数整刷回 `note.like_count` 兜底收敛偏差。

**Tech Stack:** JDK 21、Spring Boot 3.x、MyBatis-Plus + dynamic-datasource（`@DS("social")`）、`StringRedisTemplate`（字符串序列化）、fastjson2（事件 JSON 序列化）、`@Scheduled` 定时任务（`@EnableScheduling` 已开启）。

**Spec:** `docs/superpowers/specs/2026-08-26-wheel-integration-design.md`（§5.4 社交域）

**源项目参考：**
- 高并发点赞系统：`D:\qiuzhao\high-concurrency-like-system\src\main\java\com\example\likesystem\like\`

## Global Constraints

- 包名统一 `com.jiwei.social.like`（点赞子包）；实体复用 `com.jiwei.pojo.entity.Note` / `NoteLike`（**不新增实体，不新增对账表**）
- Redis 用 `StringRedisTemplate`（字符串序列化，避免 `RedisTemplate` 的 JDK 序列化）；事件 JSON 用 `com.alibaba.fastjson2.JSON`（JiweiLife 已用 fastjson2）
- 数据源 `@DS("social")`：Mapper 接口已标，批量写库的 Service（consumer/reconciler）类上再标一次，匹配现有风格
- **语义适配（关键差异）**：轮子 `like_record` 有 `status` 字段（1=赞 / 0=取消，`ON DUPLICATE KEY UPDATE status=?`）；JiweiLife 的 `note_like` 是「存在=赞，删除=取消」语义（无 status 字段）。攒批落库必须改成 **`INSERT IGNORE`（幂等赞）+ `DELETE`（幂等取消）**，不能照搬 status 翻转
- **修复已知坑**：轮子 `like.hot.window-seconds` 是死配置（yml 配 5s，`HotKeyDetector` 硬编码 `fixedDelay=2000`）——落地时 `@Scheduled` 用 `fixedDelayString` 从配置读取，让窗口配置真实生效
- **合理裁剪（非简化，是有依据的移除）**：① 去掉 `like:rank` ZSet 排行榜——JiweiLife 已有 `hot_notes:weekly` 综合热度排行（`likeCount*2+commentCount*3+collectCount*5`），纯点赞排行功能重叠；② 去掉 `MetricsService` 指标记账——JiweiLife 用 actuator/prometheus；③ 去掉 `knownArticleIds` 内存目录——JiweiLife 直接 `noteMapper.selectById` 判存在
- 攒批/对账定时周期一律 `fixedDelayString` 从 `application.yml` 读，不硬编码
- 每个 Task 完成后 `git add` 具体文件并 commit，禁止 `git add -A`

---

## File Structure 概览

**新建（social.like 子包，jiwei-modules）：**
- `com/jiwei/social/like/NoteLikeEvent.java` — 点赞事件模型（LIKE/UNLIKE）
- `com/jiwei/social/like/NoteLikeStore.java` — Redis 存取层（计数 HASH + 已赞用户 SET + 事件 LIST）
- `com/jiwei/social/like/HotKeyDetector.java` — 热点检测器（窗口速率统计）
- `com/jiwei/social/like/LocalHotLikeBuffer.java` — 热点本地大对象缓存（聚合刷写）
- `com/jiwei/social/like/NoteLikeEventConsumer.java` — 攒批消费端（peek/commit + 幂等落库）
- `com/jiwei/social/like/SyncReconciler.java` — 兜底对账（30s 整刷）
- `com/jiwei/social/like/NoteLikeService.java` — 点赞编排（分流 + 实时计数合并）

**修改：**
- `com/jiwei/social/mapper/NoteLikeMapper.java` — 加 `insertIgnoreBatch` / `deleteBatch`
- `com/jiwei/social/mapper/NoteMapper.java` — 加 `incrLikeCount` / `reconcileLikeCount`
- `com/jiwei/social/service/impl/NoteServiceImpl.java` — `like()` 委托 `NoteLikeService`，去掉 `@Transactional`
- `jiwei-life/jiwei-modules/src/main/resources/application.yml` — 追加 `like.*` 配置块

---

## Task 1: 点赞事件模型 + Redis 存取层

**Files:**
- Create: `com/jiwei/social/like/NoteLikeEvent.java`
- Create: `com/jiwei/social/like/NoteLikeStore.java`

**Interfaces:**
- Consumes: 无（第一个任务）
- Produces: `NoteLikeStore`（`applyDirect`/`flushAggregated`/`peekEvents`/`commitEvents`/`getCount`/`allCounts`/`hasLiked`/`likedFlags`），供后续检测器/缓冲/消费端/对账使用

**步骤：**

- [ ] **Step 1: 创建 NoteLikeEvent（事件模型）**

从源 `LikeEvent.java` 移植，`articleId` → `noteId`，改 JiweiLife 的 Lombok 风格：

```java
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
```

- [ ] **Step 2: 创建 NoteLikeStore（Redis 存取层）**

从源 `RedisLikeStore.java` 移植，改动：① 类名 `NoteLikeStore`；② `article` → `note`；③ key 前缀统一 `note:like:*`；④ **去掉 `like:rank` ZSet 相关方法**（`topArticles` + `flushAggregated`/`applyDirect` 里的 `zIncrBy`）；⑤ 序列化由 Jackson `ObjectMapper` 改 fastjson2 `JSON`；⑥ 去掉 `MetricsService`（用 `log` 代替记账）：

```java
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
```

- [ ] **Step 3: 编译验证**

Run: `cd jiwei-life && mvn compile -pl jiwei-common,jiwei-pojo,jiwei-framework,jiwei-modules -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add jiwei-modules/src/main/java/com/jiwei/social/like/NoteLikeEvent.java \
        jiwei-modules/src/main/java/com/jiwei/social/like/NoteLikeStore.java
git commit -m "feat(social): 点赞事件模型与 Redis 存取层（计数/用户集合/事件队列）"
```

---

## Task 2: 热点检测 + 本地聚合

**Files:**
- Create: `com/jiwei/social/like/HotKeyDetector.java`
- Create: `com/jiwei/social/like/LocalHotLikeBuffer.java`

**Interfaces:**
- Consumes: `NoteLikeStore`（Task 1）
- Produces: `HotKeyDetector.hit(long)`、`LocalHotLikeBuffer.record/pendingOp/pendingDelta/isHot/markHot/markCold`

**步骤：**

- [ ] **Step 1: 创建 LocalHotLikeBuffer（本地大对象缓存）**

从源 `LocalHotLikeBuffer.java` 移植，`article` → `note`，去掉 `MetricsService`：

```java
package com.jiwei.social.like;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 热点笔记的本地大对象缓存。
 * 识别为热点的笔记，点赞操作不再直连 Redis，而是先打到 JVM 内存聚合：
 * 每个用户只保留最终意图（like/unlike），计数按 net delta 合并，
 * 每隔 flushIntervalMs（默认 1.5s）通过一条 pipeline 批量刷回 Redis。
 */
@Component
@Slf4j
public class LocalHotLikeBuffer {

    static final class NoteBuffer {
        final ConcurrentHashMap<Long, Integer> pending = new ConcurrentHashMap<>();
        final AtomicLong delta = new AtomicLong(0);
    }

    private final NoteLikeStore store;
    private final long flushIntervalMs;

    private final Set<Long> hotNotes = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Long, NoteBuffer> buffers = new ConcurrentHashMap<>();

    public LocalHotLikeBuffer(NoteLikeStore store,
                              @Value("${like.hot.flush-interval-ms:1500}") long flushIntervalMs) {
        this.store = store;
        this.flushIntervalMs = flushIntervalMs;
    }

    public boolean isHot(long noteId) {
        return hotNotes.contains(noteId);
    }

    public void markHot(long noteId) {
        hotNotes.add(noteId);
        buffers.computeIfAbsent(noteId, k -> new NoteBuffer());
        log.info("热点模式开启: note={}，点赞将先在本地聚合，每 {}ms 刷一次 Redis", noteId, flushIntervalMs);
    }

    public void markCold(long noteId) {
        hotNotes.remove(noteId);
        log.info("热点模式关闭: note={}，恢复直连 Redis 写入", noteId);
    }

    /** 记录一次点赞操作，仅更新内存。 */
    public void record(long noteId, long userId, boolean like) {
        NoteBuffer buf = buffers.computeIfAbsent(noteId, k -> new NoteBuffer());
        Integer prev = buf.pending.put(userId, like ? 1 : 0);
        if (prev == null) {
            buf.delta.addAndGet(like ? 1 : -1);
        } else if (prev != (like ? 1 : 0)) {
            buf.delta.addAndGet(like ? 2 : -2);
        }
    }

    /** 用户对该笔记的本地未刷写意图，null 表示本地没有。 */
    public Integer pendingOp(long noteId, long userId) {
        NoteBuffer buf = buffers.get(noteId);
        return buf == null ? null : buf.pending.get(userId);
    }

    /** 笔记当前待刷写的净增量（读路径实时合并）。 */
    public long pendingDelta(long noteId) {
        NoteBuffer buf = buffers.get(noteId);
        return buf == null ? 0 : buf.delta.get();
    }

    /** 定时把本地聚合数据刷回 Redis：快照后单条 pipeline 批量写，成功才驱逐已持久化条目。 */
    @Scheduled(fixedDelayString = "${like.hot.flush-interval-ms:1500}",
               initialDelayString = "${like.hot.flush-interval-ms:1500}")
    public void flush() {
        List<NoteLikeStore.AggregatedOps> batch = new ArrayList<>();
        List<Map.Entry<Long, Map.Entry<Long, Integer>>> flushed = new ArrayList<>();

        for (Map.Entry<Long, NoteBuffer> entry : buffers.entrySet()) {
            Long noteId = entry.getKey();
            NoteBuffer buf = entry.getValue();
            if (buf.pending.isEmpty()) {
                continue;
            }
            List<Long> likeUsers = new ArrayList<>();
            List<Long> unlikeUsers = new ArrayList<>();
            List<Map.Entry<Long, Integer>> snapshot = new ArrayList<>(buf.pending.entrySet());
            for (Map.Entry<Long, Integer> op : snapshot) {
                flushed.add(Map.entry(noteId, op));
                if (op.getValue() == 1) {
                    likeUsers.add(op.getKey());
                } else {
                    unlikeUsers.add(op.getKey());
                }
            }
            batch.add(new NoteLikeStore.AggregatedOps(noteId, likeUsers, unlikeUsers));
        }

        if (batch.isEmpty()) {
            return;
        }
        try {
            store.flushAggregated(batch);
        } catch (RuntimeException e) {
            log.error("flush hot buffer to redis failed, keep pending for retry", e);
            return;
        }
        for (Map.Entry<Long, Map.Entry<Long, Integer>> item : flushed) {
            NoteBuffer buf = buffers.get(item.getKey());
            if (buf == null) {
                continue;
            }
            Map.Entry<Long, Integer> op = item.getValue();
            if (buf.pending.remove(op.getKey(), op.getValue())) {
                buf.delta.addAndGet(op.getValue() == 1 ? -1 : 1);
            }
        }
        log.info("本地缓冲刷写: 笔记数={} 操作数={} 聚合为 1 次 pipeline 写 Redis", batch.size(), flushed.size());
    }
}
```

- [ ] **Step 2: 创建 HotKeyDetector（热点检测器，修复 window-seconds 死配置）**

从源 `HotKeyDetector.java` 移植，`article` → `note`，去掉 `MetricsService`，**核心修复**：`@Scheduled` 评估周期从硬编码 `fixedDelay=2000` 改为 `fixedDelayString` 读 `like.hot.window-seconds`（默认 2s，配置 5s 时真实生效）：

```java
package com.jiwei.social.like;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 热点笔记检测器：按窗口统计每篇笔记的点赞操作速率，
 * 超过阈值即进入本地聚合模式，连续冷却则退出。
 */
@Component
@Slf4j
public class HotKeyDetector {

    private final LocalHotLikeBuffer buffer;
    private final int thresholdPerWindow;
    private final int cooldownCycles;

    private final ConcurrentHashMap<Long, AtomicLong> counters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> coldCycles = new ConcurrentHashMap<>();

    public HotKeyDetector(LocalHotLikeBuffer buffer,
                          @Value("${like.hot.threshold-per-window:30}") int thresholdPerWindow,
                          @Value("${like.hot.cooldown-cycles:5}") int cooldownCycles) {
        this.buffer = buffer;
        this.thresholdPerWindow = thresholdPerWindow;
        this.cooldownCycles = cooldownCycles;
    }

    /** 每一次点赞/取消操作都打点。 */
    public void hit(long noteId) {
        counters.computeIfAbsent(noteId, k -> new AtomicLong()).incrementAndGet();
    }

    /** 每个评估窗口（window-seconds，默认 2s）评估一次各笔记的操作速率。 */
    @Scheduled(fixedDelayString = "${like.hot.window-seconds:2}000",
               initialDelayString = "${like.hot.window-seconds:2}000")
    public void evaluate() {
        for (Map.Entry<Long, AtomicLong> entry : counters.entrySet()) {
            Long noteId = entry.getKey();
            int ops = (int) entry.getValue().getAndSet(0);
            if (ops >= thresholdPerWindow) {
                if (!buffer.isHot(noteId)) {
                    buffer.markHot(noteId);
                    log.info("热点判定: note={} 窗口操作数={} >= 阈值={} -> 切换本地聚合写入", noteId, ops, thresholdPerWindow);
                }
                coldCycles.remove(noteId);
            } else if (buffer.isHot(noteId)) {
                int cycles = coldCycles.merge(noteId, 1, Integer::sum);
                if (cycles >= cooldownCycles) {
                    buffer.markCold(noteId);
                    coldCycles.remove(noteId);
                    log.info("热点冷却: note={} 连续 {} 个周期低于阈值，退出热点模式", noteId, cycles);
                }
            }
        }
    }
}
```

> 说明：`fixedDelayString = "${like.hot.window-seconds:2}000"` 中占位符解析为配置值（如 5），拼接 `000` 得 `5000`（5 秒），默认 `2000`（2 秒）。这是修复轮子「配置 5s 实际硬编码 2s」的关键——配置从此真实生效。

- [ ] **Step 3: 编译验证**

Run: `cd jiwei-life && mvn compile -pl jiwei-common,jiwei-pojo,jiwei-framework,jiwei-modules -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add jiwei-modules/src/main/java/com/jiwei/social/like/HotKeyDetector.java \
        jiwei-modules/src/main/java/com/jiwei/social/like/LocalHotLikeBuffer.java
git commit -m "feat(social): 热点检测器与本地聚合缓冲（修复 window-seconds 死配置）"
```

---

## Task 3: 攒批落库 + 幂等 + 兜底对账

**Files:**
- Modify: `com/jiwei/social/mapper/NoteLikeMapper.java` — 加 `insertIgnoreBatch` / `deleteBatch`
- Modify: `com/jiwei/social/mapper/NoteMapper.java` — 加 `incrLikeCount` / `reconcileLikeCount`
- Create: `com/jiwei/social/like/NoteLikeEventConsumer.java`
- Create: `com/jiwei/social/like/SyncReconciler.java`

**Interfaces:**
- Consumes: `NoteLikeStore`（Task 1）、`NoteLikeMapper`/`NoteMapper`（现有）
- Produces: `note_like` 幂等落库、`note.like_count` 计数更新与整刷对账

**步骤：**

- [ ] **Step 1: 扩展 NoteLikeMapper（批量幂等 INSERT/DELETE）**

**关键语义差异**：轮子 `like_record` 有 status 字段用 `ON DUPLICATE KEY UPDATE status=?`；JiweiLife `note_like` 是「存在=赞，删除=取消」，故赞用 `INSERT IGNORE`（依赖 `uk_note_user` 唯一键幂等），取消用 `DELETE`（幂等，删不存在也无副作用）：

```java
package com.jiwei.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.NoteLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.dynamic.datasource.annotation.DS;

import java.util.List;

@DS("social")
@Mapper
public interface NoteLikeMapper extends BaseMapper<NoteLike> {

    /** 批量幂等插入点赞明细（依赖 uk_note_user 唯一键，重复赞被忽略） */
    @Insert("<script>" +
            "INSERT IGNORE INTO note_like(note_id, user_id, create_time) VALUES " +
            "<foreach collection='list' item='r' separator=','>" +
            "(#{r.noteId}, #{r.userId}, NOW())" +
            "</foreach>" +
            "</script>")
    int insertIgnoreBatch(@Param("list") List<NoteLike> list);

    /** 批量幂等删除点赞明细（取消赞，删不存在也无副作用） */
    @Delete("<script>" +
            "DELETE FROM note_like WHERE (note_id, user_id) IN " +
            "<foreach collection='list' item='r' open='(' separator=',' close=')'>" +
            "(#{r.noteId}, #{r.userId})" +
            "</foreach>" +
            "</script>")
    int deleteBatch(@Param("list") List<NoteLike> list);
}
```

- [ ] **Step 2: 扩展 NoteMapper（计数增量更新 + 对账整刷）**

```java
package com.jiwei.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.Note;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import com.baomidou.dynamic.datasource.annotation.DS;

@DS("social")
@Mapper
public interface NoteMapper extends BaseMapper<Note> {

    /** 按净增量更新点赞计数（GREATEST 下限 0，防负数） */
    @Update("UPDATE note SET like_count = GREATEST(0, like_count + #{delta}) WHERE id = #{noteId}")
    int incrLikeCount(@Param("noteId") Long noteId, @Param("delta") Integer delta);

    // ⚠️ 实现已反转（2026-10-12）：当前以 DB 为唯一账本校正 Redis，本方法的方向已被取代
    //    见 docs/实现现状对照.md §4
    /** 对账整刷：直接覆盖为 Redis 权威计数（值不等才更新，避免无谓写） */
    @Update("UPDATE note SET like_count = GREATEST(0, #{count}) " +
            "WHERE id = #{noteId} AND like_count <> GREATEST(0, #{count})")
    int reconcileLikeCount(@Param("noteId") Long noteId, @Param("count") Integer count);
}
```

- [ ] **Step 3: 创建 NoteLikeEventConsumer（攒批消费端）**

从源 `LikeEventConsumer.java` 移植，改动：① 去掉 `JdbcTemplate`/`MetricsService`，改用 `NoteLikeMapper`/`NoteMapper`；② `article` → `note`；③ **status 翻转改成 INSERT IGNORE + DELETE**；④ 类上标 `@DS("social")`：

```java
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
 * 写库按 (noteId, userId) 合并为最终状态后：赞批量 INSERT IGNORE，取消批量 DELETE，
 * 计数按 net delta 逐条 UPDATE。至少一次语义 + 幂等保证不丢不重。
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
```

- [ ] **Step 4: 创建 SyncReconciler（兜底对账）**

从源 `SyncReconciler.java` 移植，`article` → `note`，`JdbcTemplate` → `NoteMapper`，去掉 `MetricsService`：

```java
package com.jiwei.social.like;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.jiwei.social.mapper.NoteMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 兜底对账：周期性把 Redis 计数整刷回 MySQL（幂等覆盖），
 * 修正极端情况下事件流与计数之间的偏差，保证最终一致。
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
```

- [ ] **Step 5: 编译验证 + Commit**

Run: `cd jiwei-life && mvn compile -pl jiwei-common,jiwei-pojo,jiwei-framework,jiwei-modules -DskipTests`

```bash
git add jiwei-modules/src/main/java/com/jiwei/social/mapper/NoteLikeMapper.java \
        jiwei-modules/src/main/java/com/jiwei/social/mapper/NoteMapper.java \
        jiwei-modules/src/main/java/com/jiwei/social/like/NoteLikeEventConsumer.java \
        jiwei-modules/src/main/java/com/jiwei/social/like/SyncReconciler.java
git commit -m "feat(social): 点赞攒批落库（INSERT IGNORE/DELETE 幂等）与 30s 兜底对账"
```

---

## Task 4: 编排服务 + 改造 NoteServiceImpl

**Files:**
- Create: `com/jiwei/social/like/NoteLikeService.java`
- Modify: `com/jiwei/social/service/impl/NoteServiceImpl.java` — `like()` 委托，去掉 `@Transactional`
- Modify: `jiwei-life/jiwei-modules/src/main/resources/application.yml` — 追加 `like.*` 配置

**Interfaces:**
- Consumes: `NoteLikeStore`/`LocalHotLikeBuffer`/`HotKeyDetector`（Task 1/2）
- Produces: `NoteLikeService.toggle(noteId, userId) -> ToggleResult`、`realTimeCount(noteId)`、`currentLiked(noteId, userId)`

**步骤：**

- [ ] **Step 1: 创建 NoteLikeService（编排 + 实时计数合并）**

从源 `LikeService.java` 移植，改动：① `article` → `note`；② 去掉 `knownArticleIds` 内存目录（存在校验由 `NoteServiceImpl` 的 `selectById` 负责，本服务纯编排）；③ 去掉 `ArticleRepository` 依赖；④ 去掉 `infoOf`（JiweiLife 暂无需批量组装，按需再加）：

```java
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
```

- [ ] **Step 2: 改造 NoteServiceImpl.like()**

注入 `NoteLikeService`，`like()` 委托（去掉裸 DB 读写与 `@Transactional`，保留「笔记存在校验」与「热度分更新」）：

```java
// 新增字段注入
@Autowired
private NoteLikeService noteLikeService;

// 原 like() 替换为：
@Override
public boolean like(Long noteId) {
    Long userId = BaseContext.getCurrentId();

    //1、笔记存在校验
    Note note = noteMapper.selectById(noteId);
    if (note == null) {
        return false;
    }

    //2、走 Redis 挡写 → 攒批 → 热点聚合链路（异步落库，最终一致）
    NoteLikeService.ToggleResult result = noteLikeService.toggle(noteId, userId);

    //3、用实时点赞数更新热度分（comment/collect 计数仍从 DB 读）
    note.setLikeCount((int) result.count());
    updateHotScore(noteId, note);

    return result.liked();
}
```

> 说明：`@Transactional` 必须去掉——改造后 `like()` 无同步 DB 写，事务无意义且会误导。`updateHotScore` 保持原逻辑不变（它内部读 `note.getLikeCount()/commentCount/collectCount` 算 score 写 `hot_notes:weekly` ZSet），我们只需在调用前把 `note.likeCount` 刷新为实时值。`collect()` 暂不改（现状已是 Redis Set + 同步计数，无对应轮子深度，后续单独补攒批）。

- [ ] **Step 3: application.yml 追加 like.* 配置**

在 `application.yml` 末尾（`jiwei:` 块后或同级）追加：

```yaml
like:
  hot:
    window-seconds: 2          # 热点评估窗口（秒），HotKeyDetector 评估周期
    threshold-per-window: 30   # 窗口内操作数阈值，超过则进入热点模式
    cooldown-cycles: 5         # 连续低于阈值的冷却周期数，达标则退出热点
    flush-interval-ms: 1500    # 本地聚合刷回 Redis 间隔（毫秒）
  consumer:
    interval-ms: 1000          # 攒批消费窥探间隔（毫秒）
    batch-size: 500            # 攒批触发阈值（条数）
    max-drain: 5000            # 单次窥探最大事件数
  reconcile-interval-ms: 30000 # 兜底对账间隔（毫秒）
```

- [ ] **Step 4: 编译验证 + Commit**

Run: `cd jiwei-life && mvn compile -pl jiwei-common,jiwei-pojo,jiwei-framework,jiwei-modules -DskipTests`

```bash
git add jiwei-modules/src/main/java/com/jiwei/social/like/NoteLikeService.java \
        jiwei-modules/src/main/java/com/jiwei/social/service/impl/NoteServiceImpl.java \
        jiwei-modules/src/main/resources/application.yml
git commit -m "feat(social): 点赞编排服务接入 NoteServiceImpl（Redis 挡写+热点聚合+实时计数）"
```

---

## Self-Review 结论

- **Spec 覆盖**：设计文档 §5.4 的六项技术点（Redis 挡写 / 攒批 peek-commit / 热点聚合 / 幂等 upsert / 兜底对账 / 实时合并）均有对应 Task，且 4 个 Task 有清晰的编译依赖顺序（Redis 层 → 检测聚合 → 落库对账 → 编排接入）。
- **语义适配（关键）**：轮子 `like_record.status`（1/0 翻转）→ JiweiLife「存在=赞，删除=取消」，落库改为 `INSERT IGNORE` + `DELETE`，未照搬 status 翻转。这是本次移植最容易出错、已显式处理的差异点。
- **幂等闭环**：写路径靠 `uk_note_user` 唯一键 + `INSERT IGNORE`/`DELETE` 天然幂等；事件队列 peek/commit 至少一次语义 + 幂等 upsert 保证不丢不重；`SyncReconciler` 30s 整刷 `note.like_count` 兜底收敛 Redis 计数与 DB 计数的偏差。
- **已知坑修复**：`window-seconds` 死配置 → `@Scheduled(fixedDelayString = "${like.hot.window-seconds:2}000")`，配置真实生效；去掉了轮子冗余的 `like:rank`、`MetricsService`、`knownArticleIds`（均有合理替代，非简化）。
- **边界**：`collect()`（收藏）不在本计划范围，现状已用 Redis Set + 同步计数，无对应轮子深度可注入，后续单独补攒批。`like()` 的 `@Transactional` 已移除，因为改造后无同步 DB 写。
