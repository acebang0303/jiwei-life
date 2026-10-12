package com.jiwei.ai.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁定 JChatMind 流式决策所依赖的 Spring AI 聚合语义：
 *
 *   1. 原始分片流上挂 doOnNext 能拿到「每一片」——token 级 SSE 推送靠它；
 *   2. ChatClientMessageAggregator 的回调只被调用一次，且收到的是「合并后的完整响应」
 *      —— 工具执行（ToolCallingManager）靠它拿完整 tool_calls。
 *
 * 这两条如果被上游改掉，流式输出会静默退化成一次性输出（或工具调用失效），
 * 所以用测试把它钉住。
 */
class StreamingAggregatorSemanticsTest {

    private static ChatClientResponse chunk(String text) {
        return new ChatClientResponse(
                new ChatResponse(List.of(new Generation(new AssistantMessage(text)))), Map.of());
    }

    private static String textOf(ChatClientResponse response) {
        return response.chatResponse().getResult().getOutput().getText();
    }

    @Test
    void rawFluxTapSeesEachChunkWhileAggregatorYieldsMergedResponse() {
        List<String> streamedDeltas = new ArrayList<>();
        AtomicReference<ChatClientResponse> aggregated = new AtomicReference<>();

        Flux<ChatClientResponse> tapped = Flux.just(chunk("今"), chunk("天"), chunk("好"))
                .doOnNext(c -> streamedDeltas.add(textOf(c)));

        new ChatClientMessageAggregator()
                .aggregateChatClientResponse(tapped, aggregated::set)
                .then()
                .block();

        // 1、旁路观察拿到的是逐片增量
        assertThat(streamedDeltas).containsExactly("今", "天", "好");
        // 2、聚合器回调拿到的是完整合并结果
        assertThat(aggregated.get()).isNotNull();
        assertThat(textOf(aggregated.get())).isEqualTo("今天好");
    }

    @Test
    void aggregatorCallbackIsInvokedExactlyOnce() {
        List<String> callbackPayloads = new ArrayList<>();

        new ChatClientMessageAggregator()
                .aggregateChatClientResponse(
                        Flux.just(chunk("a"), chunk("b"), chunk("c")),
                        c -> callbackPayloads.add(textOf(c)))
                .then()
                .block();

        assertThat(callbackPayloads).containsExactly("abc");
    }
}
