# JChatMind Agent 落地设计

日期：2026-08-31
状态：已批准

## 1. 背景与目标

JChatMind 是 jiwei-ai 中基于 Spring AI 的智能 Agent 运行时，已实现 Think-Execute 循环、工具调用、SSE 实时推送、多模型切换。当前差距：

- 商家端已接入 `/ai/merchant/stream`（SSE），但无模型切换、无会话续接、不展示工具调用过程
- 用户端小程序 `aichat.vue` 只是语义搜索，未使用 Agent 能力（小程序不支持 SSE 是根因）
- 管理端完全没有 AI 智能体助手

目标：让 JChatMind 真正成为 jiwei-life 三个端的一部分 —— 管理端新增运营智能助手、商家端增强、用户端接入 Agent，并支持多轮会话与模型切换。

## 2. 现状（已核实）

### AI 服务（jiwei-ai :8087）
- `JChatMind`：完整 Agent 循环（状态机 IDLE→THINKING/EXECUTING→FINISHED/ERROR；maxSteps=8 默认；30s 超时；内存窗口记忆 20 条）。**缺陷：直接依赖 `SseService`，无法非流式返回**
- `ChatClientRegistry`：deepseek / kimi / qwen 三模型按 key 切换 ✓
- `ExploreAgent`（探店，4 工具：语义搜索餐厅/用户偏好/附近POI/天气）
- `MerchantAgent`（Text2SQL，按 empId→merchantId 解析）
- `AuditAgent`（内容审核，`POST /ai/audit/content`）
- `ConversationService`（MongoDB：createConversation / appendMessage(role,content,toolCalls) / getRecentMessages(convId, rounds) / listConversations(userId) / updateSummary；**缺 deleteConversation**）
- `RagService.semanticSearch(query, knowledgeBase, topK)`（pgvector）✓
- SSE 接口：`GET /ai/agent/stream`（Explore）、`GET /ai/merchant/stream`（Merchant，已有 model 参数）

### 前端
- 商家端 `AiAssistant.vue`（91 行）：已接 SSE，无模型切换、无会话、不展示 action 事件；登录态 localStorage 存 token/userName/empId
- 用户端 `aichat.vue`：语义搜索 `/user/dish/search`
- 管理端：无；登录态 store/user.ts 存 token/userName（无 employeeId）

## 3. 架构设计

```
商家端 AiAssistant ──SSE──▶ ┌──────────────────────┐
管理端 AgentAssistant ─SSE─▶ │  jiwei-ai :8087     │
小程序 aichat ──JSON─▶      │  JChatMind Agent 循环 │
                             │  (Think-Execute)     │
   ┌─────────────────────────┤                      │
   │  多模型 registry       │  deepseek/kimi/qwen   │
   │  Admin/Explore/Merchant │  工具集 + RAG + 会话  │
   └─────────────────────────┴──────────────────────┘
```

### 3.1 事件解耦（后端核心改造）

新增接口 `AgentEventSink`（`send(String sessionId, AgentEvent event)`）：

- `SseEventSink`：包装现有 `SseService`，供流式接口
- `ListEventSink`：把事件收集到 `List<AgentEvent>`，供非流式接口一次性返回

`JChatMind` 构造器从 `SseService` 改为 `AgentEventSink`（重载构造器兼容现有调用；`execute()` 与 `think()` 中的 `sseService.send` 改走 sink）。

### 3.2 非流式接口 `POST /ai/agent/chat`

```json
// 请求
{ "agentType": "EXPLORE|MERCHANT|ADMIN",
  "message": "带我去吃川菜",
  "model": "deepseek",
  "userId": 1, "empId": null, "merchantId": null,
  "conversationId": null }
// 响应
{ "conversationId": "conv_xxx",
  "events": [{"type":"message","content":"..."},{"type":"action","content":"..."}],
  "finalAnswer": "..." }
```

新增 `AgentChatService`：组装 JChatMind（ListEventSink + 会话加载）→ run() → 返回 events + finalAnswer。会话规则：有 conversationId 则从 Mongo 加载最近 20 条消息续接；无则 createConversation。结束后 appendMessage 保存用户消息 + 助手消息（含 toolCalls）。

