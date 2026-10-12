# AI 服务重写（jiwei-ai）实现计划

> ⚠️ **实现现状对照（2026-10-12）**：本文 Task 6 的 `SqlValidator` **黑名单正则缺少 DOTALL**，
> 实测带换行的 SQL（`SELECT 1\nDELETE FROM ...`）会让整条黑名单**静默失效**（现已加 `(?s)`，
> 并补语句白名单与表名提取收口）；只读数据源从「建只读连接」升级为
> 「只读账号 + 连接级 readOnly + 关闭多语句 + **启动写探针自检**（确认可写即拒绝启动）」；
> RAG 当时只有单路向量，现已补齐关键词召回 + RRF 融合 + 大模型重排 + Embedding 故障降级。
> 见 [`docs/实现现状对照.md`](../实现现状对照.md) §5–§7。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 JChatMind 的手写 Agent Loop 运行时（状态机 + ToolCallingManager + 多模型注册表）替换 jiwei-ai 现有的「ChatClient 托管 ReAct」，并把探店/商家问数/审核三个业务 Agent 重写为该运行时之上的工具实例。

**Architecture:** 引入 `JChatMind` 运行时类（think→execute→step→run 循环，`AgentState` 状态机，`MAX_STEPS=20` 硬上限），关闭 Spring AI 内部工具自动执行、用 `ToolCallingManager` 手动执行；`ChatClientRegistry` 注册 deepseek/Qwen/Kimi 三个模型；SSE 从 Flux 拉取式改为 SseEmitter 推送式。业务能力（推荐引擎、向量同步管道）不动。

**Tech Stack:** JDK 21（虚拟线程）、Spring AI 1.1.x、spring-ai-starter-model-deepseek / dashscope / openai、pgvector、RocketMQ、MongoDB、flexmark（Markdown 分块）、JUnit 5。

**Spec:** `docs/superpowers/specs/2026-08-26-wheel-integration-design.md`（§5.1 AI 服务）

