package com.laker.admin.module.assistant.tool;

/**
 * ToolArgumentResolver 已经识别出的字段值。
 *
 * <p>value 使用机器可读格式，displayValue 用于调试页和后续用户确认卡片展示。</p>
 */
public record AssistantToolArgument(
        String key,
        String label,
        String value,
        String displayValue
) {
    public AssistantToolArgument {
        key = text(key);
        label = text(label);
        value = text(value);
        displayValue = text(displayValue);
        if (key.isEmpty() || label.isEmpty() || value.isEmpty()) {
            throw new IllegalArgumentException("Tool argument key, label and value must not be blank");
        }
        if (displayValue.isEmpty()) {
            displayValue = value;
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
