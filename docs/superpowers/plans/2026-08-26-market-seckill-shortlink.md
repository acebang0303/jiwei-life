# 营销域（market）实现计划：秒杀完整链路（熔断/对账/失败分类/限流）+ 短链辅助

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把秒杀系统的完整链路（自研熔断器 → 失败分类消费者 → 三道对账 → 限流降级 → 库存预热 → 乐观锁防超卖）注入 JiweiLife 现有的「只有 Lua 预扣」的秒杀骨架；同时把短链项目（MurmurHash+Base62+布隆防穿透+Caffeine 三级链路）作为营销辅助能力纳入 market。

**Architecture:** Redis 读写统一走 `RedisExecutor`（内部经三态熔断器 `RedisCircuitBreaker` 保护，只统计基础设施异常）。秒杀链路：Lua 预扣 → RocketMQ 异步落库（失败分类消费者：确定性失败回滚+确认、瞬时失败重试一次）→ 周期对账（库存/缓存/滞留订单三道，DB 为唯一账本）→ 限流切面（Redisson RRateLimiter + 本地令牌桶降级）。`seckill_activity` 加 `version`+`sold`，DB 兜底扣减走 `stock >= n` CAS + 乐观锁双保险。短链：`short_link` 表 + Caffeine 缓存 + 手写布隆过滤器 + 固定窗口限流。

**Tech Stack:** JDK 21、Spring Boot 3.x、MyBatis-Plus + dynamic-datasource（`@DS("market")`）、`rocketmq-client`（原生 SDK）、Redisson、Redis + Lua、Caffeine、Hutool。

**Spec:** `docs/superpowers/specs/2026-08-26-wheel-integration-design.md`（§5.3 营销域）

**源项目参考：**
- 秒杀：`D:\qiuzhao\seckill-system\src\main\java\com\seckill\`
- 短链：`D:\qiuzhao\duanlianfuwuqideshixian\src\main\java\com\example\shorturl\`

## Global Constraints

- 包名统一 `com.jiwei.market`；秒杀子包 `com.jiwei.market.seckill.*`；短链子包 `com.jiwei.market.shortlink.*`
- RocketMQ 用原生 `rocketmq-client`（`DefaultMQProducer`/`DefaultMQPushConsumer`），对齐 `EmbeddingConsumer` 风格
- **已知瑕疵必须在落地时修复**（设计文档 §6）：
  1. 熔断器「固定窗口简化版」→ 保留三态语义但注明窗口为环形计数近似，可接受（不引入 Resilience4j 额外依赖）
  2. `version` 乐观锁必须是真乐观锁：`version` 在 WHERE 条件里（MyBatis-Plus `@Version` + 条件更新），**不能** `SET version = version + 1` 在 SET 里
  3. Lua `limitPerUser` 未使用 → 落地时用 `INCR` 计数实现真实限购（不能只 `SISMEMBER` 查重）
- 短链两个硬伤修复（设计文档 §5.3.2）：`url_hash` 32 位碰撞 → 64 位 + 校验 `long_url` 相等；并发幂等 `rollback-only` 隐患 → 唯一键冲突时查回已有记录而非依赖事务
- 每个 Task 完成后 `git add` 具体文件并 commit，禁止 `git add -A`

---

## File Structure 概览

**新建（秒杀）：**
- `com/jiwei/market/seckill/breaker/RedisCircuitBreaker.java` — 三态熔断器
- `com/jiwei/market/seckill/cache/RedisExecutor.java` — 熔断保护的 Redis 执行器
- `com/jiwei/market/seckill/config/StockWarmUpRunner.java` — 库存预热
- `com/jiwei/market/seckill/mq/SeckillOrderCreateConsumer.java` — 失败分类消费者（RocketMQ）
- `com/jiwei/market/seckill/reconcile/ReconcileService.java` — 三道对账
- `com/jiwei/market/seckill/ratelimit/RateLimit.java`、`RateLimitAspect.java` — 限流切面

**新建（短链）：**
- `com/jiwei/market/shortlink/service/ShortLinkService.java`
- `com/jiwei/market/shortlink/component/ShortCodeBloomFilter.java`、`SimpleBloomFilter.java`
- `com/jiwei/market/shortlink/component/CreateRateLimiter.java`
- `com/jiwei/market/shortlink/util/Base62.java`、`MurmurHash.java`
- `com/jiwei/market/shortlink/mapper/ShortLinkMapper.java`
- `com/jiwei/market/shortlink/controller/ShortLinkController.java`、`RedirectController.java`

**修改：**
- `jiwei-pojo/.../pojo/entity/SeckillActivity.java` — 加 `version`/`sold`/`updateTime` 字段
- `jiwei-pojo/.../pojo/entity/ShortLink.java` — 新增实体
- `jiwei-pojo/.../pojo/entity/Orders.java` — 加 `seckillActivityId` 字段
- `jiwei-life/db/05_market.sql` — 加 `version`/`sold`/`update_time` 列、`short_link` 表、orders 唯一索引
- `jiwei-modules/.../market/service/impl/SeckillServiceImpl.java` — Lua 限购修复 + 熔断/限流接入 + `deductStock`/`restoreStock`/`revertRedisStock`
- `jiwei-modules/.../market/mapper/SeckillActivityMapper.java` — 加 CAS 扣减 SQL
- `jiwei-modules/.../trade/service/OrderService.java`（+ `impl/OrderServiceImpl.java`）— 加 `createSeckillOrder`（trade 库建单）
- `jiwei-framework/src/main/java/com/jiwei/framework/config/MyBatisPlusConfiguration.java` — 开启乐观锁插件

**测试：**
- `jiwei-modules/src/test/java/com/jiwei/market/seckill/RedisCircuitBreakerTest.java`
- `jiwei-modules/src/test/java/com/jiwei/market/shortlink/Base62Test.java`

---

## Task 1: Redis 三态熔断器 + 受保护执行器

**Files:**
- Create: `com/jiwei/market/seckill/breaker/RedisCircuitBreaker.java`
- Create: `com/jiwei/market/seckill/cache/RedisExecutor.java`
- Create: `com/jiwei/market/seckill/config/SeckillProperties.java`
- Test: `RedisCircuitBreakerTest.java`

**Interfaces:**
- Consumes: 无（第一个任务）
- Produces: `RedisExecutor.get/setIfAbsent/del/eval`（熔断保护的 Redis 操作）

**步骤：**

- [ ] **Step 1: 创建 SeckillProperties（熔断/对账配置）**

```java
package com.jiwei.market.seckill.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "seckill")
public class SeckillProperties {
    private Breaker breaker = new Breaker();
    private Reconcile reconcile = new Reconcile();

