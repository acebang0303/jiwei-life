# 吉味生活 JiweiLife 技术栈整合设计文档

> 日期：2026-08-26
> 状态：待评审
>
> ⚠️ **实现现状对照（2026-10-12）**：本文「已知瑕疵 / 落地时修正」中列的问题**大部分已修**——
> 熔断改真滑动窗口（不再是固定窗口）、限流字段语义澄清并加多实例额度摊薄、短链点击计数改本地攒批、
> RAG 混合召回补齐（trgm 关键词 + RRF + 重排 + 降级）、NL2SQL 只读落地并加启动写探针自检（fail-fast）。
> 逐条清单见 [`docs/实现现状对照.md`](../实现现状对照.md)。
> 范围：将 7 个轮子项目的技术深度，按「抽技术点注入」原则整合进 JiweiLife 现有骨架

---

## 1. 背景与目标

### 1.1 背景

JiweiLife 是一个「O2O 本地生活 + 内容社区 + AI Agent」全栈平台，采用**模块化单体**架构。当前状态是**骨架完整、深度不足**：领域模块（trade/market/social/ai）都已搭建，但大量核心逻辑是 `TODO` 或浅实现（如支付直接标已支付、点赞裸 `selectOne+updateById`）。

用户整理的 7 个轮子项目恰好相反——**技术点很深、但无业务闭环**。本设计文档的目标，是把这 7 个项目的技术深度，注入到 JiweiLife 的对应领域骨架中，形成**深度互补**。

### 1.2 已拍板的方向（不可再摇摆）

| 决策 | 结论 |
|---|---|
| AI 部分 | **完全用 JChatMind 重写** jiwei-ai |
| 消息中间件 | **统一到 RocketMQ**（轮子的 RabbitMQ 逻辑等价迁移） |
| 短链项目 | **作为营销辅助能力**纳入 market 模块 |
| 模型策略 | **上多模型**（注册表切换，不再锁死 DeepSeek） |
| 对账账本 | **落各自业务库**（与权威数据同库同事务） |

---

## 2. 现状盘点

### 2.1 JiweiLife 现状（骨架）

| 模块 | 技术栈 | 现状 | 关键文件 |
|---|---|---|---|
| trade | Redisson + 雪花ID + RocketMQ | 支付/退款/回补全 `TODO`，`pay()` 直接标已支付 | `OrderServiceImpl.java` |
| market | Redis + Lua | Lua 预扣已实现，异步落库/预热 `TODO` | `SeckillServiceImpl.java` |
| social | Redis ZSet | `like()` 裸 DB 读写，Feed 流 `TODO` | `NoteServiceImpl.java` |
| jiwei-ai | Spring AI + DeepSeek + pgvector | ChatClient 托管 ReAct，3 个 Agent，RAG 简易切块 | `ExploreAgent.java`、`RagService.java` |

**基础能力已具备**：MyBatis-Plus + dynamic-datasource（`@DS` 多数据源）、Redisson、RocketMQ、Redis/Lua、pgvector、MongoDB。

### 2.2 7 个轮子项目技术点（精确到文件）

