package com.laker.admin.module.assistant.tool;

import java.util.List;

/** 标准工具输入字段定义。 */
public record ToolInputField(
        String name,
        String displayName,
        ToolSchemaType type,
        ToolSchemaFormat format,
        String description,
        boolean required,
        List<String> enumValues
) {
    public ToolInputField {
        name = text(name);
        displayName = text(displayName);
        if (type == null) {
            throw new IllegalArgumentException("ToolInputField.type must not be null: " + name);
        }
        format = format == null ? ToolSchemaFormat.NONE : format;
        description = text(description);
        enumValues = enumValues == null ? List.of() : enumValues.stream().map(ToolInputField::text).toList();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("ToolInputField.name must not be blank");
        }
        if (!isCanonicalFieldName(name)) {
            throw new IllegalArgumentException("ToolInputField.name must be a JSON property identifier: " + name);
        }
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("ToolInputField.displayName must not be blank");
        }
        if (description.isEmpty()) {
            throw new IllegalArgumentException("ToolInputField.description must not be blank: " + name);
        }
        if (format != ToolSchemaFormat.NONE && type != ToolSchemaType.STRING) {
            throw new IllegalArgumentException("ToolInputField.format only supports string fields: " + name);
        }
        if (type == ToolSchemaType.ARRAY || type == ToolSchemaType.OBJECT) {
            throw new IllegalArgumentException("Mini Runtime does not support structured input fields yet: " + name);
        }
        if (enumValues.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("ToolInputField.enumValues must not contain blank values: " + name);
        }
        if (enumValues.stream().distinct().count() != enumValues.size()) {
            throw new IllegalArgumentException("ToolInputField.enumValues must be unique: " + name);
        }
        if (!enumValues.isEmpty() && type != ToolSchemaType.STRING) {
            throw new IllegalArgumentException("ToolInputField.enumValues only supports string fields: " + name);
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
