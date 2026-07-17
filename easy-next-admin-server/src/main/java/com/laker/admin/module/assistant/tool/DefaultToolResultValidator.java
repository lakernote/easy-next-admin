package com.laker.admin.module.assistant.tool;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Component
public class DefaultToolResultValidator implements ToolResultValidator {
    @Override
    public ValidationResult validate(ToolDefinition definition,
                                     AssistantToolResult result,
                                     ToolExecutionPhase phase) {
        if (result == null) {
            return ValidationResult.failure("工具返回结果为空");
        }
        if (result.status() != AssistantToolRunStatus.EXECUTED) {
            return ValidationResult.success();
        }
        if (StringUtils.hasText(result.toolName()) && !definition.name().equals(result.toolName())) {
            return ValidationResult.failure("工具结果编码与定义不一致");
        }
        // outputSchema 描述只读结果或确认执行后的最终结果；草稿阶段只验证基本结果合同。
        if (phase == ToolExecutionPhase.PREPARE) {
            return ValidationResult.success();
        }
        List<String> missing = definition.outputSchema().stream()
                .filter(ToolOutputField::required)
                .filter(field -> missingValue(value(result.data(), field.name())))
                .map(ToolOutputField::name)
                .toList();
        if (!missing.isEmpty()) {
            return ValidationResult.failure("工具输出缺少必需字段: " + String.join(",", missing));
        }
        List<String> invalidTypes = definition.outputSchema().stream()
                .filter(field -> {
                    Object fieldValue = value(result.data(), field.name());
                    return fieldValue != null && !matchesType(fieldValue, field.type());
                })
                .map(ToolOutputField::name)
                .toList();
        return invalidTypes.isEmpty()
                ? ValidationResult.success()
                : ValidationResult.failure("工具输出字段类型不符合合同: " + String.join(",", invalidTypes));
    }

    private static boolean missingValue(Object value) {
        return value == null || value instanceof CharSequence text && !StringUtils.hasText(text);
    }

    private static Object value(Object data, String name) {
        if (data == null || !StringUtils.hasText(name)) {
            return null;
        }
        if (data instanceof Map<?, ?> map) {
            return map.get(name);
        }
        try {
            Method accessor;
            try {
                accessor = data.getClass().getMethod(name);
            } catch (NoSuchMethodException ignored) {
                String getter = "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
                accessor = data.getClass().getMethod(getter);
            }
            return accessor.invoke(data);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static boolean matchesType(Object value, ToolSchemaType type) {
        return switch (type) {
            case STRING -> value instanceof CharSequence || value instanceof Character;
            case INTEGER -> value instanceof Byte || value instanceof Short || value instanceof Integer
                    || value instanceof Long || value instanceof BigInteger;
            case NUMBER -> value instanceof Number;
            case BOOLEAN -> value instanceof Boolean;
            case ARRAY -> value instanceof Collection<?> || value.getClass().isArray();
            case OBJECT -> value instanceof Map<?, ?> || value.getClass().isRecord();
        };
    }
}