| 轮子项目 | 源路径 | 核心技术点 → 关键文件 |
|---|---|---|
| **JChatMind** | `D:\qiuzhao\jchatmind_v2\JChatMind` | 手写 Agent Loop 状态机 → `agent/JChatMind.java`；手动接管工具 → `internalToolExecutionEnabled(false)` + `ToolCallingManager`；RAG → `MarkdownParserService` + `ChunkBgeM3Mapper.xml`；多模型 → `ChatClientRegistry`；SSE → `SseServiceImpl`；vector TypeHandler → `PgVectorTypeHandler` |
| **智能问数** | `D:\qiuzhao\intelligent-data-query-system` | StateGraph 15 节点 → `GraphConfiguration.graph()`；双通道 RAG → `EvidenceRecallNode` + `SchemeReCallNode`；SQL 只读校验 → `SqlSecurityValidator`；权限隔离+脱敏 → `PermissionInterceptor` |
| **秒杀** | `D:\qiuzhao\seckill-system` | 自研熔断器 → `RedisCircuitBreaker`；对账闭环 → `ReconcileService`；失败分类 → `OrderCreateConsumer`；限流降级 → `RateLimitAspect`；Lua + `schema.sql` |
| **点赞①** | `D:\qiuzhao\high-concurrency-like-system` | 攒批 → `RedisLikeStore`（peek/commit）；热点聚合 → `HotKeyDetector` + `LocalHotLikeBuffer`；幂等 upsert → `LikeEventConsumer`；对账 → `SyncReconciler`；实时合并 → `LikeService.realTimeCount` |
| **支付中台** | `D:\qiuzhao\payment-processing-platform\ruoyi-pay` | 策略+工厂 → `IPayChannelHandler` + `PayChannelFactory`；幂等三重 → `updateOrderPaid`/`uk_type_biz`/`deductBalance`；同事务+decimal → `PayAccountServiceImpl.consume()`；验签+查单+留痕 → `PayNotifyController` + `handleNotify` |
| **短链** | `D:\qiuzhao\duanlianfuwuqideshixian` | MurmurHash+Base62 → `ShortLinkService`；手写布隆 → `ShortCodeBloomFilter`；Caffeine 三级 → `resolve()`；原子计数 → `incrementClickCount`；限流 → `CreateRateLimiter` |
| **异步任务** | `D:\qiuzhao\asynchronous-update` | prefetch=1 → `application.yml`；扇出+本机过滤 → `AnonymousQueue` + `NotifyWebSocketHandler`；Redis 会话 → `RedisSessionRegistry`；跨实例 WS → `WebSocketConfig` |

---

## 3. 整合原则

1. **抽技术点，不搬项目**：轮子项目不是完整系统，只抽取「深挖过的技术点」，注入到 JiweiLife 已有领域。
2. **技术栈统一**：RocketMQ 唯一、Redisson 唯一、pgvector 唯一，杜绝多套中间件并存。
3. **业务能力不丢**：jiwei-ai 现有的推荐引擎、内容审核、向量同步管道、探店工具是真实业务，必须保留。
4. **去同质化**：JChatMind/智能问数都有「二手/星球」标签，落地时用 JiweiLife 真实业务工具替换示例工具，注入个人深度。

---

## 4. 目标架构

```
基础设施层（唯一）：RocketMQ + Redisson + Redis/Lua + pgvector + MySQL(6库)
                                        │
        ┌───────────────┬───────────────┼───────────────┐
     jiwei-ai       trade           market          social
   (AI 服务 :8087)  (交易域)        (营销域)         (社交域)
        │               │               │               │
   JChatMind 运行时  支付中台技术点   秒杀+短链技术点   点赞①技术点
   + 智能问数        + 异步任务WS      │               │
   (Agent Loop/     (幂等/策略/验签)  (熔断/对账/      (攒批/热点/
   状态机/多模型/                    失败分类/限流)    幂等/对账)
   RAG/SQL校验)
```

---

## 5. 分领域整合设计

### 5.1 AI 服务（jiwei-ai）—— JChatMind + 智能问数

**这是本次整合的核心，改动最大。**

#### 5.1.1 现状 → 目标

| 维度 | 现状 | 目标（JChatMind 运行时） |
|---|---|---|
| Agent 循环 | ChatClient 托管 ReAct + prompt 软约束防死循环 | 手写 Loop + 状态机 `IDLE→THINKING→EXECUTING→FINISHED/ERROR` + `MAX_STEPS=20` 硬上限 |
| 工具执行 | 框架自动执行 | 关闭 `internalToolExecutionEnabled(false)`，手动 `ToolCallingManager` 管理 |
| 多模型 | 锁死 DeepSeek | `ChatClientRegistry` 注册表切换（deepseek  + Qwen + Kimi） |
| RAG 分块 | `chunkText` 按 500 字切 | flexmark Markdown 按 Heading 分块 + bge-m3 Embedding |
| Embedding | bge-m3（SiliconFlow API） | 保持 bge-m3 不变（无需切换） |
| SSE | 自写 `toSse` | `SseServiceImpl`（ConcurrentHashMap 管理连接） |
| vector 映射 | `vectorToString` 手工拼串 | MyBatis `PgVectorTypeHandler` |

#### 5.1.2 需要复制/借鉴的文件

从 JChatMind 复制框架骨架：
- `agent/JChatMind.java`（核心 Loop + 状态机）
- `agent/AgentState.java`、`config/ChatClientRegistry.java`、`config/MultiChatClientConfig.java`
- `service/MarkdownParserService*.java`、`service/impl/RagServiceImpl.java`
- `mapper/ChunkBgeM3Mapper.xml`、`typehandler/PgVectorTypeHandler.java`
- `controller/SseController.java`、`service/impl/SseServiceImpl.java`

