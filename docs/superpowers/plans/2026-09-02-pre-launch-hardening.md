# 上线前加固实现计划（四阶段）

> ⚠️ **实现现状对照（2026-10-12）**：本文写作时全项目仅 8 个单测；当前为 **121 例**
> （jiwei-framework 2 / jiwei-modules 59 / jiwei-ai 60），编译 warning 0 条。
> 除单测外还补了真机验证：点赞 Lua 原子性与幂等（含完全重放 delta=0）、只读写探针（含故障注入）、
> trgm 索引命中（EXPLAIN）。见 [`docs/实现现状对照.md`](../实现现状对照.md) §9。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 上线前完成四项后端加固——清洗 995 条脏订单、补核心链路自动化测试、建立 JMeter 压测基准、把秒杀「Redis 预扣+发消息」升级为 RocketMQ 事务消息保证原子性。

**Architecture:** 本计划四个阶段相互独立、可分别交付：
1. **脏数据清洗**：一次性 SQL 修正 orders 表（`pay_status=1 AND status=6` 且无支付单佐证的订单改为未支付）。纯运维，不动代码。
2. **核心链路测试**：新增 OrderService/SeckillService 的 JUnit5 + Mockito 单测，覆盖 CAS 关单、秒杀幂等、MQ 失败回滚三个已修复缺陷。纯测试代码。
3. **JMeter 压测**：新增秒杀/订单接口压测脚本 + 基准报告，验证不超卖与容量。纯测试资产。
4. **事务消息升级**：把秒杀「Redis 预扣库存 → 发 MQ」改为 RocketMQ 事务消息，解决「扣了库存但消息没发出」的原子性问题。改代码。

**Tech Stack:** Spring Boot 3.5 / JDK21 / MyBatis-Plus / dynamic-datasource / RocketMQ 5.3.0（原生 client）/ Redisson / JUnit5 / Mockito / AssertJ / JMeter 5.x / Python 3.14

**Spec:** 本计划依据《上线前测试 · 完整报告》中的「遗留问题」四项，以及业界方案（Redis Lua 原子预扣 + RocketMQ 事务消息 + 幂等保障 + 超时回补）。

## Global Constraints

- 后端根目录：`jiwei-life/jiwei-modules`；框架模块：`jiwei-life/jiwei-framework`；实体模块：`jiwei-life/jiwei-pojo`
- Java 编译目标 JDK 21；测试用 JUnit5（spring-boot-starter-test 已含，无需新增依赖）
- 数据库共 6 库，订单在 `jiwei_trade.orders`，秒杀活动在 `jiwei_market.seckill_activity`，支付单在 `jiwei_trade.pay_order`
- MySQL 容器：`docker exec jiwei-mysql mysql -uroot -proot123`
- Redis 容器：`docker exec jiwei-redis redis-cli`
- 改 common/pojo/framework 后必须 `mvn install` 再 `spring-boot:run`，否则 ClassNotFoundException
- 秒杀订单状态常量：`Orders.PENDING_PAYMENT=1`、`CANCELLED=6`（表 `status` 1待支付 2待接单 3备货中 4待取餐 5已完成 6已取消 7已退款）
- 秒杀 Redis 键：`seckill:stock:{activityId}:{dishId}`（String）、`seckill:users:{activityId}`（Hash）
- 秒杀主题：`seckill-order-topic`；RocketMQ 用传统 rocketmq-client（remoting），`apache/rocketmq:5.3.0` 容器支持事务消息

---

## Phase A：清洗脏订单

**背景**：`jiwei_trade.orders` 有 995 条 `pay_status=1 AND status=6` 的脏订单，全部**无 `pay_order` 支付单佐证**（`has_pay_order=0`），order 号 SV 开头、`pay_time` 为 mock 随机值。这些是早期测试污染——订单标记「已支付」但从未真实支付、且已取消。需把 `pay_status` 从 1 纠正为 0（未支付），保持 `status=6`（已取消）不变，使「已支付」与「已取消」不再矛盾。

### Task A1：审计脏订单构成

**Files:**
- Create: 无（只读查询）

- [ ] **Step 1: 运行审计 SQL，确认脏订单构成与本次清洗影响面**

Run:
```bash
docker exec jiwei-mysql mysql -uroot -proot123 -e "
SELECT
  CASE WHEN pay_time IS NULL THEN 'pay_time_NULL' ELSE 'pay_time_SET' END AS flag,
  COUNT(*) cnt
FROM jiwei_trade.orders
WHERE pay_status=1 AND status=6
GROUP BY flag;
SELECT COUNT(*) orders_with_payorder
FROM jiwei_trade.orders o JOIN jiwei_trade.pay_order p ON p.out_order_no=o.number
WHERE o.pay_status=1;
"
```
Expected: `pay_time_NULL` + `pay_time_SET` 合计 995；`orders_with_payorder` = 0（确认无真实支付单佐证）。

- [ ] **Step 2: 记录基线，供清洗后对比**

Run:
```bash
docker exec jiwei-mysql mysql -uroot -proot123 -e "SELECT COUNT(*) total, SUM(status=6) cancelled, SUM(pay_status=1) paid FROM jiwei_trade.orders;"
```
Expected: total=4039, cancelled=1356, paid=3338（作为清洗前基线）。

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "docs: pre-launch plan phase A baseline"
```

### Task A2：执行清洗

**Files:**
- Modify: 无（一次性 SQL）

- [ ] **Step 1: 备份待清洗订单，再执行纠正**

Run:
```bash
# 备份（记录受影响行）
docker exec jiwei-mysql mysql -uroot -proot123 -e "
SELECT COUNT(*) FROM jiwei_trade.orders
WHERE pay_status=1 AND status=6
  AND NOT EXISTS (SELECT 1 FROM jiwei_trade.pay_order p WHERE p.out_order_no=orders.number);