**源项目参考：**
- JChatMind：`D:\qiuzhao\jchatmind_v2\JChatMind\jchatmind\src\main\java\com\kama\jchatmind\`
- 智能问数：`D:\qiuzhao\intelligent-data-query-system\data-agent-backend-java\src\main\java\io\github\qifan777\server\`

## Global Constraints

- 包名统一 `com.jiwei.ai`（沿用 jiwei-ai 现有包结构，不引入 `com.kama.jchatmind`）
- Spring AI 版本对齐到 1.1.x 最新稳定（当前 jiwei-ai 用 `${spring-ai.version}`，JChatMind 用 BOM 1.1.0，智能问数用 1.1.2）
- 多模型固定为 `deepseek` + `qwen` + `kimi`（用户已确认，无 glm）
- Embedding 保持现有 bge-m3（走 SiliconFlow API，**不**换成 JChatMind 的本地 Ollama）
- 对话历史持久化用 jiwei-ai 现有的 `ConversationService`（MongoDB），**不**引入 JChatMind 的 `ChatMessageFacadeService`（MySQL）
- `RecommendEngine`、`EmbeddingConsumer`（RocketMQ 向量同步）**保持不变**
- 每个 Task 完成后 `git add` 具体文件并 commit，禁止 `git add -A`

---

## File Structure 概览

**新建：**
- `jiwei-ai/src/main/java/com/jiwei/ai/config/ChatClientRegistry.java` — 多模型注册表（Map<String, ChatClient>）
- `jiwei-ai/src/main/java/com/jiwei/ai/config/MultiChatClientConfig.java` — 注册 3 个 ChatClient bean
- `jiwei-ai/src/main/java/com/jiwei/ai/agent/AgentState.java` — Agent 状态枚举
- `jiwei-ai/src/main/java/com/jiwei/ai/agent/JChatMind.java` — Agent Loop 运行时（核心）
- `jiwei-ai/src/main/java/com/jiwei/ai/agent/AgentRuntimeFactory.java` — 业务 Agent 工厂（角色 + 工具集）
- `jiwei-ai/src/main/java/com/jiwei/ai/sse/SseService.java` + `SseServiceImpl.java` — 推送式 SSE
- `jiwei-ai/src/main/java/com/jiwei/ai/rag/MarkdownParserService.java` + `impl/MarkdownParserServiceImpl.java` — Markdown 分块

**修改：**
- `jiwei-ai/pom.xml` — 加多模型 starter + flexmark
- `jiwei-ai/src/main/resources/application.yml` — 多模型配置
- `jiwei-ai/src/main/java/com/jiwei/ai/agent/{ExploreAgent,MerchantAgent,AuditAgent}.java` — 重写为工具/实例
- `jiwei-ai/src/main/java/com/jiwei/ai/rag/RagService.java` — 分块改用 MarkdownParserService
- `jiwei-ai/src/main/java/com/jiwei/ai/nlsql/SqlValidator.java` — 补强注释剥离/字符串剥离/分号计数
- `jiwei-ai/src/main/java/com/jiwei/ai/controller/AgentController.java` — 适配 SseEmitter

**测试：**
- `jiwei-ai/src/test/java/com/jiwei/ai/nlsql/SqlValidatorTest.java`
- `jiwei-ai/src/test/java/com/jiwei/ai/rag/MarkdownParserServiceTest.java`

---

## Task 1: 多模型注册表 + 依赖对齐

**Files:**
- Modify: `jiwei-ai/pom.xml`
- Modify: `jiwei-ai/src/main/resources/application.yml`
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/config/ChatClientRegistry.java`
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/config/MultiChatClientConfig.java`

**Interfaces:**
- Consumes: 无（第一个任务）
- Produces: `ChatClientRegistry.get(String key) -> ChatClient`；三个 bean 名 `deepseek`/`qwen`/`kimi`

**步骤：**

- [ ] **Step 1: pom.xml 增加多模型 starter**

在 `jiwei-ai/pom.xml` 的 `<dependencies>` 中，替换现有的 `spring-ai-openai-spring-boot-starter` 为三个模型的 starter（版本用 `${spring-ai.version}`）：

```xml
<!-- DeepSeek -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-deepseek</artifactId>
    <version>${spring-ai.version}</version>
</dependency>
<!-- 通义千问 Qwen（DashScope） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-dashscope</artifactId>
    <version>${spring-ai.version}</version>
</dependency>
<!-- Kimi（OpenAI 兼容，用 openai starter + moonshot base-url） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
    <version>${spring-ai.version}</version>
</dependency>
<!-- Markdown 分块 -->
<dependency>
    <groupId>com.vladsch.flexmark</groupId>
    <artifactId>flexmark-all</artifactId>
    <version>0.64.8</version>
</dependency>
```

注意：若 `spring-ai-starter-model-dashscope` 在当前版本不可用，则 Qwen 改用 openai starter（Qwen 兼容 base-url `https://dashscope.aliyuncs.com/compatible-mode/v1`）。以 `mvn dependency:resolve` 实际解析为准。

- [ ] **Step 2: application.yml 增加三个模型配置**

在 `application.yml` 的 `spring.ai` 下，保留现有 deepseek 配置，新增 qwen/kimi（embedding 段保持不变）：

```yaml
spring:
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}
      chat:
        options:
          model: deepseek-chat
          temperature: 0.7
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-plus
    openai:
      base-url: ${KIMI_BASE_URL:https://api.moonshot.cn/v1}
      api-key: ${KIMI_API_KEY}
      chat:
        options:
          model: moonshot-v1-8k
```

- [ ] **Step 3: 创建 ChatClientRegistry**

```java
package com.jiwei.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ChatClientRegistry {
    private final Map<String, ChatClient> chatClients;

    public ChatClientRegistry(Map<String, ChatClient> chatClients) {
        this.chatClients = chatClients;
    }

    public ChatClient get(String key) {
        return chatClients.get(key);
    }
}
```

- [ ] **Step 4: 创建 MultiChatClientConfig**

