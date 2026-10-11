# 交易域（trade）实现计划：支付中台三重幂等 + 多实例 WebSocket

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用支付中台的「渠道策略+工厂 → 幂等三重 → 验签 → 查单补偿」替换 JiweiLife 交易域里 `OrderServiceImpl.pay()`/`refund()` 的「直接标已支付/已退款」浅实现；同时把单机 `@ServerEndpoint` WebSocket 升级为「RocketMQ 广播消费 + 本机过滤 + Redis 会话注册」的多实例定向推送。

**Architecture:** 引入独立支付单 `pay_order`（`out_order_no` 关联 `orders.number`），支付能力从订单解耦。渠道差异收敛在 `IPayChannelHandler` 实现内（`balance` 余额 / `mock` 开发 / `wechat` V3 骨架）；幂等靠三重防线——CAS `updateOrderPaid`（`status='0'` 前置条件）+ 唯一键 `uk_type_biz`（流水防重）+ 条件扣减 `deductBalance`（`balance >= amount`）。支付成功入账后发 RocketMQ 消息，由订单消费者回写 `orders.pay_status`，再由广播消费者推送到各实例的商家 WebSocket 连接。

**Tech Stack:** JDK 21、Spring Boot 3.x、MyBatis-Plus + dynamic-datasource（`@DS("trade")`）、`rocketmq-client`（原生 `DefaultMQProducer`/`DefaultMQPushConsumer`，非 spring-boot-starter）、Redisson、Hutool `IdUtil` 雪花 ID、Spring WebSocket（`TextWebSocketHandler`）。

**Spec:** `docs/superpowers/specs/2026-08-26-wheel-integration-design.md`（§5.2 交易域）

**源项目参考：**
- 支付中台：`D:\qiuzhao\payment-processing-platform\ruoyi-pay\src\main\java\com\ruoyi\pay\`
- 异步任务：`D:\qiuzhao\asynchronous-update\web-app\src\main\java\com\niit\mqws\`

## Global Constraints

- 包名统一 `com.jiwei.trade`（沿用现有 trade 包结构）；支付中台子包 `com.jiwei.trade.pay.*`；实体放 `com.jiwei.pojo.entity`
- RocketMQ 用原生 `rocketmq-client`（框架已提供 `DefaultMQProducer` bean，见 `RocketMQConfiguration`），**不**引入 `rocketmq-spring-boot-starter`
- 单号用 `IdUtil.getSnowflakeNextIdStr()`，**不要**照搬轮子的 `Math.random()` 拼接（`generateOrderNo`/`generateTransNo`）
- 金额一律 `BigDecimal`（`DECIMAL(10,2)`），扣款与流水同事务
- 微信 V3 只留骨架：RSA 验签（`Wechatpay-Signature` 头 + 平台证书），**不**照搬轮子陈旧的 V2 MD5 验签
- 幂等三重防线必须落地为 SQL 层保证（CAS 前置条件 / 唯一索引 / 条件扣减），不能只在 Java 层 `if` 判断
- 每个 Task 完成后 `git add` 具体文件并 commit，禁止 `git add -A`

---

## File Structure 概览

**新建（实体，jiwei-pojo）：**
- `jiwei-pojo/.../pojo/entity/PayChannel.java` — 支付渠道配置
- `jiwei-pojo/.../pojo/entity/PayOrder.java` — 支付单（幂等 CAS 载体）
- `jiwei-pojo/.../pojo/entity/PayNotifyLog.java` — 回调留痕
- `jiwei-pojo/.../pojo/entity/PayAccount.java` — 余额账户
- `jiwei-pojo/.../pojo/entity/PayAccountTransaction.java` — 余额流水

**新建（支付中台，jiwei-modules/trade）：**
- `com/jiwei/trade/pay/core/IPayChannelHandler.java` — 渠道策略接口
- `com/jiwei/trade/pay/core/PayChannelFactory.java` — 渠道工厂（构造器自动注册）
- `com/jiwei/trade/pay/core/model/PayResult.java`、`PayNotifyResult.java`、`RefundResult.java` — 统一返回模型
- `com/jiwei/trade/pay/channel/BalancePayChannelHandler.java`、`MockChannelHandler.java`、`WechatChannelHandler.java` — 3 个渠道实现
- `com/jiwei/trade/pay/mapper/PayOrderMapper.java`、`PayAccountMapper.java`、`PayAccountTransactionMapper.java`、`PayNotifyLogMapper.java`、`PayChannelMapper.java`
- `com/jiwei/trade/pay/service/PayOrderService.java` + `impl`、`PayAccountService.java` + `impl`
- `com/jiwei/trade/pay/controller/PayChannelNotifyController.java` — 渠道回调入口（`/api/notify/pay/{channelCode}`）

**新建（多实例 WebSocket）：**
- `com/jiwei/trade/websocket/RedisSessionRegistry.java`
- `com/jiwei/trade/websocket/NotifyWebSocketHandler.java`
- `com/jiwei/trade/websocket/WebSocketConfig.java`（注册 handler，替换 `@ServerEndpoint`）
- `com/jiwei/trade/websocket/NotifyMessage.java`
- `com/jiwei/trade/mq/NotifyMessageProducer.java`、`NotifyMessageConsumer.java`（RocketMQ 广播）

**修改：**
- `jiwei-life/db/04_trade.sql` — 加 5 张支付表
- `jiwei-modules/.../trade/service/impl/OrderServiceImpl.java` — `pay()`/`refund()` 接入渠道
- `jiwei-modules/.../trade/controller/PayNotifyController.java` — 委托 `PayOrderService`，或由新 `PayChannelNotifyController` 替代
- `jiwei-modules/.../trade/websocket/WebSocketServer.java` — 删除静态管理（被 `NotifyWebSocketHandler` 替换）
- `jiwei-framework/src/main/java/com/jiwei/framework/config/WebSocketConfiguration.java` — 删除 `ServerEndpointExporter` bean

**测试：**
- `jiwei-modules/src/test/java/com/jiwei/trade/pay/PayAccountServiceTest.java` — 余额扣减幂等/超扣测试

---

## Task 1: 支付域实体 + 表结构（幂等三重防线落 SQL）

**Files:**
- Modify: `jiwei-life/db/04_trade.sql`
- Create: 5 个实体（`PayChannel`/`PayOrder`/`PayNotifyLog`/`PayAccount`/`PayAccountTransaction`）

**Interfaces:**
- Consumes: 无（第一个任务）
- Produces: 5 张表 + 5 个 MyBatis-Plus 实体，供后续 Mapper/Service 使用

**步骤：**

- [ ] **Step 1: 04_trade.sql 追加 5 张支付表**

在 `04_trade.sql` 末尾追加（借鉴 `ruoyi-pay/sql/pay.sql` + `pay_balance.sql`，字段精简为 JiweiLife 所需）：

```sql
-- ============ 支付中台 ============