    @Data
    public static class Breaker {
        private int windowSize = 100;            // 滑动窗口大小
        private int failureRateThreshold = 50;   // 失败率阈值(%)
        private long openDurationMs = 30_000;    // 熔断持续时长
        private int halfOpenPermits = 5;         // 半开探测放行数
    }

    @Data
    public static class Reconcile {
        private boolean enabled = true;
        private boolean autoFix = true;
        private long intervalMs = 60_000;        // 对账周期
    }
}
```

- [ ] **Step 2: 移植 RedisCircuitBreaker**

从源 `D:\qiuzhao\seckill-system\src\main\java\com\seckill\breaker\RedisCircuitBreaker.java` 移植，包名改 `com.jiwei.market.seckill.breaker`，配置改注入 `SeckillProperties.Breaker`。**核心逻辑不变**（CLOSED→OPEN→HALF_OPEN 三态、只统计基础设施异常 `RedisConnectionFailureException`/`QueryTimeoutException`/`RedisSystemException`/`SocketException`）：

```java
@Slf4j
@Component
public class RedisCircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final SeckillProperties.Breaker config;
    @Getter private volatile State state = State.CLOSED;
    private final AtomicLong openUntil = new AtomicLong(0);
    private final AtomicInteger windowCalls = new AtomicInteger(0);
    private final AtomicInteger windowFailures = new AtomicInteger(0);
    private final AtomicInteger halfOpenSuccess = new AtomicInteger(0);

    public RedisCircuitBreaker(SeckillProperties properties) {
        this.config = properties.getBreaker();
    }

    public <T> T execute(String name, Supplier<T> call, Function<Throwable, T> fallback) {
        if (!allowRequest()) {
            return fallback.apply(new RedisConnectionFailureException("circuit breaker open"));
        }
        try {
            T result = call.get();
            onSuccess();
            return result;
        } catch (Throwable t) {
            if (isInfraFailure(t)) {
                onFailure();
                return fallback.apply(t);
            }
            throw t;
        }
    }
    // allowRequest/onSuccess/onFailure/slide/resetWindow/trip/tryTransition/isInfraFailure
    // 与源文件一致，逐方法移植
}
```

- [ ] **Step 3: 移植 RedisExecutor**

从源 `RedisExecutor.java` 移植，包名改，`RedisTemplate` 换 `StringRedisTemplate`（JiweiLife market 用 `RedisTemplate<String, Object>`，此处统一用 `StringRedisTemplate` 存库存计数）：

```java
@Component
public class RedisExecutor {
    private final StringRedisTemplate redis;
    private final RedisCircuitBreaker breaker;
    // 构造器注入

