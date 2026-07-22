package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolExecutionContext;
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
import com.laker.admin.module.workflow.leave.dto.LeaveApplyRequest;
import com.laker.admin.module.workflow.leave.dto.LeaveRequestView;
import com.laker.admin.module.workflow.leave.service.ILeaveRequestService;
import com.laker.admin.module.workflow.leave.support.LeavePeriodCalculator;
import com.laker.admin.module.workflow.leave.support.LeaveType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 请假申请准备工具。
 *
 * <p>第一次执行只生成待确认草稿；用户确认后才调用真实请假服务。</p>
 */
@Component
public class LeavePrepareTool implements ConfirmableTool {
    public static final String TOOL_NAME = "workflow.leave.prepare";
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 30);
    private static final LocalTime AFTERNOON_END = LocalTime.of(18, 0);
    private static final String DESCRIPTION = """
            用于为当前用户准备一笔新请假申请；不用于查询请假单、流程进度或休假制度。填写用户明确提供的假别、起止时间和原因；当用户明确要求参考本轮刚查询的请假单时，假别和原因也可复用该成功 Tool Result，不能从其他内容猜测，缺失字段省略。
            """;

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            TOOL_NAME,
            "准备请假申请",
            DESCRIPTION,
            EasyPermissions.Workflow.INSTANCE_START,
            ToolExecutionMode.REQUIRES_CONFIRMATION,
            ToolRiskLevel.MEDIUM,
            List.of(
                    input("leaveType", "请假类型", ToolSchemaType.STRING, ToolSchemaFormat.NONE,
                            "使用稳定代码；映射为：" + LeaveType.agentSchemaDescription()
                                    + "。只在用户原文明确出现对应中文假别，或用户明确要求复用本轮刚查询请假单时填写；否则省略。",
                            true,
                            LeaveType.agentCodes(),
                            LeaveType.agentLabels()),
                    input("startTime", "开始时间", ToolSchemaType.STRING, ToolSchemaFormat.DATE_TIME,
                            "ISO-8601 本地时间；上午/全天 09:00，下午 13:30。", true),
                    input("endTime", "结束时间", ToolSchemaType.STRING, ToolSchemaFormat.DATE_TIME,
                            "ISO-8601 本地时间；上午 12:00，下午/全天 18:00。", true),
                    input("reason", "请假原因", ToolSchemaType.STRING, ToolSchemaFormat.NONE,
                            "保留用户单独陈述的原因原话，或用户明确要求复用本轮刚查询请假单的原因；未说明则省略，不能从假别、时间或整句请假目标推断。", true)
            ),
            List.of(
                    new ToolOutputField("requestNo", ToolSchemaType.STRING, true),
                    new ToolOutputField("workflowInstanceId", ToolSchemaType.INTEGER, true)
            ),
            ToolSubjectScope.CURRENT_USER
    );

    private final LeavePeriodCalculator leavePeriodCalculator;
    private final ILeaveRequestService leaveRequestService;

    public LeavePrepareTool(LeavePeriodCalculator leavePeriodCalculator) {
        this(leavePeriodCalculator, (ILeaveRequestService) null);
    }

    @Autowired
    public LeavePrepareTool(LeavePeriodCalculator leavePeriodCalculator,
                            ObjectProvider<ILeaveRequestService> leaveRequestServiceProvider) {
        this(leavePeriodCalculator, leaveRequestServiceProvider.getIfAvailable());
    }

    private LeavePrepareTool(LeavePeriodCalculator leavePeriodCalculator,
                             ILeaveRequestService leaveRequestService) {
        this.leavePeriodCalculator = Objects.requireNonNull(leavePeriodCalculator, "leavePeriodCalculator must not be null");
        this.leaveRequestService = leaveRequestService;
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public AssistantToolResult prepare(AssistantToolExecutionContext context) {
        Map<String, String> arguments = context.plan().argumentMap();
        try {
            LeaveType leaveType = requireLeaveType(arguments);
            LocalDateTime startTime = parseDateTime(requireArgument(arguments, "startTime"));
            LocalDateTime endTime = parseDateTime(requireArgument(arguments, "endTime"));
            LeavePeriod normalizedPeriod = normalizeNamedPeriod(
                    context.requestContext().normalizedMessage(),
                    startTime,
                    endTime
            );
            String reason = requireArgument(arguments, "reason");
            BigDecimal days = leavePeriodCalculator.calculate(normalizedPeriod.startTime(), normalizedPeriod.endTime());
            LeaveRequestActionDraft draft = new LeaveRequestActionDraft(
                    "SUBMIT_LEAVE_REQUEST",
                    TOOL_NAME,
                    leaveType.code(),
                    leaveType.label(),
                    normalizedPeriod.startTime(),
                    normalizedPeriod.endTime(),
                    days,
                    reason,
                    "确认后将提交请假申请并启动请假审批流程"
            );
            return new AssistantToolResult(
                    TOOL_NAME,
                    AssistantToolRunStatus.EXECUTED,
                    "LEAVE_ACTION_DRAFT",
                    "已生成请假申请草稿，等待用户确认后提交",
                    draft
            );
        } catch (IllegalArgumentException ex) {
            return AssistantToolResult.failed(TOOL_NAME, ex.getMessage());
        }
    }

    @Override
    public AssistantToolResult executeConfirmed(AssistantToolExecutionContext context) {
        Map<String, String> arguments = context.plan().argumentMap();
        try {
            LeaveType leaveType = requireLeaveType(arguments);
            LocalDateTime startTime = parseDateTime(requireArgument(arguments, "startTime"));
            LocalDateTime endTime = parseDateTime(requireArgument(arguments, "endTime"));
            String reason = requireArgument(arguments, "reason");
            BigDecimal days = leavePeriodCalculator.calculate(startTime, endTime);
            return submitLeaveRequest(leaveType, startTime, endTime, days, reason);
        } catch (IllegalArgumentException ex) {
            return AssistantToolResult.failed(TOOL_NAME, ex.getMessage());
        }
    }

    private AssistantToolResult submitLeaveRequest(LeaveType leaveType,
                                                   LocalDateTime startTime,
                                                   LocalDateTime endTime,
                                                   BigDecimal days,
                                                   String reason) {
        if (leaveRequestService == null) {
            return AssistantToolResult.failed(TOOL_NAME, "请假提交服务未配置，无法提交请假申请");
        }
        LeaveApplyRequest request = new LeaveApplyRequest();
        request.setLeaveType(leaveType);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setDays(days);
        request.setReason(reason);
        LeaveRequestView view = leaveRequestService.apply(request);
        return new AssistantToolResult(
                TOOL_NAME,
                AssistantToolRunStatus.EXECUTED,
                "LEAVE_REQUEST_SUBMITTED",
                submittedSummary(view),
                view
        );
    }

    private static String submittedSummary(LeaveRequestView view) {
        if (view == null) {
            return "已提交请假申请，审批流程已启动";
        }
        StringBuilder summary = new StringBuilder("已提交请假申请，审批流程已启动");
        if (view.getRequestNo() != null && !view.getRequestNo().isBlank()) {
            summary.append("。请假单号：").append(view.getRequestNo());
        }
        if (view.getWorkflowInstanceId() != null) {
            summary.append("，流程实例号：").append(view.getWorkflowInstanceId());
        }
        return summary.toString();
    }

    private static String requireArgument(Map<String, String> arguments, String key) {
        String value = arguments.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少请假工具参数: " + key);
        }
        return value;
    }

    private static LeaveType requireLeaveType(Map<String, String> arguments) {
        String code = requireArgument(arguments, "leaveType");
        return LeaveType.fromCode(code)
                .orElseThrow(() -> new IllegalArgumentException("请假类型代码不合法: " + code));
    }

    @Override
    public List<AssistantToolArgument> confirmationArguments(AssistantToolExecutionContext context,
                                                             AssistantToolResult preparedResult) {
        if (preparedResult == null || !(preparedResult.data() instanceof LeaveRequestActionDraft draft)) {
            return ConfirmableTool.super.confirmationArguments(context, preparedResult);
        }
        return context.plan().filledArguments().stream()
                .map(argument -> switch (argument.key()) {
                    case "leaveType" -> new AssistantToolArgument(
                            argument.key(), argument.label(), draft.leaveType(), draft.leaveTypeLabel()
                    );
                    case "startTime" -> dateTimeArgument(argument, draft.startTime());
                    case "endTime" -> dateTimeArgument(argument, draft.endTime());
                    default -> argument;
                })
                .toList();
    }

    private static AssistantToolArgument dateTimeArgument(AssistantToolArgument argument, LocalDateTime value) {
        return new AssistantToolArgument(
                argument.key(),
                argument.label(),
                value.toString(),
                value.toString().replace('T', ' ')
        );
    }

    private static LeavePeriod normalizeNamedPeriod(String message,
                                                    LocalDateTime startTime,
                                                    LocalDateTime endTime) {
        if (message != null
                && message.contains("下午")
                && startTime.toLocalDate().equals(endTime.toLocalDate())
                && startTime.toLocalTime().equals(LocalTime.of(13, 0))
                && endTime.toLocalTime().equals(AFTERNOON_END)) {
            return new LeavePeriod(startTime.with(AFTERNOON_START), endTime);
        }
        return new LeavePeriod(startTime, endTime);
    }

    private static LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("请假时间格式不合法: " + value, ex);
        }
    }

    private static ToolInputField input(String key,
                                        String label,
                                        ToolSchemaType type,
                                        ToolSchemaFormat format,
                                        String description,
                                        boolean required) {
        return input(key, label, type, format, description, required, List.of());
    }

    private static ToolInputField input(String key,
                                        String label,
                                        ToolSchemaType type,
                                        ToolSchemaFormat format,
                                        String description,
                                        boolean required,
                                        List<String> options) {
        return input(key, label, type, format, description, required, options, Map.of());
    }

    private static ToolInputField input(String key,
                                        String label,
                                        ToolSchemaType type,
                                        ToolSchemaFormat format,
                                        String description,
                                        boolean required,
                                        List<String> options,
                                        Map<String, String> optionLabels) {
        return new ToolInputField(key, label, type, format, description, required, options, optionLabels);
    }

    private record LeavePeriod(LocalDateTime startTime, LocalDateTime endTime) {
    }
}
