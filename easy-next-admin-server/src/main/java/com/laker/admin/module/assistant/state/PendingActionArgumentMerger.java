package com.laker.admin.module.assistant.state;

import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentResolution;
import com.laker.admin.module.assistant.tool.AssistantToolVisibilityPlan;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ToolInputField;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 合并上一轮 pending 槽位和本轮新解析槽位。
 *
 * <p>只在本轮已经确认继续使用同一个工具时合并。生产调用还会传入当前消息明确提供的参数键，
 * 模型补全、历史回显或默认值不能覆盖用户上一轮已经明确给出的值。</p>
 */
@Component
public class PendingActionArgumentMerger {
    public AssistantToolArgumentResolution merge(AssistantToolVisibilityPlan toolVisibilityPlan,
                                                 AssistantToolArgumentResolution currentResolution,
                                                 AssistantPendingAction pendingAction) {
        List<String> currentArgumentKeys = currentResolution == null
                ? List.of()
                : currentResolution.arguments().stream().map(AssistantToolArgument::key).toList();
        return merge(toolVisibilityPlan, currentResolution, pendingAction, currentArgumentKeys);
    }

    public AssistantToolArgumentResolution merge(AssistantToolVisibilityPlan toolVisibilityPlan,
                                                 AssistantToolArgumentResolution currentResolution,
                                                 AssistantPendingAction pendingAction,
                                                 Collection<String> providedArgumentKeys) {
        if (pendingAction == null || toolVisibilityPlan.tools().isEmpty()) {
            return currentResolution;
        }
        AssistantVisibleTool selectedTool = selectTool(toolVisibilityPlan, currentResolution, pendingAction);
        if (selectedTool == null) {
            return currentResolution;
        }
        if (!selectedTool.name().equals(pendingAction.toolName())) {
            return currentResolution;
        }
        Map<String, AssistantToolArgument> valueMap = new LinkedHashMap<>();
        pendingAction.filledArguments().stream()
                .filter(PendingActionArgumentMerger::hasArgumentValue)
                .forEach(argument -> valueMap.put(argument.key(), argument));
        Set<String> allowedArgumentKeys = providedArgumentKeys == null
                ? Set.of()
                : providedArgumentKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
        currentResolution.arguments().stream()
                .filter(PendingActionArgumentMerger::hasArgumentValue)
                .filter(argument -> allowedArgumentKeys.contains(argument.key()))
                .forEach(argument -> valueMap.put(argument.key(), argument));
        List<AssistantToolArgument> arguments = selectedTool.inputSchema().stream()
                .map(ToolInputField::name)
                .map(valueMap::get)
                .filter(argument -> argument != null)
                .toList();
        List<ToolInputField> missingParameters = selectedTool.inputSchema().stream()
                .filter(ToolInputField::required)
                .filter(parameter -> !valueMap.containsKey(parameter.name()))
                .toList();
        return new AssistantToolArgumentResolution(
                selectedTool.name(),
                arguments,
                missingParameters,
                allowedArgumentKeys
        );
    }

    private static boolean hasArgumentValue(AssistantToolArgument argument) {
        return argument != null && StringUtils.hasText(argument.value());
    }

    private static AssistantVisibleTool selectTool(AssistantToolVisibilityPlan toolVisibilityPlan,
                                                   AssistantToolArgumentResolution currentResolution,
                                                   AssistantPendingAction pendingAction) {
        String toolName = currentResolution != null && StringUtils.hasText(currentResolution.toolName())
                ? currentResolution.toolName()
                : pendingAction.toolName();
        return toolVisibilityPlan.tools().stream()
                .filter(tool -> tool.name().equals(toolName))
                .findFirst()
                .orElse(null);
    }
}