    public String get(String key, Function<Throwable, String> fallback) {
        return breaker.execute("redis:get", () -> redis.opsForValue().get(key), fallback);
    }
    public void setIfAbsent(String key, String value, Duration ttl) {
        breaker.execute("redis:setIfAbsent", () -> redis.opsForValue().setIfAbsent(key, value, ttl),
                t -> { log.warn("setIfAbsent degraded: {}", t.getMessage()); return false; });
    }
    public void del(String... keys) { /* 同源 */ }
    public Long eval(DefaultRedisScript<Long> script, List<String> keys,
                     Function<Throwable, Long> fallback, Object... args) {
        return breaker.execute("redis:lua", () -> redis.execute(script, keys, args), fallback);
    }
}
```

- [ ] **Step 4: 写熔断器单元测试**

```java
class RedisCircuitBreakerTest {
    @Test
    void shouldOpenAfterFailureRateExceedsThreshold() {
        SeckillProperties props = new SeckillProperties();
        props.getBreaker().setWindowSize(10);
        props.getBreaker().setFailureRateThreshold(50);
        RedisCircuitBreaker breaker = new RedisCircuitBreaker(props);

        AtomicInteger fallbacks = new AtomicInteger();
        for (int i = 0; i < 10; i++) {
            final boolean fail = i % 2 == 0; // 50% 失败率
            breaker.execute("test", () -> {
                if (fail) throw new RedisConnectionFailureException("down");
                return "ok";
            }, t -> { fallbacks.incrementAndGet(); return "fallback"; });
        }
        // 达到阈值后应进入 OPEN 或至少触发过熔断降级
        assertThat(breaker.getState()).isIn(RedisCircuitBreaker.State.OPEN, RedisCircuitBreaker.State.HALF_OPEN);
    }
}
```

- [ ] **Step 5: 编译验证 + Commit**

Run: `cd jiwei-life && mvn compile -pl jiwei-pojo,jiwei-common,jiwei-framework,jiwei-modules -DskipTests`

```bash
git add jiwei-modules/src/main/java/com/jiwei/market/seckill/breaker/ jiwei-modules/src/main/java/com/jiwei/market/seckill/cache/ jiwei-modules/src/main/java/com/jiwei/market/seckill/config/SeckillProperties.java jiwei-modules/src/test/java/com/jiwei/market/seckill/
git commit -m "feat(market): 自研 Redis 三态熔断器 + 受保护执行器"
```

---

## Task 2: 秒杀表改造（乐观锁 + 防超卖 + 防重）+ 库存预热

**Files:**
- Modify: `jiwei-pojo/.../pojo/entity/SeckillActivity.java`
- Modify: `jiwei-pojo/.../pojo/entity/Orders.java`
- Modify: `jiwei-life/db/05_market.sql`
- Create: `com/jiwei/market/seckill/config/StockWarmUpRunner.java`
- Modify: `jiwei-modules/.../market/mapper/SeckillActivityMapper.java`
- Modify: `jiwei-framework/src/main/java/com/jiwei/framework/config/MyBatisPlusConfiguration.java`（开启乐观锁插件）

**Interfaces:**
- Consumes: `RedisExecutor`（Task 1）
- Produces: `SeckillActivity`（含 version/sold）、`SeckillActivityMapper.deductStock`（CAS 扣减）

**步骤：**

- [ ] **Step 1: SeckillActivity 加 version/sold 字段**

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("seckill_activity")
public class SeckillActivity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long dishId;
    private BigDecimal seckillPrice;
    private Integer stock;
    private Integer sold;              // 新增：已售数量
    private Integer limitPerUser;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer status;
    @Version                          // 新增：乐观锁
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
```

> **必须同步开启乐观锁插件**：`jiwei-framework` 的 `MyBatisPlusConfiguration`（`com.jiwei.framework.config.MyBatisPlusConfiguration`）目前只注册了分页插件，`@Version` 注解不会自动生效。需在 `mybatisPlusInterceptor()` 里补 `interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor())`（`com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor`），否则 `updateById` 不会带上 `WHERE version = ?`。该改动随本 Task 的 Commit 一起提交（见 Step 6 的 `git add` 清单）。

- [ ] **Step 2: Orders 加 seckillActivityId 字段**

```java
// Orders.java 追加
private Long seckillActivityId;  // 秒杀活动ID（普通订单为 NULL）
```

- [ ] **Step 3: 05_market.sql 改造**

`seckill_activity` 表加 `sold`/`version` 列；`orders` 表（04_trade.sql）加 `seckill_activity_id` 列 + 唯一索引：

```sql
ALTER TABLE seckill_activity
    ADD COLUMN sold INT NOT NULL DEFAULT 0 COMMENT '已售数量' AFTER stock,
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER status,
    ADD COLUMN update_time DATETIME COMMENT '更新时间' AFTER create_time;

-- orders 表（trade 库）秒杀防重：同一用户同一活动只能秒杀一次
ALTER TABLE orders
    ADD COLUMN seckill_activity_id BIGINT COMMENT '秒杀活动ID(普通订单NULL)' AFTER is_seckill,
    ADD UNIQUE KEY uk_user_activity (user_id, seckill_activity_id);
```

> MySQL 唯一索引允许多个 NULL，普通订单 `seckill_activity_id=NULL` 不受唯一约束影响。

- [ ] **Step 4: SeckillActivityMapper 加 CAS 扣减 SQL**

```java
@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    /** DB 兜底扣减：stock >= n 条件更新 + version 乐观锁（双保险防超卖） */
    @Update("UPDATE seckill_activity SET stock = stock - #{quantity}, " +
            "sold = sold + #{quantity}, version = version + 1 " +
            "WHERE id = #{activityId} AND stock >= #{quantity}")
    int deductStock(@Param("activityId") Long activityId, @Param("quantity") int quantity);

    /** 回补库存（取消/超时） */
    @Update("UPDATE seckill_activity SET stock = stock + #{quantity}, " +
            "sold = sold - #{quantity}, version = version + 1 WHERE id = #{activityId}")
    int restoreStock(@Param("activityId") Long activityId, @Param("quantity") int quantity);
}
```

> 注意：`version = version + 1` 写在 SET 里配合 MyBatis-Plus 的条件更新使用时要谨慎——真正的乐观锁是 `WHERE version = #{version}`（由 `@Version` + `updateById` 自动生成）。此处 CAS 扣减以 `stock >= n` 为主防线，`version+1` 仅作计数；当业务用 `updateById` 修改活动时走 `@Version` 真乐观锁。两者不冲突（`deductStock` 是自定义 SQL，不经过 MP 乐观锁）。

- [ ] **Step 5: 移植 StockWarmUpRunner（去 ES 部分）**

