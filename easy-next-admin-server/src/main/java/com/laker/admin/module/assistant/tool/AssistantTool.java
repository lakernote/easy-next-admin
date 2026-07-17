package com.laker.admin.module.assistant.tool;

/** 企业 Agent 的标准可执行 Tool 合同。 */
public interface AssistantTool {
    ToolDefinition definition();

    AssistantToolResult execute(AssistantToolExecutionContext context);
}
