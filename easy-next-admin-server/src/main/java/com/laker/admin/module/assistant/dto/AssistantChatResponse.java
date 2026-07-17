package com.laker.admin.module.assistant.dto;

import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;

/**
 * 面向最终用户的精简对话响应。
 *
 * <p>正式聊天入口只返回继续对话所需的信息，不向普通用户暴露调试页使用的
 * prompt、Tool 参数、模型响应和内部执行轨迹。</p>
 */
public record AssistantChatResponse(
        String runId,
        Long conversationId,
        String reply,
        AssistantChatStatus status
) {
    public static AssistantChatResponse from(AssistantEntryResponse response) {
        ToolExecutionStatus planStatus = ToolExecutionStatus.valueOf(response.plan().status());
        AssistantToolRunStatus toolRunStatus = response.execution() == null
                ? AssistantToolRunStatus.SKIPPED
                : AssistantToolRunStatus.valueOf(response.execution().status());
        return new AssistantChatResponse(
                response.runId(),
                response.conversationId(),
                response.reply(),
                AssistantChatStatus.from(planStatus, toolRunStatus)
        );
    }

    public enum AssistantChatStatus {
        COMPLETED,
        AWAITING_INPUT,
        AWAITING_CONFIRMATION,
        PARTIALLY_COMPLETED,
        FAILED,
        INDETERMINATE,
        BLOCKED;

        private static AssistantChatStatus from(ToolExecutionStatus planStatus,
                                                AssistantToolRunStatus toolRunStatus) {
            if (planStatus == ToolExecutionStatus.INPUT_REQUIRED) {
                return AWAITING_INPUT;
            }
            if (planStatus == ToolExecutionStatus.APPROVAL_REQUIRED) {
                return AWAITING_CONFIRMATION;
            }
            return switch (toolRunStatus) {
                case FAILED -> FAILED;
                case INDETERMINATE -> INDETERMINATE;
                case PARTIAL_SUCCESS -> PARTIALLY_COMPLETED;
                case SKIPPED, EXECUTED -> planStatus == ToolExecutionStatus.BLOCKED
                        ? BLOCKED
                        : COMPLETED;
            };
        }
    }
}
