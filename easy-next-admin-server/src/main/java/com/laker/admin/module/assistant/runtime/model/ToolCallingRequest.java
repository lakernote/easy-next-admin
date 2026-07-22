package com.laker.admin.module.assistant.runtime.model;

import com.laker.admin.module.assistant.tool.ToolDefinition;

import java.util.List;

/** 发送给支持原生 Function Calling 模型的一轮请求。 */
public record ToolCallingRequest(
        List<ToolCallingMessage> messages,
        List<ToolDefinition> tools,
        List<ToolDefinition> toolNameMappingTools,
        String operationName,
        Integer maxOutputTokens,
        ToolCallingOutputSchema outputSchema
) {
    public ToolCallingRequest(List<ToolCallingMessage> messages, List<ToolDefinition> tools) {
        this(messages, tools, tools, "agent_step", null, null);
    }

    public ToolCallingRequest(List<ToolCallingMessage> messages,
                              List<ToolDefinition> tools,
                              String operationName) {
        this(messages, tools, tools, operationName, null, null);
    }

    public ToolCallingRequest(List<ToolCallingMessage> messages,
                              List<ToolDefinition> tools,
                              String operationName,
                              Integer maxOutputTokens) {
        this(messages, tools, tools, operationName, maxOutputTokens, null);
    }

    public ToolCallingRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        toolNameMappingTools = toolNameMappingTools == null
                ? tools
                : List.copyOf(toolNameMappingTools);
        operationName = operationName == null || operationName.isBlank()
                ? "agent_step"
                : operationName;
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive when provided");
        }
    }
}