" > /tmp/dirty_before.txt
cat /tmp/dirty_before.txt
```
Expected: 995

- [ ] **Step 2: 执行清洗 UPDATE（带 NOT EXISTS 保护，避免误伤有支付单的真实订单）**

Run:
```bash
docker exec jiwei-mysql mysql -uroot -proot123 -e "
UPDATE jiwei_trade.orders o
SET o.pay_status = 0, o.update_time = NOW()
WHERE o.pay_status = 1 AND o.status = 6
  AND NOT EXISTS (SELECT 1 FROM jiwei_trade.pay_order p WHERE p.out_order_no = o.number);
"
```
Expected: `Rows matched: 995  Changed: 995`

- [ ] **Step 3: 验证清洗结果**

Run:
```bash
docker exec jiwei-mysql mysql -uroot -proot123 -e "
SELECT COUNT(*) remaining_dirty FROM jiwei_trade.orders WHERE pay_status=1 AND status=6;
SELECT COUNT(*) total, SUM(status=6) cancelled, SUM(pay_status=1) paid FROM jiwei_trade.orders;
"
```
Expected: `remaining_dirty=0`；`paid` 从 3338 减到 2343（3338-995）；`cancelled=1356` 不变。

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "fix(data): correct 995 dirty orders pay_status from paid to unpaid"
```

**协商点**：若你希望保留这 995 条作为「已支付」统计（例如对账需要），改用「改 status 而非 pay_status」。默认方案是改 pay_status（更符合「未支付+已取消」语义）。

---

## Phase B：核心链路自动化测试

**背景**：项目仅 8 个单测（AI/熔断），核心交易链路 0 测试。需为已修复的三个缺陷补回归测试，防止复发。测试风格沿用现有 `RedisCircuitBreakerTest`（JUnit5 + AssertJ + Mockito，不启动 Spring 上下文，直接 new service + mock 依赖）。

**测试目标**：
- B1: `OrderServiceImpl.handleTimeoutOrder` 用 CAS 关单，已支付订单不会被取消（回归 CRITICAL-2）
- B2: `SeckillOrderCreateConsumer` 重复投递不重复扣库存（回归 CRITICAL-1）
- B3: `SeckillServiceImpl.seckillBuy` MQ 发送失败回滚 Redis 库存（回归 HIGH）

### Task B1：测试 handleTimeoutOrder 的 CAS 语义

**Files:**
- Create: `jiwei-modules/src/test/java/com/jiwei/trade/service/impl/OrderServiceImplTest.java`

**Interfaces:**
- Consumes: `OrderMapper.cancelPendingIfUnpaid(Long orderId) -> int`（返回 0 表示状态已变更）
- Consumes: `SeckillService.restoreSeckillOnTimeout(Long activityId, Long userId)`、`CouponService.release(Long userCouponId)`
- Produces: 一个测试类，验证 CAS 语义

- [ ] **Step 1: 写失败测试（已支付订单不会被取消）**

```java
package com.jiwei.trade.service.impl;

import com.jiwei.market.service.SeckillService;
import com.jiwei.market.service.CouponService;
import com.jiwei.pojo.entity.Orders;
import com.jiwei.trade.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OrderServiceImplTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final SeckillService seckillService = mock(SeckillService.class);
    private final CouponService couponService = mock(CouponService.class);

    private OrderServiceImpl newService() {
        // OrderServiceImpl 字段全是 @Autowired，这里用反射注入 mock 过于繁琐；
        // 因此把断言逻辑抽成包内可见方法，或用构造器。见 Step 3 说明。
        return null; // placeholder replaced in step 3
    }
}
```

- [ ] **Step 2: 运行确认失败（编译失败即失败信号）**

Run: `cd jiwei-life && mvn test -pl jiwei-modules -Dtest=OrderServiceImplTest`
Expected: 编译错误（service 字段私有、无构造器，无法直接构造）——这正是需要引入可测试性改造的信号。

- [ ] **Step 3: 给 OrderServiceImpl 加包内可见构造器（最小可测试性改造）**

Modify: `jiwei-modules/src/main/java/com/jiwei/trade/service/impl/OrderServiceImpl.java`
在类上新增构造器（保留 @Autowired 字段注入不变，测试用构造器）：
```java
// 测试用构造器：避免反射注入私有字段
OrderServiceImpl(OrderMapper orderMapper, SeckillService seckillService,
                 CouponService couponService, OrderMapper orderDetailMapper) {
    // 仅为测试可构造性，业务路径仍走 Spring 字段注入
}
```
> 若改造复杂，替代方案：测试里用 `org.springframework.test.util.ReflectionTestUtils.setField` 注入私有字段（spring-test 已随 starter-test 提供），避免改业务代码。**优先用 ReflectionTestUtils**，不动业务构造器。

- [ ] **Step 4: 用 ReflectionTestUtils 写真实测试**

```java
package com.jiwei.trade.service.impl;

import com.jiwei.market.service.CouponService;
import com.jiwei.market.service.SeckillService;
import com.jiwei.pojo.entity.Orders;
import com.jiwei.trade.mapper.OrderMapper;
import com.jiwei.trade.mq.OrderMessageProducer;
import com.jiwei.trade.pay.service.PayOrderService;
import com.jiwei.trade.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderServiceImplTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final SeckillService seckillService = mock(SeckillService.class);
    private final CouponService couponService = mock(CouponService.class);

    private OrderServiceImpl newService() {
        OrderServiceImpl svc = new OrderServiceImpl();
        ReflectionTestUtils.setField(svc, "orderMapper", orderMapper);
        ReflectionTestUtils.setField(svc, "seckillService", seckillService);
        ReflectionTestUtils.setField(svc, "couponService", couponService);
        return svc;
    }

    @Test
    void timeoutShouldNotCancelAlreadyPaidOrder() {
        // CAS 返回 0 = 状态已变更（已支付），应跳过取消和回补
        when(orderMapper.cancelPendingIfUnpaid(any())).thenReturn(0);
        OrderServiceImpl svc = newService();

        svc.handleTimeoutOrder(100L);

        verify(orderMapper).cancelPendingIfUnpaid(100L);
        verify(seckillService, never()).restoreSeckillOnTimeout(any(), any());
        verify(couponService, never()).release(any());
    }

    @Test
    void timeoutShouldCancelAndRestockWhenStillPending() {
        // CAS 返回 1 = 成功取消，需回补秒杀库存
        Orders order = new Orders();
        order.setId(100L);
        order.setIsSeckill(1);
        order.setSeckillActivityId(5L);
        order.setUserId(404L);
        when(orderMapper.cancelPendingIfUnpaid(100L)).thenReturn(1);
        when(orderMapper.selectById(100L)).thenReturn(order);
        OrderServiceImpl svc = newService();

        svc.handleTimeoutOrder(100L);

        verify(seckillService).restoreSeckillOnTimeout(5L, 404L);
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cd jiwei-life && mvn test -pl jiwei-modules -Dtest=OrderServiceImplTest`
Expected: 2 tests passed, 0 failures

