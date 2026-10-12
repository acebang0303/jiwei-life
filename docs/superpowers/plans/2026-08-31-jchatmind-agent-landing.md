# JChatMind Agent 落地实现计划

> ⚠️ **实现现状对照（2026-10-12）**：SSE 流式已升级为 **token 级**——逐 chunk delta 直接推送，
> 聚合器只用于产出最终响应（依据：`javap` + 诊断测试确认 `ChatClientMessageAggregator` 返回的是
> 合并后的终值而非逐 chunk delta）；流式链路异常自动降级为阻塞调用。
> 接口与事件抽象（`AgentEventSink` / 非流式 `POST /ai/agent/chat`）不变。
> 见 [`docs/实现现状对照.md`](../实现现状对照.md) §8。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 JChatMind Agent 成为 jiwei-life 三个端的一部分 —— 管理端新增运营智能助手、商家端增强（模型切换+会话+工具展示）、用户端接入非流式 Agent，并支持多轮会话续接。

**Architecture:** AI 服务（jiwei-ai :8087）新增 AgentEventSink 事件抽象解耦流式/非流式输出，新增 AdminAgent 工具集 + 非流式 `POST /ai/agent/chat` 接口；管理端/商家端用 SSE 流式（Web EventSource），小程序用非流式 JSON。会话历史复用现有 MongoDB `ConversationService`，JChatMind 启动时从 Mongo 加载最近历史续接。

**Tech Stack:** Java 21 / Spring Boot 3.5 / Spring AI / SSE / Vue3 + Vite + Element Plus（管理端、商家端）/ uni-app（小程序）

**Spec:** `docs/superpowers/specs/2026-08-31-jchatmind-agent-landing-design.md`

## Global Constraints

- 项目非 git 仓库：**每个任务以编译/curl 验证代替 git commit**
- 后端编译命令：`cd jiwei-life && mvn compile -pl jiwei-common,jiwei-pojo,jiwei-framework,jiwei-modules,jiwei-ai -DskipTests -q`（AI 服务只编 jiwei-ai 时可单跑 `mvn compile -pl jiwei-ai -DskipTests -q`）
- AI 服务端口 8087；主服务 8080
- 小程序请求 `/ai/**` 走 AI_BASE_URL `http://localhost:8087`，非 Result 包装
- 管理端/商家端登录态：localStorage 存 `token`；商家端已存 `empId`；管理端需补存 `employeeId`
- 模型 key 固定三个：`deepseek` / `kimi` / `qwen`（`ChatClientRegistry`）
- 对话保存角色字段：`user` / `assistant`；工具调用字段 `toolCalls`
- 所有 SSE 事件类型：`thinking` / `action` / `message` / `done` / `error`
- 不改动主服务 jiwei-modules 的任何代码（本计划全部落在 jiwei-ai + 三个前端）

---

### Task 1: AgentEventSink 事件抽象（后端）

**Files:**
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/sse/AgentEventSink.java`
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/sse/SseEventSink.java`
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/sse/ListEventSink.java`
- Modify: `jiwei-ai/src/main/java/com/jiwei/ai/agent/JChatMind.java`
- Modify: `jiwei-ai/src/main/java/com/jiwei/ai/agent/AgentRuntimeFactory.java`
- Test: `jiwei-ai/src/test/java/com/jiwei/ai/sse/ListEventSinkTest.java`

**Interfaces:**
- Consumes: `AgentEvent`（record: `(String type, String content)`，已存在）
- Produces: `AgentEventSink`（`void send(String sessionId, AgentEvent event)`）、`ListEventSink`（`List<AgentEvent> getEvents()`）、`SseEventSink`

- [ ] **Step 1: 写失败测试**

创建 `ListEventSinkTest.java`：

```java
package com.jiwei.ai.sse;