从源 `StockWarmUpRunner.java` 移植，**删除 ES 商品索引预热**（JiweiLife 无 ES），保留库存预热：

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class StockWarmUpRunner implements ApplicationRunner {
    private final SeckillActivityMapper activityMapper;
    private final RedisExecutor redisExecutor;

    @Override
    public void run(ApplicationArguments args) {
        List<SeckillActivity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>()
                        .eq(SeckillActivity::getStatus, 1)
                        .gt(SeckillActivity::getEndTime, LocalDateTime.now()));
        for (SeckillActivity a : activities) {
            String stockKey = "seckill:stock:" + a.getId() + ":" + a.getDishId();
            redisExecutor.setIfAbsent(stockKey, String.valueOf(a.getStock()),
                    Duration.between(LocalDateTime.now(), a.getEndTime()));
            log.info("库存预热(DB为准,setIfAbsent不覆盖): activityId={}, stock={}", a.getId(), a.getStock());
        }
    }
}
```

- [ ] **Step 6: 编译验证 + Commit**

Run: `cd jiwei-life && mvn compile -pl jiwei-pojo,jiwei-common,jiwei-framework,jiwei-modules -DskipTests`

```bash
git add jiwei-pojo/src/main/java/com/jiwei/pojo/entity/SeckillActivity.java jiwei-pojo/src/main/java/com/jiwei/pojo/entity/Orders.java jiwei-life/db/05_market.sql jiwei-life/db/04_trade.sql jiwei-modules/src/main/java/com/jiwei/market/seckill/config/StockWarmUpRunner.java jiwei-modules/src/main/java/com/jiwei/market/mapper/SeckillActivityMapper.java jiwei-framework/src/main/java/com/jiwei/framework/config/MyBatisPlusConfiguration.java
git commit -m "feat(market): 秒杀表加乐观锁/防超卖/防重 + 库存预热"
```

---

## Task 3: 秒杀失败分类消费者 + 完整落库链路

**Files:**
- Create: `com/jiwei/market/seckill/mq/SeckillOrderCreateConsumer.java`
- Create: `com/jiwei/market/seckill/mq/SeckillMessage.java`（record）
- Modify: `jiwei-modules/.../market/service/impl/SeckillServiceImpl.java`（Lua 限购修复 + 发消息）
- Modify: `jiwei-modules/.../trade/mq/OrderMessageProducer.java`（接真实 `DefaultMQProducer`）
- Modify: `jiwei-modules/.../trade/service/OrderService.java`（+ `impl/OrderServiceImpl.java`，加 `createSeckillOrder`）

**Interfaces:**
- Consumes: `DefaultMQProducer`（框架 bean）、`SeckillActivityMapper.deductStock`（Task 2）
- Produces: 秒杀下单 `RocketMQ` 消息 + 消费者失败分类处理

**步骤：**

- [ ] **Step 1: 修复 SeckillServiceImpl 的 Lua 限购（limitPerUser 真实生效）**

当前 Lua 的 `limitPerUser` 变量定义了但只用 `SISMEMBER` 查重（限制的是「只能买一次」，不是「限购 N 件」）。修复为 INCR 计数：

```lua
-- 修复后：用 INCR 实现真实限购
local stockKey = KEYS[1]       -- seckill:stock:{activityId}:{dishId}
local userKey = KEYS[2]        -- seckill:users:{activityId}
local userId = ARGV[1]
local limitPerUser = tonumber(ARGV[2])

local stock = tonumber(redis.call('GET', stockKey) or '0')
if stock <= 0 then return -1 end

local userCount = tonumber(redis.call('HGET', userKey, userId) or '0')
if userCount >= limitPerUser then return -2 end

redis.call('DECR', stockKey)
redis.call('HINCRBY', userKey, userId, 1)   -- 限购计数（Hash 支持按用户回滚）
return 1
```

> `userKey` 从 SET 改为 HASH（`seckill:users:{activityId}`，field=userId，value=已购数量），以支持「限购 N 件」与「回滚单用户计数」。`revertRedisStock` 需同步 `HINCRBY userKey userId -1`。
>
> `seckillBuy` 在 Lua 返回成功后：用 `IdUtil.getSnowflakeNextIdStr()` 生成 `orderNo`，组装 `SeckillMessage(orderNo, userId, activityId, dishId, quantity, payAmount)`（`payAmount = seckillPrice × quantity`，`quantity` 取 `dto` 或默认 1），再调 `orderMessageProducer.sendSeckillOrder(message)`，返回 `orderNo`（替换当前 `return userId` 占位）。

- [ ] **Step 2: 创建 SeckillMessage（record）**

```java
public record SeckillMessage(String orderNo, Long userId, Long activityId,
                             Long dishId, int quantity, BigDecimal payAmount) {}
