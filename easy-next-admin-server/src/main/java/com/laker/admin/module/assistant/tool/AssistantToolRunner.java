package com.laker.admin.module.assistant.tool;

import com.laker.admin.infrastructure.idempotency.idempotent.IdempotentHandler;
import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStep;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStepStatus;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStepType;
import com.laker.admin.module.assistant.trace.AssistantRunTrace;
import com.laker.admin.module.assistant.trace.AssistantRunTraceRecorder;
import com.laker.admin.module.assistant.trace.AssistantToolCallRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 单次 Tool 执行边界。
 *
 * <p>多步推理由 {@code AgentLoop} 通过 observation 继续完成；本类只负责
 * 权限/风险策略、确认动作幂等、业务执行、返回合同验证和步骤状态。</p>
 */
@Component
@Slf4j
public class AssistantToolRunner {
    private static final long ACTION_IDEMPOTENCY_TTL_SECONDS = 30 * 60L;

    private final AssistantToolRegistry registry;
    private final IdempotentHandler idempotentHandler;
    private final ToolResultValidator resultValidator;
    private final ToolExecutionPolicy executionPolicy;
    private final AssistantRunTraceRecorder traceRecorder;

    public AssistantToolRunner(AssistantToolRegistry registry,
                               IdempotentHandler idempotentHandler,
                               ToolResultValidator resultValidator,
                               ToolExecutionPolicy executionPolicy,
                               AssistantRunTraceRecorder traceRecorder) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.idempotentHandler = Objects.requireNonNull(
                idempotentHandler,
                "idempotentHandler must not be null"
        );
        this.resultValidator = Objects.requireNonNull(resultValidator, "resultValidator must not be null");
        this.executionPolicy = Objects.requireNonNull(executionPolicy, "executionPolicy must not be null");
        this.traceRecorder = Objects.requireNonNull(traceRecorder, "traceRecorder must not be null");
    }

    public AssistantToolRunResult executePlan(AssistantRequestContext context, ToolExecutionPlan plan) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(plan, "plan must not be null");
        if (!isExecutableDecision(plan.status()) || !StringUtils.hasText(plan.toolName())) {
            AssistantToolResult skipped = AssistantToolResult.skipped("当前计划不需要执行 Tool");
            return outcome(plan, skipped);
        }
        long startedNanos = System.nanoTime();
        AssistantToolRunResult toolRunResult = null;
        try {
            toolRunResult = executeRegisteredToolPlan(context, plan);
            return toolRunResult;
        } finally {
            traceRecorder.recordToolCall(new AssistantToolCallRecord(
                    plan.toolCallId(),
                    plan.toolName(),
                    executionPhase(plan.status()),
                    toolRunResult == null || toolRunResult.result() == null
                            ? AssistantToolRunStatus.FAILED.name()
                            : toolRunResult.result().status().name(),
                    AssistantRunTrace.toMillis(System.nanoTime() - startedNanos)
            ));
        }
    }

    private AssistantToolRunResult executeRegisteredToolPlan(AssistantRequestContext context,
                                                             ToolExecutionPlan plan) {
        AssistantTool tool = registry.find(plan.toolName()).orElse(null);
        if (tool == null) {
            return outcome(plan, AssistantToolResult.failed(plan.toolName(), "Tool 未注册"));
        }

        ToolExecutionPolicy.PolicyDecision policy = executionPolicy.evaluate(context, plan, tool);
        if (!policy.allowed()) {
            return outcome(plan, AssistantToolResult.failed(tool.definition().name(), policy.reason()));
        }

        if (plan.status() == ToolExecutionStatus.APPROVED) {
            if (!StringUtils.hasText(plan.idempotencyKey())) {
                return outcome(plan, AssistantToolResult.failed(
                        plan.toolName(),
                        "确认动作缺少幂等标识，已拒绝执行"
                ));
            }
            if (!idempotentHandler.checkAndSet(plan.idempotencyKey(), ACTION_IDEMPOTENCY_TTL_SECONDS)) {
                return outcome(plan, AssistantToolResult.indeterminate(
                        plan.toolName(),
                        "该确认请求曾进入执行边界，但当前无法判断上次是否成功。请先查询业务状态或人工核对，不要重复提交"
                ));
            }
        }

        AssistantToolResult result = invokeTool(tool, context, plan);
        if (plan.status() == ToolExecutionStatus.APPROVAL_REQUIRED
                && result.status() == AssistantToolRunStatus.EXECUTED
                && tool instanceof ConfirmableTool confirmableTool) {
            try {
                AssistantToolExecutionContext executionContext = new AssistantToolExecutionContext(context, plan);
                List<AssistantToolArgument> confirmationArguments = Objects.requireNonNull(
                        confirmableTool.confirmationArguments(executionContext, result),
                        "confirmationArguments must not be null"
                );
                validateConfirmationArguments(tool.definition(), confirmationArguments);
                plan = plan.withFilledArguments(confirmationArguments);
            } catch (RuntimeException ex) {
                log.warn("Assistant confirmation snapshot failed, runId={}, toolName={}, failureType={}",
                        context.runId(), tool.definition().name(), ex.getClass().getSimpleName());
                result = AssistantToolResult.failed(tool.definition().name(), "动作草稿未能生成安全确认快照");
            }
        }
        // 一旦进入确认执行边界，失败可能发生在业务提交之后或输出校验阶段，不能贸然删除幂等键。
        // 这采用企业写操作的 fail-closed 语义：不确定是否写入成功时宁可阻止重复提交。
        AssistantToolRunResult toolRunResult = outcome(plan, result);
        if (plan.status() != ToolExecutionStatus.APPROVAL_REQUIRED
                || result.status() != AssistantToolRunStatus.FAILED) {
            return toolRunResult;
        }
        ToolExecutionPlan failedPlan = toolRunResult.plan()
                .withStatus(ToolExecutionStatus.BLOCKED, "动作草稿生成失败：" + result.summary());
        String failureSummary = result.summary();
        failedPlan = failedPlan.withExecutedSteps(failedPlan.steps().stream()
                .map(step -> step.withStatus(ToolExecutionStepStatus.FAILED, failureSummary))
                .toList());
        return new AssistantToolRunResult(result, failedPlan);
    }

    private static void validateConfirmationArguments(ToolDefinition definition,
                                                      List<AssistantToolArgument> arguments) {
        Set<String> allowedKeys = definition.inputSchema().stream()
                .map(ToolInputField::name)
                .collect(Collectors.toUnmodifiableSet());
        Set<String> actualKeys = arguments.stream()
                .map(AssistantToolArgument::key)
                .collect(Collectors.toSet());
        if (actualKeys.size() != arguments.size() || !allowedKeys.containsAll(actualKeys)) {
            throw new IllegalArgumentException("Confirmation snapshot contains duplicate or unknown arguments");
        }
        boolean missingRequired = definition.inputSchema().stream()
                .filter(ToolInputField::required)
                .map(ToolInputField::name)
                .anyMatch(key -> !actualKeys.contains(key));
        if (missingRequired) {
            throw new IllegalArgumentException("Confirmation snapshot is missing required arguments");
        }
    }

    private AssistantToolResult invokeTool(AssistantTool tool,
                                           AssistantRequestContext context,
                                           ToolExecutionPlan plan) {
        AssistantToolResult result;
        try {
            result = tool.execute(new AssistantToolExecutionContext(context, plan));
        } catch (RuntimeException ex) {
            log.warn("Assistant tool execution failed, runId={}, toolName={}, failureType={}",
                    context.runId(), tool.definition().name(), ex.getClass().getSimpleName());
            return AssistantToolResult.failed(tool.definition().name(), "Tool 执行失败，请稍后重试");
        }
        ToolResultValidator.ValidationResult validation = resultValidator.validate(
                tool.definition(),
                result,
                executionPhase(plan.status())
        );
        return validation.valid() ? result : AssistantToolResult.failed(tool.definition().name(), validation.reason());
    }

    private static AssistantToolRunResult outcome(ToolExecutionPlan plan, AssistantToolResult result) {
        List<ToolExecutionStep> steps = plan.steps().stream()
                .map(step -> completeStep(step, result))
                .toList();
        ToolExecutionPlan executedPlan = plan.withExecutedSteps(steps);
        return new AssistantToolRunResult(result, executedPlan);
    }

    private static ToolExecutionStep completeStep(ToolExecutionStep step, AssistantToolResult result) {
        if (isExecutionStep(step.type())) {
            return step.withStatus(statusFrom(result), result.summary());
        }
        return switch (step.type()) {
            case WAIT_FOR_CONFIRMATION -> step.withStatus(
                    ToolExecutionStepStatus.WAITING,
                    "等待用户确认或取消当前动作草稿"
            );
            case ASK_USER -> step.withStatus(ToolExecutionStepStatus.WAITING, "等待用户补充缺失字段");
            case OBSERVE_RESULT -> step.withStatus(statusFrom(result), result.summary());
            case FINAL_RESPONSE, STOP -> step.withStatus(ToolExecutionStepStatus.SUCCEEDED, result.summary());
            default -> step.withStatus(ToolExecutionStepStatus.SKIPPED, "当前步骤不执行 Tool");
        };
    }

    private static boolean isExecutableDecision(ToolExecutionStatus decision) {
        return decision == ToolExecutionStatus.READY
                || decision == ToolExecutionStatus.APPROVAL_REQUIRED
                || decision == ToolExecutionStatus.APPROVED;
    }

    private static boolean isExecutionStep(ToolExecutionStepType type) {
        return type == ToolExecutionStepType.CALL_TOOL
                || type == ToolExecutionStepType.CREATE_ACTION_DRAFT
                || type == ToolExecutionStepType.CONFIRM_ACTION;
    }

    private static ToolExecutionPhase executionPhase(ToolExecutionStatus decision) {
        return switch (decision) {
            case APPROVAL_REQUIRED -> ToolExecutionPhase.PREPARE;
            case APPROVED -> ToolExecutionPhase.EXECUTE;
            default -> ToolExecutionPhase.READ;
        };
    }

    private static ToolExecutionStepStatus statusFrom(AssistantToolResult result) {
        if (result == null) {
            return ToolExecutionStepStatus.SKIPPED;
        }
        return switch (result.status()) {
            case EXECUTED, PARTIAL_SUCCESS -> ToolExecutionStepStatus.SUCCEEDED;
            case SKIPPED -> ToolExecutionStepStatus.SKIPPED;
            case INDETERMINATE -> ToolExecutionStepStatus.INDETERMINATE;
            case FAILED -> ToolExecutionStepStatus.FAILED;
        };
    }
}