-- 支付渠道配置表
CREATE TABLE pay_channel (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_code VARCHAR(32) NOT NULL UNIQUE COMMENT '渠道编码 balance/mock/wechat',
    channel_name VARCHAR(64) NOT NULL COMMENT '渠道名称',
    status INT DEFAULT 0 COMMENT '状态 0启用 1停用',
    config TEXT COMMENT '渠道配置JSON(密钥/网关等)',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
) COMMENT '支付渠道配置表';

-- 支付单表（幂等 CAS 载体）
CREATE TABLE pay_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE COMMENT '支付单号',
    out_order_no VARCHAR(64) NOT NULL COMMENT '业务订单号(orders.number)',
    user_id BIGINT NOT NULL COMMENT '下单用户ID(余额扣款用)',
    channel_code VARCHAR(32) NOT NULL COMMENT '渠道编码',
    total_amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    status INT DEFAULT 0 COMMENT '状态 0待支付 1已支付 2已关闭',
    trade_no VARCHAR(64) COMMENT '渠道交易号',
    buyer_id VARCHAR(64) COMMENT '买家渠道账号',
    pay_time DATETIME COMMENT '支付完成时间',
    pay_params TEXT COMMENT '下单返回的支付参数',
    notify_count INT DEFAULT 0 COMMENT '回调通知次数',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL,
    INDEX idx_out_order_no (out_order_no),
    INDEX idx_status (status)
) COMMENT '支付单表';

-- 支付回调留痕表
CREATE TABLE pay_notify_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    channel_code VARCHAR(32) NOT NULL,
    order_no VARCHAR(64),
    notify_type INT DEFAULT 1 COMMENT '1支付 2退款',
    content TEXT COMMENT '原始通知内容',
    verify_status INT DEFAULT 0 COMMENT '0未验 1成功 2失败',
    process_status INT DEFAULT 0 COMMENT '0未处理 1成功 2失败',
    process_msg VARCHAR(512) COMMENT '处理结果说明',
    create_time DATETIME NOT NULL
) COMMENT '支付回调留痕表';

-- 余额账户表
CREATE TABLE pay_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE COMMENT '用户ID',
    balance DECIMAL(10,2) DEFAULT 0 NOT NULL COMMENT '余额',
    status INT DEFAULT 0 COMMENT '状态 0正常 1冻结',
    create_time DATETIME NOT NULL,
    update_time DATETIME NOT NULL
) COMMENT '余额账户表';

-- 余额流水表（uk_type_biz 唯一键防重）
CREATE TABLE pay_account_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trans_no VARCHAR(64) NOT NULL UNIQUE COMMENT '流水号',
    user_id BIGINT NOT NULL,
    trans_type INT NOT NULL COMMENT '1消费 2退款 3调整',
    amount DECIMAL(10,2) NOT NULL COMMENT '变动金额(正加负减)',
    balance_after DECIMAL(10,2) NOT NULL COMMENT '变动后余额',
    biz_no VARCHAR(64) NOT NULL COMMENT '业务单号(订单号/退款号)',
    remark VARCHAR(256),
    create_time DATETIME NOT NULL,
    UNIQUE KEY uk_type_biz (trans_type, biz_no),
    INDEX idx_user_id (user_id)
) COMMENT '余额流水表';
```

- [ ] **Step 2: 创建 5 个实体**

按 JiweiLife 现有实体风格（`@Data @Builder @NoArgsConstructor @AllArgsConstructor @TableName`），创建到 `jiwei-pojo/.../pojo/entity/`。字段与 Step 1 表结构一一对应，`@TableId(type = IdType.AUTO)`，`createTime`/`updateTime` 用 `@TableField(fill = FieldFill.INSERT / INSERT_UPDATE)`。

注意：`PayOrder` 实体需含 `private Long userId;`（对应 `pay_order.user_id` 列，余额渠道下单时 `BalancePayChannelHandler` 依赖它扣款，见 Task 2）。

关键字段说明（供后续 Mapper 引用）：

```java
// PayOrder 状态常量（对齐轮子 PayConstants）
public static final int STATUS_WAIT = 0;      // 待支付
public static final int STATUS_SUCCESS = 1;   // 已支付
public static final int STATUS_CLOSED = 2;    // 已关闭