从智能问数复制：
- `security/SqlSecurityValidator.java`（SQL 只读校验——补强现有 SqlValidator 的注释剥离/字符串剥离/多语句检测，而非重建）
- `security/PermissionInterceptor.java`（权限隔离 + 脱敏）
- `agent/nodes/EvidenceRecallNode.java` + `SchemeReCallNode.java`（双通道 RAG 思路）

#### 5.1.3 关键改造点

1. **业务 Agent 重写为 JChatMind 的工具/角色**：现有 3 个 Agent（探店/商家问数/审核）重写为 JChatMind 框架上的工具集。探店工具（`semanticSearchRestaurant`/`getNearbyPOI`/`getWeather`）、商家问数（走 SQL 校验链路）、内容审核（`AuditAgent`）保留为工具，替换 JChatMind 的示例工具。
2. **推荐引擎 `RecommendEngine` 保留**：它是业务能力，不是 Agent 框架，不动。
3. **向量同步管道保留**：`EmbeddingConsumer`（RocketMQ 消费笔记向量）保留，但其 Embedding 调 `EmbeddingService` 需切换到 bge-m3（与 JChatMind 统一）。
4. **版本对齐**：JChatMind 用 Spring AI BOM 1.1.0，智能问数用 1.1.2 + `spring-ai-alibaba-graph-core`。落地时统一到一个 Spring AI 版本（建议 1.1.x 最新稳定），避免版本冲突。
5. **StateGraph vs 手写状态机的关系**：智能问数的 `GraphConfiguration`（15 节点 StateGraph）专用于 Text2SQL 场景，与 JChatMind 的通用 Loop 不同。方案是——**通用对话走 JChatMind Loop，Text2SQL（商家问数）走 StateGraph**，两者共存于 jiwei-ai，按业务路由。

---

### 5.2 交易域（trade）—— 支付中台 + 异步任务

#### 5.2.1 支付链路（借支付中台）

| 现状 | 目标 | 源文件 |
|---|---|---|
| `pay()` 直接标已支付，微信 V3 `TODO` | 渠道策略+工厂 → 幂等 → 验签 → 查单补偿 | `IPayChannelHandler` + `PayChannelFactory` |
| 无幂等 | 三重防线：CAS `updateOrderPaid` + 唯一键 `uk_type_biz` + 条件扣减 `deductBalance` | `PayOrderMapper.xml`、`PayAccountMapper.xml` |
| 无渠道抽象 | `PayChannelFactory(List<IPayChannelHandler>)` 构造器自动注册 | `core/PayChannelFactory.java` |
| 回调无验签 | `PayNotifyController.receiveNotify` + `handleNotify`（验签+金额校验+`finally` 留痕） | `PayNotifyController.java`、`PayOrderServiceImpl.java` |

**改造要点**：
- 新增 `pay_channel`、`pay_order`、`pay_notify_log`、`pay_account`、`pay_account_transaction` 表到 trade 库（借鉴 `sql/pay.sql`、`sql/pay_balance.sql`）。
- 支付渠道 handler 落地为：`balance`（余额）、`mock`（开发）、`wechat`（V3 预留，骨架）。微信 V3 的 RSA 验签替换轮子里陈旧的 V2 MD5。
- 单号生成用 `IdUtil.getSnowflakeNextIdStr()`（JiweiLife 已有），**不要**照搬轮子的 `Math.random()`。
- 「扣钱必须写流水」用 `@Transactional` 同事务保证，金额 `decimal`。

#### 5.2.2 多实例 WebSocket 推送（借异步任务）

| 现状 | 目标 | 源文件 |
|---|---|---|
| 单机 `WebSocketServer.java` | 多实例定向推送：扇出 + 本机过滤 + Redis 会话 | `AnonymousQueue` + `NotifyWebSocketHandler` + `RedisSessionRegistry` |

**关键迁移（RabbitMQ → RocketMQ）**：
- 轮子用 RabbitMQ `AnonymousQueue`（每实例独享队列）实现扇出 → **RocketMQ 用广播消费模式（broadcast）** 等价实现「每个实例都收到消息」。
- 本机过滤逻辑（`localSessions` ConcurrentHashMap 查本地连接，未命中跳过）可直接复用。
- Redis 会话注册（`RedisSessionRegistry`）复用，替换现有 `WebSocketServer` 的静态管理方式。

