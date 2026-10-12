# 吉味生活 · JiweiLife

[![CI](https://github.com/acebang0303/jiwei-life/actions/workflows/ci.yml/badge.svg?branch=master)](https://github.com/acebang0303/jiwei-life/actions/workflows/ci.yml)

**吉林本地生活服务平台** —— 融合 O2O 到店/到家 + 内容社区 + AI Agent 智能助手的全栈平台。

用户端微信小程序点餐/下单/秒杀，商家端接单/备货，管理端运营，附数据大屏与 AI 智能助手（美食推荐 / 商家经营问数）。

![image-20260905103854669](./README.assets/image-20260905103854669.png)

![image-20260905103943750](./README.assets/image-20260905103943750.png)

![image-20260905104055957](./README.assets/image-20260905104055957.png)

![image-20260905104110860](./README.assets/image-20260905104110860.png)

<img width="492" height="1057" alt="image" src="https://github.com/user-attachments/assets/c59a6578-28ab-4547-8efc-3b76e7974be6" />


## 来源与边界声明

本仓库是**个人学习型整合项目**，请理性看待其工程边界：

- **底座**：在课程项目「苍穹外卖」之上重构，借鉴其分层设计与业务实现；在此基础上新增并打通**秒杀、点赞、AI 助手、优惠券、短链、对账**等模块，工作重心是**多模块整合与咬合**——处理异构方案收敛带来的分布式一致性、事务边界与高并发问题。
- **方案来源**：秒杀 / 支付中台 / 点赞攒批 / 短链 / 异步通知 / NL2SQL 问数来自开源项目二次开发，Agent 运行时（JChatMind）为知识星球项目改造；**不含从零自研的底层轮子**。
- **Mock 边界**：微信支付渠道与回调为开发环境 mock（渠道标记 mock + 模拟回调接口）；业务与演示数据由脚本生成，未对接真实支付、真实生产流量。
- **工程质量**：已配置 GitHub Actions CI（JDK 21，全量 `mvn test` + `package`）；**CD 与多实例部署尚未实施**。

## 项目结构

```
jiwei-life/
├── jiwei-common/            # 公共模块（常量、异常、Result、JWT 工具）
├── jiwei-pojo/              # 实体 / DTO / VO
├── jiwei-framework/         # 框架模块（拦截器、RocketMQ 配置、全局异常）
├── jiwei-modules/           # 主应用 :8080（DDD 领域拆分，模块化单体）
│   ├── auth/                 # 认证（JWT + RBAC，C 端微信 mock 登录）
│   ├── user/                 # 用户（地址、成长值、行为上报）
│   ├── merchant/             # 商户（店铺、菜品、分类、套餐）
│   ├── trade/                # 交易（购物车、订单、支付中台、WebSocket）
│   ├── market/               # 营销（秒杀、优惠券、签到、短链）
│   └── social/               # 内容社区（笔记、评论、点赞、关注）
├── jiwei-ai/                # AI 独立服务 :8087（Spring AI 多模型）
├── jiwei-admin/             # 管理端前端 :5173 (Vue 3 + Vite + Element Plus)
├── jiwei-merchant/          # 商家端前端 :5174 (Vue 3 + Vite)
├── jiwei-screen/            # 数据大屏 :5175 (Vue 3 + Vite)
├── jiwei-miniapp/           # 用户端微信小程序 (uni-app, 18 页)
├── db/                       # SQL：6 库初始化 + 种子数据 + mock 生成器 + 修复脚本
└── .github/workflows/ci.yml  # CI：JDK 21 全量 mvn test + package
```

## 技术栈

- **Backend**: Spring Boot 3.5, JDK 21 (Virtual Threads), Spring AI, MyBatis-Plus 3.5, dynamic-datasource
- **AI**: DeepSeek / Qwen / Kimi 多模型注册表, pgvector, RAG, ReAct Agent, NL2SQL, 混合推荐
- **Middleware**: MySQL 8.0, Redis 7.x, RocketMQ 5.3, PostgreSQL 16 + pgvector, MongoDB 7.x（Docker 统一承载）
- **Frontend**: Vue 3, Element Plus, Pinia, Vite / uni-app

## 核心业务模块

### 交易域（trade）
- 购物车 / 订单：Redisson 分布式锁防重 + RocketMQ 延迟消息超时取消（CAS 条件更新防误关已支付订单）
- 支付中台：渠道策略模式（余额 / mock / 微信），验签 + 金额校验 + CAS 幂等入账
- WebSocket 通知：RocketMQ 广播 + 本机连接过滤，`/ws/{userId}` 定向推送

### 营销域（market）
- **秒杀**：**RocketMQ 事务消息**保证 Redis Lua 原子预扣（防超卖/限购）成功才投递消息——本地事务=预扣并写预扣标记，broker 回查（标记在/订单已建）兜底防消息丢失，DB 条件扣减 + 建单在消费端异步完成 → 延迟消息超时回补库存
- 秒杀保护：Redis 三态熔断器（滑动时间窗统计故障率、仅统计基础设施异常、HALF_OPEN 限并发探针）+ Redisson 令牌桶注解式限流（AOP + SpEL 动态 key；Redis 故障降级本机令牌桶并按实例数摊薄额度）+ 定时对账（Redis vs DB 收敛）
- 短链：MurmurHash(64) + Base62 生成，布隆过滤器防穿透 + Caffeine 加速重定向；点击计数本地攒批异步落库（重定向热路径不写库），生成接口走 Redisson 全局令牌桶限流，长链仅放行 http/https
- 优惠券 / 签到（成长值联动）

### 社交域（social）
- 笔记 / 评价 / 关注 / 二级评论树 / 标签跳转（店铺或搜索）
- 点赞：Redis Lua 原子切换（非热点路径）+ 事件队列攒批 + 热点笔记本地聚合；落库按 SQL 实际影响行数计增量，保证 at-least-once 重放幂等；以 DB 为唯一账本定时校正 Redis；消费端与对账共用 Redisson 分布式锁保证多实例安全

### 用户域（user / auth）
- C 端微信登录（dev 用 mock-login），成长值体系（签到 +5 / 下单 +10 / 发笔记 +5）

### AI 服务（jiwei-ai）
- JChatMind ReAct Agent Loop：状态机流转（IDLE→THINKING→EXECUTING→FINISHED/ERROR）+ 步数上限与整体超时防死循环；关闭框架内部工具自动执行、改为手动编排每一步；决策阶段 **token 级 SSE 流式输出**（流式链路异常自动降级为阻塞调用）
- NL2SQL 商家问数：语句白名单 + 写关键字黑名单（已修 DOTALL 绕过）+ 注入模式 + 表级白名单**四层校验**；**仅路由至只读账号执行**——连接级 `readOnly` + 关闭多语句 + 启动写探针自检（确认可写即拒绝启动）
- RAG 检索：向量 + pg_trgm（中文无分词边界，故选 trgm 而非 tsvector）**双路召回 → RRF 融合 → 大模型重排**；Embedding 故障时自动降级为关键词召回
- 混合推荐（行为 + 语义向量 + 热度兜底）

## 环境要求

- JDK 21 + Maven 3.9+
- Node.js 22+
- Docker Desktop（MySQL / Redis / RocketMQ / PostgreSQL+pgvector / MongoDB）
- 微信开发者工具（跑小程序，导入 `jiwei-miniapp/dist/build/mp-weixin`）

## 快速开始

### 1. 配置环境变量
仓库根 `.env.example` → 复制为 `.env`（含各中间件连接、JWT 密钥、模型 API Key）。

### 2. 启动中间件
```bash
docker compose -f docker-compose.yml up -d
```

### 3. 初始化数据库
```bash
docker exec -i jiwei-mysql mysql -uroot -proot123 < db/init_all.sql
# 可选：更大规模演示数据
docker exec -i jiwei-mysql mysql -uroot -proot123 < db/98_mock_data.sql
# AI 服务依赖 pgvector（PostgreSQL），单独执行：
docker exec -i jiwei-postgres psql -U postgres -d jiwei_ai -f /dev/stdin < db/07_pgvector.sql
```
> 注：MySQL 容器已统一为东八区时区（`TZ=Asia/Shanghai`），与应用 JDBC、Java 时间对齐。

### 4. 启动后端（主应用 + AI）
```bash
cd jiwei-modules && mvn spring-boot:run     # 主应用 :8080
cd jiwei-ai && mvn spring-boot:run          # AI 服务 :8087
```
> 改过 jiwei-common / pojo / framework 后，需先 `mvn install -pl jiwei-common,jiwei-pojo,jiwei-framework -DskipTests` 再启动。

### 5. 启动前端
```bash
cd jiwei-admin && npm run dev     # 管理端 :5173
cd jiwei-merchant && npm run dev  # 商家端 :5174
cd jiwei-screen && npm run dev    # 数据大屏 :5175
```

### 6. 小程序
```bash
cd jiwei-miniapp && npm install
npm run dev:mp-weixin    # 开发：生成到 dist/dev/mp-weixin，微信开发者工具导入
npm run build:mp-weixin # 构建：dist/build/mp-weixin
```

### 接口与文档
- Swagger（主应用）：http://localhost:8080/swagger-ui/index.html
- AI 服务健康：http://localhost:8087/actuator/health

## 测试与质量

```bash
# 后端全量测试（CI 同款，含 AI 模块）
mvn -B test

# 秒杀并发压测（需后端运行 + 造测试活动）
cd jiwei-modules/src/test/jmeter
bash gen_tokens.sh 500        # 生成压测 token
bash run_load.sh <活动ID> <菜品ID> 500   # JMeter 500 并发
```
基准记录见 `jiwei-modules/src/test/jmeter/BENCHMARK.md`。

当前全量 **121 例**通过（jiwei-framework 2 / jiwei-modules 59 / jiwei-ai 60），编译 warning 0 条。

除单测外，以下链路在真机（Docker 中间件）上验证过：

- **点赞 Lua 原子性与幂等**：`redis-cli --eval` 直接跑脚本（含「完全重放 → delta=0」用例）；应用侧连点两次 `/user/note/{id}/like`，计数 +1 → −1、事件队列被消费、DB 计数回到基线
- **熔断滑动窗口**：时钟可注入，窗口滑出行为用可控时钟做确定性单测（不依赖 sleep）
- **只读数据源**：`SHOW GRANTS` 确认仅 SELECT；写探针在 `readOnly` 连接下被拒；故障注入（`readOnly=false`）下探针成功 → fail-fast 拒绝启动生效
- **RAG 关键词通道**：`EXPLAIN` 确认 `ILIKE` 子串谓词走 GIN trgm 索引（`Bitmap Index Scan`）

> 设计文档（`docs/superpowers/`）是 2026-08 ~ 09 的历史记录，保留当时的决策过程；
> 「设计说法 vs 当前实现」的分叉点集中列在 [`docs/实现现状对照.md`](docs/实现现状对照.md)，有冲突以该文档 + 代码为准。

## 关键设计

- **模块化单体**而非微服务：DDD 领域拆分，为平滑迁移微服务预留结构
- **AI 服务独立部署**：性能特征不同 + 独立数据库依赖
- **单数据源策略**：开发期 6 库共享一个 MySQL 实例，dynamic-datasource `@DS` 路由
- **秒杀资金安全**：Redis Lua 原子预扣 + 事务消息 + DB CAS 兜底 + 对账收敛，多层防超卖