// PayAccountTransaction 交易类型常量
public static final int TRANS_TYPE_CONSUME = 1; // 消费（余额支付）
public static final int TRANS_TYPE_REFUND = 2;  // 退款退回
public static final int TRANS_TYPE_ADJUST = 3;  // 后台调整
```

- [ ] **Step 3: 编译验证**

Run: `cd jiwei-life && mvn compile -pl jiwei-pojo -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add jiwei-life/db/04_trade.sql jiwei-pojo/src/main/java/com/jiwei/pojo/entity/Pay*.java
git commit -m "feat(trade): 新增支付中台 5 张表与实体（幂等三重防线落库）"
```

---

## Task 2: 支付渠道抽象（策略 + 工厂）

**Files:**
- Create: `com/jiwei/trade/pay/core/IPayChannelHandler.java`
- Create: `com/jiwei/trade/pay/core/PayChannelFactory.java`
- Create: `com/jiwei/trade/pay/core/model/PayResult.java`、`PayNotifyResult.java`、`RefundResult.java`
- Create: `com/jiwei/trade/pay/channel/BalancePayChannelHandler.java`、`MockChannelHandler.java`、`WechatChannelHandler.java`

**Interfaces:**
- Consumes: `PayOrder`/`PayChannel` 实体（Task 1）
- Produces: `PayChannelFactory.getHandler(String) -> IPayChannelHandler`

**步骤：**

- [ ] **Step 1: 创建 IPayChannelHandler（策略接口）**

从源 `D:\qiuzhao\payment-processing-platform\ruoyi-pay\src\main\java\com\ruoyi\pay\core\IPayChannelHandler.java` 移植，包名改 `com.jiwei.trade.pay.core`，去掉 `isRawBodyNotify`/`parseRawNotify`（JiweiLife 只有微信 V3 JSON 回调，直接用现有 `WeChatPayUtil` 解析，无需 XML 报文通道）：

```java
package com.jiwei.trade.pay.core;

import com.jiwei.pojo.entity.PayChannel;
import com.jiwei.pojo.entity.PayOrder;
import com.jiwei.trade.pay.core.model.PayNotifyResult;
import com.jiwei.trade.pay.core.model.PayResult;
import com.jiwei.trade.pay.core.model.RefundResult;

import java.util.Map;

public interface IPayChannelHandler {
    String getChannelCode();
    PayResult unifiedOrder(PayOrder order, PayChannel channel);       // 统一下单
    PayResult queryOrder(PayOrder order, PayChannel channel);         // 主动查单
    PayNotifyResult parseNotify(Map<String, String> params, PayChannel channel); // 解析+验签通知
    boolean closeOrder(PayOrder order, PayChannel channel);           // 关闭订单
    RefundResult refund(String refundNo, String refundAmount, String reason,
                        PayOrder order, PayChannel channel);          // 退款
    String notifySuccessBody();                                        // 渠道要求的成功应答
    String notifyFailBody();                                           // 失败应答
}
```

- [ ] **Step 2: 创建 PayChannelFactory（构造器自动注册）**

从源 `PayChannelFactory.java` 移植，包名/异常改 JiweiLife 风格（`OrderBusinessException` 替代 ruoyi `ServiceException`）：

```java
package com.jiwei.trade.pay.core;

import com.jiwei.common.exception.OrderBusinessException;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PayChannelFactory {
    private final Map<String, IPayChannelHandler> handlerMap = new ConcurrentHashMap<>();

    public PayChannelFactory(List<IPayChannelHandler> handlers) {
        for (IPayChannelHandler h : handlers) {
            handlerMap.put(h.getChannelCode(), h);
        }
    }

    public IPayChannelHandler getHandler(String channelCode) {
        IPayChannelHandler h = handlerMap.get(channelCode);
        if (h == null) {
            throw new OrderBusinessException("不支持的支付渠道：" + channelCode);
        }
        return h;
    }

    public boolean support(String channelCode) {
        return handlerMap.containsKey(channelCode);
    }
}
```

- [ ] **Step 3: 创建 3 个返回模型（record）**

```java
// PayResult.java
public record PayResult(boolean success, boolean paid, String tradeNo,
                        String buyerId, String payParams, String message) {
    public static PayResult ok(boolean paid, String tradeNo, String payParams) {
        return new PayResult(true, paid, tradeNo, null, payParams, null);
    }
    public static PayResult fail(String message) {
        return new PayResult(false, false, null, null, null, message);
    }
}

// PayNotifyResult.java
public record PayNotifyResult(boolean verifySuccess, boolean tradeSuccess,
                              String orderNo, String tradeNo, String failMsg,
                              java.math.BigDecimal payAmount) {}

// RefundResult.java
public record RefundResult(boolean success, String message) {}
```

- [ ] **Step 4: 实现 3 个渠道 handler**

**`MockChannelHandler`（开发闭环，下单即支付成功）**——channelCode=`mock`：

```java
@Component
public class MockChannelHandler implements IPayChannelHandler {
    @Override public String getChannelCode() { return "mock"; }
    @Override public PayResult unifiedOrder(PayOrder order, PayChannel channel) {
        return PayResult.ok(true, "MOCK_" + IdUtil.getSnowflakeNextIdStr(), null); // 同步支付成功
    }
    @Override public PayResult queryOrder(PayOrder order, PayChannel channel) {
        return PayResult.ok(true, order.getTradeNo(), null);
    }
    @Override public PayNotifyResult parseNotify(Map<String, String> params, PayChannel channel) {
        throw new UnsupportedOperationException("mock 渠道无异步通知");
    }
    @Override public boolean closeOrder(PayOrder order, PayChannel channel) { return true; }
    @Override public RefundResult refund(String refundNo, String refundAmount, String reason,
                                         PayOrder order, PayChannel channel) {
        return new RefundResult(true, null);
    }
    @Override public String notifySuccessBody() { return "SUCCESS"; }
    @Override public String notifyFailBody() { return "FAIL"; }
}
```

**`BalancePayChannelHandler`（余额，下单即扣款入账）**——channelCode=`balance`：

```java
@Component
public class BalancePayChannelHandler implements IPayChannelHandler {
    private final PayAccountService payAccountService; // Task 3 提供，先以接口占位

    public BalancePayChannelHandler(PayAccountService payAccountService) {
        this.payAccountService = payAccountService;
    }

