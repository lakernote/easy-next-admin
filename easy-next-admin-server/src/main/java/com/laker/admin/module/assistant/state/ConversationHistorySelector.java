package com.laker.admin.module.assistant.state;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * 为主模型选择最近的完整对话轮次。
 *
 * <p>这里返回结构化的 {@link AssistantConversationTurn}，不把历史重新拼成一段 Prompt 字符串。
 * 模型中间响应、Tool 原始结果、token 和 trace 属于 Run Record，不进入长期对话记忆。</p>
 */
@Component
public class ConversationHistorySelector {
    private static final int DEFAULT_MAX_TURNS = 4;
    private static final int MAX_CONTENT_LENGTH = 300;
    private static final int MAX_HISTORY_LENGTH = 1_200;

    public List<AssistantConversationTurn> selectRecentTurns(AssistantConversationState conversationState) {
        return selectRecentTurns(conversationState, DEFAULT_MAX_TURNS);
    }

    public List<AssistantConversationTurn> selectRecentTurns(AssistantConversationState conversationState,
                                                             int maxTurns) {
        if (conversationState == null || conversationState.chatMemory() == null) {
            return List.of();
        }
        List<AssistantConversationTurn> completeTurns = conversationState.chatMemory().recentTurns().stream()
                .filter(turn -> turn != null && turn.complete())
                .toList();
        if (completeTurns.isEmpty()) {
            return List.of();
        }
        int safeMax = Math.max(0, maxTurns);
        if (safeMax == 0) {
            return List.of();
        }

        Deque<AssistantConversationTurn> selected = new ArrayDeque<>();
        int selectedLength = 0;
        int earliestCandidate = Math.max(0, completeTurns.size() - safeMax);
        for (int index = completeTurns.size() - 1; index >= earliestCandidate; index--) {
            AssistantConversationTurn candidate = sanitize(completeTurns.get(index));
            int candidateLength = candidate.userMessage().length() + candidate.assistantMessage().length();
            if (!selected.isEmpty() && selectedLength + candidateLength > MAX_HISTORY_LENGTH) {
                break;
            }
            selected.addFirst(candidate);
            selectedLength += candidateLength;
        }
        return List.copyOf(selected);
    }

    private static AssistantConversationTurn sanitize(AssistantConversationTurn turn) {
        return new AssistantConversationTurn(
                sanitizeContent(turn.userMessage()),
                sanitizeContent(turn.assistantMessage()),
                turn.completedAt()
        );
    }

    private static String sanitizeContent(String value) {
        String normalized = normalizeWhitespace(value);
        if (normalized.length() <= MAX_CONTENT_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, MAX_CONTENT_LENGTH) + "...";
    }

    private static String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(value.length());
        boolean previousWasWhitespace = true;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isWhitespace(current)) {
                if (!previousWasWhitespace) {
                    normalized.append(' ');
                    previousWasWhitespace = true;
                }
                continue;
            }
            normalized.append(current);
            previousWasWhitespace = false;
        }
        int length = normalized.length();
        if (length > 0 && normalized.charAt(length - 1) == ' ') {
            normalized.setLength(length - 1);
        }
        return normalized.toString();
    }
}
