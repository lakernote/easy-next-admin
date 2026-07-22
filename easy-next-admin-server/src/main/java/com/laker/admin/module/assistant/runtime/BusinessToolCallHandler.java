package com.laker.admin.module.assistant.runtime;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.runtime.model.ToolCall;
import com.laker.admin.module.assistant.state.AssistantConversationState;
import com.laker.admin.module.assistant.state.AssistantPendingAction;
import com.laker.admin.module.assistant.state.PendingActionArgumentMerger;
import com.laker.admin.module.assistant.tool.AssistantTool;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentResolution;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentResolver;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentValidator;
import com.laker.admin.module.assistant.tool.AssistantToolRegistry;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunner;
import com.laker.admin.module.assistant.tool.AssistantToolSelection;
import com.laker.admin.module.assistant.tool.AssistantToolVisibilityPlan;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import com.laker.admin.module.assistant.tool.ToolInputField;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlanFactory;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 把一个已经通过 Agent 协议检查的业务 Tool Call 转成执行计划并运行。
 *
 * <p>这里只处理业务 Tool；确认和取消已保存草稿由 {@link PendingActionHandler} 处理。</p>
 */
@Component
final class BusinessToolCallHandler {
    private final AssistantToolRegistry toolRegistry;
    private final AssistantToolArgumentResolver argumentResolver;
    private final AssistantToolArgumentValidator argumentValidator;
    private final ToolExecutionPlanFactory planFactory;
    private final AssistantToolRunner toolRunner;
    private final PendingActionArgumentMerger pendingActionArgumentMerger;

    BusinessToolCallHandler(AssistantToolRegistry toolRegistry,
                            AssistantToolArgumentResolver argumentResolver,
                            AssistantToolArgumentValidator argumentValidator,
                            ToolExecutionPlanFactory planFactory,
                            AssistantToolRunner toolRunner,
                            PendingActionArgumentMerger pendingActionArgumentMerger) {
        this.toolRegistry = toolRegistry;
        this.argumentResolver = argumentResolver;
        this.argumentValidator = argumentValidator;
        this.planFactory = planFactory;
        this.toolRunner = toolRunner;
        this.pendingActionArgumentMerger = pendingActionArgumentMerger;
    }

    ToolCallOutcome execute(AssistantRequestContext context,
                            AssistantConversationState conversationState,
                            AgentLoopState runState,
                            ToolCall call,
                            AgentLoopListener listener) {
        AssistantVisibleTool visibleTool = runState.visibleTools().stream()
                .filter(tool -> tool.name().equals(call.name()))
                .findFirst()
                .orElse(null);
        AssistantTool tool = toolRegistry.find(call.name()).orElse(null);
        if (visibleTool == null || tool == null) {
            ToolExecutionPlan plan = AgentResultFactory.blockedPlan(
                    context,
                    call.name(),
                    "模型请求了本轮不可见工具",
                    "tool.not_visible"
            ).withToolCallId(call.id());
            return new ToolCallOutcome(plan, AssistantToolResult.failed(call.name(), "当前工具不可用或没有权限"), null);
        }

        ToolDefinition definition = tool.definition();
        AssistantToolSelection selection = AssistantToolSelection.selected(
                visibleTool,
                runState.visibleTools(),
                "模型通过原生 Tool Call 选择真实企业工具"
        );
        AssistantToolArgumentResolution resolution = argumentResolver.resolveToolCallArguments(
                context,
                visibleTool,
                call.arguments(),
                conversationState,
                runState.toolResults()
        );
        AssistantPendingAction pendingAction = pendingActionFrom(conversationState);
        if (pendingAction != null && call.name().equals(pendingAction.toolName())) {
            resolution = pendingActionArgumentMerger.merge(
                    new AssistantToolVisibilityPlan(
                            runState.visibleTools().stream().map(AssistantVisibleTool::name).toList(),
                            runState.visibleTools(),
                            List.of(),
                            "原生 Tool Calling 续接待处理动作"
                    ),
                    resolution,
                    pendingAction,
                    call.arguments().keySet()
            );
        }
        AgentLoopEvents.argumentsResolved(context, listener, resolution);
        AssistantToolArgumentResolution validated = argumentValidator.validate(
                context,
                selection,
                resolution,
                runState.toolResults()
        );
        AgentLoopEvents.argumentsValidated(context, listener, validated);
        ToolExecutionPlan plan = planFactory.create(context, definition, visibleTool, validated)
                .withToolCallId(call.id());
        if (plan.status() == ToolExecutionStatus.INPUT_REQUIRED) {
            String missing = plan.missingParameters().stream()
                    .map(ToolInputField::displayName)
                    .collect(Collectors.joining("、"));
            return new ToolCallOutcome(
                    plan,
                    AssistantToolResult.failed(call.name(), "工具参数未通过校验，还需要补充：" + missing),
                    definition
            );
        }
        AssistantToolRunResult runResult = toolRunner.executePlan(context, plan);
        return new ToolCallOutcome(
                withActionIdentity(context, runResult.plan(), pendingAction),
                runResult.result(),
                definition
        );
    }

    private static AssistantPendingAction pendingActionFrom(AssistantConversationState state) {
        return state == null ? null : state.pendingAction();
    }

    private static ToolExecutionPlan withActionIdentity(AssistantRequestContext context,
                                                        ToolExecutionPlan plan,
                                                        AssistantPendingAction pendingAction) {
        if (plan == null || plan.status() != ToolExecutionStatus.APPROVAL_REQUIRED) {
            return plan;
        }
        if (pendingAction != null
                && plan.toolName().equals(pendingAction.toolName())
                && StringUtils.hasText(pendingAction.actionId())
                && StringUtils.hasText(pendingAction.idempotencyKey())) {
            return plan.withActionIdentity(pendingAction.actionId(), pendingAction.idempotencyKey());
        }
        return plan.withActionIdentity(
                AssistantPendingAction.actionIdFor(context, plan),
                AssistantPendingAction.idempotencyKeyFor(context, plan)
        );
    }
}
