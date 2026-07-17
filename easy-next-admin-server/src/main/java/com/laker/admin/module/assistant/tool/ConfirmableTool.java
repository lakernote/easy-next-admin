package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;

import java.util.List;

/**
 * 有副作用的标准 Tool 契约。
 *
 * <p>框架固定把准备草稿和确认执行分成两个方法，避免 Tool 作者在草稿阶段误写数据。</p>
 */
public interface ConfirmableTool extends AssistantTool {
    AssistantToolResult prepare(AssistantToolExecutionContext context);

    AssistantToolResult executeConfirmed(AssistantToolExecutionContext context);

    /**
     * 返回确认阶段必须原样重放的业务参数快照。
     *
     * <p>默认保留已经校验的 Tool 参数。需要先把业务单号等人类友好标识解析成稳定资源 ID 的 Tool，
     * 可以在 {@link #prepare(AssistantToolExecutionContext)} 成功后覆盖本方法，避免确认时重新解析目标。</p>
     */
    default List<AssistantToolArgument> confirmationArguments(AssistantToolExecutionContext context,
                                                              AssistantToolResult preparedResult) {
        return context.plan().filledArguments();
    }

    @Override
    default AssistantToolResult execute(AssistantToolExecutionContext context) {
        if (definition().executionMode() != ToolExecutionMode.REQUIRES_CONFIRMATION) {
            return AssistantToolResult.failed(definition().name(), "ConfirmableTool 必须声明 REQUIRES_CONFIRMATION 执行模式");
        }
        if (context.plan().status() == ToolExecutionStatus.APPROVAL_REQUIRED) {
            return prepare(context);
        }
        if (context.plan().status() == ToolExecutionStatus.APPROVED) {
            return executeConfirmed(context);
        }
        return AssistantToolResult.failed(definition().name(), "写操作工具只能准备草稿或在用户确认后执行");
    }
}
