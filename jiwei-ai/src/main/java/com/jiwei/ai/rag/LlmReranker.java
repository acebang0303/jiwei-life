package com.jiwei.ai.rag;

import com.jiwei.ai.config.ChatClientRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于大模型的相关性重排（LLM rerank）。
 *
 * 为什么需要它：RRF 只融合「排名」，没有真正读过文本内容，无法判断哪一段真正回答了问题。
 * 让模型读一遍候选片段并给出相关度顺序，能显著把「字面命中但答非所问」的片段压下去。
 *
 * 工程上的三条防御（LLM 输出不可信）：
 *   1. 只让模型输出 id 序列（最简单的结构），不做自由文本解析；
 *   2. 解析出的 id 必须出现在候选集合里，否则丢弃——防止模型编造 id；
 *   3. 任何异常/解析失败/模型返回空 → 回退为 RRF 原顺序，绝不因为重排把检索搞挂。
 *   4. 模型给出的顺序可能漏掉部分候选，漏掉的按原顺序补在后面，保证条数不缩水。
 */
@Slf4j
@Component
public class LlmReranker implements Reranker {

    /** 一次送进模型的候选上限：太多会拉长时延并超出上下文 */
    private static final int MAX_CANDIDATES = 10;
    /** 单个候选片段截断长度，控制 token */
    private static final int SNIPPET_LEN = 300;

    private static final Pattern INT = Pattern.compile("\\d+");

    private static final String PROMPT = """
            你是检索结果排序器。下面会给出一组候选文档片段（每个片段带一个 id），
            请根据「与用户问题的相关程度」从高到低排序。

            只输出排序后的 id，用英文逗号分隔，例如：3,1,5
            不要输出任何解释、标题或多余文字。只包含上面出现过的 id。
            """;

    private final ChatClientRegistry registry;
    private final String model;

    public LlmReranker(ChatClientRegistry registry,
                       @Value("${rag.rerank.model:deepseek}") String model) {
        this.registry = registry;
        this.model = model;
    }

    @Override
    public List<Map<String, Object>> rerank(String query, List<Map<String, Object>> candidates, int topK) {
        List<Map<String, Object>> fallback = firstN(candidates, topK);
        if (candidates == null || candidates.size() <= 1 || topK <= 0) {
            return fallback;
        }
        try {
            List<Map<String, Object>> pool = candidates.size() > MAX_CANDIDATES
                    ? candidates.subList(0, MAX_CANDIDATES) : candidates;
            ChatClient client = registry.get(model);
            String answer = client.prompt()
                    .system(PROMPT)
                    .user(buildUserMessage(query, pool))
                    .call()
                    .content();
            List<Map<String, Object>> reranked = applyOrder(answer, pool, topK);
            if (reranked.isEmpty()) {
                log.warn("重排未解析出有效 id，回退 RRF 顺序");
                return fallback;
            }
            log.info("重排完成: 候选={} -> 采用={}", pool.size(), reranked.size());
            return reranked;
        } catch (Exception e) {
            log.warn("重排失败，回退 RRF 顺序: {}", e.getMessage());
            return fallback;
        }
    }

    private String buildUserMessage(String query, List<Map<String, Object>> pool) {
        StringBuilder sb = new StringBuilder("用户问题：").append(query).append("\n\n候选片段：\n");
        for (Map<String, Object> doc : pool) {
            sb.append("id=").append(doc.get("id")).append(" | ");
            Object title = doc.get("title");
            if (title != null) {
                sb.append(title).append(" | ");
            }
            sb.append(snippet(doc));
            sb.append('\n');
        }
        return sb.toString();
    }

    private String snippet(Map<String, Object> doc) {
        Object content = doc.get("content");
        String text = content == null ? "" : String.valueOf(content).replaceAll("\\s+", " ");
        return text.length() > SNIPPET_LEN ? text.substring(0, SNIPPET_LEN) : text;
    }

    /** 把模型给的 id 序列映射回候选（只认候选里真实存在的 id），漏掉的按原顺序补在后面。 */
    List<Map<String, Object>> applyOrder(String answer, List<Map<String, Object>> pool, int topK) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (answer != null) {
            Set<String> picked = new LinkedHashSet<>();
            Matcher m = INT.matcher(answer);
            while (m.find()) {
                String id = m.group();
                if (picked.contains(id)) {
                    continue;
                }
                for (Map<String, Object> doc : pool) {
                    if (id.equals(String.valueOf(doc.get("id")))) {
                        picked.add(id);
                        result.add(doc);
                        break;
                    }
                }
                if (result.size() >= topK) {
                    break;
                }
            }
        }
        if (result.isEmpty()) {
            return List.of();
        }
        // 模型没提到的候选按原顺序补足，保证不因重排而少给结果
        for (Map<String, Object> doc : pool) {
            if (result.size() >= topK) {
                break;
            }
            boolean already = result.stream().anyMatch(r -> String.valueOf(r.get("id"))
                    .equals(String.valueOf(doc.get("id"))));
            if (!already) {
                result.add(doc);
            }
        }
        return result;
    }

    private static List<Map<String, Object>> firstN(List<Map<String, Object>> list, int n) {
        if (list == null || list.isEmpty() || n <= 0) {
            return List.of();
        }
        return list.size() > n ? new ArrayList<>(list.subList(0, n)) : new ArrayList<>(list);
    }
}