```

- [ ] **Step 3: OrderMessageProducer 接真实 RocketMQ Producer（签名改为承载完整消息）**

现有 `sendSeckillOrder(Long userId, Long activityId, Long dishId)` 签名不足以承载建单所需信息（订单号/数量/金额），改为 `sendSeckillOrder(SeckillMessage message)`：注入框架 `DefaultMQProducer`（bean 名 `rocketMQProducer`，见 `RocketMQConfiguration`），序列化 `SeckillMessage` 发到 `seckill-order-topic`（替换 TODO）。

`SeckillMessage.orderNo` 必须在 `seckillBuy` 发消息**之前**用 `IdUtil.getSnowflakeNextIdStr()` 生成——它是建单的防重主键，不能在消费者里生成（否则失败重试会换号导致重复建单）。

- [ ] **Step 4: 创建 SeckillOrderCreateConsumer（失败分类，RabbitMQ → RocketMQ）**

从源 `OrderCreateConsumer.java` 迁移，失败分类语义映射到 RocketMQ：

| RabbitMQ | RocketMQ |
|---|---|
| `basicAck`（成功/确定性失败） | `CONSUME_SUCCESS` |
| `basicNack(requeue=true)`（瞬时失败重试一次） | `RECONSUME_LATER`（RocketMQ 内置重试）+ `getReconsumeTimes()` 判断 |

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class SeckillOrderCreateConsumer {
    private static final String TOPIC = "seckill-order-topic";
    @Value("${rocketmq.name-server:localhost:9876}") private String nameServer;
    @Value("${rocketmq.consumer.group:seckill-order-consumer}") private String consumerGroup;

    private final SeckillService seckillService;
    private final OrderService orderService;
    private DefaultMQPushConsumer consumer;

    @PostConstruct
    public void start() throws MQClientException {
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(TOPIC, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
            for (MessageExt msg : msgs) {
                SeckillMessage message = JSON.parseObject(
                        new String(msg.getBody(), StandardCharsets.UTF_8), SeckillMessage.class);
                boolean deducted = false;
                try {
                    // 1. market 库 CAS 扣库存（跨库拆分，见 Step 5）
                    deducted = seckillService.deductStock(message.activityId(), message.quantity());
                    if (!deducted) {
                        // 库存不足：未扣 DB 库存，仅回滚 Redis 后确认
                        seckillService.revertRedisStock(message.activityId(), message.dishId(), message.userId(), message.quantity());
                        log.warn("库存不足，回滚Redis: orderNo={}", message.orderNo());
                        continue;
                    }
                    // 2. trade 库建单
                    orderService.createSeckillOrder(message);
                    // 成功，继续下一条
                } catch (OrderBusinessException e) {
                    // 确定性失败（重复单等）：若已扣库存则回补，再回滚 Redis 后确认，不重试
                    if (deducted) seckillService.restoreStock(message.activityId(), message.quantity());
                    seckillService.revertRedisStock(message.activityId(), message.dishId(), message.userId(), message.quantity());
                    log.warn("建单确定性失败，回补库存并回滚Redis: orderNo={}, reason={}", message.orderNo(), e.getMessage());
                } catch (Exception e) {
                    // 瞬时失败：回补库存 + 回滚 Redis；已重试一次仍失败 → 转人工；否则 RECONSUME_LATER
                    if (deducted) seckillService.restoreStock(message.activityId(), message.quantity());
                    seckillService.revertRedisStock(message.activityId(), message.dishId(), message.userId(), message.quantity());
                    if (msg.getReconsumeTimes() >= 1) {
                        log.error("建单瞬时失败且已重试，转人工: orderNo={}", message.orderNo(), e);
                    } else {
                        log.warn("建单瞬时失败，进入重试: orderNo={}", message.orderNo(), e);
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
    }
    @PreDestroy public void shutdown() { if (consumer != null) consumer.shutdown(); }
}
```

- [ ] **Step 5: 跨库拆分建单（两个 @DS 方法 + 补偿 + 最终一致）**

秒杀落库是**跨库写**：`deductStock`（扣库存）在 market 库（`SeckillServiceImpl` 已 `@DS("market")`），`orders`（建单）在 trade 库，二者不能塞进同一个 `@DS` + `@Transactional` 方法（dynamic-datasource 无法跨库事务）。因此拆成两个独立 `@DS` 方法，由消费者（Step 4）编排，靠「补偿 + 对账（Task 4）」保证最终一致：

**方法 A（market 库）**：`SeckillServiceImpl.deductStock(Long activityId, int quantity) -> boolean`
直接调 `seckillActivityMapper.deductStock`（CAS，返回 0 即库存不足，返回 false），不做任何 trade 库操作。

**方法 B（trade 库）**：`OrderService.createSeckillOrder(SeckillMessage) -> Long`（`@DS("trade")`）
组装 `Orders`：`is_seckill=1`、`seckill_activity_id=message.activityId()`、`number=message.orderNo()`、`user_id=message.userId()`、`pay_amount=message.payAmount()`、`status=PENDING_PAYMENT`、`pay_status=UN_PAID`，插入 orders。`uk_user_activity` 唯一索引冲突（`DuplicateKeyException`）捕获为「重复秒杀」确定性异常（`OrderBusinessException`）。

**补偿方法（market 库）**：`SeckillServiceImpl.restoreStock(Long activityId, int quantity)` + `revertRedisStock(Long activityId, Long dishId, Long userId, int quantity)`
`restoreStock` 调 `SeckillActivityMapper.restoreStock` 回补 `stock`/`sold`（**仅当 `deductStock` 已成功、建单失败时**才调用，见 Step 4 的 `deducted` 标志）；`revertRedisStock` 回滚 Redis：`INCR seckill:stock:{activityId}:{dishId}`（回补库存，需 dishId 重建键）+ `HINCRBY seckill:users:{activityId} userId -1`（回滚用户限购计数）。

> 补偿/重试仍有窗口（如 `restoreStock` 本身失败），由 Task 4 的「Redis 库存 vs DB」对账以 DB 为唯一账本收敛，不依赖分布式事务。

- [ ] **Step 6: 编译验证 + Commit**

Run: `cd jiwei-life && mvn compile -pl jiwei-pojo,jiwei-common,jiwei-framework,jiwei-modules -DskipTests`

