package com.laker.admin.module.assistant.tool.execution;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentResolution;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import com.laker.admin.module.assistant.tool.ToolInputField;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** 默认执行计划工厂：不调用模型，只把 Schema 完整性与读写治理规则转成状态。 */
@Component
public class DefaultToolExecutionPlanFactory implements ToolExecutionPlanFactory {
    @Override
    public ToolExecutionPlan create(AssistantRequestContext context,
                                ToolDefinition definition,
                                AssistantVisibleTool visibleTool,
                                AssistantToolArgumentResolution arguments) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(definition, "definition must not be null");
        Objects.requireNonNull(visibleTool, "visibleTool must not be null");
        AssistantToolArgumentResolution safeArguments = arguments == null
                ? AssistantToolArgumentResolution.empty(visibleTool.name())
                : arguments;
        if (!safeArguments.complete()) {
            return new ToolExecutionPlan(
                    context.runId(),
                    context.traceId(),
                    ToolExecutionStatus.INPUT_REQUIRED,
                    visibleTool.name(),
                    visibleTool.readOnly(),
                    safeArguments.arguments(),
                    safeArguments.missingRequiredParameters(),
                    safeArguments.missingRequiredParameters().stream()
                            .map(parameter -> "tool.parameter." + parameter.name())
                            .toList(),
                    "Tool Schema 缺少必填字段"
            );
        }
        ToolExecutionStatus nextStep = definition.readOnly()
                ? ToolExecutionStatus.READY
                : ToolExecutionStatus.APPROVAL_REQUIRED;
        return new ToolExecutionPlan(
                context.runId(),
                context.traceId(),
                nextStep,
                visibleTool.name(),
                definition.readOnly(),
                safeArguments.arguments(),
                List.of(),
                List.of(),
                definition.readOnly()
                        ? "只读 Tool Call 已通过 Schema 和策略检查"
                        : "写操作参数已齐全，只允许生成待确认草稿"
        );
    }
}