- [ ] **Step 6: Commit**

```bash
git add jiwei-modules/src/test/java/com/jiwei/trade/service/impl/OrderServiceImplTest.java
git commit -m "test: add CAS timeout-order regression tests"
```

### Task B2：测试秒杀消费者重复投递不重复扣库存

**Files:**
- Create: `jiwei-modules/src/test/java/com/jiwei/market/seckill/mq/SeckillOrderCreateConsumerTest.java`

**Interfaces:**
- Consumes: `OrderService.seckillOrderExists(String orderNo) -> boolean`
- Consumes: `SeckillService.deductStock(Long, int) -> boolean`、`SeckillService.revertRedisStock(...)`
- Consumes: `OrderService.createSeckillOrder(SeckillMessage) -> Long`

- [ ] **Step 1: 写失败测试（重复投递时不再扣库存）**

```java
package com.jiwei.market.seckill.mq;

import com.alibaba.fastjson2.JSON;
import com.jiwei.market.service.SeckillService;
import com.jiwei.trade.service.OrderService;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class SeckillOrderCreateConsumerTest {

    private final SeckillService seckillService = mock(SeckillService.class);
    private final OrderService orderService = mock(OrderService.class);
    private SeckillOrderCreateConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new SeckillOrderCreateConsumer(seckillService, orderService);
        // consumer 无 @PostConstruct 时 start() 不会被测，需手动 mock 消费者启动
        ReflectionTestUtils.setField(consumer, "nameServer", "localhost:9876");
        ReflectionTestUtils.setField(consumer, "consumerGroup", "test-group");
    }

    @Test
    void duplicateDeliveryShouldSkipStockDeduction() throws Exception {
        SeckillMessage msg = new SeckillMessage("order-no-1", 404L, 5L, 5L, 1, new java.math.BigDecimal("9.90"));
        Message mqMsg = new Message("seckill-order-topic",
                JSON.toJSONString(msg).getBytes(StandardCharsets.UTF_8));

        // 幂等：订单已存在 → 直接跳过，不扣库存
        when(orderService.seckillOrderExists("order-no-1")).thenReturn(true);

        // 直接调用监听器方法（避免启动真实 MQ）
        // consumer 的内部 listener 是私有 lambda，需通过反射获取，
        // 或用公共方法抽取。见 Step 3。
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl jiwei-modules -Dtest=SeckillOrderCreateConsumerTest`
Expected: 编译失败（listener 私有不可测）——需要小改造。

- [ ] **Step 3: 抽取消费者核心逻辑为包内可见方法（可测试性改造）**

Modify: `jiwei-modules/src/main/java/com/jiwei/market/seckill/mq/SeckillOrderCreateConsumer.java`
把消息处理循环体抽成包内可见方法，供测试直接调用：
```java
// 包内可见：供单测直接调用，生产仍由 MQ listener 驱动
long handleMessage(SeckillMessage message) {
    if (orderService.seckillOrderExists(message.orderNo())) {
        log.info("重复投递已处理，跳过: orderNo={}", message.orderNo());
        return 0L;
    }
    boolean deducted = seckillService.deductStock(message.activityId(), message.quantity());
    if (!deducted) {
        seckillService.revertRedisStock(message.activityId(), message.dishId(),
                message.userId(), message.quantity());
        return -1L;
    }
    try {
        orderService.createSeckillOrder(message);
        return 1L;
    } catch (com.jiwei.common.exception.OrderBusinessException e) {
        seckillService.restoreStock(message.activityId(), message.quantity());
        seckillService.revertRedisStock(message.activityId(), message.dishId(),
                message.userId(), message.quantity());
        return -2L;
    } catch (Exception e) {
        seckillService.restoreStock(message.activityId(), message.quantity());
        seckillService.revertRedisStock(message.activityId(), message.dishId(),
                message.userId(), message.quantity());
        throw e;
    }
}
```
> 注意：抽取时保持与现有 listener 循环逻辑**完全等价**（幂等跳过、扣库、建单、两种失败回补）。重构后 listener 循环改为 `for (MessageExt msg : msgs) { ... handleMessage(message); ... }` 并保留 `RECONSUME_LATER` 重试逻辑。

- [ ] **Step 4: 用反射获取 listener 或直接调 handleMessage 补全测试**

