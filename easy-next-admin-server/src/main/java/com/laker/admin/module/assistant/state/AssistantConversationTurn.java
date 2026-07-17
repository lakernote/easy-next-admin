package com.laker.admin.module.assistant.state;

import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * Chat Memory 中不可拆分的一轮可见对话。
 *
 * <p>Tool Calling 中间消息不进入这里，避免旧 observation 污染下一轮推理。</p>
 */
public record AssistantConversationTurn(
        String userMessage,
        String assistantMessage,
        Instant completedAt
) {
    public AssistantConversationTurn {
        userMessage = userMessage == null ? "" : userMessage;
        assistantMessage = assistantMessage == null ? "" : assistantMessage;
    }

    public boolean complete() {
        return StringUtils.hasText(userMessage)
                && StringUtils.hasText(assistantMessage)
                && completedAt != null;
    }
}