```java
package com.jiwei.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.dashscope.DashScopeChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MultiChatClientConfig {

    @Bean("deepseek")
    public ChatClient deepSeekChatClient(DeepSeekChatModel model) {
        return ChatClient.create(model);
    }

    @Bean("qwen")
    public ChatClient qwenChatClient(DashScopeChatModel model) {
        return ChatClient.create(model);
    }

    @Bean("kimi")
    public ChatClient kimiChatClient(OpenAiChatModel model) {
        return ChatClient.create(model);
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `cd jiwei-life/jiwei-ai && mvn compile -DskipTests`
Expected: BUILD SUCCESS，三个 ChatClient bean 能被 Spring 容器加载（若报类不存在，按 Step 1 的 fallback 调整 starter）

- [ ] **Step 6: Commit**

```bash
git add jiwei-ai/pom.xml jiwei-ai/src/main/resources/application.yml jiwei-ai/src/main/java/com/jiwei/ai/config/
git commit -m "feat(ai): 引入 deepseek/qwen/kimi 多模型注册表"
```

---

## Task 2: Agent Loop 运行时（JChatMind 核心移植）

**Files:**
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/agent/AgentState.java`
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/agent/JChatMind.java`

**Interfaces:**
- Consumes: `ChatClientRegistry.get(String)`（Task 1）、`SseService`（Task 3 定义，先以接口占位引用）
- Produces: `JChatMind.run()`（同步执行 Agent Loop）、`AgentState` 枚举

**步骤：**

- [ ] **Step 1: 创建 AgentState 枚举**

```java
package com.jiwei.ai.agent;

public enum AgentState {
    IDLE,       // 空闲
    THINKING,   // 思考中
    EXECUTING,  // 执行中
    FINISHED,   // 正常结束
    ERROR       // 错误结束
}
```

- [ ] **Step 2: 创建 JChatMind 运行时（核心移植）**

从源 `D:\qiuzhao\jchatmind_v2\JChatMind\jchatmind\src\main\java\com\kama\jchatmind\agent\JChatMind.java` 移植，做以下适配：

1. 包名改 `com.jiwei.ai.agent`
2. **删除** `availableKbs`、`KnowledgeBaseDTO` 相关字段和 thinkPrompt 里的知识库提示（jiwei-ai 用 RAG 检索，不用知识库列表）
3. `SseService` 换成 `com.jiwei.ai.sse.SseService`（Task 3 提供）
4. `ChatMessageFacadeService`/`ChatMessageConverter`/`ChatMessageDTO` 替换为 jiwei-ai 现有的 `ConversationService`（对话持久化到 MongoDB）；若暂时不做对话持久化，`saveMessage`/`refreshPendingMessages` 降级为只 `sseService.send` 不落库
5. 保留核心的四个方法不动：`think()`（构建 thinkPrompt→chatClient 调用→返回是否有 toolCalls）、`execute()`（ToolCallingManager 执行→检查 terminate）、`step()`、`run()`（for 循环 MAX_STEPS=20）

核心方法签名保持不变：

```java
public class JChatMind {
    private static final Integer MAX_STEPS = 20;
    private AgentState agentState;
    private ChatClient chatClient;
    private List<ToolCallback> availableTools;
    private ChatMemory chatMemory;
    private ToolCallingManager toolCallingManager;
    private ChatOptions chatOptions; // internalToolExecutionEnabled(false)
    private SseService sseService;
    private String chatSessionId;

