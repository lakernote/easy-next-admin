package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.assistant.tool.AssistantTool;
import com.laker.admin.module.assistant.tool.AssistantToolExecutionContext;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import com.laker.admin.module.assistant.tool.ToolExecutionMode;
import com.laker.admin.module.assistant.tool.ToolInputField;
import com.laker.admin.module.assistant.tool.ToolOutputField;
import com.laker.admin.module.assistant.tool.ToolRiskLevel;
import com.laker.admin.module.assistant.tool.ToolSchemaFormat;
import com.laker.admin.module.assistant.tool.ToolSchemaType;
import com.laker.admin.module.assistant.tool.ToolSubjectScope;
import com.laker.admin.module.workflow.leave.dto.LeaveRequestView;
import com.laker.admin.module.workflow.leave.support.LeaveType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 按稳定业务标识核对真实请假单；不承担请假制度问答或通用流程节点查询。 */
@Component
public class LeaveRequestDetailTool implements AssistantTool {
    public static final String TOOL_NAME = "workflow.leave.detail";
    private static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            TOOL_NAME,
            "查询请假单详情",
            """
                    用于按请假单号或记录 ID 查询申请人、假别、时间、天数或原因；不用于查询审批节点、流程进度或休假制度。只有用户明确要求请假业务字段时使用，仅出现请假单号不构成调用理由。
                    """,
            EasyPermissions.Workflow.VIEW,
            ToolExecutionMode.READ_ONLY,
            ToolRiskLevel.LOW,
            List.of(new ToolInputField(
                    "requestIdentifier",
                    "请假单标识",
                    ToolSchemaType.STRING,
                    ToolSchemaFormat.NONE,
                    "请假单号或记录 ID，如 LV-20260714-000001。",
                    true,
                    List.of()
            )),
            List.of(
                    new ToolOutputField("requestNo", ToolSchemaType.STRING, true),
                    new ToolOutputField("applicantName", ToolSchemaType.STRING, false),
                    new ToolOutputField("leaveType", ToolSchemaType.STRING, true),
                    new ToolOutputField("startTime", ToolSchemaType.STRING, true),
                    new ToolOutputField("endTime", ToolSchemaType.STRING, true),
                    new ToolOutputField("days", ToolSchemaType.NUMBER, true),
                    new ToolOutputField("reason", ToolSchemaType.STRING, true),
                    new ToolOutputField("status", ToolSchemaType.STRING, true),
                    new ToolOutputField("workflowStatus", ToolSchemaType.STRING, false),
                    new ToolOutputField("workflowInstanceId", ToolSchemaType.INTEGER, false)
            ),
            ToolSubjectScope.AUTHORIZED_TARGET
    );

    private final LeaveRequestToolQueryService queryService;

    public LeaveRequestDetailTool(LeaveRequestToolQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public AssistantToolResult execute(AssistantToolExecutionContext context) {
        Map<String, String> arguments = context.plan().argumentMap();
        String identifier = arguments.get("requestIdentifier");
        if (!StringUtils.hasText(identifier)) {
            return AssistantToolResult.failed(TOOL_NAME, "缺少请假单标识，无法查询请假单详情");
        }
        return queryService.visibleLeaveRequest(context.requestContext().user(), identifier)
                .map(LeaveRequestDetailTool::toItem)
                .map(item -> new AssistantToolResult(
                                TOOL_NAME,
                                AssistantToolRunStatus.EXECUTED,
                                "LEAVE_REQUEST_DETAIL",
                                summary(item),
                                item
                        ))
                .orElseGet(() -> AssistantToolResult.failed(TOOL_NAME, "没有找到当前用户可见的请假单"));
    }

    private static LeaveRequestToolItem toItem(LeaveRequestView view) {
        return new LeaveRequestToolItem(
                view.getRequestNo(),
                text(view.getApplicantName()),
                LeaveType.displayLabel(view.getLeaveType()),
                dateTime(view.getStartTime()),
                dateTime(view.getEndTime()),
                view.getDays(),
                view.getReason(),
                view.getStatus(),
                text(view.getWorkflowStatus()),
                view.getWorkflowInstanceId()
        );
    }

    private static String summary(LeaveRequestToolItem item) {
        return "请假单号：%s，申请人：%s，请假类型：%s，开始时间：%s，结束时间：%s，天数：%s，"
                .formatted(
                        item.requestNo(),
                        item.applicantName(),
                        item.leaveType(),
                        item.startTime(),
                        item.endTime(),
                        item.days()
                )
                + "原因：%s，业务状态：%s，流程状态：%s，流程实例号：%s".formatted(
                item.reason(),
                item.status(),
                item.workflowStatus(),
                item.workflowInstanceId() == null ? "" : item.workflowInstanceId()
        );
    }

    private static String dateTime(LocalDateTime value) {
        return value == null ? "" : value.format(DISPLAY_DATE_TIME);
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
