package com.jiwei.ai.agent;

import com.alibaba.fastjson2.JSON;
import com.jiwei.ai.nlsql.SqlValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 商家经营数据查询工具（Text2SQL）。
 * 内部链路：LLM 生成 SQL → SqlValidator 只读校验 → 执行 → 返回结果 JSON，
 * 结果交由 JChatMind 运行时中的模型总结为自然语言答案。
 */
@Slf4j
public class MerchantQueryTools {

    private static final String NL2SQL_PROMPT = """
            你是一个SQL查询助手，负责将商家经营相关的问题转换为MySQL SELECT查询语句。

            数据库表结构（跨库查询，请使用全限定表名）：
            - jiwei_merchant.dish: 菜品表 (id, merchant_id, category_id, name, price, status, sales, create_time)，sales为累计销量
            - jiwei_merchant.category: 分类表 (id, merchant_id, type, name, sort, status)，type: 1=菜品分类 2=套餐分类，name 为分类名称（如"招牌面"、"烤串"、"热菜"）
            - jiwei_trade.orders: 订单表 (id, number, user_id, merchant_id, amount, pay_amount, pay_status, status, create_time, pay_time)
            - jiwei_trade.order_detail: 订单明细 (id, order_id, name菜品名, amount, number数量)
            - jiwei_social.review: 评价表 (id, user_id, order_id, dish_id, rating评分, content内容, create_time)，无 merchant_id 字段，按商家过滤时需 JOIN jiwei_trade.orders ON review.order_id = orders.id
            - jiwei_user.user: 用户表 (id, nickname, phone, create_time)

            规则（按问题类型选择正确的数据源）：
            1. 只生成 SELECT 语句，不要有任何写操作
            2. 所有查询必须包含 merchant_id 过滤条件（dish.merchant_id 或 orders.merchant_id），严禁查询其他店铺的数据
            3. 「销量」「卖得最好」「热销」「排行榜」类问题：直接用 jiwei_merchant.dish 表的 sales 字段 ORDER BY sales DESC，不要用订单明细统计
            4. 「类型」「菜系」「分类」「哪种菜最受欢迎」类问题：必须 JOIN jiwei_merchant.category ON dish.category_id = category.id，按 category.name 分组并返回分类名称（如"招牌面"），不要返回 category_id 数字；可用 SUM(dish.sales) 或 SUM(dish.sales) 排序取 TOP 5
            5. 「营收」「流水」「订单数」「客单价」类问题：使用 jiwei_trade.orders 表，金额字段用 pay_amount
            6. 「评价」「差评」「好评」「评分」类问题：使用 jiwei_social.review 表
            7. 需要分页时使用 LIMIT offset, count 格式
            8. 日期过滤使用 DATE() 函数

            请只返回SQL语句，不要有任何解释。
            """;

    // 商家经营允许访问的业务表
    private static final Set<String> ALLOWED_TABLES = Set.of(
            "jiwei_merchant.dish", "jiwei_merchant.category",
            "jiwei_trade.orders", "jiwei_trade.order_detail",
            "jiwei_social.review", "jiwei_user.user");

    private final Long merchantId;
    private final ChatClient chatClient;
    private final SqlValidator sqlValidator;
    private final JdbcTemplate bizJdbcTemplate;

    public MerchantQueryTools(Long merchantId, ChatClient chatClient,
                              SqlValidator sqlValidator, JdbcTemplate bizJdbcTemplate) {
        this.merchantId = merchantId;
        this.chatClient = chatClient;
        this.sqlValidator = sqlValidator;
        this.bizJdbcTemplate = bizJdbcTemplate;
    }

    @Tool(description = "查询商家经营数据（销量/营收/订单/评价等），输入自然语言问题，返回查询结果JSON")
    public String queryBusinessData(@ToolParam(description = "经营问题，如'本月销量最好的菜'") String question) {
        String sql = generateSql(question, merchantId);
        if (sql == null || sql.isEmpty()) {
            return "无法理解该问题";
        }
        // 校验并加固（补 LIMIT）；未通过多层校验一律不下发到数据库
        String hardened = sqlValidator.validateAndHarden(sql, ALLOWED_TABLES);
        if (hardened == null) {
            return "该问题涉及的数据无法查询";
        }
        // 越权兜底：提示词已要求必须带 merchant_id 过滤，但提示词可被注入绕过，
        // 这里在代码层再挡一次「不带商户维度的全平台查询」
        if (!hardened.toLowerCase(Locale.ROOT).contains("merchant_id")) {
            log.warn("经营问数被拒：SQL 缺少 merchant_id 维度过滤 - {}", hardened);
            return "该问题涉及的数据无法查询（必须限定当前商户）";
        }
        try {
            List<Map<String, Object>> rows = bizJdbcTemplate.queryForList(hardened);
            return JSON.toJSONString(rows);
        } catch (Exception e) {
            log.warn("经营助手SQL执行失败: sql={}, err={}", hardened, e.getMessage());
            return "查询失败：" + e.getMessage();
        }
    }

    private String generateSql(String question, Long merchantId) {
        String prompt = String.format("商家ID: %d\n问题: %s\n请生成SQL查询，所有查询必须限定 merchant_id = %d。",
                merchantId, question, merchantId);
        try {
            String content = chatClient.prompt()
                    .system(NL2SQL_PROMPT)
                    .user(prompt)
                    .call()
                    .content();
            log.info("LLM生成SQL: {}", content);
            return extractSql(content);
        } catch (Exception e) {
            log.warn("LLM生成SQL失败: {}", e.getMessage());
            return null;
        }
    }

    /** 从模型输出里抠出 SQL：优先取 ``` 代码块，其次从第一个 SELECT 起截断到分号。 */
    private String extractSql(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Matcher m = Pattern.compile("```(?:sql)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE).matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        int idx = content.toUpperCase(Locale.ROOT).indexOf("SELECT");
        if (idx < 0) {
            return content.trim();
        }
        String sql = content.substring(idx).trim();
        int semi = sql.indexOf(';');
        if (semi > 0) {
            sql = sql.substring(0, semi);
        }
        return sql.trim();
    }
}
