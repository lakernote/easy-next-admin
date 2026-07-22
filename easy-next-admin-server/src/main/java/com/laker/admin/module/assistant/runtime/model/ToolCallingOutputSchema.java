package com.laker.admin.module.assistant.runtime.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 模型最终内容的可选严格 JSON Schema；Tool Call 响应不受该内容合同影响。 */
public record ToolCallingOutputSchema(
        String name,
        Map<String, Object> schema
) {
    private static final ToolCallingOutputSchema ASSISTANT_REPLY = new ToolCallingOutputSchema(
            "assistant_reply",
            Map.of(
                    "type", "object",
                    "additionalProperties", false,
                    "properties", Map.of(
                            "reply", Map.of(
                                    "type", "string",
                                    "description", "直接展示给用户的简洁中文最终回复"
                            )
                    ),
                    "required", List.of("reply")
            )
    );

    public ToolCallingOutputSchema {
        name = name == null ? "" : name.trim();
        if (name.isEmpty() || !name.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("output schema name must use provider-safe characters");
        }
        if (schema == null || schema.isEmpty()) {
            throw new IllegalArgumentException("output schema must not be empty");
        }
        schema = Map.copyOf(new LinkedHashMap<>(schema));
    }

    public static ToolCallingOutputSchema assistantReply() {
        return ASSISTANT_REPLY;
    }
}