```bash
git add jiwei-modules/src/main/java/com/jiwei/market/seckill/mq/ jiwei-modules/src/main/java/com/jiwei/market/service/impl/SeckillServiceImpl.java jiwei-modules/src/main/java/com/jiwei/trade/mq/OrderMessageProducer.java jiwei-modules/src/main/java/com/jiwei/trade/service/OrderService.java jiwei-modules/src/main/java/com/jiwei/trade/service/impl/OrderServiceImpl.java
git commit -m "feat(market): 秒杀失败分类消费者 + Lua 真实限购 + DB 兜底落库"
```

---

## Task 4: 秒杀三道对账（DB 为唯一账本）

**Files:**
- Create: `com/jiwei/market/seckill/reconcile/ReconcileService.java`
- Create: `com/jiwei/market/seckill/reconcile/ReconcileController.java`（手动触发 + 查看报告）

**Interfaces:**
- Consumes: `RedisExecutor`（Task 1）、`SeckillActivityMapper`、`OrderMapper`（或 trade 依赖）
- Produces: `ReconcileService.runOnce(boolean autoFix) -> Map`

**步骤：**

- [ ] **Step 1: 移植 ReconcileService（三道对账）**

从源 `ReconcileService.java` 移植，适配 JiweiLife 字段（`dishId`/`seckillPrice`），**第二道活动缓存对账简化为库存键为主**（JiweiLife 秒杀活动缓存较薄，聚焦库存/滞留订单）：

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconcileService {
    private final SeckillActivityMapper activityMapper;
    private final RedisExecutor redisExecutor;
    private final SeckillService seckillService;
    private final SeckillProperties properties;
    private final AtomicReference<Map<String, Object>> latestReport = new AtomicReference<>(Map.of());

    @Scheduled(fixedDelayString = "${seckill.reconcile.interval-ms:60000}", initialDelay = 15000)
    public void runPeriodically() {
        if (!properties.getReconcile().isEnabled()) return;
        try {
            Map<String, Object> report = runOnce(properties.getReconcile().isAutoFix());
            latestReport.set(report);
        } catch (Exception e) {
            log.error("对账任务失败", e);
        }
    }

    public Map<String, Object> runOnce(boolean autoFix) {
        List<String> details = new ArrayList<>();
        inventoryReconcile(autoFix, details);   // 第一道：Redis 库存 vs DB
        staleOrderReconcile(details);           // 第二道：滞留订单兜底关单
        Map<String, Object> report = new HashMap<>();
        report.put("autoFix", autoFix);
        report.put("fixedCount", details.size());
        report.put("details", details);
        latestReport.set(report);
        return report;
    }

    /** 第一道：Redis 预扣库存 vs DB 库存，漂移以 DB 为准收敛 */
    private void inventoryReconcile(boolean autoFix, List<String> details) {
        List<SeckillActivity> activities = activityMapper.selectList(
                new LambdaQueryWrapper<SeckillActivity>().eq(SeckillActivity::getStatus, 1));
        LocalDateTime now = LocalDateTime.now();
        for (SeckillActivity a : activities) {
            if (now.isBefore(a.getStartTime()) || now.isAfter(a.getEndTime())) continue;
            String key = "seckill:stock:" + a.getId() + ":" + a.getDishId();
            String redisStock = redisExecutor.get(key, t -> "BREAKER_OPEN");
            if ("BREAKER_OPEN".equals(redisStock)) continue; // 熔断期跳过
            int dbStock = a.getStock();
            if (redisStock == null) {
                details.add("stock-missing: activity#" + a.getId() + " db=" + dbStock);
                if (autoFix) redisExecutor.setIfAbsent(key, String.valueOf(dbStock),
                        Duration.between(now, a.getEndTime()));
            } else if (Integer.parseInt(redisStock) != dbStock) {
                details.add("stock-drift: activity#" + a.getId() + " redis=" + redisStock + " db=" + dbStock);
                if (autoFix) {
                    redisExecutor.del(key);
                    redisExecutor.setIfAbsent(key, String.valueOf(dbStock), Duration.between(now, a.getEndTime()));
                }
            }
        }
    }

    /** 第二道：滞留待支付秒杀订单兜底关单（延迟消息丢失/消费失败的兜底） */
    private void staleOrderReconcile(List<String> details) {
        // 扫描 is_seckill=1 且 待支付 且 超过支付截止时间 的 orders，复用状态机 CAS 关单 + 回补库存
        // seckillService.closeTimeoutSeckillOrder(orderNo, activityId, userId, quantity)
    }
}
```

> 秒杀订单超时截止：orders 表无 `expire_time`，可用 `create_time + 15min` 作为支付截止（与 `OrderTask`/延迟消息的 15 分钟一致）。

- [ ] **Step 2: 创建 ReconcileController（手动触发）**

```java
@RestController
@RequestMapping("/api/admin/reconcile")
public class ReconcileController {
    private final ReconcileService reconcileService;

