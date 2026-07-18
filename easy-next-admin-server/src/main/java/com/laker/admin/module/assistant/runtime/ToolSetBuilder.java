package com.laker.admin.module.assistant.runtime;

import com.laker.admin.module.assistant.state.AssistantConversationState;
import com.laker.admin.module.assistant.state.AssistantPendingAction;
import com.laker.admin.module.assistant.state.AssistantPendingActionStatus;
import com.laker.admin.module.assistant.tool.AssistantTool;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolRegistry;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import com.laker.admin.module.assistant.tool.ToolExecutionMode;
import com.laker.admin.module.assistant.tool.ToolInputField;
import com.laker.admin.module.assistant.tool.ToolRiskLevel;
import com.laker.admin.module.assistant.tool.ToolSubjectScope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 构建每一步真正交给模型的 Tool 集合。
 *
 * <p>权限过滤在进入这里之前已经完成；这里仅负责把业务 Tool 转成模型可见合同，
 * 并在存在待处理动作时增加补字段、确认和取消所需的控制 Tool。</p>
 */
@Component
public class ToolSetBuilder {
    public static final String CONFIRM_PENDING_ACTION = "system.pending_action.confirm";
    public static final String CANCEL_PENDING_ACTION = "system.pending_action.cancel";

    private final AssistantToolRegistry toolRegistry;

    public ToolSetBuilder(AssistantToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    List<ToolDefinition> buildBusinessTools(List<AssistantVisibleTool> visibleTools) {
        return visibleTools.stream()
                .map(AssistantVisibleTool::name)
                .map(toolRegistry::find)
                .flatMap(java.util.Optional::stream)
                .map(AssistantTool::definition)
                .map(ToolSetBuilder::toModelFacingDefinition)
                .toList();
    }

    List<ToolDefinition> buildInitialTools(List<ToolDefinition> businessTools,
                                           AssistantConversationState conversationState) {
        AssistantPendingAction pendingAction = pendingActionFrom(conversationState);
        if (pendingAction == null) {
            return businessTools;
        }
        List<ToolDefinition> tools = new ArrayList<>();
        ToolDefinition pendingDefinition = null;
        for (ToolDefinition definition : businessTools) {
            if (definition.name().equals(pendingAction.toolName())) {
                pendingDefinition = definition;
                tools.add(withPendingActionContext(definition, pendingAction));
            } else {
                tools.add(definition);
            }
        }
        if (pendingDefinition != null
                && pendingAction.status() == AssistantPendingActionStatus.WAITING_FOR_CONFIRMATION
                && pendingAction.missingParameters().isEmpty()) {
            tools.add(confirmPendingActionDefinition(pendingDefinition));
        }
        tools.add(cancelPendingActionDefinition());
        return List.copyOf(tools);
    }

    static boolean isConfirmTool(String toolName) {
        return CONFIRM_PENDING_ACTION.equals(toolName);
    }

    static boolean isCancelTool(String toolName) {
        return CANCEL_PENDING_ACTION.equals(toolName);
    }

    static boolean isControlTool(String toolName) {
        return isConfirmTool(toolName) || isCancelTool(toolName);
    }

    private static ToolDefinition toModelFacingDefinition(ToolDefinition definition) {
        if (definition.readOnly()) {
            return definition;
        }
        List<ToolInputField> partialSchema = definition.inputSchema().stream()
                .map(field -> new ToolInputField(
                        field.name(),
                        field.displayName(),
                        field.type(),
                        field.format(),
                        field.description(),
                        false,
                        field.enumValues(),
                        field.enumLabels()
                ))
                .toList();
        return new ToolDefinition(
                definition.name(),
                definition.displayName(),
                definition.description() + " 生成草稿时缺失字段省略。",
                definition.requiredPermission(),
                definition.executionMode(),
                definition.riskLevel(),
                partialSchema,
                definition.outputSchema(),
                definition.subjectScope()
        );
    }

    private static ToolDefinition withPendingActionContext(ToolDefinition definition,
                                                           AssistantPendingAction pendingAction) {
        Set<String> filledKeys = pendingAction.filledArguments().stream()
                .map(AssistantToolArgument::key)
                .collect(Collectors.toUnmodifiableSet());
        List<ToolInputField> inputSchema = definition.inputSchema().stream()
                .map(field -> new ToolInputField(
                        field.name(),
                        field.displayName(),
                        field.type(),
                        field.format(),
                        filledKeys.contains(field.name())
                                ? field.description() + " 当前草稿已有值；仅在用户本轮明确修改时传入。"
                                : field.description(),
                        field.required() && !filledKeys.contains(field.name()),
                        field.enumValues(),
                        field.enumLabels()
                ))
                .toList();
        return new ToolDefinition(
                definition.name(),
                definition.displayName(),
                definition.description() + " 当前有本 Tool 草稿：补充或修改字段时调用本 Tool；明确确认或取消时使用控制 Tool。",
                definition.requiredPermission(),
                definition.executionMode(),
                definition.riskLevel(),
                inputSchema,
                definition.outputSchema(),
                definition.subjectScope()
        );
    }

    private static ToolDefinition confirmPendingActionDefinition(ToolDefinition pendingDefinition) {
        return new ToolDefinition(
                CONFIRM_PENDING_ACTION,
                "确认待处理动作",
                "仅当用户本轮明确确认提交或执行当前完整草稿时使用。执行服务端已保存的参数快照，不能修改业务参数。",
                "",
                ToolExecutionMode.REQUIRES_CONFIRMATION,
                pendingDefinition.riskLevel(),
                List.of(),
                pendingDefinition.outputSchema(),
                pendingDefinition.subjectScope()
        );
    }

    private static ToolDefinition cancelPendingActionDefinition() {
        return new ToolDefinition(
                CANCEL_PENDING_ACTION,
                "取消待处理动作",
                "仅当用户明确取消或放弃当前草稿时使用；不会执行业务写入。",
                "",
                ToolExecutionMode.READ_ONLY,
                ToolRiskLevel.LOW,
                List.of(),
                List.of(),
                ToolSubjectScope.NONE
        );
    }

    private static AssistantPendingAction pendingActionFrom(AssistantConversationState state) {
        return state == null ? null : state.pendingAction();
    }
}