### 3.3 管理端 AdminAgent + AdminTools

- `AgentRuntimeFactory.createAdmin(model, sessionId, empId, message)`
- systemPrompt：平台运营助手，工具集：
  - `queryPlatformData(sqlHint)`：Text2SQL 查全平台经营数据（复用 `SqlValidator` + `bizJdbcTemplate`，白名单只读）
  - `auditContent(content, contentType)`：委托 `AuditAgent.audit`
  - `searchKnowledgeBase(query)`：`RagService.semanticSearch(query, "jiwei_ops", 5)`
  - `merchantSuggestion(merchantId)`：查该商户订单/评价聚合 → ChatClient 生成经营建议
- 新增 SSE 接口 `GET /ai/admin/stream?message&empId&model&conversationId`

### 3.4 会话续接与生命周期

- `JChatMind` 启动记忆改为：`ConversationService.getRecentMessages(convId, 10)` → 转 `Message[]`（SystemMessage + 交替 User/Assistant）
- `AgentController` / `AgentChatService` 统一处理 conversationId 解析
- `ConversationService` 新增 `deleteConversation(conversationId)`

### 3.5 商家端接口补强

`GET /ai/merchant/stream` 增加 `conversationId` 参数，复用会话续接逻辑。

## 4. 前端改造

### 4.1 管理端（新增）

- `src/views/ai/AgentAssistant.vue`：
  - 聊天窗：用户/助手气泡，`action` 事件折叠展示"正在调用工具…"
  - 模型下拉（deepseek/kimi/qwen，持久化到 localStorage）
  - 会话列表侧栏：新建/切换/删除（`/ai/conversation/list?userId=` + `DELETE /ai/conversation/{id}`）
  - 快速问题：本周营收TOP3 / 分析差评原因 / 入驻流程是什么 / 商户运营建议
  - EventSource 接 `/ai/admin/stream?message=&empId=&model=&conversationId=`
- 路由 `/ai/assistant` + 菜单项"AI 智能助手"
- `store/user.ts` 登录后存 `employeeId`（后端 `/admin/employee/login` 返回 `id`）

### 4.2 商家端（增强）

`AiAssistant.vue`：
- 请求带 `model` 与 `conversationId` 参数
- 模型下拉（持久化）
- 处理 `action` 事件展示工具调用
- 会话列表（新建/切换/删除）

### 4.3 用户端小程序（改造）

`aichat.vue`：从语义搜索改为 `POST /ai/agent/chat`（agentType=EXPLORE）：
- 展示 `events`：message 累积为气泡文本，action 折叠为"工具调用"标签
- `conversationId` 存本地 storage（每会话一个），支持多轮
- 模型固定 deepseek（不加切换，按用户决定）
- 保留"搜索失败"兜底

`api/index.js` 新增 `aiAgentChat(data)`（走 AI_BASE_URL 8087，非 Result 包装，直接解析返回对象）。

## 5. 身份与权限

沿用现状：AI 服务无 JWT 拦截器，用 empId/userId 识别身份（开发环境约定）。管理端/商家端 SSE 带 empId；用户端非流式带 userId。管理端 store 需补存 employeeId。

## 6. 测试与验收

- 后端：`mvn compile`；curl 验证 `/ai/agent/chat`（含无会话/有会话）、`/ai/admin/stream`、`/ai/merchant/stream?conversationId=`
- 前端：三个端各自构建通过
- 人工验收路径：管理端提问经营数据 → SSE 流式输出；小程序提问探店 → 非流式返回；切换模型后回答风格变化可感知

## 7. 落地顺序

1. AI 服务：AgentEventSink 解耦 + ListEventSink + ConversationService.deleteConversation
2. AI 服务：会话续接（JChatMind 记忆加载 + AgentController 统一 conversationId）
3. AI 服务：AdminAgent + AdminTools + `/ai/admin/stream` + `POST /ai/agent/chat`
4. 管理端：AgentAssistant.vue + 路由菜单 + store 补 employeeId
5. 商家端：AiAssistant.vue 增强
6. 小程序：aichat.vue 改造 + api
7. 编译验证（后端 + 三前端）+ curl 冒烟