    private boolean think() { /* 构建决策 prompt，调用 chatClient，saveMessage+refreshPendingMessages，返回 !toolCalls.isEmpty() */ }
    private void execute() { /* toolCallingManager.executeToolCalls，若含 terminate 工具则 FINISHED */ }
    private void step() { if (think()) execute(); else agentState = FINISHED; }
    public void run() { /* for (i<MAX_STEPS && agentState!=FINISHED) step(); 异常置 ERROR */ }
}
```

- [ ] **Step 3: 编译验证（SseService 用临时空实现）**

若 Task 3 尚未做，先建一个最小 `SseService` 接口 + 空实现让编译通过：

```java
package com.jiwei.ai.sse;
public interface SseService {
    void send(String sessionId, Object message);
}
```

Run: `cd jiwei-life/jiwei-ai && mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add jiwei-ai/src/main/java/com/jiwei/ai/agent/
git commit -m "feat(ai): 移植 JChatMind 手写 Agent Loop 运行时与状态机"
```

---

## Task 3: 推送式 SSE 服务

**Files:**
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/sse/SseService.java`
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/sse/SseServiceImpl.java`
- Modify: `jiwei-ai/src/main/java/com/jiwei/ai/controller/AgentController.java`

**Interfaces:**
- Consumes: `AgentEvent`（现有 dto）
- Produces: `SseService.connect(String sessionId) -> SseEmitter`、`SseService.send(String sessionId, Object message)`

**步骤：**

- [ ] **Step 1: 定义 SseService 接口**

```java
package com.jiwei.ai.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SseService {
    SseEmitter connect(String sessionId);
    void send(String sessionId, Object message);
    void close(String sessionId);
}
```

- [ ] **Step 2: 实现 SseServiceImpl（ConcurrentHashMap 管理连接）**

```java
package com.jiwei.ai.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SseServiceImpl implements SseService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter connect(String sessionId) {
        SseEmitter emitter = new SseEmitter(0L); // 0 = 不超时
        emitters.put(sessionId, emitter);
        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> emitters.remove(sessionId));
        emitter.onError(e -> emitters.remove(sessionId));
        return emitter;
    }

    @Override
    public void send(String sessionId, Object message) {
        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) return;
        try {
            emitter.send(message);
        } catch (IOException e) {
            emitters.remove(sessionId);
            log.warn("SSE 发送失败，session={}: {}", sessionId, e.getMessage());
        }
    }

    @Override
    public void close(String sessionId) {
        SseEmitter emitter = emitters.remove(sessionId);
        if (emitter != null) emitter.complete();
    }
}
```

- [ ] **Step 3: 修改 AgentController，从 Flux 拉取式改为 SseEmitter 推送式**

`AgentController` 的 `/ai/agent/stream` 改为：先 `connect` 建立连接，再异步 `run` Agent Loop（`@Async` 或虚拟线程），Agent 内部通过 `sseService.send` 推送。

```java
@GetMapping(value = "/agent/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter exploreChat(@RequestParam String message,
                              @RequestParam(defaultValue = "deepseek") String model) {
    String sessionId = UUID.randomUUID().toString();
    SseEmitter emitter = sseService.connect(sessionId);
    // 异步执行 Agent Loop，内部 sseService.send(sessionId, event)
    executor.execute(() -> agentRuntimeFactory.createExplore(model, sessionId).run());
    return emitter;
}
```

> 注意：`agentRuntimeFactory`（`AgentRuntimeFactory`）在 Task 4 才定义，本 Task 编译前可先注入临时占位（同 Task 2 Step 3 对 `SseService` 的做法），或把 `AgentController` 的完整接线延迟到 Task 4 一并完成，确保本 Task 编译通过。

- [ ] **Step 4: 编译验证**

Run: `cd jiwei-life/jiwei-ai && mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add jiwei-ai/src/main/java/com/jiwei/ai/sse/ jiwei-ai/src/main/java/com/jiwei/ai/controller/AgentController.java
git commit -m "feat(ai): SSE 改为 SseEmitter 推送式，支持 Agent Loop 内部主动推送"
```

---

## Task 4: 业务 Agent 重写为运行时实例

**Files:**
- Modify: `jiwei-ai/src/main/java/com/jiwei/ai/agent/ExploreAgent.java`
- Modify: `jiwei-ai/src/main/java/com/jiwei/ai/agent/MerchantAgent.java`
- Modify: `jiwei-ai/src/main/java/com/jiwei/ai/agent/AuditAgent.java`
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/agent/AgentRuntimeFactory.java`

**Interfaces:**
- Consumes: `JChatMind`（Task 2）、`ChatClientRegistry`（Task 1）、`ExploreTools`（现有）
- Produces: `AgentRuntimeFactory.createExplore(String model, String sessionId) -> JChatMind`

**步骤：**

