package com.laker.admin.module.assistant.tool;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具参数解析结果。
 *
 * <p>arguments 是本轮已经抽取到的工具参数值；missingRequiredParameters
 * 直接来自 {@link ToolDefinition#inputSchema()} 中 required=true 的字段。
 * providedArgumentKeys 只记录模型在本轮 Tool Call 中显式提交的参数键；Pending Action
 * 合并进来的历史值不在其中。它用于确定性来源校验，不能被当成“用户一定说过”的事实。</p>
 */
public record AssistantToolArgumentResolution(
        String toolName,
        List<AssistantToolArgument> arguments,
        List<ToolInputField> missingRequiredParameters,
        Set<String> providedArgumentKeys
) {
    public AssistantToolArgumentResolution {
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        missingRequiredParameters = missingRequiredParameters == null ? List.of() : List.copyOf(missingRequiredParameters);
        providedArgumentKeys = providedArgumentKeys == null ? Set.of() : Set.copyOf(providedArgumentKeys);
    }

    /** 普通解析结果中的全部参数都来自当前 Tool Call。 */
    public AssistantToolArgumentResolution(String toolName,
                                           List<AssistantToolArgument> arguments,
                                           List<ToolInputField> missingRequiredParameters) {
        this(toolName, arguments, missingRequiredParameters, argumentKeys(arguments));
    }

    public static AssistantToolArgumentResolution empty(String toolName) {
        return new AssistantToolArgumentResolution(toolName, List.of(), List.of(), Set.of());
    }

    public boolean complete() {
        return missingRequiredParameters.isEmpty();
    }

    public Map<String, String> argumentMap() {
        return arguments.stream()
                .collect(Collectors.toUnmodifiableMap(
                        AssistantToolArgument::key,
                        AssistantToolArgument::value,
                        (left, right) -> right
                ));
    }

    private static Set<String> argumentKeys(List<AssistantToolArgument> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return Set.of();
        }
        return arguments.stream()
                .map(AssistantToolArgument::key)
                .collect(Collectors.toUnmodifiableSet());
    }
}
