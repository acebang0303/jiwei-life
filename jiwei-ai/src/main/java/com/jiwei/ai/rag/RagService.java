package com.jiwei.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 检索服务：混合召回（语义向量 + 字面关键词）→ RRF 融合，并对大模型故障自动降级。
 *
 * 两条召回通道：
 *   1. 语义向量：pgvector 余弦距离（<->），负责「语义相近但用词不同」的召回；
 *   2. 关键词字面：pg_trgm 三元组相似度，负责「专有名词/编号/短词」这类向量容易漏掉的召回。
 *      中文没有天然分词边界，trgm 在字符级做三元组，比 to_tsvector('simple') 更实用
 *      （后者对中文整句只产生一个 token，等于失效）。
 *
 * 融合用 RRF（见 RrfFusion）：只用排名、不用分数，避免余弦相似度和 trgm 得分量纲不可比。
 *
 * 可用性降级（对应「大模型故障时自动降级」）：
 *   - Embedding 服务不可用（LLM/硅基流动挂了）→ 自动退化为纯关键词召回，检索不中断；
 *   - pg_trgm 不可用（未装扩展）→ 自动退化为纯向量召回；
 *   - 两路都不可用 → 返回空结果，由上层 Agent 决定「基于自身知识回答 + 沉淀知识库」，
 *     而不是抛异常打断整个对话。
 */
@Service
@Slf4j
public class RagService {

    /** 每路召回的候选倍数：先多召回，再融合收敛到 topK */
    private static final int RECALL_MULTIPLIER = 3;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private JdbcTemplate pgJdbcTemplate;

    @Autowired
    private MarkdownParserService markdownParserService;

    /** 重排器：可选依赖，缺失或异常都退化为 RRF 顺序 */
    @Autowired(required = false)
    private Reranker reranker;

    @Value("${rag.rerank.enabled:true}")
    private boolean rerankEnabled;

    /**
     * 混合召回入口：向量 + 关键词 → RRF 融合，任一通道故障自动降级。
     *
     * @param query         用户查询
     * @param knowledgeBase 知识库名称
     * @param topK          返回条数
     */
    public List<Map<String, Object>> hybridSearch(String query, String knowledgeBase, int topK) {
        if (!StringUtils.hasText(query) || topK <= 0) {
            return List.of();
        }
        int recallSize = Math.max(topK * RECALL_MULTIPLIER, topK + 5);

        List<Map<String, Object>> vectorHits = vectorRecall(query, knowledgeBase, recallSize);
        List<Map<String, Object>> keywordHits = keywordRecall(query, knowledgeBase, recallSize);

        if (vectorHits.isEmpty() && keywordHits.isEmpty()) {
            log.warn("混合召回无结果（向量={} 关键词={}），上层应走「无知识库依据」分支",
                    vectorHits.size(), keywordHits.size());
            return List.of();
        }
        // 先融合出一个「重排候选池」，再交给 reranker 收敛到 topK
        int pool = Math.max(topK * 2, topK + 2);
        List<Map<String, Object>> fused = RrfFusion.fuseRows(
                List.of(vectorHits, keywordHits), List.of("vector", "keyword"), pool);
        log.info("混合召回完成: 向量={} 关键词={} -> 融合候选={}", vectorHits.size(), keywordHits.size(), fused.size());

        if (rerankEnabled && reranker != null && fused.size() > 1) {
            List<Map<String, Object>> reranked = reranker.rerank(query, fused, topK);
            if (reranked != null && !reranked.isEmpty()) {
                return reranked;
            }
        }
        return fused.size() > topK ? new ArrayList<>(fused.subList(0, topK)) : fused;
    }

    /**
     * 语义向量检索（单通道）。保留该入口供「只要向量」的场景使用；
     * 需要最佳效果请用 hybridSearch。
     */
    public List<Map<String, Object>> semanticSearch(String query, String knowledgeBase, int topK) {
        if (!StringUtils.hasText(query) || topK <= 0) {
            return List.of();
        }
        float[] queryEmbedding = embeddingModel.embed(query);
        String vectorStr = vectorToString(queryEmbedding);
        String sql = """
                SELECT id, title, content, metadata,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM knowledge_document
                WHERE knowledge_base = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;
        return pgJdbcTemplate.queryForList(sql, vectorStr, knowledgeBase, vectorStr, topK);
    }

    /** 向量召回：Embedding 或 pgvector 故障时降级为空集（交给关键词通道兜住）。 */
    private List<Map<String, Object>> vectorRecall(String query, String knowledgeBase, int topK) {
        try {
            return semanticSearch(query, knowledgeBase, topK);
        } catch (Exception e) {
            log.warn("向量召回降级为跳过（Embedding/pgvector 不可用）: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 关键词召回：字面子串命中 + trgm 模糊命中。
     *
     * 为什么用 ILIKE 子串而不是 to_tsvector：中文没有天然分词边界，
     * to_tsvector('simple') 会把整句变成一个 token，等于失效；而「子串是否出现」
     * 对中文关键词恰恰是最有效的判据。ILIKE 配合 gin_trgm_ops 索引是可走索引的
     * （pg_trgm 为 LIKE/ILIKE 提供了操作符类的索引支持）。
     *
     * 注：JDBC 参数化后模式串在计划期不是常量，PG 可能选择顺序扫描；表规模上来后
     * 可考虑 SET plan_cache_mode 或改写为常量拼接。当前知识库规模下扫描成本可接受。
     */
    private List<Map<String, Object>> keywordRecall(String query, String knowledgeBase, int topK) {
        String sql = """
                SELECT id, title, content, metadata,
                       similarity(coalesce(title, ''), ?) AS keyword_score
                FROM knowledge_document
                WHERE knowledge_base = ?
                  AND (title ILIKE '%' || ? || '%'
                       OR content ILIKE '%' || ? || '%'
                       OR title % ?)
                ORDER BY keyword_score DESC
                LIMIT ?
                """;
        try {
            return pgJdbcTemplate.queryForList(sql, query, knowledgeBase, query, query, query, topK);
        } catch (Exception e) {
            log.warn("关键词召回降级为跳过（pg_trgm 不可用？）: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 加载文档到知识库：Markdown 按 Heading 切块 → 逐块 Embedding → 写入 pgvector。
     * 同标题覆盖，避免反复沉淀同一问题导致知识库膨胀。
     */
    public void loadDocument(String knowledgeBase, String title, String content) {
        log.info("加载文档到知识库: base={}, title={}", knowledgeBase, title);

        pgJdbcTemplate.update("DELETE FROM knowledge_document WHERE knowledge_base = ? AND title = ?",
                knowledgeBase, title);

        List<String> chunks = markdownParserService.extractSections(content);
        if (chunks.isEmpty() && StringUtils.hasText(content)) {
            // 纯文本（无 Markdown 标题）时按整段兜底，避免整篇文档丢失
            chunks = List.of(content);
        }

        String sql = """
                INSERT INTO knowledge_document
                    (knowledge_base, title, content, chunk_index, embedding)
                VALUES (?, ?, ?, ?, ?::vector)
                """;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            float[] embedding = embeddingModel.embed(chunk);
            pgJdbcTemplate.update(sql, knowledgeBase, title, chunk, i, vectorToString(embedding));
        }

        log.info("文档加载完成: {}个chunk", chunks.size());
    }

    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