import com.jiwei.ai.dto.AgentEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListEventSinkTest {

    @Test
    void collectEventsInOrder() {
        ListEventSink sink = new ListEventSink();
        sink.send("s1", new AgentEvent("action", "工具A 返回"));
        sink.send("s1", new AgentEvent("message", "结论"));
        List<AgentEvent> events = sink.getEvents();
        assertEquals(2, events.size());
        assertEquals("action", events.get(0).type());
        assertEquals("结论", events.get(1).content());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd jiwei-life && mvn test -pl jiwei-ai -Dtest=ListEventSinkTest -DskipTests=false -q`
Expected: FAIL — `ListEventSink` 不存在（编译错误）

- [ ] **Step 3: 实现三个类**

`AgentEventSink.java`：

```java
package com.jiwei.ai.sse;

import com.jiwei.ai.dto.AgentEvent;

/**
 * Agent 事件输出通道。流式场景由 SseEventSink 推给前端，
 * 非流式场景由 ListEventSink 收集后一次性返回。
 */
public interface AgentEventSink {
    void send(String sessionId, AgentEvent event);
}
```

`SseEventSink.java`：

```java
package com.jiwei.ai.sse;

import com.jiwei.ai.dto.AgentEvent;
import org.springframework.stereotype.Component;

/**
 * 流式事件通道：包装现有 SseService 推送到前端。
 */
@Component
public class SseEventSink implements AgentEventSink {

    private final SseService sseService;

    public SseEventSink(SseService sseService) {
        this.sseService = sseService;
    }

    @Override
    public void send(String sessionId, AgentEvent event) {
        sseService.send(sessionId, event);
    }
}
```

`ListEventSink.java`：

```java
package com.jiwei.ai.sse;

import com.jiwei.ai.dto.AgentEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * 非流式事件通道：把 Agent 执行过程收集到内存列表，供一次性返回。
 */
public class ListEventSink implements AgentEventSink {

    private final List<AgentEvent> events = new ArrayList<>();

    @Override
    public void send(String sessionId, AgentEvent event) {
        events.add(event);
    }

    public List<AgentEvent> getEvents() {
        return events;
    }
}
```

- [ ] **Step 4: 改 JChatMind 用接口**

修改 `JChatMind.java`：
- import 中 `com.jiwei.ai.sse.SseService` → `com.jiwei.ai.sse.AgentEventSink`
- 字段 `private final SseService sseService;` → `private final AgentEventSink eventSink;`
- 构造器参数 `SseService sseService` → `AgentEventSink eventSink`（共 4 处构造器），赋值 `this.eventSink = eventSink`
- 方法内两处 `sseService.send(chatSessionId, ...)` → `eventSink.send(chatSessionId, ...)`
- 构造器调用不变（参数名/类型变化自动跟随）

- [ ] **Step 5: 改 AgentRuntimeFactory**

`AgentRuntimeFactory.java`：
- 字段 `private final SseService sseService;` 改为 `private final SseEventSink sseEventSink;`
- 构造器参数同改，注入 `SseEventSink sseEventSink`
- 两处 `new JChatMind(client, ..., sseService, sessionId, ...)` → `new JChatMind(client, ..., sseEventSink, sessionId, ...)`

- [ ] **Step 6: 运行测试确认通过**

Run: `cd jiwei-life && mvn test -pl jiwei-ai -Dtest=ListEventSinkTest -DskipTests=false -q`
Expected: PASS

- [ ] **Step 7: 编译验证**

Run: `cd jiwei-life && mvn compile -pl jiwei-ai -DskipTests -q`
Expected: 无输出（成功）

---

### Task 2: 会话续接 + 生命周期（后端）

**Files:**
- Modify: `jiwei-ai/src/main/java/com/jiwei/ai/service/ConversationService.java`
- Create: `jiwei-ai/src/test/java/com/jiwei/ai/service/ConversationMessageLoaderTest.java`
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/agent/ConversationHistoryLoader.java`

**Interfaces:**
- Consumes: `ConversationService.getRecentMessages(String, int)` → `List<Map<String,Object>>`，消息 map 含 `role`(user/assistant)、`content`
- Produces: `ConversationService.deleteConversation(String)`；`ConversationHistoryLoader.toMessages(List<Map<String,Object>>)` → `List<Message>`（Spring AI `UserMessage`/`AssistantMessage`）；`ConversationHistoryLoader.persist(ConversationService, String convId, String userMsg, String assistantMsg)`（写 user+assistant 两条 + toolCalls 可选）

- [ ] **Step 1: 写失败测试**

创建 `ConversationMessageLoaderTest.java`：

```java
package com.jiwei.ai.agent;

import com.jiwei.ai.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversationMessageLoaderTest {

    private static Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    @Test
    void convertsMongoMessagesToSpringAiMessages() {
        List<Map<String, Object>> raw = List.of(
                msg("user", "你好"),
                msg("assistant", "你好！有什么可以帮你？"));
        List<Message> messages = ConversationHistoryLoader.toMessages(raw);
        assertEquals(2, messages.size());
        assertTrue(messages.get(0) instanceof UserMessage);
        assertTrue(messages.get(1) instanceof AssistantMessage);
        assertEquals("你好", messages.get(0).getText());
    }

    @Test
    void buildToolCallList() {
        List<Map<String, Object>> calls = ConversationHistoryLoader.buildToolCallList(
                List.of("queryPlatformData", "searchKnowledgeBase"));
        assertEquals(2, calls.size());
        assertEquals("queryPlatformData", calls.get(0).get("name"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd jiwei-life && mvn test -pl jiwei-ai -Dtest=ConversationMessageLoaderTest -DskipTests=false -q`
Expected: FAIL — 类不存在

- [ ] **Step 3: 实现 ConversationHistoryLoader**

`ConversationHistoryLoader.java`：

```java
package com.jiwei.ai.agent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mongo 会话消息 ↔ Spring AI Message 转换。
 * JChatMind 续接时把历史消息转回模型可读的消息列表。
 */
public final class ConversationHistoryLoader {

    private ConversationHistoryLoader() {
    }

    @SuppressWarnings("unchecked")
    public static List<Message> toMessages(List<Map<String, Object>> rawMessages) {
        List<Message> messages = new ArrayList<>();
        for (Map<String, Object> m : rawMessages) {
            String role = m.get("role") == null ? "user" : m.get("role").toString();
            String content = m.get("content") == null ? "" : m.get("content").toString();
            if (content.isEmpty()) {
                continue;
            }
            if ("assistant".equals(role)) {
                messages.add(new AssistantMessage(content));
            } else {
                messages.add(new UserMessage(content));
            }
        }
        return messages;
    }

    public static List<Map<String, Object>> buildToolCallList(List<String> toolNames) {
        List<Map<String, Object>> calls = new ArrayList<>();
        for (String name : toolNames) {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("name", name);
            call.put("arguments", "");
            calls.add(call);
        }
        return calls;
    }

    /**
     * 一轮 Agent 完成后保存 user + assistant 消息（assistant 带工具调用名）。
     */
    public static void persistRound(ConversationService service, String conversationId,
                                    String userMsg, String assistantMsg, List<String> toolNames) {
        service.appendMessage(conversationId, "user", userMsg);
        service.appendMessage(conversationId, "assistant", assistantMsg,
                buildToolCallList(toolNames == null ? new ArrayList<>() : toolNames));
    }
}
```

- [ ] **Step 4: ConversationService 加 deleteConversation**

在 `ConversationService.java` 末尾（`updateSummary` 之后）加：

```java
    /**
     * 删除对话
     */
    public void deleteConversation(String conversationId) {
        Query query = new Query(Criteria.where("_id").is(conversationId));
        mongoTemplate.remove(query, COLLECTION);
        log.info("删除对话: {}", conversationId);
    }
```

- [ ] **Step 5: 运行测试确认通过**

Run: `cd jiwei-life && mvn test -pl jiwei-ai -Dtest=ConversationMessageLoaderTest -DskipTests=false -q`
Expected: PASS

- [ ] **Step 6: 编译验证**

Run: `cd jiwei-life && mvn compile -pl jiwei-ai -DskipTests -q`
Expected: 成功

---

### Task 3: 非流式接口 `POST /ai/agent/chat`（后端）

**Files:**
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/dto/AgentChatRequest.java`
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/dto/AgentChatResponse.java`
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/service/AgentChatService.java`
- Modify: `jiwei-ai/src/main/java/com/jiwei/ai/controller/AgentController.java`

**Interfaces:**
- Consumes: `JChatMind`、`AgentRuntimeFactory.createExplore/createMerchant/createAdmin`（createAdmin 在 Task 4，本任务先用 EXPLORE/MERCHANT 两个分支）、`ConversationService`、`ConversationHistoryLoader`、`ListEventSink`、`AgentEvent`
- Produces: `POST /ai/agent/chat`；`AgentChatRequest(agentType, message, model, userId, empId, merchantId, conversationId)`；`AgentChatResponse(conversationId, events, finalAnswer)`

- [ ] **Step 1: 写 DTO**

`AgentChatRequest.java`：

```java
package com.jiwei.ai.dto;

import lombok.Data;

@Data
public class AgentChatRequest {
    private String agentType;   // EXPLORE / MERCHANT / ADMIN
    private String message;
    private String model = "deepseek";
    private Long userId;
    private Long empId;
    private Long merchantId;
    private String conversationId;
}
```

`AgentChatResponse.java`：

```java
package com.jiwei.ai.dto;

import java.util.List;

public record AgentChatResponse(String conversationId, List<AgentEvent> events, String finalAnswer) {
}
```

- [ ] **Step 2: 实现 AgentChatService**

`AgentChatService.java`：

```java
package com.jiwei.ai.service;

import com.jiwei.ai.agent.AgentRuntimeFactory;
import com.jiwei.ai.agent.ConversationHistoryLoader;
import com.jiwei.ai.agent.JChatMind;
import com.jiwei.ai.dto.AgentChatRequest;
import com.jiwei.ai.dto.AgentChatResponse;
import com.jiwei.ai.dto.AgentEvent;
import com.jiwei.ai.sse.ListEventSink;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 非流式 Agent 调用服务：一次请求跑完 Agent 循环，
 * 收集全部事件后一次性返回（供小程序等无 SSE 能力的前端）。
 */
@Service
@Slf4j
public class AgentChatService {

    private static final int HISTORY_ROUNDS = 10;

    private final AgentRuntimeFactory factory;
    private final ConversationService conversationService;

    public AgentChatService(AgentRuntimeFactory factory, ConversationService conversationService) {
        this.factory = factory;
        this.conversationService = conversationService;
    }

    public AgentChatResponse chat(AgentChatRequest req) {
        //1、解析/创建会话
        Long ownerId = req.getUserId() != null ? req.getUserId()
                : (req.getEmpId() != null ? req.getEmpId() : 0L);
        String agentType = req.getAgentType() == null ? "EXPLORE" : req.getAgentType().toUpperCase();
        String conversationId = req.getConversationId();
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = conversationService.createConversation(ownerId, agentType);
        }

        //2、加载历史
        List<Message> history = ConversationHistoryLoader.toMessages(
                conversationService.getRecentMessages(conversationId, HISTORY_ROUNDS));

        //3、组装 Agent
        String sessionId = UUID.randomUUID().toString();
        ListEventSink sink = new ListEventSink();
        JChatMind runtime;
        switch (agentType) {
            case "MERCHANT":
                runtime = factory.createMerchant(req.getModel(), sessionId, req.getMerchantId(), req.getMessage(), history, sink);
                break;
            case "ADMIN":
                runtime = factory.createAdmin(req.getModel(), sessionId, req.getEmpId(), req.getMessage(), history, sink);
                break;
            case "EXPLORE":
            default:
                runtime = factory.createExplore(req.getModel(), sessionId, req.getMessage(), history, sink);
                break;
        }
        if (runtime == null) {
            return new AgentChatResponse(conversationId, List.of(
                    new AgentEvent("message", "无法初始化助手，请检查参数")), "无法初始化助手");
        }

        //4、执行并收集
        runtime.run();
        List<AgentEvent> events = sink.getEvents();
        String finalAnswer = events.stream()
                .filter(e -> "message".equals(e.type()))
                .map(AgentEvent::content)
                .reduce("", (a, b) -> a + b);

        //5、持久化本轮
        List<String> toolNames = events.stream()
                .filter(e -> "action".equals(e.type()))
                .map(e -> e.content().split(" ")[0])
                .toList();
        ConversationHistoryLoader.persistRound(conversationService, conversationId,
                req.getMessage(), finalAnswer, toolNames);

        return new AgentChatResponse(conversationId, events, finalAnswer);
    }
}
```

- [ ] **Step 3: 给 AgentRuntimeFactory 加历史+ sink 的构造方法**

在 `AgentRuntimeFactory.java` 改造 `createExplore`/`createMerchant` 支持 `List<Message> history` 和 `AgentEventSink`。为兼容 Task 4 的 `createAdmin`，把现有 `createExplore`/`createMerchant` 重载为：

```java
    public JChatMind createExplore(String model, String sessionId, String message) {
        return createExplore(model, sessionId, message, List.of(), sseEventSink);
    }

    public JChatMind createExplore(String model, String sessionId, String message,
                                   List<Message> history, AgentEventSink sink) {
        ChatClient client = registry.get(model);
        List<ToolCallback> tools = Arrays.asList(MethodToolCallbackProvider.builder()
                .toolObjects(exploreTools).build().getToolCallbacks());
        List<Message> memory = new ArrayList<>(history);
        memory.add(new UserMessage(message));
        return new JChatMind(client, EXPLORE_SYSTEM_PROMPT, tools, sink, sessionId, memory,
                agentProperties.getMaxIterations(), agentProperties.getTimeoutSeconds());
    }

    public JChatMind createMerchant(String model, String sessionId, Long merchantId, String message) {
        return createMerchant(model, sessionId, merchantId, message, List.of(), sseEventSink);
    }

    public JChatMind createMerchant(String model, String sessionId, Long merchantId, String message,
                                    List<Message> history, AgentEventSink sink) {
        ChatClient client = registry.get(model);
        MerchantQueryTools queryTools = new MerchantQueryTools(merchantId, client, sqlValidator, bizJdbcTemplate);
        List<ToolCallback> tools = Arrays.asList(MethodToolCallbackProvider.builder()
                .toolObjects(queryTools).build().getToolCallbacks());
        List<Message> memory = new ArrayList<>(history);
        memory.add(new UserMessage(message));
        return new JChatMind(client, MERCHANT_SYSTEM_PROMPT, tools, sink, sessionId, memory,
                agentProperties.getMaxIterations(), agentProperties.getTimeoutSeconds());
    }
```

（import 增加 `com.jiwei.ai.sse.AgentEventSink`、`java.util.ArrayList`）

- [ ] **Step 4: AgentController 加 POST /ai/agent/chat**

`AgentController.java` 注入 `AgentChatService`，加：

```java
    private final AgentChatService agentChatService;

    // 构造器加 agentChatService 参数（Spring 自动注入）

    @PostMapping(value = "/agent/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Agent 非流式对话")
    public AgentChatResponse chat(@RequestBody AgentChatRequest request) {
        log.info("Agent非流式对话: type={}, model={}", request.getAgentType(), request.getModel());
        return agentChatService.chat(request);
    }
```

（import：`org.springframework.web.bind.annotation.RequestBody`、`AgentChatRequest`、`AgentChatResponse`）

- [ ] **Step 5: 编译验证**

Run: `cd jiwei-life && mvn compile -pl jiwei-ai -DskipTests -q`
Expected: 成功

- [ ] **Step 6: curl 冒烟（EXPLORE，无会话）**

Run:
```bash
curl -s -X POST http://localhost:8087/ai/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"agentType":"EXPLORE","message":"帮我找一家适合约会的川菜馆","userId":1}' | head -c 500
```
Expected: 返回 JSON，含 `conversationId`、`events` 数组、`finalAnswer`（若 AI 服务未重启则跳过此步，编译通过即视为本任务完成）

---

### Task 4: 管理端 AdminAgent + AdminTools + `/ai/admin/stream`（后端）

**Files:**
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/agent/AdminTools.java`
- Modify: `jiwei-ai/src/main/java/com/jiwei/ai/agent/AgentRuntimeFactory.java`
- Modify: `jiwei-ai/src/main/java/com/jiwei/ai/controller/AgentController.java`

**Interfaces:**
- Consumes: `SqlValidator`（`boolean validate(String sql)`）、`AuditAgent.audit(String, String)` → `JSONObject`、`RagService.semanticSearch(String, String, int)` → `List<Map>`、`bizJdbcTemplate`、`ChatClientRegistry`
- Produces: `AdminTools`（`queryPlatformData`、`auditContent`、`searchKnowledgeBase`、`merchantSuggestion` 四个 @Tool 方法）、`AgentRuntimeFactory.createAdmin(...)`、`GET /ai/admin/stream`

- [ ] **Step 1: 实现 AdminTools**

`AdminTools.java`：

```java
package com.jiwei.ai.agent;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.jiwei.ai.config.ChatClientRegistry;
import com.jiwei.ai.nlsql.SqlValidator;
import com.jiwei.ai.rag.RagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 管理端平台运营助手工具集。
 */
@Component
@Slf4j
public class AdminTools {

    private static final String NL2SQL_PROMPT = """
            你是一个SQL查询助手，负责将平台运营问题转换为MySQL SELECT查询语句。

            数据库表结构（跨库查询，请使用全限定表名）：
            - jiwei_trade.orders: 订单表 (id, number, user_id, merchant_id, amount, pay_amount, pay_status, status, create_time, pay_time)，status: 5=已完成 6=已取消
            - jiwei_trade.order_detail: 订单明细 (id, order_id, name菜品名, amount, number数量)
            - jiwei_merchant.merchant_info: 商户表 (id, name, address, status, create_time)
            - jiwei_merchant.dish: 菜品表 (id, merchant_id, name, price, status, sales, create_time)
            - jiwei_social.note: 笔记表 (id, user_id, merchant_id, title, like_count, collect_count, comment_count, audit_status, create_time)
            - jiwei_user.user: 用户表 (id, nickname, phone, growth_value, create_time)

            规则：
            1. 只生成 SELECT 语句，不要有任何写操作
            2. 「营收」「订单数」「客单价」：用 orders 表，金额用 pay_amount，只统计 status=5
            3. 「商户排行」「哪家店最好」：orders.merchant_id 分组 JOIN merchant_info 取名称
            4. 「菜品」「销量」「热销」：dish.sales 字段 ORDER BY sales DESC
            5. 「笔记」「内容」：note 表
            6. 日期过滤用 DATE()，如 DATE(create_time) >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)
            7. 需要分页时用 LIMIT offset, count

            请只返回SQL语句，不要有任何解释。
            """;

    private final ChatClientRegistry registry;
    private final SqlValidator sqlValidator;
    private final JdbcTemplate bizJdbcTemplate;
    private final AuditAgent auditAgent;
    private final RagService ragService;

    public AdminTools(ChatClientRegistry registry, SqlValidator sqlValidator,
                      @Qualifier("bizJdbcTemplate") JdbcTemplate bizJdbcTemplate,
                      AuditAgent auditAgent, RagService ragService) {
        this.registry = registry;
        this.sqlValidator = sqlValidator;
        this.bizJdbcTemplate = bizJdbcTemplate;
        this.auditAgent = auditAgent;
        this.ragService = ragService;
    }

    @Tool(description = "查询平台经营数据（订单/营收/商户排行/菜品销量/笔记数据等），输入自然语言问题，返回查询结果JSON")
    public String queryPlatformData(@ToolParam(description = "平台运营问题，如'本周营收TOP3商户'") String question) {
        String sql = generateSql(question);
        if (sql == null || sql.isEmpty()) {
            return "无法理解该问题";
        }
        if (!sqlValidator.validate(sql)) {
            return "该问题涉及的数据无法查询";
        }
        sql = ensureLimit(sql);
        try {
            List<Map<String, Object>> rows = bizJdbcTemplate.queryForList(sql);
            return JSON.toJSONString(rows);
        } catch (Exception e) {
            log.warn("平台数据分析SQL执行失败: sql={}, err={}", sql, e.getMessage());
            return "查询失败：" + e.getMessage();
        }
    }

    @Tool(description = "审核内容（笔记/评价）是否合规，返回审核结果JSON")
    public String auditContent(@ToolParam(description = "待审核内容文本") String content,
                               @ToolParam(description = "内容类型：note 或 review", required = false) String contentType) {
        JSONObject result = auditAgent.audit(content, contentType == null ? "note" : contentType);
        return result.toJSONString();
    }

    @Tool(description = "从运营知识库检索制度/流程/入驻规则等，返回相关文档片段")
    public String searchKnowledgeBase(@ToolParam(description = "知识库检索问题，如'入驻流程是什么'") String query) {
        List<Map<String, Object>> docs = ragService.semanticSearch(query, "jiwei_ops", 5);
        return JSON.toJSONString(docs);
    }

    @Tool(description = "针对指定商户生成经营建议，输入商户ID，返回该商户经营数据分析与改进建议")
    public String merchantSuggestion(@ToolParam(description = "商户ID") Long merchantId) {
        //1、拉取该商户近30天订单与评价聚合
        String orderSql = "SELECT COUNT(*) AS order_cnt, IFNULL(SUM(pay_amount),0) AS revenue, " +
                "IFNULL(AVG(pay_amount),0) AS avg_price FROM jiwei_trade.orders " +
                "WHERE merchant_id = " + merchantId + " AND status = 5";
        String reviewSql = "SELECT IFNULL(AVG(r.rating),0) AS avg_rating, COUNT(*) AS review_cnt " +
                "FROM jiwei_social.review r JOIN jiwei_trade.orders o ON r.order_id = o.id " +
                "WHERE o.merchant_id = " + merchantId;
        try {
            Map<String, Object> orders = bizJdbcTemplate.queryForList(orderSql).get(0);
            Map<String, Object> reviews = bizJdbcTemplate.queryForList(reviewSql).get(0);
            String dataJson = JSON.toJSONString(Map.of("orders", orders, "reviews", reviews));
            //2、LLM 生成建议
            ChatClient client = registry.get("deepseek");
            String advice = client.prompt()
                    .system("你是餐饮运营顾问，基于商户经营数据给出3条具体、可执行的改进建议。直接输出建议文本。")
                    .user("商户ID: " + merchantId + "\n经营数据: " + dataJson)
                    .call()
                    .content();
            return advice == null ? "暂无建议" : advice;
        } catch (Exception e) {
            log.warn("商户建议生成失败: merchantId={}, err={}", merchantId, e.getMessage());
            return "商户ID " + merchantId + " 数据查询失败";
        }
    }

    private String generateSql(String question) {
        try {
            ChatClient client = registry.get("deepseek");
            String content = client.prompt()
                    .system(NL2SQL_PROMPT)
                    .user(question)
                    .call()
                    .content();
            log.info("管理端LLM生成SQL: {}", content);
            return extractSql(content);
        } catch (Exception e) {
            log.warn("管理端LLM生成SQL失败: {}", e.getMessage());
            return null;
        }
    }

    private static String extractSql(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(?is)```(?:sql)?\\s*(.*?)```").matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        String trimmed = content.trim();
        if (trimmed.toLowerCase().startsWith("select")) {
            return trimmed;
        }
        return null;
    }

    private static String ensureLimit(String sql) {
        String lower = sql.toLowerCase();
        if (!lower.contains("limit")) {
            return sql + " LIMIT 20";
        }
        return sql;
    }
}
```

- [ ] **Step 2: AgentRuntimeFactory 加 createAdmin + ADMIN_SYSTEM_PROMPT**

`AgentRuntimeFactory.java` 加常量与方法：

```java
    private static final String ADMIN_SYSTEM_PROMPT = """
            你是吉味生活平台的运营智能助手，帮助平台运营人员分析经营数据、审核内容、检索运营规则、给出商户建议。

            可用工具：
            - queryPlatformData: 查询平台经营数据（订单/营收/商户排行/菜品销量）
            - auditContent: 审核笔记/评价内容是否合规
            - searchKnowledgeBase: 检索运营知识库（制度/流程/入驻规则）
            - merchantSuggestion: 针对某商户生成经营建议

            回答要求：
            1. 涉及数据的问题，先调用 queryPlatformData 获取真实数据，再基于数据回答
            2. 涉及违规判断的问题，调用 auditContent，给出通过/不通过及理由
            3. 涉及制度流程的问题，调用 searchKnowledgeBase 检索后回答
            4. 不要编造查询结果里没有的数据
            """;

    public JChatMind createAdmin(String model, String sessionId, Long empId, String message) {
        return createAdmin(model, sessionId, empId, message, List.of(), sseEventSink);
    }

    public JChatMind createAdmin(String model, String sessionId, Long empId, String message,
                                 List<Message> history, AgentEventSink sink) {
        ChatClient client = registry.get(model);
        AdminTools adminTools = new AdminTools(registry, sqlValidator, bizJdbcTemplate, auditAgent, ragService);
        List<ToolCallback> tools = Arrays.asList(MethodToolCallbackProvider.builder()
                .toolObjects(adminTools).build().getToolCallbacks());
        List<Message> memory = new ArrayList<>(history);
        memory.add(new UserMessage(message));
        return new JChatMind(client, ADMIN_SYSTEM_PROMPT, tools, sink, sessionId, memory,
                agentProperties.getMaxIterations(), agentProperties.getTimeoutSeconds());
    }
```

需要在 `AgentRuntimeFactory` 构造器注入 `AuditAgent`、`RagService`（新字段）。

- [ ] **Step 3: 给 ExploreAgent/MerchantAgent 加续接重载**

为让 `/ai/agent/stream`、`/ai/merchant/stream` 也支持 conversationId 续接，给两个 Agent 加带 history + sink 的重载 `execute`：

`ExploreAgent.java` 追加：

```java
    public JChatMind execute(String model, String sessionId, String message,
                             List<Message> history, AgentEventSink sink) {
        return factory.createExplore(model, sessionId, message, history, sink);
    }
```

`MerchantAgent.java` 追加：

```java
    public JChatMind execute(String model, String sessionId, String question, Long empId,
                             List<Message> history, AgentEventSink sink) {
        Long merchantId = resolveMerchantId(empId);
        if (merchantId == null) {
            return null;
        }
        return factory.createMerchant(model, sessionId, merchantId, question, history, sink);
    }
```

两个文件的 import 增加：`org.springframework.ai.chat.messages.Message`、`com.jiwei.ai.sse.AgentEventSink`、`java.util.List`。

- [ ] **Step 4: AgentController 注入新依赖并加 GET /ai/admin/stream + 会话续接**

`AgentController.java` 改造：

1. 构造器新增参数：`AgentRuntimeFactory agentRuntimeFactory`、`ConversationService conversationService`、`SseEventSink sseEventSink`（Spring 自动注入）
2. 新增 `adminChat`：

```java
    @GetMapping(value = "/admin/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter adminChat(@RequestParam String message,
                                @RequestParam Long empId,
                                @RequestParam(defaultValue = "deepseek") String model,
                                @RequestParam(required = false) String conversationId) {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = sseService.connect(sessionId);
        EXECUTOR.execute(() -> {
            try {
                List<Message> history = loadHistory(conversationId);
                JChatMind runtime = agentRuntimeFactory.createAdmin(model, sessionId, empId, message, history, sseEventSink);
                runtime.run();
            } catch (Exception e) {
                log.error("管理端助手执行失败", e);
                sseService.send(sessionId, new AgentEvent("error", e.getMessage()));
            } finally {
                sseService.close(sessionId);
            }
        });
        return emitter;
    }
```

3. 改造 `exploreChat` 与 `merchantChat`：方法签名加 `@RequestParam(required = false) String conversationId`，调用改为：

```java
    // exploreChat 内
    List<Message> history = loadHistory(conversationId);
    exploreAgent.execute(model, sessionId, message, history, sseEventSink).run();

    // merchantChat 内
    List<Message> history = loadHistory(conversationId);
    JChatMind runtime = merchantAgent.execute(model, sessionId, question, empId, history, sseEventSink);
    if (runtime == null) {
        sseService.send(sessionId, new AgentEvent("message",
                "未找到对应的商户信息，请联系管理员确认账号绑定。"));
    } else {
        runtime.run();
    }
```

4. 私有方法：

```java
    private List<Message> loadHistory(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        return ConversationHistoryLoader.toMessages(
                conversationService.getRecentMessages(conversationId, 10));
    }
```

import 增加：`org.springframework.ai.chat.messages.Message`、`com.jiwei.ai.agent.ConversationHistoryLoader`、`com.jiwei.ai.agent.AgentRuntimeFactory`、`com.jiwei.ai.sse.SseEventSink`、`com.jiwei.ai.service.ConversationService`、`java.util.List`。

- [ ] **Step 4: 编译验证**

Run: `cd jiwei-life && mvn compile -pl jiwei-ai -DskipTests -q`
Expected: 成功

- [ ] **Step 5: curl 冒烟（ADMIN 非流式）**

Run:
```bash
curl -s -X POST http://localhost:8087/ai/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"agentType":"ADMIN","message":"本周营收TOP3商户","empId":1}' | head -c 800
```
Expected: 返回含 events（应含 action 工具调用 + message 结论）。若未重启 AI 服务则跳过。

---

### Task 5: 后端整体编译 + 冒烟验证

**Files:** 无新增

- [ ] **Step 1: 全量编译**

Run: `cd jiwei-life && mvn compile -pl jiwei-common,jiwei-pojo,jiwei-framework,jiwei-modules,jiwei-ai -DskipTests -q`
Expected: 无输出（成功）

- [ ] **Step 2: 单元测试**

Run: `cd jiwei-life && mvn test -pl jiwei-ai -Dtest='ListEventSinkTest,ConversationMessageLoaderTest' -DskipTests=false -q`
Expected: 两个测试 PASS

- [ ] **Step 3: 记录验收**

本任务无代码产物，作为后端完成里程碑。在 IDEA 重启 AI 服务（8087）后，用 curl 验证三个 SSE 端点可连、`POST /ai/agent/chat` 返回结构正确（用户随后在 IDE 重启时执行）。

---

### Task 6: 管理端前端（新增 AgentAssistant + 模型切换 + 会话）

**Files:**
- Modify: `jiwei-admin/src/store/user.ts`
- Create: `jiwei-admin/src/views/ai/AgentAssistant.vue`
- Modify: `jiwei-admin/src/router/index.ts`
- Modify: `jiwei-admin/src/views/layout/*`（侧边菜单项，按项目现有 Layout 结构）

**Interfaces:**
- Consumes: `GET /ai/conversation/list?userId=` → `[{_id, summary, updatedAt}]`；`GET /ai/admin/stream?message&empId&model&conversationId`（SSE）；`DELETE /ai/conversation/{id}`
- Produces: 管理端 AI 助手页面

- [ ] **Step 1: store/user.ts 补 employeeId**

在 `jiwei-admin/src/store/user.ts` 登录成功后追加 `localStorage.setItem('employeeId', res.data.id)`，并新增 `const employeeId = ref<string>(localStorage.getItem('employeeId') || '')` 暴露。参考现有 `name`/`setLogin` 实现。

- [ ] **Step 2: 创建 AgentAssistant.vue**

完整代码：

```vue
<template>
  <div class="agent-assistant">
    <el-row :gutter="16">
      <el-col :span="6">
        <el-card class="conv-card">
          <template #header>
            <div class="conv-header">
              <span>会话列表</span>
              <el-button size="small" type="primary" @click="newConversation">+ 新建</el-button>
            </div>
          </template>
          <div class="conv-list">
            <div v-for="c in conversations" :key="c._id"
                 :class="['conv-item', { active: c._id === conversationId }]"
                 @click="switchConversation(c)">
              <div class="conv-title">{{ c.summary || '未命名会话' }}</div>
              <el-button size="small" text type="danger" @click.stop="removeConversation(c)">删除</el-button>
            </div>
            <el-empty v-if="!conversations.length" description="暂无历史会话" :image-size="60" />
          </div>
        </el-card>
      </el-col>

      <el-col :span="18">
        <el-card>
          <template #header>
            <div class="chat-header">
              <span>🤖 平台运营智能助手</span>
              <el-select v-model="model" size="small" style="width: 140px" @change="saveModel">
                <el-option label="DeepSeek" value="deepseek" />
                <el-option label="Kimi" value="kimi" />
                <el-option label="通义千问" value="qwen" />
              </el-select>
            </div>
          </template>

          <div class="chat-messages" ref="chatRef">
            <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role === 'user' ? 'msg-user' : 'msg-ai']">
              <div class="msg-bubble">
                <span v-if="m.role === 'ai'">🤖 </span>{{ m.content }}
                <div v-if="m.tools && m.tools.length" class="tool-list">
                  <el-tag v-for="t in m.tools" :key="t" size="small" type="warning">🔧 {{ t }}</el-tag>
                </div>
              </div>
            </div>
            <div v-if="loading" class="msg msg-ai">
              <div class="msg-bubble">
                <span>🤖 正在思考
                  <template v-if="currentTool"> → 调用工具: {{ currentTool }}</template>
                </span>
              </div>
            </div>
          </div>

          <div class="quick-questions">
            <el-tag v-for="q in quickQuestions" :key="q" class="quick-tag" @click="send(q)">{{ q }}</el-tag>
          </div>

          <div class="chat-input">
            <el-input v-model="input" placeholder="问我任何平台运营问题..." size="large"
                      @keyup.enter="send(input)">
              <template #append>
                <el-button :loading="loading" @click="send(input)">发送</el-button>
              </template>
            </el-input>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const EMP_ID = localStorage.getItem('employeeId') || '1'
const chatRef = ref<HTMLDivElement>()
const input = ref('')
const loading = ref(false)
const currentTool = ref('')
const model = ref(localStorage.getItem('aiModel') || 'deepseek')
const conversationId = ref('')
const conversations = ref<any[]>([])
const messages = reactive<{ role: string; content: string; tools: string[] }[]>([
  { role: 'ai', content: '你好！我是平台运营智能助手。可以问我经营数据、内容审核、运营制度、商户建议等问题。', tools: [] }
])

const quickQuestions = ['本周营收TOP3商户', '分析一下最近的差评原因', '入驻流程是什么？', '给我第1号商户的经营建议']

const saveModel = () => localStorage.setItem('aiModel', model.value)

const scrollToBottom = () => {
  nextTick(() => { if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight })
}

const loadConversations = async () => {
  const res = await fetch(`/ai/conversation/list?userId=${EMP_ID}`)
  const list = await res.json()
  conversations.value = Array.isArray(list) ? list : []
}

const newConversation = () => {
  conversationId.value = ''
  messages.splice(0, messages.length, { role: 'ai', content: '新会话已开始，请问你想了解什么？', tools: [] })
  loadConversations()
}

const switchConversation = (c: any) => {
  conversationId.value = c._id
  messages.splice(0, messages.length, { role: 'ai', content: '已切换到历史会话：' + (c.summary || ''), tools: [] })
}

const removeConversation = async (c: any) => {
  await fetch(`/ai/conversation/${c._id}`, { method: 'DELETE' })
  ElMessage.success('已删除')
  loadConversations()
}

async function send(text?: string) {
  const question = (text ?? input.value).trim()
  if (!question || loading.value) return
  input.value = ''
  messages.push({ role: 'user', content: question, tools: [] })
  loading.value = true
  scrollToBottom()

  const aiMsg = reactive({ role: 'ai', content: '', tools: [] })
  messages.push(aiMsg)
  const tools = new Set<string>()

  const es = new EventSource(`/ai/admin/stream?message=${encodeURIComponent(question)}&empId=${EMP_ID}&model=${model.value}${conversationId.value ? '&conversationId=' + conversationId.value : ''}`)
  let finished = false
  es.addEventListener('message', (e) => {
    try {
      const data = JSON.parse(e.data)
      if (data.type === 'message' && data.content) {
        aiMsg.content += data.content
        scrollToBottom()
      } else if (data.type === 'action') {
        const name = (data.content || '').split(' ')[0]
        if (name) {
          tools.add(name)
          aiMsg.tools = Array.from(tools)
          currentTool.value = name
        }
      } else if (data.type === 'error') {
        aiMsg.content = (aiMsg.content || '') + (data.content || '出错了')
      }
    } catch { /* 忽略无法解析的帧 */ }
  })
  es.addEventListener('done', () => {
    finished = true
    currentTool.value = ''
    es.close()
    loading.value = false
    if (!conversationId.value && aiMsg.content) {
      // 首轮后刷新会话列表
      loadConversations()
    }
    scrollToBottom()
  })
  es.onerror = () => {
    es.close()
    loading.value = false
    if (!finished && !aiMsg.content) aiMsg.content = '连接中断，请稍后重试。'
  }
}

onMounted(loadConversations)
</script>

<style scoped>
.agent-assistant { padding: 16px; }
.conv-header { display: flex; justify-content: space-between; align-items: center; }
.conv-list { max-height: 480px; overflow-y: auto; }
.conv-item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 8px 8px; border-radius: 6px; cursor: pointer;
}
.conv-item.active { background: var(--el-color-primary-light-9); }
.conv-title { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat-header { display: flex; justify-content: space-between; align-items: center; }
.chat-messages { height: 460px; overflow-y: auto; padding: 12px; background: var(--jiwei-bg-page, #f7f6f2); border-radius: 8px; margin-bottom: 10px; }
.msg { margin-bottom: 10px; display: flex; }
.msg-user { justify-content: flex-end; }
.msg-bubble {
  max-width: 78%; padding: 10px 14px; border-radius: 10px;
  font-size: 14px; line-height: 1.6; white-space: pre-wrap;
}
.msg-user .msg-bubble { background: var(--el-color-primary); color: #fff; border-bottom-right-radius: 3px; }
.msg-ai .msg-bubble { background: #fff; border: 1px solid var(--el-border-color); border-bottom-left-radius: 3px; }
.tool-list { margin-top: 8px; display: flex; gap: 6px; flex-wrap: wrap; }
.quick-questions { margin-bottom: 8px; }
.quick-tag { cursor: pointer; margin: 0 6px 6px 0; }
.chat-input { margin-top: 4px; }
</style>
```

- [ ] **Step 3: vite 代理加 /ai**

`vite.config.ts` 的 `server.proxy` 里，在现有 `/api` 后追加：

```ts
'/ai': {
  target: 'http://localhost:8087',
  changeOrigin: true
}
```

（管理端 dev server 需要 `/ai/admin/stream` 与 `/ai/conversation/*` 转发到 AI 服务；管理端当前只配了 `/api`。）

- [ ] **Step 4: 路由 + 菜单**

在 `jiwei-admin/src/router/index.ts` 的 MainLayout `children` 数组末尾加：

```ts
{
  path: 'ai-assistant',
  name: 'AgentAssistant',
  component: () => import('@/views/ai/AgentAssistant.vue'),
  meta: { title: 'AI 智能助手', icon: 'MagicStick' }
}
```

在 `src/views/layout/MainLayout.vue` 的 `menuGroups` 数组末尾追加一组（放在"系统"组之后）：

```ts
{
  title: '智能',
  items: [{ path: '/ai-assistant', title: 'AI 智能助手', icon: 'MagicStick' }]
}
```

- [ ] **Step 5: 前端构建验证**

Run: `cd jiwei-life/jiwei-admin && npm run build`
Expected: 构建成功

---

### Task 7: 商家端前端增强（模型切换 + 会话 + 工具展示）

**Files:**
- Modify: `jiwei-merchant/src/views/AiAssistant.vue`

**Interfaces:**
- Consumes: `GET /ai/conversation/list?userId=`、`GET /ai/merchant/stream?question&empId&model&conversationId`、`DELETE /ai/conversation/{id}`
- Produces: 增强版商家 AI 助手

- [ ] **Step 1: 改模板——header 加模型下拉与会话控制**

`AiAssistant.vue` 的 `<template #header>` 改为：

```vue
<template #header>
  <div style="display:flex;align-items:center;justify-content:space-between">
    <span>🤖 AI 经营助手</span>
    <div style="display:flex;gap:8px;align-items:center">
      <el-select v-model="model" size="small" style="width:120px" @change="saveModel">
        <el-option label="DeepSeek" value="deepseek" />
        <el-option label="Kimi" value="kimi" />
        <el-option label="通义千问" value="qwen" />
      </el-select>
      <el-button size="small" text @click="newConversation">新会话</el-button>
      <el-dropdown trigger="click" @command="switchConversation">
        <el-button size="small" text>历史会话 ▾</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="c in conversations" :key="c._id" :command="c">
              {{ c.summary || '未命名会话' }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>
```

在聊天消息区的"正在思考"占位处，加当前工具提示（改为条件渲染）：

```vue
<div v-if="loading" class="msg msg-ai">
  <div class="msg-content">🤖 正在思考{{ currentTool ? ' → 调用: ' + currentTool : '...' }}</div>
</div>
```

- [ ] **Step 2: 改脚本——模型/会话状态 + action 展示 + 会话续接**

`AiAssistant.vue` 的 `<script setup>` 在现有基础上增加：

```ts
import { onMounted } from 'vue'

const model = ref(localStorage.getItem('aiModel') || 'deepseek')
const conversationId = ref('')
const currentTool = ref('')
const conversations = ref<any[]>([])

const saveModel = () => localStorage.setItem('aiModel', model.value)

const loadConversations = async () => {
  const empId = localStorage.getItem('empId')
  const res = await fetch(`/ai/conversation/list?userId=${empId}`)
  const list = await res.json()
  conversations.value = Array.isArray(list) ? list : []
}

const newConversation = () => {
  conversationId.value = ''
  messages.splice(0, messages.length, { role: 'ai', content: '新会话已开始，请问你想了解什么？' })
}

const switchConversation = (c: any) => {
  conversationId.value = c._id
  messages.splice(0, messages.length, { role: 'ai', content: '已切换会话：' + (c.summary || '') })
}
```

`send()` 中 EventSource URL 改为：

```ts
const url = `/ai/merchant/stream?question=${encodeURIComponent(question)}&empId=${empId}&model=${model.value}${conversationId.value ? '&conversationId=' + conversationId.value : ''}`
const es = new EventSource(url)
```

`message` 事件监听改为同时处理 `action`（工具调用）：

```ts
es.addEventListener('message', (e) => {
  try {
    const data = JSON.parse(e.data)
    if (data.type === 'action') {
      currentTool.value = (data.content || '').split(' ')[0]
    } else if (data.type === 'message' && data.content) {
      aiMsg.content += data.content
      nextTick(() => scrollToBottom())
    }
  } catch { /* 忽略无法解析的帧 */ }
})
```

`done` 事件监听改为：

```ts
es.addEventListener('done', () => {
  finished = true
  currentTool.value = ''
  es.close()
  loading.value = false
  if (!conversationId.value && aiMsg.content) loadConversations()
  nextTick(() => scrollToBottom())
})
```

末尾加 `onMounted(loadConversations)`。

- [ ] **Step 3: 前端构建验证**

Run: `cd jiwei-life/jiwei-merchant && npm run build`
Expected: 构建成功

---

### Task 8: 用户端小程序接入非流式 Agent

**Files:**
- Modify: `jiwei-miniapp/src/api/index.js`
- Modify: `jiwei-miniapp/src/pages/aichat/aichat.vue`

**Interfaces:**
- Consumes: `POST /ai/agent/chat` → `{conversationId, events:[{type,content}], finalAnswer}`
- Produces: `aiAgentChat(data)` API；Agent 版对话页

- [ ] **Step 1: api/index.js 加 aiAgentChat**

在 AI 推荐附近加：

```js
// ===== AI Agent 对话 =====
export const aiAgentChat = (data) => {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token')
    uni.request({
      url: AI_BASE_URL + '/ai/agent/chat',
      method: 'POST',
      data,
      header: { 'Content-Type': 'application/json', ...(token ? { Authorization: token } : {}) },
      success: (res) => {
        if (res.statusCode === 200) resolve(res.data)
        else reject(new Error(res.data?.msg || 'AI服务异常'))
      },
      fail: reject
    })
  })
}
```

并在 default 导出中加入 `aiAgentChat`。

- [ ] **Step 2: 改造 aichat.vue**

将脚本中的 `send` 改为 Agent 调用，并新增会话记忆：

```js
import { aiAgentChat } from '@/api/index.js'

const conversationId = ref(uni.getStorageSync('aiConvId') || '')

const send = async (text) => {
  const kw = (text || '').trim()
  if (!kw || loading.value) return
  input.value = ''
  userMessages.value.push(kw)
  loading.value = true
  const cur = messages.value.length
  messages.value.push({ text: '🤖 正在规划中...', dishes: [] })
  try {
    const res = await aiAgentChat({
      agentType: 'EXPLORE',
      message: kw,
      model: 'deepseek',
      userId: uni.getStorageSync('userInfo')?.id || 1,
      conversationId: conversationId.value || null
    })
    if (res.conversationId) {
      conversationId.value = res.conversationId
      uni.setStorageSync('aiConvId', res.conversationId)
    }
    // 工具调用过程
    const toolNames = (res.events || [])
      .filter(e => e.type === 'action')
      .map(e => (e.content || '').split(' ')[0])
    const toolText = toolNames.length ? '\n\n🔧 已检索：' + [...new Set(toolNames)].join('、') : ''
    messages.value.push({ text: res.finalAnswer + toolText, dishes: [] })
  } catch (e) {
    messages.value.push({ text: 'AI 服务暂不可用，请稍后再试。', dishes: [] })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}
```

模板不变（消息列表 + 快捷问题 + 输入栏），保留 `goShop` 函数供未来结果卡片使用（本版本 Agent 返回纯文本，不渲染菜品卡片）。

- [ ] **Step 3: 小程序构建验证**

Run: `cd jiwei-life/jiwei-miniapp && npm run build:mp-weixin`
Expected: Build complete

---

### Task 9: 整体验证

**Files:** 无新增

- [ ] **Step 1: 三个前端构建**

Run:
```bash
cd jiwei-life/jiwei-admin && npm run build
cd jiwei-life/jiwei-merchant && npm run build
cd jiwei-life/jiwei-miniapp && npm run build:mp-weixin
```
Expected: 全部成功

- [ ] **Step 2: 后端编译**

Run: `cd jiwei-life && mvn compile -pl jiwei-common,jiwei-pojo,jiwei-framework,jiwei-modules,jiwei-ai -DskipTests -q`
Expected: 成功

- [ ] **Step 3: 人工验收路径（交付用户）**

在 IDEA 重启 AI 服务（8087）后，验证：
1. 管理端"AI 智能助手"：问"本周营收TOP3商户" → SSE 流式输出 + 工具标签；切换模型后回答风格变化；新建/切换/删除会话
2. 商家端：模型下拉切换 + 历史会话续接（"上周什么菜卖得最好"后追问"那差评呢"）
3. 小程序：aichat 问"带我找一家适合深夜的烧烤店" → 非流式返回结论 + 工具过程；再次提问能记住上文
