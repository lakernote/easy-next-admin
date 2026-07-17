package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.infrastructure.security.permission.EasyPermissions;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private static final String DESCRIPTION = """
            用于为当前用户准备一笔新请假申请；不用于查询请假单、流程进度或休假制度。仅填写用户明确提供的假别、起止时间和原因，缺失字段省略。
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
                            "年假、事假、病假、调休等；不能从他人的事件推断。", true,
                            List.of("年假", "事假", "病假", "调休", "补休", "婚假", "产假", "陪产假", "丧假")),
                    input("startTime", "开始时间", ToolSchemaType.STRING, ToolSchemaFormat.DATE_TIME,
                            "ISO-8601 本地时间；上午/全天 09:00，下午 13:30。", true),
                    input("endTime", "结束时间", ToolSchemaType.STRING, ToolSchemaFormat.DATE_TIME,
                            "ISO-8601 本地时间；上午 12:00，下午/全天 18:00。", true),
                    input("reason", "请假原因", ToolSchemaType.STRING, ToolSchemaFormat.NONE,
                            "保留用户原话；未说明则省略，不能从假别或时间推断。", true)
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
            String leaveType = requireArgument(arguments, "leaveType");
            LocalDateTime startTime = parseDateTime(requireArgument(arguments, "startTime"));
            LocalDateTime endTime = parseDateTime(requireArgument(arguments, "endTime"));
            String reason = requireArgument(arguments, "reason");
            BigDecimal days = leavePeriodCalculator.calculate(startTime, endTime);
            LeaveRequestActionDraft draft = new LeaveRequestActionDraft(
                    "SUBMIT_LEAVE_REQUEST",
                    TOOL_NAME,
                    leaveType,
                    startTime,
                    endTime,
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
            String leaveType = requireArgument(arguments, "leaveType");
            LocalDateTime startTime = parseDateTime(requireArgument(arguments, "startTime"));
            LocalDateTime endTime = parseDateTime(requireArgument(arguments, "endTime"));
            String reason = requireArgument(arguments, "reason");
            BigDecimal days = leavePeriodCalculator.calculate(startTime, endTime);
            return submitLeaveRequest(leaveType, startTime, endTime, days, reason);
        } catch (IllegalArgumentException ex) {
            return AssistantToolResult.failed(TOOL_NAME, ex.getMessage());
        }
    }

    private AssistantToolResult submitLeaveRequest(String leaveType,
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
        return new ToolInputField(key, label, type, format, description, required, options);
    }
}