- [ ] **Step 1: 创建 AgentRuntimeFactory，把业务 Agent 定义为「角色 + 工具集」**

```java
package com.jiwei.ai.agent;

import com.jiwei.ai.config.ChatClientRegistry;
import com.jiwei.ai.sse.SseService;
import com.jiwei.ai.tool.ExploreTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AgentRuntimeFactory {
    private final ChatClientRegistry registry;
    private final SseService sseService;
    private final ExploreTools exploreTools;

    public AgentRuntimeFactory(ChatClientRegistry registry, SseService sseService, ExploreTools exploreTools) {
        this.registry = registry;
        this.sseService = sseService;
        this.exploreTools = exploreTools;
    }

    public JChatMind createExplore(String model, String sessionId) {
        ChatClient client = registry.get(model);
        List<ToolCallback> tools = MethodToolCallbackProvider.builder()
                .toolObjects(exploreTools).build().getToolCallbacks();
        return new JChatMind(client, EXPLORE_SYSTEM_PROMPT, tools, sseService, sessionId);
    }

    private static final String EXPLORE_SYSTEM_PROMPT = """
            你是吉味生活的探店助手，帮助用户规划本地约会、聚餐、出游路线。
            可用工具：semanticSearchRestaurant / getUserPreferenceTags / getNearbyPOI / getWeather
            收敛规则：本轮最多调用工具 8 次，达到上限后必须基于已有结果直接回答。
            """;
}
```

- [ ] **Step 2: 重写 ExploreAgent**

删除原来的「ChatClient 托管 ReAct」（`ChatClient.builder(chatModel).defaultTools(...).prompt().stream()`），改为委托 `AgentRuntimeFactory.createExplore`。原 `ExploreTools` 里的 `@Tool` 方法保持不变（它们已经是 Spring AI 1.1.x 的 `@Tool`/`@ToolParam` 注解风格）。

- [ ] **Step 3: 重写 MerchantAgent（商家问数，走 SQL 校验链路）**

商家问数的工具不是 `ExploreTools`，而是一个「Text2SQL」工具：内部调 `SqlValidator`（Task 6 补强后）校验 → 执行只读 SQL。MerchantAgent 定义为「商家经营助手」角色 + `queryBusinessData` 工具。

- [ ] **Step 4: 重写 AuditAgent（内容审核，保持独立）**

AuditAgent 是「同步调用」（`/ai/audit/content` 直接返回审核结果），不走 Agent Loop。**保持现有实现不变**，仅在需要时改为通过 `ChatClientRegistry` 选择模型。

- [ ] **Step 5: 编译验证**

Run: `cd jiwei-life/jiwei-ai && mvn compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add jiwei-ai/src/main/java/com/jiwei/ai/agent/
git commit -m "feat(ai): 业务 Agent 重写为 JChatMind 运行时实例（角色+工具集）"
```

---

## Task 5: RAG 分块升级（Markdown 按 Heading 切）

