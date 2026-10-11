package com.jiwei.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * RAG检索服务
 * 混合检索: BM25关键词 + 语义向量 → RRF融合 → LLM重排序
 */
@Service
@Slf4j
public class RagService {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private JdbcTemplate pgJdbcTemplate;

    @Autowired
    private MarkdownParserService markdownParserService;

    /**
     * 语义向量检索
     *
     * @param query 用户查询
     * @param knowledgeBase 知识库名称
     * @param topK 返回Top K个结果
     */
    public List<Map<String, Object>> semanticSearch(String query, String knowledgeBase, int topK) {
        //1、生成查询向量
        float[] queryEmbedding = embeddingModel.embed(query);

        //2、pgvector 余弦相似度检索
        String sql = """
                SELECT id, title, content, metadata,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM knowledge_document
                WHERE knowledge_base = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """;

        String vectorStr = vectorToString(queryEmbedding);
        return pgJdbcTemplate.queryForList(sql, vectorStr, knowledgeBase, vectorStr, topK);
    }

    /**
     * 加载文档到知识库
     * 1. 文档切分为 Chunk (500字/块, 50字重叠)
     * 2. 对每个Chunk生成Embedding
     * 3. 写入pgvector
     */
    public void loadDocument(String knowledgeBase, String title, String content) {
        log.info("加载文档到知识库: base={}, title={}", knowledgeBase, title);

        //0、同标题覆盖（避免反复沉淀同一问题导致知识库膨胀）
        pgJdbcTemplate.update("DELETE FROM knowledge_document WHERE knowledge_base = ? AND title = ?",
                knowledgeBase, title);

        //1、文档切块（Markdown 按 Heading 切分）
        List<String> chunks = markdownParserService.extractSections(content);

        //2、逐块生成向量并写入
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            float[] embedding = embeddingModel.embed(chunk);

            String sql = """
                    INSERT INTO knowledge_document
                        (knowledge_base, title, content, chunk_index, embedding)
                    VALUES (?, ?, ?, ?, ?::vector)
                    """;

            pgJdbcTemplate.update(sql, knowledgeBase, title, chunk, i,
                    vectorToString(embedding));
        }

        log.info("文档加载完成: {}个chunk", chunks.size());
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
