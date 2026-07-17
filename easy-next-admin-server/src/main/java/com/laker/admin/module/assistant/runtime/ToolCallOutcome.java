package com.laker.admin.module.assistant.runtime;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.state.AssistantPendingAction;
import com.laker.admin.module.assistant.state.AssistantPendingActionUpdate;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import org.springframework.util.StringUtils;

/** 一次 Tool Call 经过服务端校验和执行后的标准结果。 */
record ToolCallOutcome(
        ToolExecutionPlan plan,
        AssistantToolResult result,
        ToolDefinition definition,
        AssistantPendingActionUpdate pendingActionUpdate
) {
    ToolCallOutcome(ToolExecutionPlan plan,
                    AssistantToolResult result,
                    ToolDefinition definition) {
        this(plan, result, definition, AssistantPendingActionUpdate.unchanged());
    }

    ToolCallOutcome resolveConversationUpdate(AssistantRequestContext context) {
        AssistantPendingActionUpdate update = switch (plan.status()) {
            case INPUT_REQUIRED, APPROVAL_REQUIRED -> canPersistPendingAction(plan)
                    ? AssistantPendingActionUpdate.upsert(AssistantPendingAction.from(context, plan))
                    : AssistantPendingActionUpdate.unchanged();
            case APPROVED -> successful()
                    ? AssistantPendingActionUpdate.clear()
                    : AssistantPendingActionUpdate.unchanged();
            case CANCELED -> AssistantPendingActionUpdate.clear();
            case DIRECT_RESPONSE, READY, BLOCKED -> AssistantPendingActionUpdate.unchanged();
        };
        return new ToolCallOutcome(plan, result, definition, update);
    }

    boolean waitsForUser() {
        return plan.status() == ToolExecutionStatus.INPUT_REQUIRED
                || plan.status() == ToolExecutionStatus.APPROVAL_REQUIRED;
    }

    boolean confirmedActionCompleted() {
        return plan.status() == ToolExecutionStatus.APPROVED && successful();
    }

    private boolean successful() {
        return result.status() == AssistantToolRunStatus.EXECUTED
                || result.status() == AssistantToolRunStatus.PARTIAL_SUCCESS;
    }

    private static boolean canPersistPendingAction(ToolExecutionPlan plan) {
        return StringUtils.hasText(plan.toolName())
                && (!plan.filledArguments().isEmpty() || !plan.missingParameters().isEmpty());
    }
}
