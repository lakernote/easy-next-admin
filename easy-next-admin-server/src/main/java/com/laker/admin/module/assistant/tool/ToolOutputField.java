package com.laker.admin.module.assistant.tool;

/** 工具输出字段合同，字段只保留当前 Runtime 会执行的校验信息。 */
public record ToolOutputField(
        String name,
        ToolSchemaType type,
        boolean required
) {
    public ToolOutputField {
        name = text(name);
        if (type == null) {
            throw new IllegalArgumentException("ToolOutputField.type must not be null: " + name);
        }
        if (name.isEmpty()) {
            throw new IllegalArgumentException("ToolOutputField.name must not be blank");
        }
        if (!isCanonicalFieldName(name)) {
            throw new IllegalArgumentException("ToolOutputField.name must be a JSON property identifier: " + name);
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isCanonicalFieldName(String value) {
        if (value.isEmpty() || !(Character.isLetter(value.charAt(0)) || value.charAt(0) == '_')) {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!(Character.isLetterOrDigit(current) || current == '_')) {
                return false;
            }
        }
        return true;
    }
}
