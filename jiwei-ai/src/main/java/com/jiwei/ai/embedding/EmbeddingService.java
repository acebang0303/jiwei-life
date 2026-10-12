package com.jiwei.ai.embedding;

import com.jiwei.ai.rag.RrfFusion;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Embedding 灌入与语义检索服务
 * 读 MySQL 业务数据 → 生成向量 → 写 pgvector；以及对 dish/note 做语义检索
 */
@Service
@Slf4j
public class EmbeddingService {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    @Qualifier("pgJdbcTemplate")
    private JdbcTemplate pgJdbcTemplate;

    @Autowired
    @Qualifier("bizJdbcTemplate")
    private JdbcTemplate bizJdbcTemplate;

    @Autowired
    private MeterRegistry meterRegistry;

    /** 硅基流动 batch 上限 64，留余量 */
    private static final int BATCH_SIZE = 32;

    private static final int SUMMARY_LEN = 512;

    /**
     * 重建菜品向量：读 MySQL dish → embedding → 写 pgvector dish_embedding
     */
    public Map<String, Integer> rebuildDishEmbeddings() {
        String sql = """
                SELECT d.id, d.merchant_id, d.name, d.description, d.price,
                       c.name AS category_name
                FROM jiwei_merchant.dish d
                LEFT JOIN jiwei_merchant.category c ON d.category_id = c.id
                WHERE d.status = 1
                """;
        List<Map<String, Object>> dishes = bizJdbcTemplate.queryForList(sql);
        log.info("开始重建菜品向量，共 {} 条", dishes.size());

        pgJdbcTemplate.update("DELETE FROM dish_embedding");

        int success = 0;
        int fail = 0;
        List<Map<String, Object>> batch = new ArrayList<>();
        for (Map<String, Object> dish : dishes) {
            batch.add(dish);
            if (batch.size() >= BATCH_SIZE) {
                int[] r = embedAndInsertDishes(batch);
                success += r[0];
                fail += r[1];
                batch.clear();
                sleep();
            }
        }
        if (!batch.isEmpty()) {
            int[] r = embedAndInsertDishes(batch);
            success += r[0];
            fail += r[1];
        }

        log.info("菜品向量重建完成: total={}, success={}, fail={}", dishes.size(), success, fail);
        return result(dishes.size(), success, fail);
    }

    /**
     * 重建笔记向量：读 MySQL note → embedding → 写 pgvector note_embedding
     */
    public Map<String, Integer> rebuildNoteEmbeddings() {
        String sql = "SELECT id, title, content FROM jiwei_social.note WHERE audit_status = 1";
        List<Map<String, Object>> notes = bizJdbcTemplate.queryForList(sql);
        log.info("开始重建笔记向量，共 {} 条", notes.size());

        pgJdbcTemplate.update("DELETE FROM note_embedding");

        int success = 0;
        int fail = 0;
        List<Map<String, Object>> batch = new ArrayList<>();
        for (Map<String, Object> note : notes) {
            batch.add(note);
            if (batch.size() >= BATCH_SIZE) {
                int[] r = embedAndInsertNotes(batch);
                success += r[0];
                fail += r[1];
                batch.clear();
                sleep();
            }
        }
        if (!batch.isEmpty()) {
            int[] r = embedAndInsertNotes(batch);
            success += r[0];
            fail += r[1];
        }

        log.info("笔记向量重建完成: total={}, success={}, fail={}", notes.size(), success, fail);
        return result(notes.size(), success, fail);
    }

