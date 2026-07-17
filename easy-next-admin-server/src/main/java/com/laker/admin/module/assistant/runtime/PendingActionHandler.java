package com.laker.admin.module.assistant.runtime;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.runtime.model.ToolCall;
import com.laker.admin.module.assistant.state.AssistantConversationState;
import com.laker.admin.module.assistant.state.AssistantPendingAction;
import com.laker.admin.module.assistant.state.AssistantPendingActionStatus;
import com.laker.admin.module.assistant.tool.AssistantTool;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentResolution;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentValidator;
import com.laker.admin.module.assistant.tool.AssistantToolRegistry;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.AssistantToolRunner;
import com.laker.admin.module.assistant.tool.AssistantToolSelection;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import com.laker.admin.module.assistant.tool.ToolInputField;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlanFactory;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 执行服务端已保存动作的确认或取消，不处理普通业务 Tool。 */
@Component
public class PendingActionHandler {
    private final AssistantToolRegistry toolRegistry;
    private final AssistantToolArgumentValidator argumentValidator;
    private final ToolExecutionPlanFactory planFactory;
    private final AssistantToolRunner toolRunner;

    public PendingActionHandler(AssistantToolRegistry toolRegistry,
                                AssistantToolArgumentValidator argumentValidator,
                                ToolExecutionPlanFactory planFactory,
                                AssistantToolRunner toolRunner) {
        this.toolRegistry = toolRegistry;
        this.argumentValidator = argumentValidator;
        this.planFactory = planFactory;
        this.toolRunner = toolRunner;
    }

    boolean supports(String toolName) {
        return ToolSetBuilder.isControlTool(toolName);
    }

    ToolCallOutcome execute(AssistantRequestContext context,
                            AssistantConversationState conversationState,
                            List<AssistantVisibleTool> visibleTools,
                            AgentLoopListener listener,
                            ToolCall call) {
        if (ToolSetBuilder.isConfirmTool(call.name())) {
            return confirm(context, conversationState, visibleTools, listener, call.id());
        }
        return cancel(context, conversationState, call.id());
    }

    private ToolCallOutcome confirm(AssistantRequestContext context,
                                    AssistantConversationState conversationState,
                                    List<AssistantVisibleTool> visibleTools,
                                    AgentLoopListener listener,
                                    String toolCallId) {
        AssistantPendingAction pendingAction = pendingActionFrom(conversationState);
        if (pendingAction == null
                || pendingAction.status() != AssistantPendingActionStatus.WAITING_FOR_CONFIRMATION
                || !pendingAction.missingParameters().isEmpty()) {
            ToolExecutionPlan plan = AgentResultFactory.blockedPlan(
                    context,
                    ToolSetBuilder.CONFIRM_PENDING_ACTION,
                    "当前没有可以确认执行的完整动作草稿",
                    "pending_action.not_confirmable"
            ).withToolCallId(toolCallId);
            return new ToolCallOutcome(
                    plan,
                    AssistantToolResult.failed(ToolSetBuilder.CONFIRM_PENDING_ACTION, plan.reason()),
                    null
            );
        }
        AssistantVisibleTool visibleTool = visibleTools.stream()
                .filter(tool -> tool.name().equals(pendingAction.toolName()))
                .findFirst()
                .orElse(null);
        AssistantTool tool = toolRegistry.find(pendingAction.toolName()).orElse(null);
        if (visibleTool == null || tool == null || tool.definition().readOnly()) {
            ToolExecutionPlan plan = AgentResultFactory.blockedPlan(
                    context,
                    pendingAction.toolName(),
                    "待确认动作当前不可用或没有权限",
                    "pending_action.tool_not_visible"
            ).withToolCallId(toolCallId);
            return new ToolCallOutcome(
                    plan,
                    AssistantToolResult.failed(pendingAction.toolName(), plan.reason()),
                    tool == null ? null : tool.definition()
            );
        }

        ToolDefinition definition = tool.definition();
        AssistantToolSelection selection = AssistantToolSelection.selected(
                visibleTool,
                visibleTools,
                "用户通过受控确认 Tool 确认已保存动作"
        );
        AgentLoopEvents.toolSelected(context, listener, selection);
        Set<String> filledKeys = pendingAction.filledArguments().stream()
                .map(AssistantToolArgument::key)
                .collect(Collectors.toSet());
        AssistantToolArgumentResolution resolution = new AssistantToolArgumentResolution(
                pendingAction.toolName(),
                pendingAction.filledArguments(),
                visibleTool.inputSchema().stream()
                        .filter(ToolInputField::required)
                        .filter(field -> !filledKeys.contains(field.name()))
                        .toList(),
                Set.of()
        );
        AgentLoopEvents.argumentsResolved(context, listener, resolution);
        AssistantToolArgumentResolution validated = argumentValidator.validate(context, selection, resolution);
        AgentLoopEvents.argumentsValidated(context, listener, validated);
        ToolExecutionPlan draft = planFactory.create(context, definition, visibleTool, validated)
                .withToolCallId(toolCallId);
        if (draft.status() != ToolExecutionStatus.APPROVAL_REQUIRED) {
            return new ToolCallOutcome(
                    draft,
                    AssistantToolResult.failed(pendingAction.toolName(), "已保存动作未通过确认前校验"),
                    definition
            );
        }
        ToolExecutionPlan confirmed = draft
                .withStatus(ToolExecutionStatus.APPROVED, "用户已明确确认，执行已保存动作快照")
                .withActionIdentity(pendingAction.actionId(), pendingAction.idempotencyKey());
        AssistantToolRunResult runResult = toolRunner.executePlan(context, confirmed);
        return new ToolCallOutcome(runResult.plan(), runResult.result(), definition);
    }

    private ToolCallOutcome cancel(AssistantRequestContext context,
                                   AssistantConversationState conversationState,
                                   String toolCallId) {
        AssistantPendingAction pendingAction = pendingActionFrom(conversationState);
        if (pendingAction == null) {
            ToolExecutionPlan plan = AgentResultFactory.blockedPlan(
                    context,
                    ToolSetBuilder.CANCEL_PENDING_ACTION,
                    "当前没有待取消动作",
                    "pending_action.not_found"
            ).withToolCallId(toolCallId);
            return new ToolCallOutcome(
                    plan,
                    AssistantToolResult.failed(ToolSetBuilder.CANCEL_PENDING_ACTION, plan.reason()),
                    null
            );
        }
        ToolDefinition definition = toolRegistry.find(pendingAction.toolName())
                .map(AssistantTool::definition)
                .orElse(null);
        ToolExecutionPlan plan = new ToolExecutionPlan(
                context.runId(),
                context.traceId(),
                ToolExecutionStatus.CANCELED,
                pendingAction.toolName(),
                false,
                pendingAction.filledArguments(),
                pendingAction.missingParameters(),
                List.of(),
                "用户已明确取消待处理动作",
                pendingAction.actionId(),
                pendingAction.idempotencyKey(),
                List.of(),
                toolCallId
        );
        AssistantToolResult result = new AssistantToolResult(
                pendingAction.toolName(),
                AssistantToolRunStatus.SKIPPED,
                "CANCELED",
                "已取消之前的待处理动作",
                Map.of("actionId", StringUtils.hasText(pendingAction.actionId()) ? pendingAction.actionId() : "")
        );
        return new ToolCallOutcome(plan, result, definition);
    }

    private static AssistantPendingAction pendingActionFrom(AssistantConversationState state) {
        return state == null ? null : state.pendingAction();
    }
}