    @Override public String getChannelCode() { return "balance"; }
    @Override public PayResult unifiedOrder(PayOrder order, PayChannel channel) {
        // 余额同步扣款：consume 内部条件扣减 + 写流水（幂等）
        payAccountService.consume(order.getUserId(), order.getTotalAmount(),
                order.getOrderNo(), "user");
        return PayResult.ok(true, "BALANCE_" + IdUtil.getSnowflakeNextIdStr(), null);
    }
    // queryOrder/parseNotify/closeOrder/refund/notifySuccessBody/notifyFailBody 类似 mock，
    // refund 调用 payAccountService.refundToAccount(...)
}
```

> 注意：`PayOrder` 需补 `userId` 字段（下单用户，余额扣款用）。在 Task 1 实体里补上 `private Long userId;`，并在 `04_trade.sql` 的 `pay_order` 表加 `user_id BIGINT NOT NULL` 列。

**`WechatChannelHandler`（V3 骨架）**——channelCode=`wechat`：

```java
@Component
public class WechatChannelHandler implements IPayChannelHandler {
    @Override public String getChannelCode() { return "wechat"; }
    @Override public PayResult unifiedOrder(PayOrder order, PayChannel channel) {
        // TODO 骨架：构建 JSAPI 下单请求（appid/mchid/amount/notify_url），
        // 调用微信 V3 API 获取 prepay_id。当前返回 fail 提示未配置
        return PayResult.fail("微信支付未配置（骨架预留）");
    }
    @Override public PayResult queryOrder(PayOrder order, PayChannel channel) {
        return PayResult.fail("微信支付未配置（骨架预留）");
    }
    @Override public PayNotifyResult parseNotify(Map<String, String> params, PayChannel channel) {
        // TODO 骨架：RSA 验签（Wechatpay-Signature 头 + 平台证书）+ AES-GCM 解密
        throw new UnsupportedOperationException("微信 V3 验签骨架，待接入");
    }
    // closeOrder/refund/notifySuccessBody/notifyFailBody：骨架返回
    @Override public String notifySuccessBody() { return "{\"code\":\"SUCCESS\",\"message\":\"成功\"}"; }
    @Override public String notifyFailBody() { return "{\"code\":\"FAIL\",\"message\":\"失败\"}"; }
}
```

- [ ] **Step 5: 编译验证（PayAccountService 先建空接口占位）**

先建 `com/jiwei/trade/pay/service/PayAccountService.java` 空接口让编译通过（Task 3 补实现）：

```java
public interface PayAccountService {
    void consume(Long userId, java.math.BigDecimal amount, String orderNo, String operator);
    void refundToAccount(Long userId, java.math.BigDecimal amount, String refundNo);
}
```

Run: `cd jiwei-life && mvn compile -pl jiwei-pojo,jiwei-common,jiwei-framework,jiwei-modules -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add jiwei-modules/src/main/java/com/jiwei/trade/pay/
git commit -m "feat(trade): 引入支付渠道策略+工厂（balance/mock/wechat 骨架）"
```

---

## Task 3: 支付编排 + 余额账户服务（幂等入账 + 条件扣减）

**Files:**
- Create: `com/jiwei/trade/pay/mapper/PayOrderMapper.java`、`PayAccountMapper.java`、`PayAccountTransactionMapper.java`、`PayNotifyLogMapper.java`、`PayChannelMapper.java`
- Create: `com/jiwei/trade/pay/service/impl/PayOrderServiceImpl.java`
- Create: `com/jiwei/trade/pay/service/impl/PayAccountServiceImpl.java`

**Interfaces:**
- Consumes: `PayChannelFactory`（Task 2）、5 个实体（Task 1）
- Produces: `PayOrderService.handleNotify(String, Map) -> String`、`PayAccountService.consume(...)`

**步骤：**

- [ ] **Step 1: 创建 5 个 Mapper（MyBatis-Plus BaseMapper + 自定义幂等 SQL）**

其余 4 个用 `BaseMapper<T>` 即可；`PayOrderMapper` 和 `PayAccountMapper` 额外加自定义幂等 SQL（用 `@Update` 注解，等价轮子 XML 的 `updateOrderPaid`/`deductBalance`）：

```java
package com.jiwei.trade.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.PayOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PayOrderMapper extends BaseMapper<PayOrder> {

    /** CAS 入账：status='0' 前置条件保证并发/重复回调只生效一次 */
    @Update("UPDATE pay_order SET status = 1, trade_no = #{tradeNo}, " +
            "buyer_id = #{buyerId}, pay_time = NOW(), update_time = NOW() " +
            "WHERE order_no = #{orderNo} AND status = 0")
    int updateOrderPaid(@Param("orderNo") String orderNo,
                        @Param("tradeNo") String tradeNo,
                        @Param("buyerId") String buyerId);

    /** 关闭订单：仅待支付可关（CAS） */
    @Update("UPDATE pay_order SET status = 2, update_time = NOW() " +
            "WHERE order_no = #{orderNo} AND status = 0")
    int updateOrderClosed(@Param("orderNo") String orderNo);

    /** 回调计数 +1 */
    @Update("UPDATE pay_order SET notify_count = notify_count + 1 WHERE order_no = #{orderNo}")
    int increaseNotifyCount(@Param("orderNo") String orderNo);
}
```

```java
package com.jiwei.trade.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jiwei.pojo.entity.PayAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface PayAccountMapper extends BaseMapper<PayAccount> {

    /** 条件扣减：balance >= amount 防止超扣，status=0 仅正常账户 */
    @Update("UPDATE pay_account SET balance = balance - #{amount}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND status = 0 AND balance >= #{amount}")
    int deductBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

    /** 加余额 */
    @Update("UPDATE pay_account SET balance = balance + #{amount}, update_time = NOW() " +
            "WHERE user_id = #{userId} AND status = 0")
    int addBalance(@Param("userId") Long userId, @Param("amount") BigDecimal amount);
}
```

`PayAccountTransactionMapper` 需要 `selectByTypeAndBizNo`（流水幂等查重）：

```java
@Mapper
public interface PayAccountTransactionMapper extends BaseMapper<PayAccountTransaction> {
    default PayAccountTransaction selectByTypeAndBizNo(int transType, String bizNo) {
        return selectOne(new LambdaQueryWrapper<PayAccountTransaction>()
                .eq(PayAccountTransaction::getTransType, transType)
                .eq(PayAccountTransaction::getBizNo, bizNo));
    }
}
```

- [ ] **Step 2: 创建 PayAccountServiceImpl（条件扣减 + 流水同事务幂等）**

从源 `PayAccountServiceImpl.java` 移植核心方法，`@DS("trade")`，异常改 `OrderBusinessException`，单号改雪花：

```java
package com.jiwei.trade.pay.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.jiwei.common.exception.OrderBusinessException;
import com.jiwei.pojo.entity.PayAccount;
import com.jiwei.pojo.entity.PayAccountTransaction;
import com.jiwei.trade.pay.mapper.PayAccountMapper;
import com.jiwei.trade.pay.mapper.PayAccountTransactionMapper;
import com.jiwei.trade.pay.service.PayAccountService;
import cn.hutool.core.util.IdUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@DS("trade")
@Service
public class PayAccountServiceImpl implements PayAccountService {
    private final PayAccountMapper accountMapper;
    private final PayAccountTransactionMapper transactionMapper;

