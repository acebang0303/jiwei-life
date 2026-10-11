package com.jiwei.ai.recommend;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.jiwei.ai.config.ChatClientRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合推荐引擎（行为驱动）
 *
 * 候选来源（按优先级）:
 * 1. 用户行为关联：点赞/收藏/评论的笔记关联店铺 + 浏览过的店铺 → 店内热门菜品
 * 2. 语义向量：用户偏好画像文本 × 菜品向量 → 余弦相似度
 * 3. 冷启动兜底：全站销量 Top 菜品
 *
 * 排序：LLM 重排序（不可用时按销量降级），并为每道菜生成推荐理由
 */
@Service
@Slf4j
public class RecommendEngine {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private ChatClientRegistry registry;

    @Autowired
    private JdbcTemplate pgJdbcTemplate;

    @Autowired
    @Qualifier("bizJdbcTemplate")
    private JdbcTemplate bizJdbcTemplate;

    @Value("${agent.recommend-model:deepseek}")
    private String recommendModel;

    /**
     * AI个性化菜品推荐
     *
     * @param userId 用户ID
     * @param topN 返回Top N个推荐
     * @return 推荐结果JSON数组，每项: {rank, dishId, name, price, image, merchantId, merchantName, reason}
     */
    public String recommend(Long userId, int topN) {
        log.info("个性化推荐: userId={}, topN={}", userId, topN);

        //1、用户偏好画像文本（行为驱动，如 "张记面馆 蜀味川菜 烧烤"）
        String preferenceText = getUserPreferenceText(userId);
        log.info("用户偏好画像: userId={}, text={}", userId, preferenceText);

        //2、候选集：行为关联 > 语义匹配 > 热度兜底
        Set<Long> merged = new LinkedHashSet<>();
        merged.addAll(behaviorBasedCandidates(userId, 50));
        merged.addAll(semanticFilter(preferenceText, 50));
        if (merged.isEmpty()) {
            merged.addAll(topRatedDishIds(topN * 3));
        }
        List<Long> candidates = merged.stream().limit(50).collect(Collectors.toList());
        if (candidates.isEmpty()) {
            return "[]";
        }

        //3、查询菜品真实数据（名称/价格/图片/店铺）
        List<Map<String, Object>> dishes = queryDishes(candidates);
        if (dishes.isEmpty()) {
            return "[]";
        }

        //4、LLM 重排序（失败自动降级为销量排序）
        String llmJson = null;
        try {
            llmJson = llmRerank(userId, preferenceText, dishes, topN);
        } catch (Exception e) {
            log.warn("LLM重排序不可用，降级为销量排序: {}", e.getMessage());
        }

        return buildResponse(dishes, llmJson, topN);
    }

