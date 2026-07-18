package com.laker.admin.module.assistant.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 标准工具输入字段定义。 */
public record ToolInputField(
        String name,
        String displayName,
        ToolSchemaType type,
        ToolSchemaFormat format,
        String description,
        boolean required,
        List<String> enumValues,
        Map<String, String> enumLabels
) {
    public ToolInputField(String name,
                          String displayName,
                          ToolSchemaType type,
                          ToolSchemaFormat format,
                          String description,
                          boolean required,
                          List<String> enumValues) {
        this(name, displayName, type, format, description, required, enumValues, Map.of());
    }

    public ToolInputField {
        name = text(name);
        displayName = text(displayName);
        if (type == null) {
            throw new IllegalArgumentException("ToolInputField.type must not be null: " + name);
        }
        format = format == null ? ToolSchemaFormat.NONE : format;
        description = text(description);
        enumValues = enumValues == null ? List.of() : enumValues.stream().map(ToolInputField::text).toList();
        Map<String, String> normalizedLabels = new LinkedHashMap<>();
        if (enumLabels != null) {
            enumLabels.forEach((value, label) -> normalizedLabels.put(text(value), text(label)));
        }
        enumLabels = Collections.unmodifiableMap(normalizedLabels);
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
        if (enumLabels.entrySet().stream().anyMatch(entry -> entry.getKey().isEmpty() || entry.getValue().isEmpty())) {
            throw new IllegalArgumentException("ToolInputField.enumLabels must not contain blank keys or labels: " + name);
        }
        if (!enumValues.containsAll(enumLabels.keySet())) {
            throw new IllegalArgumentException("ToolInputField.enumLabels keys must exist in enumValues: " + name);
        }
    }

    public String displayValue(String value) {
        return enumLabels.getOrDefault(value, value);
    }

    public List<String> enumDisplayValues() {
        return enumValues.stream().map(this::displayValue).toList();
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
