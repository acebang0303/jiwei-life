package com.jiwei.ai.agent;

import com.jiwei.ai.config.AgentProperties;
import com.jiwei.ai.config.ChatClientRegistry;
import com.jiwei.ai.nlsql.SqlValidator;
import com.jiwei.ai.rag.RagService;
import com.jiwei.ai.sse.AgentEventSink;
import com.jiwei.ai.sse.SseEventSink;
import com.jiwei.ai.tool.ExploreTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 业务 Agent 运行时工厂：把业务 Agent 定义为「角色(systemPrompt) + 工具集」，
 * 按模型 key 从 ChatClientRegistry 取 ChatClient，组装成 JChatMind 运行时实例。
 */
@Component
public class AgentRuntimeFactory {

    private static final String EXPLORE_SYSTEM_PROMPT = """
            你是吉味生活的探店助手，帮助用户规划本地的约会、聚餐、出游路线。

            可用工具：
            - semanticSearchRestaurant: 根据用户需求语义搜索合适的餐厅
            - getUserPreferenceTags: 获取用户的美食偏好标签
            - getNearbyPOI: 搜索附近的兴趣地点
            - getWeather: 获取天气信息

            回答要求：
            1. 每次规划要考虑餐厅的口味匹配度、环境氛围、人均消费是否在预算内
            2. 给出3个详细的路线方案供用户选择
            3. 每个方案包含：餐厅推荐 + 周边活动/地点 + 建议时间安排

            收敛规则：
            1. 本轮最多调用工具 8 次，达到上限后必须基于已有结果直接回答
            2. 同一工具连续调用不要超过 2 次
            3. 已获得足够信息时立即停止调用工具并输出最终方案
            """;

    private static final String MERCHANT_SYSTEM_PROMPT = """
            你是餐饮商家的经营数据分析助手。
            可用工具：queryBusinessData（查询销量/营收/评价等经营数据）。
            工作方式：先调用 queryBusinessData 获取数据，再基于数据用自然、专业、简洁的中文回答商家的问题。
            要求：直接给出结论和数据，不提及SQL/表名/字段名；不编造查询结果里没有的数据；适当给出经营建议。
            """;

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
            3. 涉及制度流程的问题，先调用 searchKnowledgeBase 检索后回答
            4. 知识库自学习：若 searchKnowledgeBase 返回「未检索到相关内容」，而你基于自身知识能回答，请先深度思考给出完整准确的回答，再调用 saveKnowledgeBase 把「问题+回答」沉淀到知识库，供后续检索
            5. 不要编造查询结果里没有的数据
            """;

    private final ChatClientRegistry registry;
    private final SseEventSink sseEventSink;
    private final ExploreTools exploreTools;
    private final SqlValidator sqlValidator;
    private final JdbcTemplate bizJdbcTemplate;
    private final AuditAgent auditAgent;
    private final RagService ragService;
    private final AgentProperties agentProperties;

    public AgentRuntimeFactory(ChatClientRegistry registry,
                               SseEventSink sseEventSink,
                               ExploreTools exploreTools,
                               SqlValidator sqlValidator,
                               @Qualifier("bizJdbcTemplate") JdbcTemplate bizJdbcTemplate,
                               AuditAgent auditAgent,
                               RagService ragService,
                               AgentProperties agentProperties) {
        this.registry = registry;
        this.sseEventSink = sseEventSink;
        this.exploreTools = exploreTools;
        this.sqlValidator = sqlValidator;
        this.bizJdbcTemplate = bizJdbcTemplate;
        this.auditAgent = auditAgent;
        this.ragService = ragService;
        this.agentProperties = agentProperties;
    }

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

    public JChatMind createAdmin(String model, String sessionId, Long empId, String message) {
        return createAdmin(model, sessionId, empId, message, List.of(), sseEventSink);
    }

    public JChatMind createAdmin(String model, String sessionId, Long empId, String message,
                                 List<Message> history, AgentEventSink sink) {
        ChatClient client = registry.get(model);
        AdminTools adminTools = new AdminTools(registry, sqlValidator, bizJdbcTemplate, auditAgent, ragService, model);
        List<ToolCallback> tools = Arrays.asList(MethodToolCallbackProvider.builder()
                .toolObjects(adminTools).build().getToolCallbacks());
        List<Message> memory = new ArrayList<>(history);
        memory.add(new UserMessage(message));
        return new JChatMind(client, ADMIN_SYSTEM_PROMPT, tools, sink, sessionId, memory,
                agentProperties.getMaxIterations(), agentProperties.getTimeoutSeconds());
    }
}
