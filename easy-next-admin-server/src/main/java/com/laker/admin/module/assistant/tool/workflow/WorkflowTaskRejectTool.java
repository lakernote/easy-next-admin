package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.assistant.tool.AssistantToolExecutionContext;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.ConfirmableTool;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import com.laker.admin.module.assistant.tool.ToolExecutionMode;
import com.laker.admin.module.assistant.tool.ToolInputField;
import com.laker.admin.module.assistant.tool.ToolOutputField;
import com.laker.admin.module.assistant.tool.ToolRiskLevel;
import com.laker.admin.module.assistant.tool.ToolSchemaFormat;
import com.laker.admin.module.assistant.tool.ToolSchemaType;
import com.laker.admin.module.assistant.tool.ToolSubjectScope;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 准备并在用户确认后驳回一个流程待办。 */
@Component
public class WorkflowTaskRejectTool implements ConfirmableTool {
    public static final String TOOL_NAME = "workflow.task.reject";
    private static final String DESCRIPTION = """
            用于为当前用户可处理的一个待办准备驳回草稿，确认后执行；不用于同意、退回或批量审批。仅传入能唯一定位的待办标识和用户明确给出的意见。
            """;

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            TOOL_NAME,
            "驳回流程待办",
            DESCRIPTION,
            EasyPermissions.Workflow.TASK_REJECT,
            ToolExecutionMode.REQUIRES_CONFIRMATION,
            ToolRiskLevel.HIGH,
            List.of(
                    new ToolInputField(
                            "taskIdentifier",
                            "待驳回任务标识",
                            ToolSchemaType.STRING,
                            ToolSchemaFormat.NONE,
                            "必须唯一定位一个待办；可使用任务号、流程实例号或业务单号。",
                            true,
                            List.of()
                    ),
                    new ToolInputField(
                            "comment",
                            "驳回意见",
                            ToolSchemaType.STRING,
                            ToolSchemaFormat.NONE,
                            "用户明确给出的驳回意见，最多 1000 字。",
                            false,
                            List.of()
                    )
            ),
            List.of(
                    new ToolOutputField("taskId", ToolSchemaType.INTEGER, true),
                    new ToolOutputField("instanceId", ToolSchemaType.INTEGER, true),
                    new ToolOutputField("businessId", ToolSchemaType.STRING, false),
                    new ToolOutputField("currentNodeKey", ToolSchemaType.STRING, false),
                    new ToolOutputField("status", ToolSchemaType.STRING, true)
            ),
            ToolSubjectScope.AUTHORIZED_TARGET
    );

    private final WorkflowTaskActionToolService actionService;

    public WorkflowTaskRejectTool(WorkflowTaskActionToolService actionService) {
        this.actionService = Objects.requireNonNull(actionService, "actionService must not be null");
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public AssistantToolResult prepare(AssistantToolExecutionContext context) {
        try {
            WorkflowTaskActionTarget target = resolveTarget(context);
            String comment = normalizeComment(context.plan().argumentMap().get("comment"));
            WorkflowTaskDecisionDraft draft = new WorkflowTaskDecisionDraft(
                    WorkflowTaskDecision.REJECT,
                    TOOL_NAME,
                    target.taskId(),
                    target.instanceId(),
                    target.businessId(),
                    target.title(),
                    target.nodeName(),
                    comment,
                    "确认后将驳回该流程待办"
            );
            return new AssistantToolResult(
                    TOOL_NAME,
                    AssistantToolRunStatus.EXECUTED,
                    "WORKFLOW_TASK_REJECTION_DRAFT",
                    draftSummary(target, comment),
                    draft
            );
        } catch (BusinessException ex) {
            return AssistantToolResult.failed(TOOL_NAME, ex.getMessage());
        }
    }

    @Override
    public List<AssistantToolArgument> confirmationArguments(AssistantToolExecutionContext context,
                                                            AssistantToolResult preparedResult) {
        if (!(preparedResult.data() instanceof WorkflowTaskDecisionDraft draft)) {
            return ConfirmableTool.super.confirmationArguments(context, preparedResult);
        }
        return context.plan().filledArguments().stream()
                .map(argument -> "taskIdentifier".equals(argument.key())
                        ? new AssistantToolArgument(
                        argument.key(), argument.label(), String.valueOf(draft.taskId()), String.valueOf(draft.taskId()))
                        : argument)
                .toList();
    }

    @Override
    public AssistantToolResult executeConfirmed(AssistantToolExecutionContext context) {
        try {
            WorkflowTaskActionTarget target = resolveTarget(context);
            WorkflowTaskActionResult result = actionService.reject(
                    target.taskId(),
                    context.plan().argumentMap().get("comment")
            );
            return new AssistantToolResult(
                    TOOL_NAME,
                    AssistantToolRunStatus.EXECUTED,
                    "WORKFLOW_TASK_REJECTED",
                    resultSummary(result),
                    result
            );
        } catch (BusinessException ex) {
            return AssistantToolResult.failed(TOOL_NAME, ex.getMessage());
        }
    }

    private WorkflowTaskActionTarget resolveTarget(AssistantToolExecutionContext context) {
        Map<String, String> arguments = context.plan().argumentMap();
        return actionService.resolveTarget(
                context.requestContext().user(),
                arguments.get("taskIdentifier")
        );
    }

    private static String draftSummary(WorkflowTaskActionTarget target, String comment) {
        StringBuilder summary = new StringBuilder("已生成驳回草稿：")
                .append(target.title())
                .append("，待办任务号：").append(target.taskId());
        if (target.businessId() != null && !target.businessId().isBlank()) {
            summary.append("，业务单号：").append(target.businessId());
        }
        if (comment != null) {
            summary.append("，驳回意见：").append(comment);
        }
        return summary.append("。请确认后我再执行驳回").toString();
    }

    private static String resultSummary(WorkflowTaskActionResult result) {
        StringBuilder summary = new StringBuilder("已驳回待办任务 ")
                .append(result.taskId());
        if (result.businessId() != null && !result.businessId().isBlank()) {
            summary.append("，业务单号：").append(result.businessId());
        }
        return summary.append("，流程状态：").append(result.status()).toString();
    }

    private static String normalizeComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            return null;
        }
        String normalized = comment.trim();
        if (normalized.length() > 1000) {
            throw new BusinessException("审批意见不能超过1000个字符");
        }
        return normalized;
    }
}