    // 构造器注入...

    @Override
    @Transactional
    public void consume(Long userId, BigDecimal amount, String orderNo, String operator) {
        PayAccount account = getOrCreateAccount(userId);
        if (account.getStatus() != 0) {
            throw new OrderBusinessException("账户已冻结，无法使用余额支付");
        }
        int updated = accountMapper.deductBalance(userId, amount);
        if (updated == 0) {
            throw new OrderBusinessException("账户余额不足");
        }
        try {
            insertTransaction(userId, PayAccountTransaction.TRANS_TYPE_CONSUME,
                    amount.negate(), orderNo, "余额支付订单 [" + orderNo + "]", operator);
        } catch (DuplicateKeyException e) {
            throw new OrderBusinessException("订单 [" + orderNo + "] 已扣款，请勿重复支付");
        }
    }

    @Override
    @Transactional
    public void refundToAccount(Long userId, BigDecimal amount, String refundNo) {
        // 幂等：同退款号已存在直接返回
        PayAccountTransaction exists = transactionMapper.selectByTypeAndBizNo(
                PayAccountTransaction.TRANS_TYPE_REFUND, refundNo);
        if (exists != null) return;
        accountMapper.addBalance(userId, amount);
        try {
            insertTransaction(userId, PayAccountTransaction.TRANS_TYPE_REFUND,
                    amount, refundNo, "退款单 [" + refundNo + "] 退回余额", "system");
        } catch (DuplicateKeyException ignored) {
            // 并发重复退款，唯一键兜底，静默返回
        }
    }

    private PayAccount getOrCreateAccount(Long userId) {
        PayAccount account = accountMapper.selectOne(
                new LambdaQueryWrapper<PayAccount>().eq(PayAccount::getUserId, userId));
        if (account != null) return account;
        try {
            PayAccount insert = PayAccount.builder()
                    .userId(userId).balance(BigDecimal.ZERO).status(0).build();
            accountMapper.insert(insert);
            return accountMapper.selectOne(
                    new LambdaQueryWrapper<PayAccount>().eq(PayAccount::getUserId, userId));
        } catch (DuplicateKeyException e) {
            return accountMapper.selectOne(
                    new LambdaQueryWrapper<PayAccount>().eq(PayAccount::getUserId, userId));
        }
    }

    private void insertTransaction(Long userId, int transType, BigDecimal amount,
                                   String bizNo, String remark, String operator) {
        PayAccount account = accountMapper.selectOne(
                new LambdaQueryWrapper<PayAccount>().eq(PayAccount::getUserId, userId));
        PayAccountTransaction t = PayAccountTransaction.builder()
                .transNo("B" + IdUtil.getSnowflakeNextIdStr())
                .userId(userId).transType(transType).amount(amount)
                .balanceAfter(account.getBalance()).bizNo(bizNo)
                .remark(remark).build();
        transactionMapper.insert(t);
    }
}
```

- [ ] **Step 3: 创建 PayOrderServiceImpl（验签 + 金额校验 + 幂等入账 + finally 留痕 + 查单）**

从源 `PayOrderServiceImpl.java` 移植 `handleNotify`/`syncPayOrder`/`markOrderPaid`，`@DS("trade")`：

```java
@DS("trade")
@Service
public class PayOrderServiceImpl implements PayOrderService {
    // 注入 PayOrderMapper / PayNotifyLogMapper / PayChannelMapper / PayChannelFactory
    // 以及 OrderService（入账后回写业务订单，见 Task 4）

