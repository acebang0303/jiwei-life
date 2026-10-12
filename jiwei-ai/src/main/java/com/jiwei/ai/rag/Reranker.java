package com.jiwei.ai.rag;

import java.util.List;
import java.util.Map;

/**
 * 召回后的重排（rerank）扩展点。
 *
 * 约定：实现**不得抛异常影响主链路**——任何失败都要回退成「按传入顺序返回」，
 * 检索可用性优先于排序质量。
 */
public interface Reranker {

    /**
     * 对候选做重排并截断。
     *
     * @param query      用户查询
     * @param candidates 融合后的候选（已按 RRF 排序），元素含 id 字段
     * @param topK       最终条数
     * @return 重排后的结果；失败时返回「原顺序前 topK 条」
     */
    List<Map<String, Object>> rerank(String query, List<Map<String, Object>> candidates, int topK);
}
