package com.jiwei.ai.rag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RRF（Reciprocal Rank Fusion）多路召回融合。
 *
 * 为什么用 RRF 而不是直接加权分数相加：
 *   向量召回给的是余弦相似度（0~1 区间），关键词召回给的是 trgm/BM25 得分，
 *   两者量纲不可比，加权求和需要调参且对数据分布敏感。RRF 只用「排名」：
 *
 *       score(d) = Σ_over_lists 1 / (k + rank_list(d))        （k 常取 60）
 *
 * 无需归一化、无需调参，对异构召回通道天然鲁棒，实现只有几行。
 * k 压低头部排名的权重差，让多路同时命中的文档稳定胜出。
 */
public final class RrfFusion {

    public static final int DEFAULT_K = 60;

    private RrfFusion() {
    }

    /**
     * 融合多路有序召回结果（只要标识）。
     *
     * @param rankedLists 每路召回的文档标识，按相关度降序（rank 从 1 开始）
     * @param topK        最终保留条数
     */
    public static <T> List<T> fuse(List<List<T>> rankedLists, int topK) {
        return fuse(rankedLists, topK, DEFAULT_K);
    }

    public static <T> List<T> fuse(List<List<T>> rankedLists, int topK, int k) {
        List<T> fused = rank(rankedLists, k);
        return fused.size() > topK ? new ArrayList<>(fused.subList(0, topK)) : fused;
    }

    /**
     * 融合多路召回的行数据：按 keyField 去重（先出现的行作为正文来源），
     * 并给每行标注命中的召回通道（channels 字段），便于排查「为什么召回了它」。
     *
     * @param rankedLists  每路的行列表，按相关度降序
     * @param channelNames 与 rankedLists 一一对应的通道名（如 vector / keyword）
     */
    public static List<Map<String, Object>> fuseRows(List<List<Map<String, Object>>> rankedLists,
                                                    List<String> channelNames,
                                                    int topK) {
        return fuseRows(rankedLists, channelNames, topK, DEFAULT_K, "id");
    }

    public static List<Map<String, Object>> fuseRows(List<List<Map<String, Object>>> rankedLists,
                                                     List<String> channelNames,
                                                     int topK, int k, String keyField) {
        if (rankedLists == null || rankedLists.isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> picked = new LinkedHashMap<>();
        Map<String, List<String>> channelsById = new LinkedHashMap<>();
        List<List<String>> idLists = new ArrayList<>(rankedLists.size());

        for (int i = 0; i < rankedLists.size(); i++) {
            List<String> ids = new ArrayList<>();
            List<Map<String, Object>> source = rankedLists.get(i);
            if (source != null) {
                for (Map<String, Object> row : source) {
                    if (row == null || row.get(keyField) == null) {
                        continue;
                    }
                    String id = String.valueOf(row.get(keyField));
                    picked.putIfAbsent(id, row);
                    channelsById.computeIfAbsent(id, x -> new ArrayList<>())
                            .add(i < channelNames.size() ? channelNames.get(i) : "ch" + i);
                    ids.add(id);
                }
            }
            idLists.add(ids);
        }

        List<String> fused = fuse(idLists, topK, k);
        List<Map<String, Object>> result = new ArrayList<>(fused.size());
        for (String id : fused) {
            Map<String, Object> row = picked.get(id);
            if (row == null) {
                continue;
            }
            Map<String, Object> enriched = new LinkedHashMap<>(row);
            enriched.put("channels", channelsById.getOrDefault(id, List.of()));
            result.add(enriched);
        }
        return result;
    }

    /** 返回按 RRF 得分降序排列的全部标识（不截断）。 */
    private static <T> List<T> rank(List<List<T>> rankedLists, int k) {
        if (rankedLists == null || rankedLists.isEmpty()) {
            return List.of();
        }
        Map<T, Double> scores = new HashMap<>();
        Map<T, Integer> firstSeen = new LinkedHashMap<>();
        int order = 0;
        for (List<T> list : rankedLists) {
            if (list == null) {
                continue;
            }
            int rank = 0;
            for (T doc : list) {
                if (doc == null) {
                    continue;
                }
                rank++;
                scores.merge(doc, 1.0 / (k + rank), Double::sum);
                if (!firstSeen.containsKey(doc)) {
                    firstSeen.put(doc, order++);
                }
            }
        }
        List<T> fused = new ArrayList<>(new HashSet<>(scores.keySet()));
        fused.sort((a, b) -> {
            int byScore = Double.compare(scores.get(b), scores.get(a));
            return byScore != 0 ? byScore : Integer.compare(firstSeen.get(a), firstSeen.get(b));
        });
        return fused;
    }
}
