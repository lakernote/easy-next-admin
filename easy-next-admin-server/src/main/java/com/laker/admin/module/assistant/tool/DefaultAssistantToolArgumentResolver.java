package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.state.AssistantConversationState;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 原生 Tool Call 参数绑定器。
 *
 * <p>语义理解由同一次 Tool Calling 模型调用完成；这里只做白名单字段绑定、
 * ISO 时间规范化和结构化上轮引用解析，不再二次调用 LLM，也不扫描用户消息做词面映射。</p>
 */
@Component
public class DefaultAssistantToolArgumentResolver implements AssistantToolArgumentResolver {
    private final AssistantToolArgumentReferenceResolverRegistry referenceResolverRegistry;

    public DefaultAssistantToolArgumentResolver(AssistantToolArgumentReferenceResolverRegistry referenceResolverRegistry) {
        this.referenceResolverRegistry = Objects.requireNonNull(
                referenceResolverRegistry,
                "referenceResolverRegistry must not be null"
        );
    }

    @Override
    public AssistantToolArgumentResolution resolveToolCallArguments(AssistantRequestContext context,
                                                                    AssistantVisibleTool tool,
                                                                    Map<String, Object> rawArguments,
                                                                    AssistantConversationState conversationState) {
        Objects.requireNonNull(context, "context must not be null");
        if (tool == null) {
            return AssistantToolArgumentResolution.empty(null);
        }
        Map<String, ToolInputField> parameterMap = tool.inputSchema().stream()
                .collect(Collectors.toMap(
                        ToolInputField::name,
                        Function.identity(),
                        (left, right) -> left
                ));
        Map<String, Object> safeArguments = rawArguments == null ? Map.of() : rawArguments;
        List<AssistantToolArgument> modelArguments = safeArguments.entrySet().stream()
                .filter(entry -> parameterMap.containsKey(entry.getKey()) && entry.getValue() != null)
                .map(entry -> toArgument(context, parameterMap.get(entry.getKey()), String.valueOf(entry.getValue())))
                .flatMap(Optional::stream)
                .toList();
        List<AssistantToolArgument> arguments = orderArguments(tool, modelArguments);
        arguments = referenceResolverRegistry.resolveReferences(
                context,
                tool,
                conversationState,
                arguments
        );
        return resolutionFromArguments(tool, arguments);
    }

    private static Optional<AssistantToolArgument> toArgument(AssistantRequestContext context,
                                                              ToolInputField parameter,
                                                              String value) {
        Optional<String> normalizedValue = normalizeValue(context, parameter, value);
        return normalizedValue.map(normalized -> new AssistantToolArgument(
                parameter.name(),
                parameter.displayName(),
                normalized,
                displayValue(parameter, normalized)
        ));
    }

    private static Optional<String> normalizeValue(AssistantRequestContext context,
                                                   ToolInputField parameter,
                                                   String value) {
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (parameter.format() == ToolSchemaFormat.DATE_TIME) {
            return normalizeDateTime(context, trimmed);
        }
        if (parameter.format() == ToolSchemaFormat.DATE) {
            try {
                return Optional.of(LocalDate.parse(trimmed).toString());
            } catch (DateTimeParseException ignored) {
                return Optional.empty();
            }
        }
        try {
            return switch (parameter.type()) {
                case INTEGER -> Optional.of(new BigInteger(trimmed).toString());
                case NUMBER -> Optional.of(new BigDecimal(trimmed).stripTrailingZeros().toPlainString());
                case BOOLEAN -> "true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)
                        ? Optional.of(trimmed.toLowerCase())
                        : Optional.empty();
                case STRING -> Optional.of(trimmed);
                case ARRAY, OBJECT -> Optional.empty();
            };
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<String> normalizeDateTime(AssistantRequestContext context, String value) {
        try {
            return Optional.of(LocalDateTime.parse(value).toString());
        } catch (DateTimeParseException ignored) {
            // 继续尝试带时区的 ISO 时间。
        }
        try {
            return Optional.of(OffsetDateTime.parse(value)
                    .atZoneSameInstant(context.zoneId())
                    .toLocalDateTime()
                    .toString());
        } catch (DateTimeParseException ignored) {
            // 继续尝试带区域 ID 的 ISO 时间。
        }
        try {
            return Optional.of(ZonedDateTime.parse(value)
                    .withZoneSameInstant(context.zoneId())
                    .toLocalDateTime()
                    .toString());
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private static String displayValue(ToolInputField parameter, String value) {
        if (!parameter.enumValues().isEmpty()) {
            return parameter.displayValue(value);
        }
        return parameter.format() == ToolSchemaFormat.DATE_TIME ? value.replace('T', ' ') : value;
    }

    private static List<AssistantToolArgument> orderArguments(AssistantVisibleTool tool,
                                                              List<AssistantToolArgument> modelValues) {
        Map<String, AssistantToolArgument> valueMap = new LinkedHashMap<>();
        modelValues.forEach(value -> valueMap.put(value.key(), value));
        return tool.inputSchema().stream()
                .map(ToolInputField::name)
                .map(valueMap::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private static AssistantToolArgumentResolution resolutionFromArguments(AssistantVisibleTool tool,
                                                                           List<AssistantToolArgument> arguments) {
        List<String> filledKeys = arguments.stream().map(AssistantToolArgument::key).toList();
        List<ToolInputField> missing = tool.inputSchema().stream()
                .filter(ToolInputField::required)
                .filter(parameter -> !filledKeys.contains(parameter.name()))
                .toList();
        return new AssistantToolArgumentResolution(tool.name(), arguments, missing);
    }
}