```java
@Test
void duplicateDeliveryShouldSkipStockDeduction() {
    SeckillMessage msg = new SeckillMessage("order-no-1", 404L, 5L, 5L, 1, new java.math.BigDecimal("9.90"));
    when(orderService.seckillOrderExists("order-no-1")).thenReturn(true);

    long result = consumer.handleMessage(msg);

    assertThat(result).isEqualTo(0L);
    verify(seckillService, never()).deductStock(any(), anyInt());
    verify(orderService, never()).createSeckillOrder(any());
}

@Test
void freshDeliveryDeductsThenCreatesOrder() {
    SeckillMessage msg = new SeckillMessage("order-no-2", 404L, 5L, 5L, 1, new java.math.BigDecimal("9.90"));
    when(orderService.seckillOrderExists("order-no-2")).thenReturn(false);
    when(seckillService.deductStock(5L, 1)).thenReturn(true);
    when(orderService.createSeckillOrder(msg)).thenReturn(1L);

    long result = consumer.handleMessage(msg);

    assertThat(result).isEqualTo(1L);
    verify(seckillService).deductStock(5L, 1);
    verify(orderService).createSeckillOrder(msg);
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn test -pl jiwei-modules -Dtest=SeckillOrderCreateConsumerTest`
Expected: 3 tests passed（跳过、成功建单 + 库存不足），0 failures

- [ ] **Step 6: Commit**

```bash
git add jiwei-modules/src/main/java/com/jiwei/market/seckill/mq/SeckillOrderCreateConsumer.java \
        jiwei-modules/src/test/java/com/jiwei/market/seckill/mq/SeckillOrderCreateConsumerTest.java
git commit -m "test+refactor: expose seckill consumer handler for idempotency tests"
```

### Task B3：测试秒杀 MQ 发送失败回滚 Redis

**Files:**
- Create: `jiwei-modules/src/test/java/com/jiwei/market/service/impl/SeckillServiceImplTest.java`

**Interfaces:**
- Consumes: `OrderMessageProducer.sendSeckillOrder(SeckillMessage) throws Exception`（失败抛异常）
- Consumes: `SeckillServiceImpl.revertRedisStock(Long, Long, Long, int)`（包内私有 → 通过 spy 验证）

- [ ] **Step 1: 写失败测试（MQ 发送失败时回滚 Redis 库存）**

```java
package com.jiwei.market.service.impl;

import com.jiwei.common.context.BaseContext;
import com.jiwei.common.exception.OrderBusinessException;
import com.jiwei.market.dto.SeckillBuyDTO;
import com.jiwei.market.mapper.SeckillActivityMapper;
import com.jiwei.pojo.entity.SeckillActivity;
import com.jiwei.trade.mq.OrderMessageProducer;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class SeckillServiceImplTest {

    @Test
    void mqSendFailureShouldRollbackRedisStock() throws Exception {
        SeckillServiceImpl svc = new SeckillServiceImpl();
        SeckillActivityMapper activityMapper = mock(SeckillActivityMapper.class);
        StringRedisTemplate stringRedis = mock(StringRedisTemplate.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        OrderMessageProducer producer = mock(OrderMessageProducer.class);

        ReflectionTestUtils.setField(svc, "seckillActivityMapper", activityMapper);
        ReflectionTestUtils.setField(svc, "stringRedisTemplate", stringRedis);
        ReflectionTestUtils.setField(svc, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(svc, "orderMessageProducer", producer);

        // 用 spy 拦截 revertRedisStock 断言被调用
        SeckillServiceImpl spy = spy(svc);

        SeckillActivity act = new SeckillActivity();
        act.setId(5L);
        act.setDishId(5L);
        act.setStartTime(LocalDateTime.now().minusHours(1));
        act.setEndTime(LocalDateTime.now().plusHours(1));
        act.setLimitPerUser(1);
        act.setSeckillPrice(new BigDecimal("9.90"));
        when(activityMapper.selectById(5L)).thenReturn(act);

        BaseContext.setCurrentId(404L);
        SeckillBuyDTO dto = new SeckillBuyDTO();
        dto.setActivityId(5L);
        dto.setDishId(5L);

        doThrow(new RuntimeException("mq down")).when(producer).sendSeckillOrder(any());

        assertThatThrownBy(() -> spy.seckillBuy(dto))
                .isInstanceOf(OrderBusinessException.class);

        // 关键断言：MQ 失败后回滚了 Redis 库存 + 限购计数
        verify(spy).revertRedisStock(eq(5L), eq(5L), eq(404L), eq(1));
        BaseContext.removeCurrentId();
    }
}
```

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -pl jiwei-modules -Dtest=SeckillServiceImplTest`
Expected: 失败——`seckillBuy` 若未正确处理 MQ 异常，`revertRedisStock` 不会被调用。

> 注：当前代码已修过该缺陷，测试应通过。此步验证回归测试有效。

- [ ] **Step 3: 运行确认通过**

Run: `mvn test -pl jiwei-modules -Dtest=SeckillServiceImplTest`
Expected: 1 test passed

- [ ] **Step 4: Commit**

```bash
git add jiwei-modules/src/test/java/com/jiwei/market/service/impl/SeckillServiceImplTest.java
git commit -m "test: add seckill MQ-failure rollback regression test"
```

### Task B4：全量测试回归

- [ ] **Step 1: 运行全部模块测试**

Run: `cd jiwei-life && mvn test -pl jiwei-common,jiwei-pojo,jiwei-framework,jiwei-modules -am`
Expected: 原 8 个测试 + 新增测试全部通过

- [ ] **Step 2: 确认测试不会因缺少 Docker 中间件而失败（单测不连真实 Redis/MQ）**

Run: `mvn test -pl jiwei-modules -Dtest='*Test' 2>&1 | tail -20`
Expected: 单测用 mock，无 Spring 上下文，不依赖中间件

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "test: full regression pass for core transaction chain"
```

---

## Phase C：JMeter 压测基准

**背景**：项目无任何压测资产。需建立 JMeter 压测脚本，覆盖秒杀（并发正确性）与订单提交流程，给出基准报告。

**前提**：JMeter 未安装（已确认）。需下载安装 Apache JMeter 5.x（依赖 JDK 21，本机已有）。

### Task C1：安装 JMeter

- [ ] **Step 1: 下载并解压 JMeter**

Run:
```bash
# 下载 5.6.3（需外网）
curl -L -o /tmp/jmeter.tgz https://dlcdn.apache.org//jmeter/binaries/apache-jmeter-5.6.3.tgz
tar -xzf /tmp/jmeter.tgz -C /d/software/
```
Expected: `/d/software/apache-jmeter-5.6.3/bin/jmeter` 存在

