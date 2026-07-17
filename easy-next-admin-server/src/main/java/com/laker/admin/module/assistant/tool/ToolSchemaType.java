package com.laker.admin.module.assistant.tool;

import com.fasterxml.jackson.annotation.JsonValue;

/** JSON Schema 支持的基础类型。 */
public enum ToolSchemaType {
    STRING("string"),
    INTEGER("integer"),
    NUMBER("number"),
    BOOLEAN("boolean"),
    ARRAY("array"),
    OBJECT("object");

    private final String value;

    ToolSchemaType(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
