package com.jiwei.ai.rag;

import com.jiwei.ai.config.ChatClientRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 重排的三条防御：合法 id 才采纳、漏掉的候选要补齐、模型失败必须回退原顺序。
 */
class LlmRerankerTest {

    private final LlmReranker reranker = new LlmReranker(null, "deepseek");

    private static Map<String, Object> doc(long id, String title) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("content", title + " 的正文内容");
        return m;
    }

    private static List<Map<String, Object>> pool() {
        return new ArrayList<>(List.of(doc(1, "A"), doc(2, "B"), doc(3, "C")));
    }

    // ---------- applyOrder：解析与校验 ----------

    @Test
    void shouldApplyModelOrder() {
        List<Map<String, Object>> result = reranker.applyOrder("3,1,2", pool(), 3);

        assertThat(result).extracting(m -> m.get("id")).containsExactly(3L, 1L, 2L);
    }

    @Test
    void shouldIgnoreFabricatedIdsFromModel() {
        // 模型编了 99/88 这两个不存在的 id，必须被丢弃
        List<Map<String, Object>> result = reranker.applyOrder("99,2,88", pool(), 3);

        assertThat(result).extracting(m -> m.get("id")).startsWith(2L);
        assertThat(result).hasSize(3);
        assertThat(result).extracting(m -> m.get("id")).doesNotContain(99L, 88L);
    }

    @Test
    void shouldFillCandidatesMissedByModel() {
        // 模型只给了 2，1 和 3 要按原顺序补在后面，保证条数不缩水
        List<Map<String, Object>> result = reranker.applyOrder("2", pool(), 3);

        assertThat(result).extracting(m -> m.get("id")).containsExactly(2L, 1L, 3L);
    }

    @Test
    void shouldIgnoreProseAndDedupeIds() {
        List<Map<String, Object>> result = reranker.applyOrder("排序结果：3,3,1（已按相关度）", pool(), 2);

        assertThat(result).extracting(m -> m.get("id")).containsExactly(3L, 1L);
    }

    @Test
    void shouldReturnEmptyWhenModelOutputHasNoUsableId() {
        assertThat(reranker.applyOrder("没有可用答案", pool(), 3)).isEmpty();
    }

    // ---------- rerank：成功路径 ----------

    @Test
    void shouldRerankThroughChatClient() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(client.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("3,2");
        ChatClientRegistry registry = new ChatClientRegistry(new HashMap<>(Map.of("deepseek", client)), "deepseek");
        LlmReranker llmReranker = new LlmReranker(registry, "deepseek");

        List<Map<String, Object>> result = llmReranker.rerank("怎么入驻", pool(), 2);

        assertThat(result).extracting(m -> m.get("id")).containsExactly(3L, 2L);
    }

    // ---------- rerank：失败必须回退 ----------

    @Test
    void shouldFallBackToInputOrderWhenModelFails() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(client.prompt().system(anyString()).user(anyString()).call().content())
                .thenThrow(new RuntimeException("model down"));
        ChatClientRegistry registry = new ChatClientRegistry(new HashMap<>(Map.of("deepseek", client)), "deepseek");
        LlmReranker llmReranker = new LlmReranker(registry, "deepseek");

        List<Map<String, Object>> result = llmReranker.rerank("怎么入驻", pool(), 2);

        // 不回退就抛异常了；这里必须是 RRF 原顺序的前 2 条
        assertThat(result).extracting(m -> m.get("id")).containsExactly(1L, 2L);
    }

    @Test
    void shouldReturnInputOrderWhenModelGivesNothingUsable() {
        ChatClient client = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(client.prompt().system(anyString()).user(anyString()).call().content())
                .thenReturn("抱歉，我无法排序");
        ChatClientRegistry registry = new ChatClientRegistry(new HashMap<>(Map.of("deepseek", client)), "deepseek");
        LlmReranker llmReranker = new LlmReranker(registry, "deepseek");

        List<Map<String, Object>> result = llmReranker.rerank("怎么入驻", pool(), 2);

        assertThat(result).extracting(m -> m.get("id")).containsExactly(1L, 2L);
    }

    @Test
    void shouldShortCircuitSingleCandidate() {
        List<Map<String, Object>> single = List.of(doc(7, "唯一"));
        assertThat(reranker.rerank("问题", single, 5)).isEqualTo(single);
    }
}
