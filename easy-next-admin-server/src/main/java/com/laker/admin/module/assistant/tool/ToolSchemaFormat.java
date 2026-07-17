package com.laker.admin.module.assistant.tool;

import com.fasterxml.jackson.annotation.JsonValue;

/** 当前 Mini Runtime 支持的 JSON Schema 字符串格式。 */
public enum ToolSchemaFormat {
    NONE(""),
    DATE("date"),
    DATE_TIME("date-time");

    private final String value;

    ToolSchemaFormat(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