---

### 5.3 营销域（market）—— 秒杀 + 短链

#### 5.3.1 秒杀（借秒杀系统）

| 现状 | 目标 | 源文件 |
|---|---|---|
| 只有 Lua 预扣，落库/预热 `TODO` | 完整链路：Lua 预扣 + RocketMQ 落库 + 熔断 + 对账 + 限流 | — |
| 无熔断 | 自研 `RedisCircuitBreaker`（三态，只统计基础设施异常） | `RedisCircuitBreaker.java` + `RedisExecutor.java` |
| 无对账 | `ReconcileService` 三道对账（库存/缓存/滞留订单），DB 为唯一账本 | `ReconcileService.java` |
| 无失败分类 | 确定性失败回滚+ack；瞬时失败 nack 重试一次 | `OrderCreateConsumer.java` |
| 无限流降级 | `RateLimitAspect`：Redisson RRateLimiter + 本地令牌桶降级 | `RateLimitAspect.java` |

**关键迁移**：
- MQ 从 RabbitMQ 迁 RocketMQ：`OrderCreateConsumer` 的 `basicNack/basicAck` → RocketMQ 的 `RECONSUME_LATER`/`SUCCESS`，失败分类逻辑保留。
- 秒杀表借鉴 `sql/schema.sql`（`seckill_activity` 带 `version` 乐观锁、`seckill_order` 唯一索引防重）落入 market 库。
- **注意坑**：轮子的熔断器是「固定窗口简化版」、`version+1` 在 SET 不在 WHERE 非真乐观锁、Lua `limitPerUser` 未使用。落地时修正这些已知瑕疵（见 §6）。

#### 5.3.2 短链（营销辅助）

| 能力 | 落地场景 | 源文件 |
|---|---|---|
| MurmurHash + Base62 + 幂等 | 优惠券分享短链、邀请码 | `ShortLinkService.java` |
| 手写布隆防穿透 | 短码重定向防缓存穿透 | `ShortCodeBloomFilter.java` + `SimpleBloomFilter.java` |
| Caffeine 三级链路 | 缓存→布隆→DB | `resolve()` |
| 原子 UPDATE 计数 | 点击统计 | `incrementClickCount` |
| 固定窗口限流 | 生成接口限流 | `CreateRateLimiter.java` |

**改造要点**：
- 短链表 `short_link` 落入 market 库。
- 修复轮子的两个硬伤：`url_hash` 32 位哈希碰撞（改 64 位 + 校验 longUrl 相等）、并发幂等的 `rollback-only` 隐患。

---

### 5.4 社交域（social）—— 点赞系统①

| 现状 | 目标 | 源文件 |
|---|---|---|
| `like()` 裸 `selectOne+updateById` | Redis 挡写 → 攒批 → 热点聚合 → 幂等 upsert → 对账 | — |
| 无攒批 | `RedisLikeStore`（peek LRANGE + commit LTRIM，至少一次语义） | `RedisLikeStore.java` + `LikeEventConsumer.java` |
| 无热点聚合 | `HotKeyDetector` + `LocalHotLikeBuffer`（JVM 本地聚合） | `HotKeyDetector.java` + `LocalHotLikeBuffer.java` |
| 无幂等 | `ON DUPLICATE KEY UPDATE`（`article_id,user_id` 唯一键） | `LikeEventConsumer.upsertRecords()` |
| 无对账 | `SyncReconciler` 30s 整刷 | `SyncReconciler.java` |
| 无实时合并 | `realTimeCount()`（Redis 计数 + 本地待刷增量） | `LikeService.java` |

**改造要点**：
- 点赞/收藏统一走 Redis 攒批链路，替换现有 `NoteServiceImpl.like()` 的裸 DB 操作。
- `note_like` 表加 `(note_id, user_id)` 唯一索引以支持幂等 upsert。
- 点赞对账表落 social 库（与 `note.like_count` 同库同事务）。
- **注意坑**：轮子 `window-seconds` 是死配置（配置 5s 实际硬编码 2s），落地时修复。

---

## 6. 关键技术迁移方案（3 个已知坑）

### 6.1 RabbitMQ → RocketMQ 等价映射