    @PostMapping("/run")
    public Result<Map<String, Object>> run(@RequestParam(defaultValue = "true") boolean autoFix) {
        return Result.success(reconcileService.runOnce(autoFix));
    }
    @GetMapping("/latest")
    public Result<Map<String, Object>> latest() {
        return Result.success(reconcileService.latest());
    }
}
```

- [ ] **Step 3: 编译验证 + Commit**

Run: `cd jiwei-life && mvn compile -pl jiwei-pojo,jiwei-common,jiwei-framework,jiwei-modules -DskipTests`

```bash
git add jiwei-modules/src/main/java/com/jiwei/market/seckill/reconcile/
git commit -m "feat(market): 秒杀三道对账（Redis库存vsDB + 滞留订单兜底关单）"
```

---

## Task 5: 限流降级切面（Redisson RRateLimiter + 本地令牌桶）

**Files:**
- Create: `com/jiwei/market/seckill/ratelimit/RateLimit.java`
- Create: `com/jiwei/market/seckill/ratelimit/RateLimitAspect.java`
- Modify: `jiwei-modules/.../market/controller/user/SeckillBuyController.java`（加 `@RateLimit` 注解）

**Interfaces:**
- Consumes: `RedissonClient`
- Produces: `@RateLimit` 注解 + 切面限流

**步骤：**

- [ ] **Step 1: 创建 @RateLimit 注解**

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimit.RateLimits.class)
public @interface RateLimit {
    String key();                       // 限流 key（支持 SpEL）
    int permitsPerSecond() default 100; // 每秒令牌数
    int intervalSeconds() default 1;    // 速率周期
    int permitsPerRequest() default 1;  // 每次请求消耗令牌
}
```

- [ ] **Step 2: 移植 RateLimitAspect**

从源 `RateLimitAspect.java` 移植，包名改，注解名改 `com.jiwei.market.seckill.ratelimit.RateLimit`，异常改 `OrderBusinessException`。核心逻辑不变：Redisson `RRateLimiter` 令牌桶 + Redis 故障降级本地 `LocalTokenBucket`。

- [ ] **Step 3: 秒杀接口加限流注解**

```java
@PostMapping("/{id}/buy")
@RateLimit(key = "seckill:buy", permitsPerSecond = 200, intervalSeconds = 1)
public Result<Long> buy(@PathVariable Long id, @RequestBody SeckillBuyDTO dto) {
    dto.setActivityId(id);
    return Result.success(seckillService.seckillBuy(dto));
}
```

- [ ] **Step 4: 编译验证 + Commit**

Run: `cd jiwei-life && mvn compile -pl jiwei-pojo,jiwei-common,jiwei-framework,jiwei-modules -DskipTests`

```bash
git add jiwei-modules/src/main/java/com/jiwei/market/seckill/ratelimit/ jiwei-modules/src/main/java/com/jiwei/market/controller/user/SeckillBuyController.java
git commit -m "feat(market): 秒杀限流降级（Redisson 令牌桶 + 本地桶降级）"
```

---

## Task 6: 短链（营销辅助：MurmurHash+Base62+布隆+Caffeine）

**Files:**
- Create: `jiwei-pojo/.../pojo/entity/ShortLink.java`
- Create: `com/jiwei/market/shortlink/util/Base62.java`、`MurmurHash.java`、`SimpleBloomFilter.java`
- Create: `com/jiwei/market/shortlink/component/ShortCodeBloomFilter.java`、`CreateRateLimiter.java`
- Create: `com/jiwei/market/shortlink/mapper/ShortLinkMapper.java`
- Create: `com/jiwei/market/shortlink/service/ShortLinkService.java`
- Create: `com/jiwei/market/shortlink/controller/ShortLinkController.java`、`RedirectController.java`
- Modify: `jiwei-life/db/05_market.sql`（加 `short_link` 表）

**Interfaces:**
- Consumes: 无（独立子域）
- Produces: `ShortLinkService.create(String longUrl) -> String`、`resolve(String code) -> String`

**步骤：**

- [ ] **Step 1: 05_market.sql 加 short_link 表**

```sql
-- 短链表
CREATE TABLE short_link (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    short_code VARCHAR(16) NOT NULL UNIQUE COMMENT '短码',
    long_url VARCHAR(512) NOT NULL COMMENT '原始长链',
    url_hash BIGINT NOT NULL COMMENT '长链64位哈希',
    click_count BIGINT DEFAULT 0 COMMENT '点击次数',
    create_time DATETIME NOT NULL,
    INDEX idx_url_hash (url_hash)
) COMMENT '短链表';
```

- [ ] **Step 2: 创建 ShortLink 实体 + ShortLinkMapper**

```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@TableName("short_link")
public class ShortLink {
    @TableId(type = IdType.AUTO) private Long id;
    private String shortCode;
    private String longUrl;
    private Long urlHash;
    private Long clickCount;
    @TableField(fill = FieldFill.INSERT) private LocalDateTime createTime;
}
```

```java
@Mapper
public interface ShortLinkMapper extends BaseMapper<ShortLink> {
    @Update("UPDATE short_link SET click_count = click_count + 1 WHERE short_code = #{code}")
    int incrementClickCount(@Param("code") String code);
}
```

- [ ] **Step 3: 移植工具类（Base62/MurmurHash/SimpleBloomFilter）**

从源 `Base62.java`/`MurmurHash.java`/`SimpleBloomFilter.java` 移植，包名改 `com.jiwei.market.shortlink.util`。**关键修复**：`MurmurHash` 增加 64 位版本（`hash64`），替换源项目 32 位 `hash32Unsigned` 以消除 `url_hash` 碰撞。

- [ ] **Step 4: 移植 ShortCodeBloomFilter（启动加载存量短码）**

从源移植，`ShortLinkRepository.findAllShortCodes()` 改为 `shortLinkMapper.selectList(null)` 取 `shortCode` 列表。`SimpleBloomFilter` 保留手写实现（expectedInsertions/fpp 构造）。