    /**
     * 菜品语义搜索（单通道，向量）
     */
    public List<Map<String, Object>> searchDish(String query, int topK) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            float[] qv = embeddingModel.embed(query);
            String v = vectorToString(qv);
            String sql = """
                    SELECT id, dish_name, merchant_id, category_name, price,
                           1 - (embedding <=> ?::vector) AS similarity
                    FROM dish_embedding
                    ORDER BY embedding <=> ?::vector
                    LIMIT ?
                    """;
            return pgJdbcTemplate.queryForList(sql, v, v, topK);
        } finally {
            sample.stop(meterRegistry.timer("jiwei.embedding.search.dish"));
        }
    }

    /**
     * 菜品关键词召回（pg_trgm 三元组相似度）。
     * 中文没有天然分词边界，to_tsvector('simple') 会把整句变成一个 token，
     * trgm 在字符级做三元组更实用；同时也是 Embedding 不可用时的降级通道。
     */
    public List<Map<String, Object>> searchDishByKeyword(String query, int topK) {
        String sql = """
                SELECT id, dish_name, merchant_id, category_name, price,
                       similarity(coalesce(dish_name, '') || ' ' || coalesce(category_name, ''), ?) AS keyword_score
                FROM dish_embedding
                ORDER BY keyword_score DESC
                LIMIT ?
                """;
        return pgJdbcTemplate.queryForList(sql, query, topK);
    }

    /**
     * 菜品混合召回：向量 + 关键词 → RRF 融合。
     * Embedding 服务（外部大模型）故障时自动降级为纯关键词召回，检索能力不丢失。
     */
    public List<Map<String, Object>> searchDishHybrid(String query, int topK) {
        int recallSize = Math.max(topK * 3, topK + 5);
        List<Map<String, Object>> vectorHits = List.of();
        try {
            vectorHits = searchDish(query, recallSize);
        } catch (Exception e) {
            log.warn("菜品向量召回降级为跳过（Embedding 不可用）: {}", e.getMessage());
        }
        List<Map<String, Object>> keywordHits = List.of();
        try {
            keywordHits = searchDishByKeyword(query, recallSize);
        } catch (Exception e) {
            log.warn("菜品关键词召回降级为跳过（pg_trgm 不可用）: {}", e.getMessage());
        }
        if (vectorHits.isEmpty() && keywordHits.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> fused = RrfFusion.fuseRows(
                List.of(vectorHits, keywordHits), List.of("vector", "keyword"), topK);
        log.info("菜品混合召回: 向量={} 关键词={} -> 融合后={}",
                vectorHits.size(), keywordHits.size(), fused.size());
        return fused;
    }

    /**
     * 笔记语义搜索
     */
    public List<Map<String, Object>> searchNote(String query, int topK) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            float[] qv = embeddingModel.embed(query);
            String v = vectorToString(qv);
            String sql = """
                    SELECT id, title, content_summary,
                           1 - (embedding <=> ?::vector) AS similarity
                    FROM note_embedding
                    ORDER BY embedding <=> ?::vector
                    LIMIT ?
                    """;
            return pgJdbcTemplate.queryForList(sql, v, v, topK);
        } finally {
            sample.stop(meterRegistry.timer("jiwei.embedding.search.note"));
        }
    }

    /**
     * 同步单条菜品向量：存在且上架 → upsert；否则删除
     */
    public void syncDishEmbedding(Long dishId) {
        String sql = """
                SELECT d.id, d.merchant_id, d.name, d.description, d.price,
                       c.name AS category_name
                FROM jiwei_merchant.dish d
                LEFT JOIN jiwei_merchant.category c ON d.category_id = c.id
                WHERE d.id = ? AND d.status = 1
                """;
        List<Map<String, Object>> rows = bizJdbcTemplate.queryForList(sql, dishId);
        if (rows.isEmpty()) {
            pgJdbcTemplate.update("DELETE FROM dish_embedding WHERE id = ?", dishId);
            log.info("删除菜品向量: dishId={}", dishId);
            return;
        }
        Map<String, Object> dish = rows.get(0);
        try {
            float[] vector = embeddingModel.embed(buildDishText(dish));
            upsertDish(dish, vector);
            log.info("同步菜品向量: dishId={}", dishId);
        } catch (Exception e) {
            log.warn("同步菜品向量失败 dishId={}: {}", dishId, e.getMessage());
        }
    }

    /**
     * 同步单条笔记向量：存在且审核通过 → upsert；否则删除
     */
    public void syncNoteEmbedding(Long noteId) {
        String sql = "SELECT id, title, content FROM jiwei_social.note WHERE id = ? AND audit_status = 1";
        List<Map<String, Object>> rows = bizJdbcTemplate.queryForList(sql, noteId);
        if (rows.isEmpty()) {
            pgJdbcTemplate.update("DELETE FROM note_embedding WHERE id = ?", noteId);
            log.info("删除笔记向量: noteId={}", noteId);
            return;
        }
        Map<String, Object> note = rows.get(0);
        try {
            float[] vector = embeddingModel.embed(buildNoteText(note));
            upsertNote(note, vector);
            log.info("同步笔记向量: noteId={}", noteId);
        } catch (Exception e) {
            log.warn("同步笔记向量失败 noteId={}: {}", noteId, e.getMessage());
        }
    }

    private void upsertDish(Map<String, Object> d, float[] vector) {
        pgJdbcTemplate.update("""
                INSERT INTO dish_embedding
                    (id, dish_name, merchant_id, category_name, price, embedding)
                VALUES (?, ?, ?, ?, ?, ?::vector)
                ON CONFLICT (id) DO UPDATE SET
                    dish_name = EXCLUDED.dish_name,
                    merchant_id = EXCLUDED.merchant_id,
                    category_name = EXCLUDED.category_name,
                    price = EXCLUDED.price,
                    embedding = EXCLUDED.embedding
                """,
                ((Number) d.get("id")).longValue(),
                d.get("name"),
                ((Number) d.get("merchant_id")).longValue(),
                d.get("category_name"),
                d.get("price"),
                vectorToString(vector));
    }

    private void upsertNote(Map<String, Object> n, float[] vector) {
        pgJdbcTemplate.update("""
                INSERT INTO note_embedding (id, title, content_summary, embedding)
                VALUES (?, ?, ?, ?::vector)
                ON CONFLICT (id) DO UPDATE SET
                    title = EXCLUDED.title,
                    content_summary = EXCLUDED.content_summary,
                    embedding = EXCLUDED.embedding
                """,
                ((Number) n.get("id")).longValue(),
                n.get("title"),
                summaryOf(n),
                vectorToString(vector));
    }

    private int[] embedAndInsertDishes(List<Map<String, Object>> batch) {
        List<String> texts = new ArrayList<>();
        for (Map<String, Object> d : batch) {
            texts.add(buildDishText(d));
        }
        List<float[]> vectors;
        try {
            vectors = embeddingModel.embed(texts);
        } catch (Exception e) {
            log.error("批量生成菜品向量失败: {}", e.getMessage());
            return new int[]{0, batch.size()};
        }

        int success = 0;
        int fail = 0;
        for (int i = 0; i < batch.size(); i++) {
            Map<String, Object> d = batch.get(i);
            try {
                pgJdbcTemplate.update("""
                        INSERT INTO dish_embedding
                            (id, dish_name, merchant_id, category_name, price, embedding)
                        VALUES (?, ?, ?, ?, ?, ?::vector)
                        """,
                        ((Number) d.get("id")).longValue(),
                        d.get("name"),
                        ((Number) d.get("merchant_id")).longValue(),
                        d.get("category_name"),
                        d.get("price"),
                        vectorToString(vectors.get(i)));
                success++;
            } catch (Exception e) {
                log.warn("写入菜品向量失败 id={}: {}", d.get("id"), e.getMessage());
                fail++;
            }
        }
        return new int[]{success, fail};
    }

    private int[] embedAndInsertNotes(List<Map<String, Object>> batch) {
        List<String> texts = new ArrayList<>();
        for (Map<String, Object> n : batch) {
            texts.add(buildNoteText(n));
        }
        List<float[]> vectors;
        try {
            vectors = embeddingModel.embed(texts);
        } catch (Exception e) {
            log.error("批量生成笔记向量失败: {}", e.getMessage());
            return new int[]{0, batch.size()};
        }

        int success = 0;
        int fail = 0;
        for (int i = 0; i < batch.size(); i++) {
            Map<String, Object> n = batch.get(i);
            try {
                pgJdbcTemplate.update("""
                        INSERT INTO note_embedding (id, title, content_summary, embedding)
                        VALUES (?, ?, ?, ?::vector)
                        """,
                        ((Number) n.get("id")).longValue(),
                        n.get("title"),
                        summaryOf(n),
                        vectorToString(vectors.get(i)));
                success++;
            } catch (Exception e) {
                log.warn("写入笔记向量失败 id={}: {}", n.get("id"), e.getMessage());
                fail++;
            }
        }
        return new int[]{success, fail};
    }

    private String buildDishText(Map<String, Object> d) {
        String name = str(d.get("name"));
        String desc = str(d.get("description"));
        String category = str(d.get("category_name"));
        return name + "，" + desc + "，" + category;
    }

    private String buildNoteText(Map<String, Object> n) {
        return str(n.get("title")) + "。" + str(n.get("content"));
    }

    private String summaryOf(Map<String, Object> n) {
        String content = str(n.get("content"));
        return content.length() > SUMMARY_LEN ? content.substring(0, SUMMARY_LEN) : content;
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o);
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

    private Map<String, Integer> result(int total, int success, int fail) {
        Map<String, Integer> r = new LinkedHashMap<>();
        r.put("total", total);
        r.put("success", success);
        r.put("fail", fail);
        return r;
    }

    private void sleep() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
