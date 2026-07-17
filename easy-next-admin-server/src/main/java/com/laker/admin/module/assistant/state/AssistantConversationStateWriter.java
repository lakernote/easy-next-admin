package com.laker.admin.module.assistant.state;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 根据当前快照和本轮结果生成下一版会话状态，并做一次原子提交。
 *
 * <p>这里是 Conversation State 唯一的状态转移入口。不要在 Tool 或 Controller 中分别更新
 * Chat Memory、Pending Action；否则一次 Turn 会重新出现半写入状态。</p>
 */
@Component
public class AssistantConversationStateWriter {
    private static final int MAX_RECENT_TURNS = 6;

    private final AssistantConversationStateStore stateStore;
    private final AssistantRunReferenceExtractorRegistry runReferenceExtractorRegistry;

    public AssistantConversationStateWriter(AssistantConversationStateStore stateStore,
                                            AssistantRunReferenceExtractorRegistry runReferenceExtractorRegistry) {
        this.stateStore = stateStore;
        this.runReferenceExtractorRegistry = runReferenceExtractorRegistry;
    }

    public AssistantConversationState writeTurn(AssistantRequestContext context,
                                                AssistantConversationState currentState,
                                                AssistantPendingActionUpdate pendingActionUpdate,
                                                AssistantToolResult toolResult,
                                                String reply) {
        AssistantConversationState current = currentState == null
                ? AssistantConversationState.empty()
                : currentState;
        AssistantPendingActionUpdate safePendingActionUpdate = Objects.requireNonNull(
                pendingActionUpdate,
                "pendingActionUpdate must not be null"
        );
        AssistantPendingAction pendingAction = safePendingActionUpdate.applyTo(current.pendingAction());
        AssistantChatMemory chatMemory = appendCompletedTurn(current.chatMemory(), context, reply);
        List<AssistantRunReference> references = runReferenceExtractorRegistry.extract(toolResult);
        AssistantLastResult lastResult = shouldReplaceLastResult(toolResult, references)
                ? AssistantLastResult.from(toolResult, references, context.requestInstant())
                : current.lastResult();
        AssistantConversationState next = new AssistantConversationState(
                current.version() + 1,
                chatMemory,
                pendingAction,
                lastResult,
                context.requestInstant()
        );
        stateStore.save(context, current.version(), next);
        return next;
    }

    private static boolean shouldReplaceLastResult(AssistantToolResult toolResult,
                                                   List<AssistantRunReference> references) {
        if (references != null && !references.isEmpty()) {
            return true;
        }
        return toolResult != null
                && (toolResult.status() == AssistantToolRunStatus.EXECUTED
                || toolResult.status() == AssistantToolRunStatus.PARTIAL_SUCCESS)
                && StringUtils.hasText(toolResult.toolName());
    }

    private static AssistantChatMemory appendCompletedTurn(AssistantChatMemory currentMemory,
                                                           AssistantRequestContext context,
                                                           String reply) {
        AssistantChatMemory memory = currentMemory == null ? AssistantChatMemory.empty() : currentMemory;
        if (!StringUtils.hasText(reply)) {
            return memory;
        }
        return memory.append(new AssistantConversationTurn(
                context.normalizedMessage(), reply, context.requestInstant()), MAX_RECENT_TURNS);
    }
}