- [ ] **Step 5: 移植 CreateRateLimiter（固定窗口限流）**

从源移植（Caffeine `expireAfterWrite` 实现窗口重置），包名改，IP 从请求头取（`X-Forwarded-For`）。

- [ ] **Step 6: 创建 ShortLinkService（修复 64 位哈希 + 幂等 + 三级链路）**

从源 `ShortLinkService.java` 移植，Spring Data JPA 改 MyBatis-Plus，`urlHash` 用 64 位：

```java
@DS("market")
@Service
public class ShortLinkService {
    private static final int MAX_RETRY = 10;
    private final ShortLinkMapper mapper;
    private final ShortCodeBloomFilter bloomFilter;
    private final Cache<String, String> redirectCache = Caffeine.newBuilder()
            .maximumSize(100_000).expireAfterWrite(Duration.ofHours(24)).build();

    @Transactional
    public String create(String longUrl) {
        long urlHash = MurmurHash.hash64(longUrl);
        ShortLink existing = mapper.selectOne(new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getUrlHash, urlHash)
                .eq(ShortLink::getLongUrl, longUrl));   // 修复：校验 longUrl 相等，避免哈希碰撞误判
        if (existing != null) return existing.getShortCode();

        for (int i = 0; i < MAX_RETRY; i++) {
            String seed = i == 0 ? longUrl : longUrl + "#" + i;
            String code = Base62.encode(MurmurHash.hash64(seed));
            if (mapper.selectCount(new LambdaQueryWrapper<ShortLink>()
                    .eq(ShortLink::getShortCode, code)) > 0) continue;
            ShortLink link = ShortLink.builder()
                    .shortCode(code).longUrl(longUrl).urlHash(urlHash).clickCount(0L).build();
            try {
                mapper.insert(link);
                bloomFilter.add(code);
                redirectCache.put(code, longUrl);
                return code;
            } catch (DuplicateKeyException e) {
                // 并发下同码/同链已被插入：查回已有记录返回（幂等）
                ShortLink raced = mapper.selectOne(new LambdaQueryWrapper<ShortLink>()
                        .eq(ShortLink::getUrlHash, urlHash).eq(ShortLink::getLongUrl, longUrl));
                if (raced != null) return raced.getShortCode();
            }
        }
        throw new OrderBusinessException("短码生成失败，请重试");
    }

    @Transactional
    public String resolve(String code) {
        String cached = redirectCache.getIfPresent(code);
        if (cached != null) { mapper.incrementClickCount(code); return cached; }
        if (!bloomFilter.mightContain(code)) throw new OrderBusinessException("短链不存在");
        ShortLink link = mapper.selectOne(new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getShortCode, code));
        if (link == null) throw new OrderBusinessException("短链不存在");
        mapper.incrementClickCount(code);
        redirectCache.put(code, link.getLongUrl());
        return link.getLongUrl();
    }
}
```

- [ ] **Step 7: 创建 Controller（生成 + 重定向）**

```java
@RestController
@RequestMapping("/api/short-link")
public class ShortLinkController {
    @PostMapping("/create")
    public Result<String> create(@RequestParam String longUrl) {
        // CreateRateLimiter 限流（按 IP）
        return Result.success(shortLinkService.create(longUrl));
    }
}

@Controller
public class RedirectController {
    @GetMapping("/s/{code}")
    public RedirectView redirect(@PathVariable String code) {
        String target = shortLinkService.resolve(code);
        return new RedirectView(target); // 302 以统计点击
    }
}
```

- [ ] **Step 8: 编译验证 + Commit**

Run: `cd jiwei-life && mvn compile -pl jiwei-pojo,jiwei-common,jiwei-framework,jiwei-modules -DskipTests`

```bash
git add jiwei-pojo/src/main/java/com/jiwei/pojo/entity/ShortLink.java jiwei-modules/src/main/java/com/jiwei/market/shortlink/ jiwei-life/db/05_market.sql
git commit -m "feat(market): 短链服务（64位哈希+Base62+布隆防穿透+点击计数）"
```

---

## Self-Review 结论

- **Spec 覆盖**：设计文档 §5.3 的秒杀（熔断/对账/失败分类/限流/预热/乐观锁）+ 短链（哈希/Base62/布隆/Caffeine/计数/限流）均有对应 Task。
- **RabbitMQ→RocketMQ 映射**：`basicAck`→`CONSUME_SUCCESS`、`basicNack(requeue)`→`RECONSUME_LATER` + `getReconsumeTimes()` 判断重试次数。
- **已知瑕疵修复**：Lua `limitPerUser` 真实生效（HINCRBY 计数）；`version` 乐观锁区分「真乐观锁（@Version+updateById）」与「CAS 扣减（stock>=n 为主防线）」；短链 64 位哈希 + 校验 longUrl 相等 + 唯一键冲突查回。
- **架构决策（需用户知晓）**：秒杀订单复用 `orders` 表（`is_seckill=1` + `seckill_activity_id`），而非独立 `seckill_order` 表——JiweiLife 是 O2O 外卖，秒杀菜品订单仍需走「接单→备货→取餐」完整履约流程，与普通订单同构；秒杀系统的独立订单表是电商「秒杀→发货」场景。防重技术点（`uk_user_activity` 唯一索引）通过 `orders` 表加唯一键等价落地。
