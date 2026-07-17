package com.laker.admin.module.assistant.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 唯一 Tool 注册表：定义和可执行实例由同一个 {@link AssistantTool} 提供。 */
@Component
public class AssistantToolRegistry {
    private final Map<String, AssistantTool> tools;

    public AssistantToolRegistry(List<AssistantTool> tools) {
        List<AssistantTool> safeTools = tools == null ? List.of() : List.copyOf(tools);
        safeTools.forEach(AssistantToolRegistry::validateContract);
        this.tools = safeTools.stream()
                .collect(Collectors.toUnmodifiableMap(
                        tool -> tool.definition().name(),
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalArgumentException("重复的助手工具名称: " + left.definition().name());
                        }
                ));
    }

    private static void validateContract(AssistantTool tool) {
        Objects.requireNonNull(tool, "AssistantTool must not be null");
        ToolDefinition definition = Objects.requireNonNull(
                tool.definition(),
                "AssistantTool.definition must not be null"
        );
        if (definition.requiresConfirmation() && !(tool instanceof ConfirmableTool)) {
            throw new IllegalArgumentException(
                    "有副作用的 Tool 必须实现 ConfirmableTool: " + definition.name()
            );
        }
        if (definition.readOnly() && tool instanceof ConfirmableTool) {
            throw new IllegalArgumentException(
                    "ConfirmableTool 必须声明 REQUIRES_CONFIRMATION: " + definition.name()
            );
        }
    }

    public Optional<AssistantTool> find(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    /** 返回稳定排序的工具快照，供权限过滤后的 Agent 工具面使用。 */
    public List<AssistantTool> tools() {
        return tools.values().stream()
                .sorted(java.util.Comparator.comparing(tool -> tool.definition().name()))
                .toList();
    }
}
