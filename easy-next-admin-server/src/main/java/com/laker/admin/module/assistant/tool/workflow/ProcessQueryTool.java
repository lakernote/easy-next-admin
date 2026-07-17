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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ProcessQueryTool implements AssistantTool {
    public static final String TOOL_NAME = "workflow.process.query";
    private static final int DEFAULT_LIMIT = 5;

    private static final ToolDefinition DEFINITION = new ToolDefinition(
            TOOL_NAME,
            "查询我发起的流程",
            "用于查询当前用户自己发起的流程或申请列表；每个结果已包含流程实例号、业务单号、标题、当前节点和状态，可直接回答“最近一笔申请单号”或“最近一个流程进度”。不用于查询待办任务、他人流程、申请表中的时间、天数、原因等业务详情、组织统计或制度知识，也不为列表项补查详情。",
            EasyPermissions.Workflow.VIEW,
            ToolExecutionMode.READ_ONLY,
            ToolRiskLevel.LOW,
            List.of(
                    new ToolInputField(
                            "keyword",
                            "查询关键词",
                            ToolSchemaType.STRING,
                            ToolSchemaFormat.NONE,
                            "流程标题、业务单号或关键词。",
                            false,
                            List.of()
                    ),
                    new ToolInputField(
                            "status",
                            "流程状态",
                            ToolSchemaType.STRING,
                            ToolSchemaFormat.NONE,
                            "进行中为 RUNNING，已结束为 COMPLETED，也可用具体状态。",
                            false,
                            List.of("RUNNING", "COMPLETED", "APPROVED", "REJECTED", "REVOKED", "TERMINATED")
                    )
            ),
            List.of(
                    new ToolOutputField("total", ToolSchemaType.INTEGER, true),
                    new ToolOutputField("items", ToolSchemaType.ARRAY, true)
            ),
            ToolSubjectScope.CURRENT_USER
    );

    private final WorkflowProcessToolQueryService queryService;

    public ProcessQueryTool(WorkflowProcessToolQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public AssistantToolResult execute(AssistantToolExecutionContext context) {
        Map<String, String> arguments = context.plan().argumentMap();
        String keyword = arguments.get("keyword");
        WorkflowProcessStatusFilter statusFilter = WorkflowProcessStatusFilter.fromCode(arguments.get("status"))
                .orElse(null);
        List<WorkflowProcessToolItem> processes = queryService.myStartedProcesses(
                context.requestContext().user(), keyword, statusFilter, DEFAULT_LIMIT);
        WorkflowProcessQueryResult result = new WorkflowProcessQueryResult(processes.size(), processes);
        String summary = summary(processes);
        return new AssistantToolResult(
                TOOL_NAME,
                AssistantToolRunStatus.EXECUTED,
                "PROCESS_LIST",
                summary,
                result
        );
    }

    private static String summary(List<WorkflowProcessToolItem> processes) {
        if (processes.isEmpty()) {
            return "没有找到当前用户自己发起的流程实例";
        }
        WorkflowProcessToolItem first = processes.get(0);
        String businessId = StringUtils.hasText(first.businessId()) ? "，业务单号：" + first.businessId() : "";
        return "当前用户自己发起的最近流程：%s，流程实例号：%s%s，状态：%s".formatted(
                first.title(),
                first.instanceId(),
                businessId,
                first.status()
        );
    }
}