    /**
     * 行为关联候选：点赞/收藏/评论的笔记关联店铺 + 浏览过的店铺 → 店内热门菜品
     */
    private List<Long> behaviorBasedCandidates(Long userId, int n) {
        try {
            String merchantSql = """
                    SELECT DISTINCT m.id AS merchant_id
                    FROM jiwei_user.user_behavior ub
                    LEFT JOIN jiwei_social.note n ON ub.target_id = n.id
                            AND ub.type IN ('LIKE_NOTE','COLLECT_NOTE','COMMENT_NOTE')
                    LEFT JOIN jiwei_merchant.merchant_info m ON m.id = n.merchant_id
                    WHERE ub.user_id = ?
                    UNION
                    SELECT DISTINCT ub.target_id AS merchant_id
                    FROM jiwei_user.user_behavior ub
                    WHERE ub.user_id = ? AND ub.type = 'VIEW_MERCHANT'
                    """;
            List<Long> merchantIds = bizJdbcTemplate.queryForList(merchantSql, userId, userId).stream()
                    .map(r -> r.get("merchant_id") == null ? null : Long.valueOf(r.get("merchant_id").toString()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (merchantIds.isEmpty()) {
                return Collections.emptyList();
            }
            String inClause = merchantIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            String dishSql = "SELECT id FROM jiwei_merchant.dish WHERE merchant_id IN (" + inClause
                    + ") AND status = 1 ORDER BY sales DESC LIMIT " + n;
            return bizJdbcTemplate.queryForList(dishSql).stream()
                    .map(r -> Long.valueOf(r.get("id").toString()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("行为关联候选查询失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 语义向量匹配：用户偏好画像文本 × 菜品向量 → 余弦相似度排序
     */
    private List<Long> semanticFilter(String preferenceText, int n) {
        if (preferenceText == null || preferenceText.isEmpty()) {
            log.info("用户无偏好画像，跳过语义匹配");
            return Collections.emptyList();
        }
        try {
            float[] preferenceEmbedding = embeddingModel.embed(preferenceText);
            String vectorStr = vectorToString(preferenceEmbedding);
            String sql = """
                    SELECT id FROM dish_embedding
                    ORDER BY embedding <=> ?::vector
                    LIMIT ?
                    """;
            return pgJdbcTemplate.queryForList(sql, vectorStr, n).stream()
                    .map(r -> Long.valueOf(r.get("id").toString()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("语义向量检索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 冷启动：全站销量 Top 菜品
     */
    private List<Long> topRatedDishIds(int n) {
        try {
            String sql = "SELECT id FROM jiwei_merchant.dish WHERE status = 1 ORDER BY sales DESC LIMIT " + n;
            return bizJdbcTemplate.queryForList(sql).stream()
                    .map(r -> Long.valueOf(r.get("id").toString()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("热度兜底查询失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 查询菜品真实数据（含店铺名）
     */
    private List<Map<String, Object>> queryDishes(List<Long> ids) {
        try {
            String inClause = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
            String sql = """
                    SELECT d.id, d.merchant_id, d.name, d.image, d.price, d.sales,
                           m.name AS merchant_name
                    FROM jiwei_merchant.dish d
                    LEFT JOIN jiwei_merchant.merchant_info m ON d.merchant_id = m.id
                    WHERE d.id IN (%s) AND d.status = 1
                    """.formatted(inClause);
            return bizJdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.warn("查询菜品数据失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * LLM重排序（开发环境无 Key 时抛异常，由调用方降级）
     */
    private String llmRerank(Long userId, String preferenceText, List<Map<String, Object>> dishes, int topN)
            throws Exception {
        String candidatesDesc = dishes.stream()
                .map(d -> d.get("id") + "(" + d.get("name") + "-" + d.get("merchant_name") + ")")
                .collect(Collectors.joining(", "));
        String prompt = String.format("""
                你是一个美食推荐专家。请为用户%d推荐%d道最合适的菜品。

                候选菜品: %s

                用户偏好: %s

                请按推荐优先级排序，为每道菜写一句15字左右的推荐理由（亲切、有感染力）。
                输出格式（JSON数组，只返回JSON）:
                [{"rank":1,"dishId":123,"reason":"..."}]
                """, userId, topN, candidatesDesc, preferenceText);
        ChatClient client = registry.get(recommendModel);
        String response = client.prompt().user(prompt).call().content();
        if (response == null || response.isBlank()) {
            throw new RuntimeException("LLM 返回为空");
        }
        return response;
    }

    /**
     * 组装返回：优先采用 LLM 的 rank/reason，失败按销量降序 + 模板理由
     */
    private String buildResponse(List<Map<String, Object>> dishes, String llmJson, int topN) {
        //1、解析 LLM 排序（rank → dishId → reason）
        Map<Long, String> reasonMap = new HashMap<>();
        List<Long> llmOrder = null;
        if (llmJson != null) {
            try {
                String cleaned = llmJson.replaceAll("```json|```", "").trim();
                int start = cleaned.indexOf('[');
                int end = cleaned.lastIndexOf(']');
                if (start >= 0 && end > start) {
                    cleaned = cleaned.substring(start, end + 1);
                }
                JSONArray arr = JSON.parseArray(cleaned);
                if (arr != null && !arr.isEmpty()) {
                    List<JSONObject> items = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        if (o.getLong("dishId") != null) {
                            items.add(o);
                            reasonMap.put(o.getLong("dishId"), o.getString("reason"));
                        }
                    }
                    items.sort(Comparator.comparingInt(o -> o.getIntValue("rank", 999)));
                    llmOrder = items.stream().map(o -> o.getLong("dishId")).collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn("解析LLM重排序结果失败: {}", e.getMessage());
            }
        }

        //2、确定最终顺序
        Map<Long, Map<String, Object>> dishMap = new LinkedHashMap<>();
        for (Map<String, Object> d : dishes) {
            dishMap.put(Long.valueOf(d.get("id").toString()), d);
        }
        List<Long> orderedIds = new ArrayList<>();
        if (llmOrder != null) {
            for (Long id : llmOrder) {
                if (dishMap.containsKey(id) && !orderedIds.contains(id)) {
                    orderedIds.add(id);
                }
            }
        }
        for (Long id : dishMap.keySet()) {
            if (!orderedIds.contains(id)) {
                orderedIds.add(id);
            }
        }

        //3、组装返回
        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 0;
        for (Long id : orderedIds) {
            if (rank >= topN) {
                break;
            }
            Map<String, Object> d = dishMap.get(id);
            rank++;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", rank);
            item.put("dishId", id);
            item.put("name", d.get("name"));
            item.put("price", d.get("price"));
            item.put("image", d.get("image"));
            item.put("merchantId", d.get("merchant_id"));
            item.put("merchantName", d.get("merchant_name"));
            String merchantName = d.get("merchant_name") != null ? d.get("merchant_name").toString() : "本店";
            String dishName = d.get("name") != null ? d.get("name").toString() : "美食";
            item.put("reason", reasonMap.getOrDefault(id, "「" + merchantName + "」的" + dishName + "，回头客最多，值得一试"));
            result.add(item);
        }
        return JSON.toJSONString(result);
    }

    /**
     * 查询用户偏好画像：行为关联的店铺名 + 已有偏好标签
     */
    String getUserPreferenceText(Long userId) {
        try {
            List<String> parts = new ArrayList<>();
            //1、行为关联店铺名
            String merchantSql = """
                    SELECT DISTINCT m.name
                    FROM jiwei_user.user_behavior ub
                    LEFT JOIN jiwei_social.note n ON ub.target_id = n.id
                            AND ub.type IN ('LIKE_NOTE','COLLECT_NOTE','COMMENT_NOTE')
                    LEFT JOIN jiwei_merchant.merchant_info m ON m.id = n.merchant_id
                    WHERE ub.user_id = ?
                    UNION
                    SELECT DISTINCT m.name
                    FROM jiwei_user.user_behavior ub
                    JOIN jiwei_merchant.merchant_info m ON m.id = ub.target_id
                    WHERE ub.user_id = ? AND ub.type = 'VIEW_MERCHANT'
                    """;
            bizJdbcTemplate.queryForList(merchantSql, userId, userId).forEach(r -> {
                if (r.get("name") != null) {
                    parts.add(r.get("name").toString());
                }
            });
            //2、已有偏好标签
            List<Map<String, Object>> rows = bizJdbcTemplate.queryForList(
                    "SELECT preference_tags FROM jiwei_user.user WHERE id = ?", userId);
            if (!rows.isEmpty() && rows.get(0).get("preference_tags") != null) {
                JSONArray arr = JSON.parseArray(rows.get(0).get("preference_tags").toString());
                if (arr != null) {
                    arr.forEach(tag -> parts.add(tag.toString()));
                }
            }
            return parts.stream().filter(Objects::nonNull).distinct().collect(Collectors.joining(" "));
        } catch (Exception e) {
            log.warn("查询用户偏好画像失败 userId={}: {}", userId, e.getMessage());
            return "";
        }
    }

    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