    @Override
    @Transactional
    public String handleNotify(String channelCode, Map<String, String> params) {
        IPayChannelHandler handler = payChannelFactory.getHandler(channelCode);
        PayNotifyLog log = new PayNotifyLog();
        log.setChannelCode(channelCode);
        log.setContent(JSON.toJSONString(params));
        try {
            PayChannel channel = payChannelMapper.selectOne(
                    new LambdaQueryWrapper<PayChannel>().eq(PayChannel::getChannelCode, channelCode));
            if (channel == null) {
                log.setVerifyStatus(2); log.setProcessStatus(2);
                log.setProcessMsg("渠道配置不存在");
                return handler.notifyFailBody();
            }
            PayNotifyResult result = handler.parseNotify(params, channel);
            log.setOrderNo(result.orderNo());
            payOrderMapper.increaseNotifyCount(result.orderNo());
            if (!result.verifySuccess()) {
                log.setVerifyStatus(2); log.setProcessStatus(2);
                log.setProcessMsg(result.failMsg());
                return handler.notifyFailBody();
            }
            log.setVerifyStatus(1);
            if (!result.tradeSuccess()) {
                log.setProcessStatus(1); log.setProcessMsg("交易未成功，忽略入账");
                return handler.notifySuccessBody();
            }
            PayOrder order = payOrderMapper.selectOne(
                    new LambdaQueryWrapper<PayOrder>().eq(PayOrder::getOrderNo, result.orderNo()));
            if (order == null) {
                log.setProcessStatus(2); log.setProcessMsg("订单不存在");
                return handler.notifyFailBody();
            }
            // 金额校验（防篡改）
            if (result.payAmount() != null &&
                    order.getTotalAmount().compareTo(result.payAmount()) != 0) {
                log.setProcessStatus(2); log.setProcessMsg("金额不一致，已拒绝入账");
                return handler.notifyFailBody();
            }
            int updated = markOrderPaid(order, result.tradeNo(), result.buyerId());
            log.setProcessStatus(1);
            log.setProcessMsg(updated > 0 ? "入账成功" : "订单已是支付成功状态（重复通知，幂等返回）");
            return handler.notifySuccessBody();
        } finally {
            payNotifyLogMapper.insert(log);
        }
    }

    private int markOrderPaid(PayOrder order, String tradeNo, String buyerId) {
        int updated = payOrderMapper.updateOrderPaid(order.getOrderNo(), tradeNo, buyerId);
        if (updated > 0) {
            // 入账成功：发 RocketMQ 消息，由订单消费者回写 orders.pay_status + 推送商家
            orderPaidProducer.send(order.getOutOrderNo());
        }
        return updated;
    }

    // syncPayOrder（主动查单补偿）：渠道 queryOrder 后若 paid 则 markOrderPaid
}
```

> `OrderPaidProducer` 是 Task 5 的消息生产者占位，本 Task 先建最小接口或直接注入 `DefaultMQProducer` 发 topic `order-paid-topic`。

- [ ] **Step 4: 编译验证**

Run: `cd jiwei-life && mvn compile -pl jiwei-pojo,jiwei-common,jiwei-framework,jiwei-modules -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add jiwei-modules/src/main/java/com/jiwei/trade/pay/
git commit -m "feat(trade): 支付编排服务（验签+金额校验+幂等入账+留痕）与余额账户条件扣减"
```

---

## Task 4: 改造 OrderServiceImpl 接入支付渠道

**Files:**
- Modify: `jiwei-modules/.../trade/service/impl/OrderServiceImpl.java`
- Create: `com/jiwei/trade/pay/controller/PayChannelNotifyController.java`（或改造现有 `PayNotifyController`）
- Create: `com/jiwei/trade/mq/OrderPaidConsumer.java`（消费支付成功消息，回写业务订单）

**Interfaces:**
- Consumes: `PayOrderService.createPayOrder(...)`（Task 3）
- Produces: `OrderService.pay(orderId, userId, channelCode)` 支持多渠道

**步骤：**

- [ ] **Step 1: 改写 OrderService.pay 签名（增加渠道参数）**

`OrderService.pay(Long orderId, Long userId)` → `OrderService.pay(Long orderId, Long userId, String channelCode)`。更新接口与所有调用方（`UserOrderController`）。

- [ ] **Step 2: 改写 OrderServiceImpl.pay（替换「直接标已支付」）**

```java
@Override
@Transactional
public void pay(Long orderId, Long userId, String channelCode) {
    Orders order = orderMapper.selectById(orderId);
    if (order == null || !order.getUserId().equals(userId)) {
        throw new OrderBusinessException("订单不存在");
    }
    if (!order.getStatus().equals(Orders.PENDING_PAYMENT)) {
        throw new OrderBusinessException("订单状态异常");
    }
    // 委托支付中台：创建支付单 → 渠道下单
    payOrderService.createPayOrder(order.getNumber(), channelCode,
            order.getPayAmount(), userId);
    // 余额/mock 渠道在下单时同步入账，状态由 OrderPaidConsumer 回写；
    // 微信渠道此处返回支付参数给前端调起，等待异步回调。
}
```

- [ ] **Step 3: 在 PayOrderService 补充 createPayOrder（统一下单）**

签名：`PayResult createPayOrder(String outOrderNo, String channelCode, BigDecimal totalAmount, Long userId)`（`outOrderNo`=业务订单号 `orders.number`，`userId`=下单用户，余额渠道扣款用）。

从源 `PayOrderServiceImpl.createPayOrder` 移植：生成支付单号（`P` + 雪花）→ `payOrderMapper.insert`（含 `user_id`）→ `payChannelFactory.getHandler(channelCode).unifiedOrder` → 若 `result.isPaid()` 直接 `markOrderPaid`。

- [ ] **Step 4: 创建 OrderPaidConsumer（消费 order-paid-topic，回写 orders.pay_status）**

用 JiweiLife 的 `DefaultMQPushConsumer` 风格（对齐 `EmbeddingConsumer`）：

```java
@Component
@Slf4j
public class OrderPaidConsumer {
    private static final String TOPIC = "order-paid-topic";
    @Value("${rocketmq.name-server:localhost:9876}") private String nameServer;
    @Value("${rocketmq.consumer.group:order-paid-consumer}") private String consumerGroup;
    @Autowired private OrderMapper orderMapper;

    private DefaultMQPushConsumer consumer;