> 若下载失败（外网受限），改用本机已有的压测工具（如 Python 脚本并发，上一轮已用 40 并发验证过正确性），或让用户手动安装后继续。

- [ ] **Step 2: 验证 JMeter 可运行**

Run: `/d/software/apache-jmeter-5.6.3/bin/jmeter --version`
Expected: 显示版本信息

### Task C2：创建秒杀压测脚本

**Files:**
- Create: `jiwei-modules/src/test/jmeter/seckill-load.jmx`

- [ ] **Step 1: 编写 .jmx 秒杀压测计划**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jmeterTestPlan version="1.2" properties="5.0" jmeter="5.6.3">
  <hashTree>
    <TestPlan guiclass="TestPlanGui" testclass="TestPlan" testname="Seckill Load Test">
      <elementProp name="TestPlan.user_defined_variables" elementType="Arguments" guiclass="ArgumentsPanel" testclass="Arguments"/>
    </TestPlan>
    <hashTree>
      <ThreadGroup guiclass="ThreadGroupGui" testclass="ThreadGroup" testname="Seckill 100 Users">
        <stringProp name="ThreadGroup.num_threads">100</stringProp>
        <stringProp name="ThreadGroup.ramp_time">2</stringProp>
        <stringProp name="ThreadGroup.duration">10</stringProp>
        <boolProp name="ThreadGroup.scheduler">true</boolProp>
        <stringProp name="ThreadGroup.on_sample_error">continue</stringProp>
      </ThreadGroup>
      <hashTree>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="Mock Login">
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8080</stringProp>
          <stringProp name="HTTPSampler.path">/user/user/mock-login</stringProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <stringProp name="HTTPSampler.arguments">
            <collectionProp name="HTTPsampler.Arguments">
              <elementProp name="openid" elementType="HTTPArgument">
                <stringProp name="Argument.name">openid</stringProp>
                <stringProp name="Argument.value">jmeter_${__time(1000)}</stringProp>
              </elementProp>
            </collectionProp>
          </stringProp>
        </HTTPSamplerProxy>
        <hashTree/>
        <HTTPSamplerProxy guiclass="HttpTestSampleGui" testclass="HTTPSamplerProxy" testname="Seckill Buy">
          <stringProp name="HTTPSampler.domain">localhost</stringProp>
          <stringProp name="HTTPSampler.port">8080</stringProp>
          <stringProp name="HTTPSampler.path">/user/seckill/5/buy</stringProp>
          <stringProp name="HTTPSampler.method">POST</stringProp>
          <stringProp name="HTTPSampler.postBodyRaw">{"dishId":5}</stringProp>
          <stringProp name="HTTPSampler.header_manager">
            <collectionProp name="HeaderManager.headers">
              <elementProp name="Authorization" elementType="Header">
                <stringProp name="Header.name">Authorization</stringProp>
                <stringProp name="Header.value">${__property(token_${__threadNum})}</stringProp>
              </elementProp>
            </collectionProp>
          </stringProp>
        </HTTPSamplerProxy>
        <hashTree/>
      </hashTree>
    </hashTree>
  </hashTree>
