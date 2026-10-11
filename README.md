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
- 秒杀保护：Redis 三态熔断器 + Redisson 令牌桶限流 + 定时对账（Redis vs DB 收敛）
- 优惠券 / 签到（成长值联动）

### 社交域（social）
- 笔记 / 评价 / 关注 / 二级评论树 / 标签跳转（店铺或搜索）
- 点赞：Redis 攒批 + 热点聚合 + 定时对账

### 用户域（user / auth）
- C 端微信登录（dev 用 mock-login），成长值体系（签到 +5 / 下单 +10 / 发笔记 +5）

### AI 服务（jiwei-ai）
- JChatMind ReAct Agent Loop / NL2SQL 商家问数 / 混合推荐（行为 + 语义向量 + 热度兜底）

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

## 关键设计

- **模块化单体**而非微服务：DDD 领域拆分，为平滑迁移微服务预留结构
- **AI 服务独立部署**：性能特征不同 + 独立数据库依赖
- **单数据源策略**：开发期 6 库共享一个 MySQL 实例，dynamic-datasource `@DS` 路由
- **秒杀资金安全**：Redis Lua 原子预扣 + 事务消息 + DB CAS 兜底 + 对账收敛，多层防超卖
