package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;

/**
 * ToolExecutor 调用真实工具时传入的稳定上下文。
 */
public record AssistantToolExecutionContext(
        AssistantRequestContext requestContext,
        ToolExecutionPlan plan
) {
}
