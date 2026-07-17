package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/** 默认 Tool 提供策略：只做注册表枚举和确定性权限过滤，不做语义路由。 */
@Component
public class DefaultToolVisibilityPolicy implements ToolVisibilityPolicy {
    private final AssistantToolRegistry registry;

    public DefaultToolVisibilityPolicy(AssistantToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public AssistantToolVisibilityPlan selectVisibleTools(AssistantRequestContext context) {
        Objects.requireNonNull(context, "context must not be null");
        List<AssistantTool> registeredTools = registry.tools();
        List<String> registeredToolNames = registeredTools.stream()
                .map(tool -> tool.definition().name())
                .toList();
        List<AssistantVisibleTool> visibleTools = registeredTools.stream()
                .map(AssistantTool::definition)
                .filter(definition -> hasPermission(context, definition.requiredPermission()))
                .map(AssistantVisibleTool::from)
                .toList();
        if (visibleTools.isEmpty()) {
            return new AssistantToolVisibilityPlan(
                    registeredToolNames,
                    List.of(),
                    List.of("tool.permission"),
                    "当前用户没有可用的企业 Tool"
            );
        }
        return new AssistantToolVisibilityPlan(
                registeredToolNames,
                visibleTools,
                List.of(),
                "已按当前用户权限提供可执行企业 Tool"
        );
    }

    private static boolean hasPermission(AssistantRequestContext context, String permission) {
        if (context.user().superAdmin() || !StringUtils.hasText(permission)) {
            return true;
        }
        return context.user().permissions().contains(permission);
    }
}
