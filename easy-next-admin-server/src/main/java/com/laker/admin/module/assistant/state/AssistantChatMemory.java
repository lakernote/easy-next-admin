package com.laker.admin.module.assistant.state;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat Memory：只保存用户与助手最终可见的最近完整对话轮次。
 *
 * <p>一个 Turn 同时包含 user/assistant 文本，窗口淘汰时也按 Turn 淘汰，避免出现只有助手回复、
 * 没有对应用户问题的半轮上下文。完整聊天历史、Prompt、Tool 原始结果和 Trace 不属于 Chat Memory。</p>
 *
 * <pre>{@code
 * {
 *   "recentTurns": [
 *     {
 *       "userMessage": "查一下我的待办",
 *       "assistantMessage": "你当前有 2 个待办。",
 *       "completedAt": "2026-07-14T02:30:00Z"
 *     }
 *   ]
 * }
 * }</pre>
 */
public record AssistantChatMemory(
        List<AssistantConversationTurn> recentTurns
) {
    public AssistantChatMemory {
        recentTurns = recentTurns == null ? List.of() : List.copyOf(recentTurns);
    }

    public static AssistantChatMemory empty() {
        return new AssistantChatMemory(List.of());
    }

    public AssistantChatMemory append(AssistantConversationTurn turn, int maxTurns) {
        if (turn == null || !turn.complete()) {
            return this;
        }
        int boundedMaxTurns = Math.max(1, maxTurns);
        List<AssistantConversationTurn> next = new ArrayList<>(recentTurns);
        next.add(turn);
        int firstRetainedIndex = Math.max(0, next.size() - boundedMaxTurns);
        return new AssistantChatMemory(next.subList(firstRetainedIndex, next.size()));
    }
}
