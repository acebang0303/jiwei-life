package com.jiwei.ai.agent;

import com.jiwei.ai.sse.ListEventSink;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 角色提示词注入的回归：修复前 think() 用 .system(决策提示) 追加了第二条 system message，
 * 把 chatMemory 里的角色设定（含工具说明）挤到后面；窗口裁剪后甚至会整体丢失。
 */
class JChatMindPromptTest {

    private static final String ROLE_PROMPT = "你是吉味生活的探店助手，可用工具 semanticSearchRestaurant ...";

    private JChatMind mind(List<Message> memory) {
        return new JChatMind(mock(ChatClient.class), ROLE_PROMPT, List.of(),
                new ListEventSink(), "session-1", memory, 8, 30);
    }

    @Test
    void shouldInjectExactlyOneSystemMessageWithRolePrompt() {
        JChatMind mind = mind(List.of(new UserMessage("帮我安排一个约会路线")));

        List<Message> messages = mind.messagesWithSystemPrompt();

        assertThat(messages.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(((SystemMessage) messages.get(0)).getText()).contains(ROLE_PROMPT);
        assertThat(messages.stream().filter(m -> m.getMessageType() == MessageType.SYSTEM)).hasSize(1);
    }

    @Test
    void shouldNotDuplicateSystemMessageWhenAlreadyPresent() {
        JChatMind mind = mind(List.of(new SystemMessage("已有角色设定"), new UserMessage("你好")));

        List<Message> messages = mind.messagesWithSystemPrompt();

        assertThat(messages.stream().filter(m -> m.getMessageType() == MessageType.SYSTEM)).hasSize(1);
        assertThat(((SystemMessage) messages.get(0)).getText()).isEqualTo("已有角色设定");
    }

    @Test
    void shouldKeepRolePromptAfterMemoryWindowTrimsOldMessages() {
        // 超过 MessageWindowChatMemory 的 maxMessages(20)，角色设定可能被窗口挤掉
        List<Message> longHistory = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            longHistory.add(new UserMessage("历史消息 " + i));
        }

        List<Message> messages = mind(longHistory).messagesWithSystemPrompt();

        assertThat(messages.stream().anyMatch(m -> m.getMessageType() == MessageType.SYSTEM)).isTrue();
        assertThat(messages.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
    }
}
