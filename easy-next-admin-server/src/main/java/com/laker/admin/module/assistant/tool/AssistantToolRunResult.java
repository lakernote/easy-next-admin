package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
/**
 * {@link AssistantToolRunner} 执行一份 Tool Plan 后的标准结果。
 *
 * <p>result 是本轮主工具结果；plan 已包含执行后的 step 状态，避免再保存一份重复步骤快照。</p>
 */
public record AssistantToolRunResult(
        AssistantToolResult result,
        ToolExecutionPlan plan
) {
}
