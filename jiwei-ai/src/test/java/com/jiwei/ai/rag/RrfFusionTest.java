package com.jiwei.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RRF 融合的离线回归：多路召回的相对顺序必须符合 RRF 语义。
 */
class RrfFusionTest {

    @Test
    void shouldRankDocumentHitByBothChannelsFirst() {
        // b 同时被两路召回（向量 rank2、关键词 rank1），应排第一；
        // a 只在向量 rank1，c 只在关键词 rank2
        List<String> fused = RrfFusion.fuse(List.of(
                List.of("a", "b"),
                List.of("b", "c")), 3);

        assertThat(fused).containsExactly("b", "a", "c");
    }

    @Test
    void shouldKeepVectorOrderWhenOnlyOneChannelPresent() {
        List<String> fused = RrfFusion.fuse(List.of(
                List.of("x", "y", "z"),
                List.of()), 2);

        assertThat(fused).containsExactly("x", "y");
    }

    @Test
    void shouldTruncateToTopK() {
        List<String> fused = RrfFusion.fuse(List.of(
                List.of("1", "2", "3", "4", "5")), 3);

        assertThat(fused).hasSize(3).containsExactly("1", "2", "3");
    }

    @Test
    void shouldReturnEmptyForNoRecall() {
        assertThat(RrfFusion.fuse(List.of(List.of(), List.of()), 5)).isEmpty();
        assertThat(RrfFusion.fuse(List.of(), 5)).isEmpty();
        assertThat(RrfFusion.fuse(List.of(List.of("a")), 0)).isEmpty();
    }

    @Test
    void shouldTolerateDuplicateIdsWithinOneChannel() {
        List<String> fused = RrfFusion.fuse(List.of(List.of("a", "a", "b")), 5);

        assertThat(fused).containsExactly("a", "b");
    }

    @Test
    void shouldDedupeRowsAndAnnotateHitChannels() {
        Map<String, Object> vectorRow = row(1L, "向量正文");
        Map<String, Object> keywordRow = row(1L, "关键词正文");
        Map<String, Object> onlyKeyword = row(2L, "只被关键词召回");

        List<Map<String, Object>> fused = RrfFusion.fuseRows(
                List.of(List.of(vectorRow), List.of(keywordRow, onlyKeyword)),
                List.of("vector", "keyword"), 5);

        assertThat(fused).hasSize(2);
        // 两路同时命中的 id=1 排最前，正文取先出现的向量行
        assertThat(fused.get(0).get("id")).isEqualTo(1L);
        assertThat(fused.get(0).get("content")).isEqualTo("向量正文");
        assertThat(fused.get(0).get("channels")).isEqualTo(List.of("vector", "keyword"));
        assertThat(fused.get(1).get("channels")).isEqualTo(List.of("keyword"));
    }

    @Test
    void shouldSkipRowsWithoutKeyField() {
        Map<String, Object> noId = new LinkedHashMap<>();
        noId.put("content", "没有 id 的脏数据");

        List<Map<String, Object>> fused = RrfFusion.fuseRows(
                List.of(List.of(noId), List.of(row(9L, "正常"))),
                List.of("vector", "keyword"), 5);

        assertThat(fused).hasSize(1);
        assertThat(fused.get(0).get("id")).isEqualTo(9L);
    }

    private static Map<String, Object> row(Long id, String content) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("content", content);
        return row;
    }
}
