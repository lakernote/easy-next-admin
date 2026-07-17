package com.laker.admin.module.assistant.runtime.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 模型按 Tool Schema 返回的一次标准工具调用。 */
public record ToolCall(
        String id,
        String name,
        Map<String, Object> arguments,
        String argumentError
) {
    public ToolCall(String id, String name, Map<String, Object> arguments) {
        this(id, name, arguments, "");
    }

    public ToolCall {
        id = id == null ? "" : id.trim();
        name = name == null ? "" : name.trim();
        arguments = arguments == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
        argumentError = argumentError == null ? "" : argumentError.trim();
    }

    /** 参数 JSON 解析失败时保留错误，让 Agent 把失败 observation 回填模型并允许其自行修正。 */
    public boolean hasArgumentError() {
        return !argumentError.isEmpty();
    }
}
