package com.laker.admin.module.assistant.tool;

import java.util.List;

/** 经过用户权限过滤的脱敏 Tool 快照。 */
public record AssistantVisibleTool(
        String name,
        String displayName,
        String description,
        boolean readOnly,
        List<ToolInputField> inputSchema
) {
    public AssistantVisibleTool {
        inputSchema = inputSchema == null ? List.of() : List.copyOf(inputSchema);
    }

    public static AssistantVisibleTool from(ToolDefinition definition) {
        return new AssistantVisibleTool(
                definition.name(),
                definition.displayName(),
                definition.description(),
                definition.readOnly(),
                definition.inputSchema()
        );
    }
}
