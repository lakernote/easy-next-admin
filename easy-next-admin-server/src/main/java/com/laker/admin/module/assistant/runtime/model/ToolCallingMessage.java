package com.laker.admin.module.assistant.runtime.model;

import java.util.List;

/** 一次 Tool Calling 循环中的标准消息。 */
public record ToolCallingMessage(
        ToolCallingMessageRole role,
        String content,
        String toolCallId,
        List<ToolCall> toolCalls
) {
    public ToolCallingMessage {
        toolCallId = toolCallId == null ? "" : toolCallId.trim();
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }

    public static ToolCallingMessage system(String content) {
        return new ToolCallingMessage(ToolCallingMessageRole.SYSTEM, content, "", List.of());
    }

    public static ToolCallingMessage user(String content) {
        return new ToolCallingMessage(ToolCallingMessageRole.USER, content, "", List.of());
    }

    public static ToolCallingMessage assistant(String content, List<ToolCall> toolCalls) {
        return new ToolCallingMessage(ToolCallingMessageRole.ASSISTANT, content, "", toolCalls);
    }

    public static ToolCallingMessage tool(String toolCallId, String content) {
        return new ToolCallingMessage(ToolCallingMessageRole.TOOL, content, toolCallId, List.of());
    }
}
