package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.context.AssistantRequestContext;

import java.util.List;

public interface AssistantToolArgumentValidationRule {
    String toolName();

    List<ToolInputField> invalidParameters(AssistantRequestContext context,
                                           AssistantVisibleTool tool,
                                           AssistantToolArgumentResolution resolution);

    default List<ToolInputField> invalidParameters(AssistantRequestContext context,
                                                   AssistantVisibleTool tool,
                                                   AssistantToolArgumentResolution resolution,
                                                   List<AssistantToolResult> trustedToolResults) {
        return invalidParameters(context, tool, resolution);
    }
}