    @PostConstruct
    public void start() throws MQClientException {
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.subscribe(TOPIC, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
            for (MessageExt msg : msgs) {
                String orderNo = new String(msg.getBody(), StandardCharsets.UTF_8);
                try {
                    Orders order = orderMapper.selectOne(new LambdaQueryWrapper<Orders>()
                            .eq(Orders::getNumber, orderNo));
                    if (order != null && order.getPayStatus().equals(Orders.UN_PAID)) {
                        order.setPayStatus(Orders.PAID);
                        order.setStatus(Orders.TO_BE_CONFIRMED);
                        order.setPayTime(LocalDateTime.now());
                        orderMapper.updateById(order);
                    }
                } catch (Exception e) {
                    log.warn("回写订单支付状态失败 orderNo={}: {}", orderNo, e.getMessage());
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
    }
    @PreDestroy public void shutdown() { if (consumer != null) consumer.shutdown(); }
}
```

- [ ] **Step 5: 改写 refund 接入渠道退款**

`OrderServiceImpl.refund` 从「直接标已退款」改为：`payOrderService.refund(order, reason)`，渠道退款成功后（余额渠道调 `refundToAccount`）回写 `orders.status = REFUNDED`。微信渠道骨架直接返回未实现提示。

- [ ] **Step 6: 创建渠道回调入口 PayChannelNotifyController**

委托 `PayOrderService.handleNotify`（替换原 `PayNotifyController` 里直接 `updateById` 的幂等缺陷）：

```java
@RestController
@RequestMapping("/api/notify/pay")
public class PayChannelNotifyController {
    private final PayOrderService payOrderService;
    private final PayChannelFactory factory;

    @PostMapping("/{channelCode}")
    public String receiveNotify(@PathVariable String channelCode,
                                @RequestParam Map<String, String> params) {
        if (!factory.support(channelCode)) return "fail";
        return payOrderService.handleNotify(channelCode, params);
    }
}
```

> 现有 `PayNotifyController`（微信 V3 头验签）保留，其验签逻辑迁入 `WechatChannelHandler.parseNotify` 后，此 controller 改为委托 `payOrderService.handleNotify("wechat", params)`，或删除并由 `PayChannelNotifyController` 统一承接。以编译通过为准。

- [ ] **Step 7: 编译验证 + Commit**

Run: `cd jiwei-life && mvn compile -pl jiwei-pojo,jiwei-common,jiwei-framework,jiwei-modules -DskipTests`

```bash
git add jiwei-modules/src/main/java/com/jiwei/trade/
git commit -m "feat(trade): 订单支付/退款接入支付渠道，回调经幂等入账回写业务订单"
```

---

## Task 5: 多实例 WebSocket 推送（RocketMQ 广播 + 本机过滤 + Redis 会话）

**Files:**
- Create: `com/jiwei/trade/websocket/RedisSessionRegistry.java`
- Create: `com/jiwei/trade/websocket/NotifyWebSocketHandler.java`
- Create: `com/jiwei/trade/websocket/NotifyMessage.java`
- Create: `com/jiwei/trade/websocket/WebSocketConfig.java`
- Create: `com/jiwei/trade/mq/NotifyMessageProducer.java`、`NotifyMessageConsumer.java`
- Modify: `jiwei-modules/.../trade/websocket/WebSocketServer.java`（删除或改造为委托）
- Modify: `jiwei-framework/src/main/java/com/jiwei/framework/config/WebSocketConfiguration.java`（删除 `ServerEndpointExporter` bean）

**Interfaces:**
- Consumes: `DefaultMQProducer`（框架 bean）、`StringRedisTemplate`
- Produces: `NotifyWebSocketHandler.dispatch(NotifyMessage)`（定向/广播推送）

**步骤：**

- [ ] **Step 1: 创建 NotifyMessage（消息模型）**

```java
@Data
@Builder
public class NotifyMessage {
    private String id;          // 消息ID（雪花）
    private String userId;      // 目标用户（定向），null 表示广播
    private boolean broadcast;  // 是否广播
    private String type;        // 消息类型（新订单/催单/接单）
    private String content;     // 消息体（JSON）
}
```

- [ ] **Step 2: 创建 RedisSessionRegistry**

从源 `RedisSessionRegistry.java` 移植（Redis Set 记录在线用户/会话归属），包名改 `com.jiwei.trade.websocket`，key 前缀改 JiweiLife 风格：

```java
@Component
public class RedisSessionRegistry {
    private static final String ONLINE_USERS_KEY = "ws:online:users";
    private static final String USER_SESSIONS_PREFIX = "ws:user:";
    private static final String SESSION_USER_PREFIX = "ws:session:";
    private final StringRedisTemplate redisTemplate;

    public RedisSessionRegistry(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void register(String sessionId, String userId) {
        redisTemplate.opsForSet().add(ONLINE_USERS_KEY, userId);
        redisTemplate.opsForSet().add(USER_SESSIONS_PREFIX + userId + ":sessions", sessionId);
        redisTemplate.opsForValue().set(SESSION_USER_PREFIX + sessionId, userId);
    }

    public void unregister(String sessionId) {
        String userId = redisTemplate.opsForValue().get(SESSION_USER_PREFIX + sessionId);
        if (userId == null) return;
        redisTemplate.delete(SESSION_USER_PREFIX + sessionId);
        String key = USER_SESSIONS_PREFIX + userId + ":sessions";
        redisTemplate.opsForSet().remove(key, sessionId);
        Long remaining = redisTemplate.opsForSet().size(key);
        if (remaining == null || remaining == 0) {
            redisTemplate.delete(key);
            redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, userId);
        }
    }
}
```

- [ ] **Step 3: 创建 NotifyWebSocketHandler（本机过滤）**

从源 `NotifyWebSocketHandler.java` 移植，包名/序列化改 fastjson2，去掉 `InstanceIdProvider`（JiweiLife 单实例部署为主，广播消息天然覆盖）：

```java
@Component
public class NotifyWebSocketHandler extends TextWebSocketHandler {
    private final RedisSessionRegistry sessionRegistry;
    private final Map<String, Set<WebSocketSession>> localSessions = new ConcurrentHashMap<>();

    public NotifyWebSocketHandler(RedisSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = resolveUserId(session);
        session.getAttributes().put("userId", userId);
        localSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
        sessionRegistry.register(session.getId(), userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            Set<WebSocketSession> sessions = localSessions.get(userId);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) localSessions.remove(userId, sessions);
            }
        }
        sessionRegistry.unregister(session.getId());
    }

    /** 本机过滤：查本地连接表，未命中跳过（可能连在别的实例） */
    public void dispatch(NotifyMessage msg) {
        String payload = JSON.toJSONString(msg);
        if (msg.isBroadcast()) {
            localSessions.values().forEach(sessions -> sendAll(sessions, payload));
        } else {
            Set<WebSocketSession> sessions = localSessions.getOrDefault(msg.getUserId(), Set.of());
            sendAll(sessions, payload);
        }
    }

    private void sendAll(Set<WebSocketSession> sessions, String payload) {
        for (WebSocketSession s : sessions) {
            try { if (s.isOpen()) s.sendMessage(new TextMessage(payload)); }
            catch (IOException e) { log.warn("WS 推送失败 session={}: {}", s.getId(), e.getMessage()); }
        }
    }

    private String resolveUserId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri != null) {
            String path = uri.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        }
        return "anonymous-" + session.getId();
    }
}
```

- [ ] **Step 4: 创建 WebSocketConfig（注册 handler，替换 @ServerEndpoint）**

```java
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final NotifyWebSocketHandler handler;
    public WebSocketConfig(NotifyWebSocketHandler handler) { this.handler = handler; }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/{userId}").setAllowedOrigins("*");
    }
}
```

同时删除 `WebSocketServer.java`（`@ServerEndpoint` + 静态 `sessionMap`）或将其改造为委托 `NotifyWebSocketHandler`。注意删除后需处理 `OrderServiceImpl.confirm()` 里「TODO WebSocket 推送」的调用点（改走 `NotifyMessageProducer`）。

还要删除 `jiwei-framework` 的 `WebSocketConfiguration.java`（`ServerEndpointExporter` bean）——改用 `WebSocketConfigurer` 后 `@ServerEndpoint` 注解已不生效，`ServerEndpointExporter` 会因容器里再无 `@ServerEndpoint` 类而成为无用的空导出器，应一并移除（该文件位于 `jiwei-framework`，需在框架模块编译验证）。

- [ ] **Step 5: 创建 NotifyMessageProducer + NotifyMessageConsumer（RocketMQ 广播）**

**关键迁移**：RabbitMQ `AnonymousQueue` 扇出 → RocketMQ `MessageModel.BROADCASTING`（每个实例都收到消息）：

```java
@Component
@Slf4j
public class NotifyMessageConsumer {
    private static final String TOPIC = "ws-notify-topic";
    @Value("${rocketmq.name-server:localhost:9876}") private String nameServer;
    @Value("${rocketmq.consumer.group:ws-notify-consumer}") private String consumerGroup;
    private final NotifyWebSocketHandler handler;

