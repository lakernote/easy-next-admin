package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 工具参数校验器。
 *
 * <p>LLM 负责抽取和语义推断；这里只做确定性合法性校验，例如枚举值、时间格式和工具自己的安全约束。
 * 校验失败的字段会回到 missingParameters，让同一个 Tool Calling 模型继续追问或让用户修正。</p>
 */
@Component
public class DefaultAssistantToolArgumentValidator implements AssistantToolArgumentValidator {
    private final Map<String, AssistantToolArgumentValidationRule> rules;

    public DefaultAssistantToolArgumentValidator(List<AssistantToolArgumentValidationRule> rules) {
        this.rules = rules == null ? Map.of() : rules.stream()
                .collect(Collectors.toUnmodifiableMap(
                        AssistantToolArgumentValidationRule::toolName,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalArgumentException("重复的工具参数校验规则: " + left.toolName());
                        }
                ));
    }

    @Override
    public AssistantToolArgumentResolution validate(AssistantRequestContext context,
                                                    AssistantToolSelection toolSelection,
                                                    AssistantToolArgumentResolution resolution) {
        return validate(context, toolSelection, resolution, List.of());
    }

    @Override
    public AssistantToolArgumentResolution validate(AssistantRequestContext context,
                                                    AssistantToolSelection toolSelection,
                                                    AssistantToolArgumentResolution resolution,
                                                    List<AssistantToolResult> trustedToolResults) {
        Objects.requireNonNull(context, "context must not be null");
        if (toolSelection == null || !toolSelection.hasSelectedTool() || resolution == null) {
            return resolution == null ? AssistantToolArgumentResolution.empty(null) : resolution;
        }
        AssistantVisibleTool tool = toolSelection.selectedTool();
        Map<String, ToolInputField> parameterMap = tool.inputSchema().stream()
                .collect(Collectors.toMap(
                        ToolInputField::name,
                        Function.identity(),
                        (left, right) -> left
                ));
        Map<String, ToolInputField> invalidParameters = new LinkedHashMap<>();
        resolution.arguments().forEach(argument -> {
            ToolInputField parameter = parameterMap.get(argument.key());
            if (parameter != null && !isBasicValid(parameter, argument)) {
                invalidParameters.put(parameter.name(), parameter);
            }
        });
        AssistantToolArgumentValidationRule rule = rules.get(tool.name());
        if (rule != null) {
            rule.invalidParameters(
                            context,
                            tool,
                            resolution,
                            trustedToolResults == null ? List.of() : List.copyOf(trustedToolResults)
                    )
                    .forEach(parameter -> invalidParameters.put(parameter.name(), parameter));
        }
        if (invalidParameters.isEmpty()) {
            return resolution;
        }
        List<String> invalidKeys = List.copyOf(invalidParameters.keySet());
        List<AssistantToolArgument> validArguments = resolution.arguments().stream()
                .filter(argument -> !invalidKeys.contains(argument.key()))
                .toList();
        Map<String, ToolInputField> missingParameters = new LinkedHashMap<>();
        resolution.missingRequiredParameters()
                .forEach(parameter -> missingParameters.put(parameter.name(), parameter));
        invalidParameters.values()
                .forEach(parameter -> missingParameters.put(parameter.name(), parameter));
        Set<String> validProvidedArgumentKeys = resolution.providedArgumentKeys().stream()
                .filter(key -> !invalidKeys.contains(key))
                .collect(Collectors.toUnmodifiableSet());
        return new AssistantToolArgumentResolution(
                resolution.toolName(),
                validArguments,
                List.copyOf(missingParameters.values()),
                validProvidedArgumentKeys
        );
    }

    private static boolean isBasicValid(ToolInputField parameter, AssistantToolArgument argument) {
        if (argument == null || !StringUtils.hasText(argument.value())) {
            return false;
        }
        if (!parameter.enumValues().isEmpty()) {
            return parameter.enumValues().contains(argument.value());
        }
        if (parameter.format() == ToolSchemaFormat.DATE_TIME) {
            return canParseDateTime(argument.value());
        }
        if (parameter.format() == ToolSchemaFormat.DATE) {
            return canParseDate(argument.value());
        }
        return switch (parameter.type()) {
            case INTEGER -> canParseInteger(argument.value());
            case NUMBER -> canParseNumber(argument.value());
            case BOOLEAN -> "true".equals(argument.value()) || "false".equals(argument.value());
            case STRING -> true;
            case ARRAY, OBJECT -> false;
        };
    }

    private static boolean canParseInteger(String value) {
        try {
            new java.math.BigInteger(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean canParseNumber(String value) {
        try {
            new java.math.BigDecimal(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean canParseDateTime(String value) {
        try {
            LocalDateTime.parse(value);
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private static boolean canParseDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }
}