| RabbitMQ 能力 | RocketMQ 等价实现 | 注意 |
|---|---|---|
| `prefetch=1` 公平分发 | 集群消费模式默认均摊，但「谁空闲谁拿」需自行控制消费位点节奏 | RocketMQ 无 prefetch 概念 |
| `AnonymousQueue` 扇出 | 广播消费（`MessageModel.BROADCASTING`） | 广播消息不保证进度语义，需自行处理 |
| `basicNack(requeue=true)` 重试一次 | `ConsumeConcurrentlyStatus.RECONSUME_LATER` | 结合重试次数判断确定性失败 |
| `basicAck` 确认 | `ConsumeConcurrentlyStatus.CONSUME_SUCCESS` | — |

### 6.2 JChatMind 重写边界

「完全用 JChatMind 重写」的准确含义：**用 JChatMind 的运行时（Loop+状态机+工具执行+多模型+RAG）替换「ChatClient 托管 ReAct」**，但以下 jiwei-ai 现有业务能力**必须保留并重写为 JChatMind 工具**：
- `RecommendEngine`（推荐引擎）
- `AuditAgent`（内容审核）
- `EmbeddingConsumer`（RocketMQ 向量同步管道）
- `ExploreTools`（探店工具：搜餐厅/天气/POI）

否则 AI 部分反而退化。

### 6.3 去同质化

JChatMind 是付费星球项目、智能问数是二手开源（`io.github.qifan777`）。落地时：
- 示例工具全部替换为 JiweiLife 真实业务工具（查订单、问经营数据、搜本地餐厅）。
- 每个设计决策要能讲清「为什么」：为什么关闭 Spring AI 自动执行、为什么 pgvector 不用 Milvus、状态机怎么防死循环。

---

## 7. 数据库设计（对账落各自业务库）

对账账本**不建统一对账库**，而是落各自业务库、与权威数据同库同事务：

| 对账 | 权威数据 | 对账表落库 |
|---|---|---|
| 秒杀对账 | `seckill_activity.stock`（market 库） | market 库 |
| 点赞对账 | `note.like_count`（social 库） | social 库 |
| 支付流水 | `pay_order`/`pay_account`（trade 库） | trade 库 |

**理由**：对账的终点是「修正权威数据」，该动作必须在权威数据所在库的本地事务内原子完成，否则变成跨库分布式事务。「统一对账库」只适合财务级稽核对账中心（异步抽取副本+差异），JiweiLife 暂无此诉求；将来数据大屏若要全局对账健康度，可异步汇聚成读模型，不参与修正。

---

## 8. 实施顺序与里程碑

按依赖关系，建议分 4 个阶段：

1. **阶段一：基础设施统一**（无业务风险，先行）
   - 确认 Spring AI 版本统一（1.1.x）
   - 搭好 RocketMQ 广播消费、Redisson 限流降级的基建
   - 引入 bge-m3 Embedding（Ollama）

2. **阶段二：AI 服务重写**（jiwei-ai，独立服务，隔离性最好）
   - JChatMind 运行时落地 + 业务 Agent 重写为工具
   - 智能问数 SQL 校验 + 双通道 RAG 落地

3. **阶段三：交易域**（trade，最需谨慎，涉及资金）
   - 支付链路：策略+工厂 → 幂等三重 → 验签 → 查单
   - 多实例 WebSocket 推送

4. **阶段四：营销+社交域**（market + social）
   - 秒杀完整链路（熔断/对账/失败分类/限流）
   - 点赞攒批+热点聚合
   - 短链营销辅助

---

## 9. 风险清单

| 风险 | 等级 | 应对 |
|---|---|---|
| Spring AI 版本冲突（1.1.0 vs 1.1.2） | 高 | 统一到 1.1.x 最新稳定，先做依赖兼容性验证 |
| RocketMQ 广播消费进度语义 | 中 | 扇出场景自行处理进度，本机过滤兜底 |
| 资金链路改造出错 | 高 | trade 支付先走 mock+余额渠道闭环，微信 V3 骨架预留 |
| 轮子项目的已知瑕疵被带入 | 中 | 熔断器固定窗口、`version` 非真乐观锁、`window-seconds` 死配置等，落地时修正 |
| AI 同质化 | 中 | 业务工具替换 + 决策讲清「为什么」 |
| 对账跨库事务诱惑 | 低 | 严守「各自业务库」原则，不建统一对账库 |