</jmeterTestPlan>
```
> 说明：登录响应提取 token 需加 JSON Extractor（PostProcessor），将 `data.token` 存入属性。此 .jmx 是骨架，执行时需补 JSON Extractor 与 token 注入，或改用「预处理 Python 生成 token 文件 + CSV 参数化」。**推荐 CSV 参数化**（更可靠）：先用脚本批量生成 token 写入 `tokens.csv`，jmx 用 CSV Data Set Config 读取。

- [ ] **Step 2: 预生成 100 个测试 token**

Create: `jiwei-modules/src/test/jmeter/gen_tokens.sh`
```bash
#!/bin/bash
# 生成 N 个压测用户的登录 token 写入 tokens.csv
: > tokens.csv
for i in $(seq 1 100); do
  openid="jm_${i}_$RANDOM"
  token=$(curl -s -X POST "http://localhost:8080/user/user/mock-login?openid=$openid" \
    | grep -o '"token":"[^"]*' | cut -d\" -f4)
  echo "$token" >> tokens.csv
done
echo "generated $(wc -l < tokens.csv) tokens"
```

- [ ] **Step 3: 运行秒杀压测（100 并发，活动库存先重置）**

Run:
```bash
# 重置活动5库存到 50
docker exec jiwei-mysql mysql -uroot -proot123 -e "UPDATE jiwei_market.seckill_activity SET stock=50, sold=0 WHERE id=5;"
docker exec jiwei-redis redis-cli set seckill:stock:5:5 50
docker exec jiwei-redis redis-cli del seckill:users:5
# 跑压测
/d/software/apache-jmeter-5.6.3/bin/jmeter -n -t jiwei-modules/src/test/jmeter/seckill-load.jmx -l /tmp/seckill.jtl -e -o /tmp/seckill-report
```
Expected: 生成报告 `/tmp/seckill-report/index.html`

- [ ] **Step 4: 验证不超卖（压测后三方一致）**

Run:
```bash
docker exec jiwei-redis redis-cli get seckill:stock:5:5
docker exec jiwei-mysql mysql -uroot -proot123 -e "SELECT stock, sold FROM jiwei_market.seckill_activity WHERE id=5;"
docker exec jiwei-mysql mysql -uroot -proot123 -e "SELECT COUNT(*) FROM jiwei_trade.orders WHERE seckill_activity_id=5 AND is_seckill=1;"
```
Expected: Redis库存 + 订单数 = 初始 50；DB sold = 订单数；**无超卖**（订单数 ≤ 50）

- [ ] **Step 5: 记录基准数据到报告**

Create: `jiwei-modules/src/test/jmeter/BENCHMARK.md`
记录：并发数、成功数、失败原因分布、TPS、p95 延迟、三方一致性结果。

- [ ] **Step 6: Commit**

```bash
git add jiwei-modules/src/test/jmeter/
git commit -m "test: add jmeter seckill load test and benchmark"
```

---

## Phase D：秒杀升级为 RocketMQ 事务消息

**背景**：当前 `seckillBuy` 先 Redis Lua 扣库存，再 `sendSeckillOrder` 发普通消息。若 MQ 发送失败（客户端超时），库存已扣但消息没发出——上一轮用「catch 回滚」补偿，但存在「消息实际发出、客户端以为失败而回滚」的少卖窗口。升级为 **RocketMQ 事务消息**：本地事务执行「校验+扣库存」，事务回调决定消息是否提交，从根上保证「库存扣减」与「消息发送」原子。

**原理**：
- Producer 用 `TransactionMQProducer` + `TransactionListener`
- `sendMessageInTransaction` 同步执行本地事务（执行 Lua 扣库存 + 生成 orderNo），返回 `LocalTransactionState.COMMIT/ROLLBACK`
- Broker 事务回查（checkLocalTransaction）时用 Redis 里是否存在「预扣记录」判断
- 回滚分支：本地事务返回 ROLLBACK，或回查确认未扣 → 消息不投递

**改动清单**：
- `RocketMQConfiguration`：新增 `TransactionMQProducer` Bean
- `OrderMessageProducer`：新增 `sendSeckillOrderTransactional(SeckillMessage, TransactionListener)` 
- `SeckillServiceImpl`：改为「本地事务扣库存 → sendMessageInTransaction」；回滚时同步回滚 Redis
- 新增 `SeckillTransactionListener`：`executeLocalTransaction`（返回扣减结果）、`checkLocalTransaction`（回查 Redis 预扣标记）

### Task D1：RocketMQ 配置加 TransactionMQProducer

**Files:**
- Modify: `jiwei-framework/src/main/java/com/jiwei/framework/config/RocketMQConfiguration.java`

- [ ] **Step 1: 新增 TransactionMQProducer Bean**

```java
@Bean(initMethod = "start", destroyMethod = "shutdown")
public TransactionMQProducer rocketMQTransactionProducer() throws MQClientException {
    TransactionMQProducer producer = new TransactionMQProducer(
            producerGroup + "-tx");
    producer.setNamesrvAddr(nameServer);
    producer.setSendMsgTimeout(5000);
    log.info("RocketMQ TransactionProducer 初始化成功, NameServer: {}", nameServer);
    return producer;
}
```
> 事务消息 producer 与普通 producer 必须用**不同 group**，避免消息路由冲突。

- [ ] **Step 2: 编译验证**

Run: `cd jiwei-life && mvn compile -pl jiwei-framework -am -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add jiwei-framework/src/main/java/com/jiwei/framework/config/RocketMQConfiguration.java
git commit -m "feat(mq): add TransactionMQProducer bean"
```

### Task D2：新增秒杀事务消息监听器

**Files:**
- Create: `jiwei-modules/src/main/java/com/jiwei/market/seckill/mq/SeckillTransactionListener.java`

**Interfaces:**
- Consumes: `SeckillService.seckillBuyTx(...)`（下一任务新增的本地事务方法）
- Produces: `LocalTransactionState`（COMMIT/ROLLBACK）

- [ ] **Step 1: 编写 TransactionListener**

```java
package com.jiwei.market.seckill.mq;

import com.alibaba.fastjson2.JSON;
import com.jiwei.market.service.SeckillService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.stereotype.Component;

/**
 * 秒杀事务消息监听器：
 * - executeLocalTransaction：本地事务（Redis Lua 扣库存），返回 COMMIT 则消息投递、ROLLBACK 则丢弃
 * - checkLocalTransaction：broker 回查，用 Redis 预扣标记判断
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SeckillTransactionListener implements TransactionListener {

    private final SeckillService seckillService;

    @Override
    public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            SeckillMessage message = JSON.parseObject(
                    new String(msg.getBody(), StandardCharsets.UTF_8), SeckillMessage.class);
            // 本地事务：预扣 Redis 库存 + 限购计数，写预扣标记
            boolean ok = seckillService.preDeductSeckillStock(message);
            return ok ? LocalTransactionState.COMMIT_MESSAGE : LocalTransactionState.ROLLBACK_MESSAGE;
        } catch (Exception e) {
            log.error("秒杀本地事务执行异常: {}", e.getMessage(), e);
            return LocalTransactionState.ROLLBACK_MESSAGE;
        }
    }

    @Override
    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
        try {
            SeckillMessage message = JSON.parseObject(
                    new String(msg.getBody(), StandardCharsets.UTF_8), SeckillMessage.class);
            // 回查：Redis 预扣标记仍存在 → 已扣，提交；否则回滚
            boolean exists = seckillService.isPreDeducted(message.orderNo());
            return exists ? LocalTransactionState.COMMIT_MESSAGE : LocalTransactionState.ROLLBACK_MESSAGE;
        } catch (Exception e) {
            log.error("秒杀事务回查异常: {}", e.getMessage(), e);
            return LocalTransactionState.UNKNOW;
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn compile -pl jiwei-modules -am -DskipTests`
Expected: BUILD SUCCESS（`preDeductSeckillStock` / `isPreDeducted` 尚未实现，编译会报错——这是预期信号，进入 D3 补齐）

- [ ] **Step 3: Commit（先提交 listener 骨架，方法待 D3 实现）**

> 注：若编译必须全绿才提交，则先跳到 D3 实现方法，再回来提交。**建议把 D2 和 D3 合并为一个 task 完成再提交。**

### Task D3：SeckillService 增加事务扣减方法

**Files:**
- Modify: `jiwei-modules/src/main/java/com/jiwei/market/service/SeckillService.java`
- Modify: `jiwei-modules/src/main/java/com/jiwei/market/service/impl/SeckillServiceImpl.java`

**Interfaces:**
- Produces: `boolean preDeductSeckillStock(SeckillMessage)`——Redis Lua 预扣库存+限购+写预扣标记
- Produces: `boolean isPreDeducted(String orderNo)`——判断预扣标记
- Produces: `void rollbackPreDeduct(SeckillMessage)`——回滚预扣（回补 Redis 库存+限购+删标记）
- Consumes: `SeckillMessage`（record：orderNo, userId, activityId, dishId, quantity, payAmount）

**架构要点**：Lua 扣库存逻辑**统一收口到 `preDeductSeckillStock`**（本地事务执行体）。`seckillBuy` 不再直接跑 Lua，只做「时间校验 → 生成 orderNo → sendMessageInTransaction」。`SeckillTransactionListener.executeLocalTransaction` 调用 `preDeductSeckillStock`，其返回值决定 COMMIT/ROLLBACK。这样库存扣减只有一处，不会出现「seckillBuy 扣一次、listener 再扣一次」的双扣。

- [ ] **Step 1: 接口加方法**

```java
// SeckillService.java 新增
boolean preDeductSeckillStock(SeckillMessage message);
boolean isPreDeducted(String orderNo);
void rollbackPreDeduct(SeckillMessage message);
```

- [ ] **Step 2: 抽取私有 Lua 执行方法（从现有 seckillBuy 中提取，保持脚本完全一致）**

```java
// SeckillServiceImpl.java
private static final String PRE_DEDUCT_KEY = "seckill:prededuct:";
private static final String SECKILL_ORDER_TOPIC = "seckill-order-topic";

/**
 * 执行秒杀 Lua：校验库存 + 限购 + 扣减。返回 true=成功。
 * 从原 seckillBuy 内联逻辑提取，脚本 SECKILL_LUA_SCRIPT 保持不变。
 */
private boolean executeSeckillLua(Long activityId, Long dishId, Long userId, int limitPerUser) {
    DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
    redisScript.setScriptText(SECKILL_LUA_SCRIPT);
    redisScript.setResultType(Long.class);
    String stockKey = "seckill:stock:" + activityId + ":" + dishId;
    String userKey = "seckill:users:" + activityId;
    Long result = stringRedisTemplate.execute(
            redisScript,
            Arrays.asList(stockKey, userKey),
            userId.toString(),
            String.valueOf(limitPerUser));
    return result != null && result == 1;
}
```

- [ ] **Step 3: 实现三个事务方法**

```java
// SeckillServiceImpl.java
@Override
public boolean preDeductSeckillStock(SeckillMessage message) {
    SeckillActivity activity = seckillActivityMapper.selectById(message.activityId());
    if (activity == null) {
        return false;
    }
    boolean ok = executeSeckillLua(message.activityId(), message.dishId(),
            message.userId(), activity.getLimitPerUser());
    if (ok) {
        // 预扣标记：broker 回查用，TTL 覆盖活动剩余时间（下限 30 分钟）
        String key = PRE_DEDUCT_KEY + message.orderNo();
        long ttlSeconds = Math.max(30 * 60L,
                java.time.Duration.between(java.time.LocalDateTime.now(), activity.getEndTime()).getSeconds());
        try {
            stringRedisTemplate.opsForValue().set(key, "1", java.time.Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            // 标记写失败：回补已扣库存，返回 false 触发消息回滚，避免"扣了库存、无标记、无人补"
            revertRedisStock(message.activityId(), message.dishId(),
                    message.userId(), message.quantity());
            log.error("秒杀预扣标记写失败，已回滚库存: orderNo={}", message.orderNo(), e);
            return false;
        }
    }
    return ok;
}

@Override
public boolean isPreDeducted(String orderNo) {
    return Boolean.TRUE.equals(
            stringRedisTemplate.hasKey(PRE_DEDUCT_KEY + orderNo));
}

@Override
public void rollbackPreDeduct(SeckillMessage message) {
    revertRedisStock(message.activityId(), message.dishId(),
            message.userId(), message.quantity());
    stringRedisTemplate.delete(PRE_DEDUCT_KEY + message.orderNo());
}
```

- [ ] **Step 4: 改造 seckillBuy 使用事务消息（移除原内联 Lua + 普通 send）**

```java
// seckillBuy 重构为：
@Override
public Long seckillBuy(SeckillBuyDTO dto) {
    Long userId = BaseContext.getCurrentId();
    Long activityId = dto.getActivityId();

    //1、查询活动
    SeckillActivity activity = seckillActivityMapper.selectById(activityId);
    if (activity == null) {
        throw new OrderBusinessException("秒杀活动不存在");
    }
    //2、服务端二次校验时间窗口
    LocalDateTime now = LocalDateTime.now();
    if (now.isBefore(activity.getStartTime())) {
        throw new OrderBusinessException(MessageConstant.SECKILL_NOT_STARTED);
    }
    if (now.isAfter(activity.getEndTime())) {
        throw new OrderBusinessException(MessageConstant.SECKILL_ENDED);
    }

    //3、生成订单号，发事务消息：本地事务(Redis扣库存) 与 消息发送 原子化
    String orderNo = IdUtil.getSnowflakeNextIdStr();
    SeckillMessage message = new SeckillMessage(
            orderNo, userId, activityId, dto.getDishId(), 1, activity.getSeckillPrice());

    // 事务消息的本地事务由 SeckillTransactionListener.executeLocalTransaction 执行
    TransactionSendResult sendResult = transactionProducer.sendMessageInTransaction(
            new Message(SECKILL_ORDER_TOPIC,
                    com.alibaba.fastjson2.JSON.toJSONString(message).getBytes(StandardCharsets.UTF_8)),
            message);

    //4、本地事务回滚（库存不足/限购）→ 提示失败；否则返回预占单号
    if (sendResult.getLocalTransactionState() == LocalTransactionState.ROLLBACK_MESSAGE) {
        throw new OrderBusinessException(MessageConstant.SECKILL_STOCK_OUT);
    }
    log.info("秒杀事务消息已发送，userId: {}, activityId: {}, orderNo: {}", userId, activityId, orderNo);
    return Long.valueOf(orderNo);
}
```

> 需要新增注入：`@Autowired private TransactionMQProducer transactionProducer;`（来自 D1 的 `rocketMQTransactionProducer` Bean）。`SeckillServiceImpl` 里原有的 `OrderMessageProducer orderMessageProducer` 若不再用于秒杀（普通订单延迟消息仍用它），保留。原 `sendSeckillOrder` 从 `OrderMessageProducer` 移除或保留给其他调用方（检查是否有其它调用点）。

- [ ] **Step 5: 编译验证**

Run: `mvn compile -pl jiwei-modules -am -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 6: 更新 B3 测试（SeckillServiceImplTest 适配事务消息）**

Modify: `jiwei-modules/src/test/java/com/jiwei/market/service/impl/SeckillServiceImplTest.java`
原「`sendSeckillOrder` 失败回滚」测试改为：mock `transactionProducer.sendMessageInTransaction` 返回 `ROLLBACK_MESSAGE`，断言 `seckillBuy` 抛 `OrderBusinessException`，并 `verify(spy).rollbackPreDeduct(message)`。

```java
@Test
void transactionRollbackShouldThrowAndCallRollbackPreDeduct() throws Exception {
    // 组装 svc + mock 字段（activityMapper/stringRedisTemplate/redisTemplate/transactionProducer）
    SeckillServiceImpl spy = spy(svc);
    SeckillActivity act = new SeckillActivity();
    act.setId(5L); act.setDishId(5L);
    act.setStartTime(LocalDateTime.now().minusHours(1));
    act.setEndTime(LocalDateTime.now().plusHours(1));
    act.setLimitPerUser(1); act.setSeckillPrice(new BigDecimal("9.90"));
    when(activityMapper.selectById(5L)).thenReturn(act);

    TransactionSendResult rollbackResult = new TransactionSendResult();
    rollbackResult.setLocalTransactionState(LocalTransactionState.ROLLBACK_MESSAGE);
    when(transactionProducer.sendMessageInTransaction(any(), any()))
            .thenReturn(rollbackResult);

    BaseContext.setCurrentId(404L);
    SeckillBuyDTO dto = new SeckillBuyDTO();
    dto.setActivityId(5L); dto.setDishId(5L);

    assertThatThrownBy(() -> spy.seckillBuy(dto))
            .isInstanceOf(OrderBusinessException.class);

    // 事务回滚时 listener 会调用 preDeductSeckillStock→false 或 rollbackPreDeduct；
    // 断言至少回滚了预扣标记（此处验证 spy 方法被调用）
    verify(spy).preDeductSeckillStock(any());
    BaseContext.removeCurrentId();
}
```

> 说明：`sendMessageInTransaction` 的本地事务实际在 listener 内执行，单测中 listener 不会触发；因此此测试主要验证「ROLLBACK 状态 → 抛异常」的分支，以及 `preDeductSeckillStock` 作为本地事务入口被正确接线。若 `TransactionSendResult` 无 setter，改用 Mockito `deepStub` 或构造 `TransactionSendResult` 时用反射设私有字段。

- [ ] **Step 7: 运行测试确认通过**

Run: `mvn test -pl jiwei-modules -Dtest='Seckill*Test,OrderServiceImplTest,SeckillOrderCreateConsumerTest'`
Expected: 全部通过

- [ ] **Step 8: Commit**

```bash
git add jiwei-modules/src/main/java/com/jiwei/market/service/SeckillService.java \
        jiwei-modules/src/main/java/com/jiwei/market/service/impl/SeckillServiceImpl.java \
        jiwei-modules/src/main/java/com/jiwei/market/seckill/mq/SeckillTransactionListener.java \
        jiwei-modules/src/test/java/com/jiwei/market/service/impl/SeckillServiceImplTest.java
git commit -m "feat(seckill): transactional message for atomic stock-deduct + order-create"
```

### Task D4：事务消息链路验证

- [ ] **Step 1: install 并重启后端**

Run:
```bash
cd jiwei-life && mvn install -pl jiwei-common,jiwei-pojo,jiwei-framework,jiwei-modules -am -DskipTests
# 杀 8080 旧进程后重启
cd jiwei-modules && mvn spring-boot:run
```
Expected: 启动无报错，日志出现 TransactionProducer 初始化成功

- [ ] **Step 2: 功能验证——正常秒杀**

Run: 用 mock-login 拿 token，调 `/user/seckill/{id}/buy` 一次
Expected: 返回 code=1 + orderNo；DB 出现秒杀订单；库存正确扣减

- [ ] **Step 3: 功能验证——模拟 MQ 故障（停 broker）**

Run:
```bash
docker stop jiwei-rmq-broker
# 调秒杀接口
docker start jiwei-rmq-broker
```
Expected: 秒杀接口返回「系统繁忙」类失败；**Redis 库存已回滚**（不流失）；broker 恢复后无残留半消息

- [ ] **Step 4: 运行 B2/B3 全量回归**

Run: `mvn test -pl jiwei-modules -Dtest='*Test'`
Expected: 全部通过

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "test: verify seckill transactional message flow"
```

---

## 自审记录（执行者忽略）

- **覆盖度**：Phase A 覆盖 995 脏订单；Phase B 覆盖 3 个已修复缺陷回归；Phase C 覆盖秒杀并发正确性与基准；Phase D 覆盖事务消息原子性。
- **占位符**：D2 的 listener 引用了 D3 才实现的方法——已在任务内注明合并提交顺序，避免执行者中途卡在编译。
- **类型一致**：`preDeductSeckillStock` / `isPreDeducted` / `rollbackPreDeduct` 三个方法名在 D2/D3 一致；`handleMessage` 抽取保持与现有 listener 等价。
