package com.laker.admin.module.assistant.tool.execution;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentResolution;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ToolDefinition;

/** 将已验证的 Tool Call 转成确定性企业执行计划。 */
public interface ToolExecutionPlanFactory {
    ToolExecutionPlan create(AssistantRequestContext context,
                         ToolDefinition definition,
                         AssistantVisibleTool visibleTool,
                         AssistantToolArgumentResolution arguments);
}