**Files:**
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/rag/MarkdownParserService.java`
- Create: `jiwei-ai/src/main/java/com/jiwei/ai/rag/impl/MarkdownParserServiceImpl.java`
- Modify: `jiwei-ai/src/main/java/com/jiwei/ai/rag/RagService.java`
- Test: `jiwei-ai/src/test/java/com/jiwei/ai/rag/MarkdownParserServiceTest.java`

**Interfaces:**
- Consumes: 无
- Produces: `MarkdownParserService.extractSections(String markdown) -> List<String>`（按 Heading 切分，返回标题+内容的段落列表）

**步骤：**

- [ ] **Step 1: 写失败测试**

```java
package com.jiwei.ai.rag;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class MarkdownParserServiceTest {
    private final MarkdownParserService parser = new MarkdownParserServiceImpl();

    @Test
    void extractSections_shouldSplitByHeading() {
        String md = """
            # 标题一
            内容一第一段。

            ## 标题二
            内容二。
            """;
        List<String> sections = parser.extractSections(md);
        assertThat(sections).hasSize(2);
        assertThat(sections.get(0)).contains("标题一").contains("内容一第一段");
        assertThat(sections.get(1)).contains("标题二").contains("内容二");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd jiwei-life/jiwei-ai && mvn test -Dtest=MarkdownParserServiceTest`
Expected: 编译失败（`MarkdownParserService` 不存在）

- [ ] **Step 3: 实现 MarkdownParserService**

从源 `D:\qiuzhao\jchatmind_v2\JChatMind\jchatmind\src\main\java\com\kama\jchatmind\service\MarkdownParserService.java` 和 `impl/MarkdownParserServiceImpl.java` 移植，包名改 `com.jiwei.ai.rag`。核心逻辑：用 flexmark 解析 Markdown，`extractSections` 按 Heading 节点切分成「标题 + 正文」段落。

- [ ] **Step 4: 运行测试确认通过**

Run: `cd jiwei-life/jiwei-ai && mvn test -Dtest=MarkdownParserServiceTest`
Expected: PASS

- [ ] **Step 5: RagService 改用 MarkdownParserService**

`RagService.loadDocument` 里的 `chunkText(content, 500, 50)` 替换为 `markdownParserService.extractSections(content)`，每个 section 作为一个 chunk 生成 Embedding 写入 pgvector。

- [ ] **Step 6: Commit**

```bash
git add jiwei-ai/src/main/java/com/jiwei/ai/rag/ jiwei-ai/src/test/java/com/jiwei/ai/rag/
git commit -m "feat(ai): RAG 分块从定长切分升级为 Markdown 按 Heading 切分"
```

---

## Task 6: SQL 校验补强（注释剥离 + 字符串剥离 + 分号计数）

**Files:**
- Modify: `jiwei-ai/src/main/java/com/jiwei/ai/nlsql/SqlValidator.java`
- Test: `jiwei-ai/src/test/java/com/jiwei/ai/nlsql/SqlValidatorTest.java`

**Interfaces:**
- Consumes: 现有 `SqlValidator.validate(String) -> boolean`
- Produces: 增强后的 `validate`，能拦截「注释混淆」和「多语句」注入

**步骤：**

- [ ] **Step 1: 写失败测试（针对新增的三类注入）**

```java
package com.jiwei.ai.nlsql;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SqlValidatorTest {
    private final SqlValidator validator = new SqlValidator();

    @Test
    void shouldRejectMultipleStatements() {
        // 两个独立 SELECT：当前实现无分号计数会误放行（本次要补的漏洞）
        assertThat(validator.validate("SELECT id FROM a; SELECT id FROM b")).isFalse();
    }

    @Test
    void shouldAcceptSemicolonInsideStringLiteral() {
        // 分号在字符串字面量内，非语句分隔符，不应误判为多语句（字符串剥离的回归守卫）
        assertThat(validator.validate("SELECT id, name FROM merchant WHERE remark = 'a;b'")).isTrue();
    }

    @Test
    void shouldAcceptPlainSelect() {
        assertThat(validator.validate("SELECT id, name FROM merchant WHERE id = 1")).isTrue();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `cd jiwei-life/jiwei-ai && mvn test -Dtest=SqlValidatorTest`
Expected: `shouldRejectMultipleStatements` 失败（当前实现无分号计数，会误放行两个 SELECT）；`shouldAcceptSemicolonInsideStringLiteral` 通过（字符串内分号本就不该拦截，作为剥离逻辑的回归守卫）

- [ ] **Step 3: 补强 SqlValidator**

从源 `D:\qiuzhao\intelligent-data-query-system\data-agent-backend-java\src\main\java\io\github\qifan777\server\security\SqlSecurityValidator.java` 移植三个方法，插进现有 `validate` 的最前面：

```java
public boolean validate(String sql) {
    if (sql == null || sql.isEmpty()) return false;

    // 新增：剥离注释与字符串字面量后，检测「中间」的多语句分号
    String noComment = sql.replaceAll("/\\*.*?\\*/", "").replaceAll("--[^\\n]*", "");
    String stripped = stripStrings(noComment).trim();
    // 去掉末尾单个分号（单条语句允许以 ; 结尾），仅拦截中间的语句分隔分号
    if (stripped.endsWith(";")) {
        stripped = stripped.substring(0, stripped.length() - 1).trim();
    }
    if (stripped.contains(";")) {
        log.warn("SQL校验失败: 检测到多语句");
        return false;
    }
    // ... 其余沿用现有大写校验、写关键字黑名单、注入模式、系统表、LIMIT 等逻辑
}

private String stripStrings(String sql) {
    return sql.replaceAll("'[^']*'", "''");
}
```

注意：新增的「多语句分号检测」需插在现有 `validate` 的**最前面**（`startsWith("SELECT")` 校验之前）。注释剥离与现有 `INJECTION_PATTERNS` 里的 `/*`、`--` 黑名单有重叠，但保留无害——它保证注释内分号不会被误判为语句分隔符；字符串剥离同理，保证 `'a;b'` 这类字面量不触发误判。

- [ ] **Step 4: 运行测试确认通过**

Run: `cd jiwei-life/jiwei-ai && mvn test -Dtest=SqlValidatorTest`
Expected: 3 个测试全 PASS

- [ ] **Step 5: Commit**

```bash
git add jiwei-ai/src/main/java/com/jiwei/ai/nlsql/SqlValidator.java jiwei-ai/src/test/java/com/jiwei/ai/nlsql/SqlValidatorTest.java
git commit -m "feat(ai): SQL 校验补强注释剥离/字符串剥离/多语句分号检测"
```

---

## Task 7: 端到端集成验证（不含 pgvector TypeHandler）

**Files:**
- 无新建/修改（本 Task 为验证，pgvector TypeHandler 不落地）

**Interfaces:**
- Consumes: 无
- Produces: 无

**步骤：**

- [ ] **Step 1: 确认不落地 PgVectorTypeHandler**

jiwei-ai 的 `RagService` 用 `JdbcTemplate` 直连 PostgreSQL（**无 MyBatis**，`jiwei-ai/pom.xml` 未引入 mybatis starter），已有 `vectorToString(float[])` 把 `float[]` 手工序列化为 pgvector 的 `"[...]"` 字符串（配合 `?::vector` 参数绑定）。因此**不需要**移植 JChatMind 的 MyBatis `BaseTypeHandler<float[]>`；TypeHandler 仅在 RagService 未来迁到 MyBatis 时才需要，本 plan 不落地。

- [ ] **Step 2: 端到端验证（启动 + 对话）**

Run: `cd jiwei-life/jiwei-ai && mvn spring-boot:run`
Expected: 服务在 :8087 启动，日志显示 deepseek/qwen/kimi 三个 ChatClient bean 加载成功

验证对话（curl）：
```bash
curl -N "http://localhost:8087/ai/agent/stream?message=推荐一家适合约会的西餐厅&model=deepseek"
```
Expected: SSE 流式返回，能观察到 Agent Loop 的「思考→工具调用→回答」过程，且 `semanticSearchRestaurant` 工具被调用

> 若验证暴露问题，回改对应 Task 并 `git add` 具体文件 commit（本 Task 无独立 commit）。

---

## Self-Review 结论

- **Spec 覆盖**：设计文档 §5.1 的 7 项（Agent 循环/工具执行/多模型/RAG 分块/SSE/vector 映射/智能问数 SQL 校验）均有对应 Task；其中「vector 映射」已由现有 `RagService.vectorToString` + JdbcTemplate 覆盖，无需新增 TypeHandler。
- **占位符扫描**：无 TBD/TODO；两处明确的迁移决策（Task 1 的 dashscope starter fallback、Task 7 的 TypeHandler 不落地）非占位符。
- **类型一致性**：`SseService`（Task 3）被 `JChatMind`（Task 2）和 `AgentRuntimeFactory`（Task 4）引用，签名一致；`ChatClientRegistry.get(String)` 在 Task 1 定义、Task 4 消费，一致。
- **两处设计文档偏差已在 plan 修正**：Embedding 已是 bge-m3（无需切换）、SqlValidator 已完善（仅补强而非重建）。
