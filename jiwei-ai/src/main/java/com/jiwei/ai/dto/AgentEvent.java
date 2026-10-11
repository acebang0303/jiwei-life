package com.jiwei.ai.dto;

/**
 * Agent SSE 事件负载
 *
 * @param type    事件类型: thinking / action / message / done / error
 * @param content 事件内容（message 为合并后的文本；done 为 conversationId 供前端续接；action 为工具调用过程）
 */
public record AgentEvent(String type, String content) {
}