    private DefaultMQPushConsumer consumer;

    @PostConstruct
    public void start() throws MQClientException {
        consumer = new DefaultMQPushConsumer(consumerGroup);
        consumer.setNamesrvAddr(nameServer);
        consumer.setMessageModel(MessageModel.BROADCASTING); // 广播：每个实例都收到
        consumer.subscribe(TOPIC, "*");
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, ctx) -> {
            for (MessageExt msg : msgs) {
                NotifyMessage notify = JSON.parseObject(new String(msg.getBody(), StandardCharsets.UTF_8),
                        NotifyMessage.class);
                handler.dispatch(notify); // 本机过滤后推送
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        log.info("NotifyMessageConsumer 启动（广播消费）: {}", TOPIC);
    }
    @PreDestroy public void shutdown() { if (consumer != null) consumer.shutdown(); }
}
```

`NotifyMessageProducer` 复用框架 `DefaultMQProducer` bean，`send` 到 `ws-notify-topic`。

- [ ] **Step 6: 打通业务触发点**

在 `OrderServiceImpl.confirm()`（商家接单）与 `PayChannelNotifyController` 入账成功后，调 `notifyMessageProducer.sendToUser(userId, "接单", content)`，触发定向推送。

- [ ] **Step 7: 编译验证 + Commit**

Run: `cd jiwei-life && mvn compile -pl jiwei-pojo,jiwei-common,jiwei-framework,jiwei-modules -DskipTests`

```bash
git add jiwei-modules/src/main/java/com/jiwei/trade/websocket/ jiwei-modules/src/main/java/com/jiwei/trade/mq/ jiwei-framework/src/main/java/com/jiwei/framework/config/WebSocketConfiguration.java
git commit -m "feat(trade): 多实例 WebSocket 定向推送（RocketMQ 广播 + 本机过滤 + Redis 会话）"
```

---

## Self-Review 结论

- **Spec 覆盖**：设计文档 §5.2 的两块（支付中台技术点 + 多实例 WebSocket）均有对应 Task。
- **RabbitMQ→RocketMQ 映射**：`AnonymousQueue` 扇出 → `MessageModel.BROADCASTING`；`basicAck` → `CONSUME_SUCCESS`；`basicNack(requeue)` → `RECONSUME_LATER`（秒杀失败分类在 market plan 落地，本 plan 的消费者均为「尽力而为」语义）。
- **幂等三重落地**：CAS `updateOrderPaid`（`status='0'`）、唯一键 `uk_type_biz`、条件扣减 `deductBalance`（`balance >= amount`），均为 SQL 层保证。
- **已知瑕疵修复**：单号 `Math.random()` → `IdUtil.getSnowflakeNextIdStr()`；微信 V2 MD5 → V3 RSA 骨架。
- **边界**：`pay_order` 与 `orders` 通过 `out_order_no` 关联，`orders.pay_status` 是回写快照，支付权威状态在 `pay_order`。两表同库（`@DS("trade")`），无需分布式事务。
