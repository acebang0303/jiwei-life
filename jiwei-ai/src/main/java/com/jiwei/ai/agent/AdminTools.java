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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 管理端平台运营助手工具集。
 */
@Slf4j
public class AdminTools {

    // 平台运营允许访问的业务表（与 NL2SQL_PROMPT 表清单一致）
    private static final Set<String> ALLOWED_TABLES = Set.of(
            "jiwei_trade.orders", "jiwei_trade.order_detail",
            "jiwei_merchant.merchant_info", "jiwei_merchant.dish",
            "jiwei_social.note", "jiwei_social.review", "jiwei_user.user");

    private static final String NL2SQL_PROMPT = """
            你是一个SQL查询助手，负责将平台运营问题转换为MySQL SELECT查询语句。

            数据库表结构（跨库查询，请使用全限定表名）：
            - jiwei_trade.orders: 订单表 (id, number, user_id, merchant_id, amount, pay_amount, pay_status, status, create_time, pay_time)，status: 5=已完成 6=已取消
            - jiwei_trade.order_detail: 订单明细 (id, order_id, name菜品名, amount, number数量)
            - jiwei_merchant.merchant_info: 商户表 (id, name, address, status, create_time)
            - jiwei_merchant.dish: 菜品表 (id, merchant_id, name, price, status, sales, create_time)
            - jiwei_social.note: 笔记表 (id, user_id, merchant_id, title, like_count, collect_count, comment_count, audit_status, create_time)
            - jiwei_user.user: 用户表 (id, nickname, phone, growth_value, create_time)
            - jiwei_social.review: 评价表 (id, user_id, order_id, dish_id, rating评分, content内容, create_time)，按商家过滤时需 JOIN jiwei_trade.orders ON review.order_id = orders.id

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
    private final String model;

    public AdminTools(ChatClientRegistry registry, SqlValidator sqlValidator,
                      @Qualifier("bizJdbcTemplate") JdbcTemplate bizJdbcTemplate,
                      AuditAgent auditAgent, RagService ragService, String model) {
        this.registry = registry;
        this.sqlValidator = sqlValidator;
        this.bizJdbcTemplate = bizJdbcTemplate;
        this.auditAgent = auditAgent;
        this.ragService = ragService;
        this.model = model;
    }

    @Tool(description = "查询平台经营数据（订单/营收/商户排行/菜品销量/笔记数据等），输入自然语言问题，返回查询结果JSON")
    public String queryPlatformData(@ToolParam(description = "平台运营问题，如'本周营收TOP3商户'") String question) {
        String sql = generateSql(question);
        if (sql == null || sql.isEmpty()) {
            return "无法理解该问题";
        }
        // 校验 + 加固（补 LIMIT）：不通过就不下发数据库
        String hardened = sqlValidator.validateAndHarden(sql, ALLOWED_TABLES);
        if (hardened == null) {
            return "该问题涉及的数据无法查询";
        }
        try {
            List<Map<String, Object>> rows = bizJdbcTemplate.queryForList(hardened);
            return JSON.toJSONString(rows);
        } catch (Exception e) {
            log.warn("平台数据分析SQL执行失败: sql={}, err={}", hardened, e.getMessage());
            return "查询失败：" + e.getMessage();
        }
    }

    @Tool(description = "审核内容（笔记/评价）是否合规，返回审核结果JSON")
    public String auditContent(@ToolParam(description = "待审核内容文本") String content,
                               @ToolParam(description = "内容类型：note 或 review", required = false) String contentType) {
        JSONObject result = auditAgent.audit(content, contentType == null ? "note" : contentType);
        return result.toJSONString();
    }

    @Tool(description = "从运营知识库检索制度/流程/入驻规则等，返回相关文档片段；若返回知识库中未检索到相关内容，应基于自身知识回答并调用 saveKnowledgeBase 沉淀")
    public String searchKnowledgeBase(@ToolParam(description = "知识库检索问题，如'入驻流程是什么'") String query) {
        // 混合召回（向量 + 关键词 → RRF），Embedding 故障时自动降级为关键词召回
        List<Map<String, Object>> docs = ragService.hybridSearch(query, "jiwei_ops", 5);
        if (docs == null || docs.isEmpty()) {
            return "知识库中未检索到相关内容（knowledge_base=jiwei_ops）。若你能基于自身知识回答，请直接回答，然后用 saveKnowledgeBase 工具把这个问题与答案沉淀到知识库。";
        }
        return JSON.toJSONString(docs);
    }

    @Tool(description = "把新学到的运营知识问答沉淀到知识库（检索无结果时由模型深度思考后的答案），同问题覆盖更新")
    public String saveKnowledgeBase(@ToolParam(description = "问题，如'入驻流程是什么'") String question,
                                    @ToolParam(description = "基于深度思考给出的完整答案") String answer) {
        try {
            ragService.loadDocument("jiwei_ops", question, answer);
            return "已沉淀到知识库：" + question;
        } catch (Exception e) {
            log.warn("知识库沉淀失败: {}", e.getMessage());
            return "知识库沉淀失败：" + e.getMessage();
        }
    }

    @Tool(description = "针对指定商户生成经营建议，输入商户ID，返回该商户经营数据分析与改进建议")
    public String merchantSuggestion(@ToolParam(description = "商户ID") Long merchantId) {
        //1、拉取该商户近30天订单与评价聚合（参数化查询，不要把入参拼进 SQL 文本）
        String orderSql = "SELECT COUNT(*) AS order_cnt, IFNULL(SUM(pay_amount),0) AS revenue, "
                + "IFNULL(AVG(pay_amount),0) AS avg_price FROM jiwei_trade.orders "
                + "WHERE merchant_id = ? AND status = 5 "
                + "AND DATE(create_time) >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)";
        String reviewSql = "SELECT IFNULL(AVG(r.rating),0) AS avg_rating, COUNT(*) AS review_cnt "
                + "FROM jiwei_social.review r JOIN jiwei_trade.orders o ON r.order_id = o.id "
                + "WHERE o.merchant_id = ? "
                + "AND DATE(r.create_time) >= DATE_SUB(CURDATE(), INTERVAL 30 DAY)";
        try {
            Map<String, Object> orders = bizJdbcTemplate.queryForList(orderSql, merchantId).get(0);
            Map<String, Object> reviews = bizJdbcTemplate.queryForList(reviewSql, merchantId).get(0);
            String dataJson = JSON.toJSONString(Map.of("orders", orders, "reviews", reviews));
            //2、LLM 生成建议
            ChatClient client = registry.get(this.model);
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
            ChatClient client = registry.get(this.model);
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
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("select")
                || trimmed.toLowerCase(Locale.ROOT).startsWith("with")) {
            return trimmed;
        }
        return null;
    }
}
