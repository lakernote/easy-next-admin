package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.assistant.tool.AssistantTool;
import com.laker.admin.module.assistant.tool.AssistantToolExecutionContext;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import com.laker.admin.module.assistant.tool.ToolExecutionMode;
import com.laker.admin.module.assistant.tool.ToolOutputField;
import com.laker.admin.module.assistant.tool.ToolRiskLevel;
import com.laker.admin.module.assistant.tool.ToolSchemaType;
import com.laker.admin.module.assistant.tool.ToolSubjectScope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class QueryMyTasksTool implements AssistantTool {
    public static final String TOOL_NAME = "workflow.task.list_my_pending";
    private static final int DEFAULT_LIMIT = 5;
    private static final ToolDefinition DEFINITION = new ToolDefinition(
            TOOL_NAME,
            "查询我的待办",
            "用于查询当前用户自己的待审批任务列表；不用于查询已发起流程、他人任务、组织统计或制度知识。",
            EasyPermissions.Workflow.VIEW,
            ToolExecutionMode.READ_ONLY,
            ToolRiskLevel.LOW,
            List.of(),
            List.of(
                    new ToolOutputField("total", ToolSchemaType.INTEGER, true),
                    new ToolOutputField("items", ToolSchemaType.ARRAY, true)
            ),
            ToolSubjectScope.CURRENT_USER
    );

    private final WorkflowTaskToolQueryService queryService;

    public QueryMyTasksTool(WorkflowTaskToolQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    @Override
    public ToolDefinition definition() {
        return DEFINITION;
    }

    @Override
    public AssistantToolResult execute(AssistantToolExecutionContext context) {
        // 这里只执行当前用户自己的只读待办查询；写操作工具后续必须先生成动作草稿并等待确认。
        Long userId = context.requestContext().user().userId();
        List<WorkflowTaskToolItem> tasks = queryService.myPendingTasks(userId, DEFAULT_LIMIT);
        WorkflowTaskQueryResult result = new WorkflowTaskQueryResult(tasks.size(), tasks);
        return new AssistantToolResult(
                TOOL_NAME,
                AssistantToolRunStatus.EXECUTED,
                "TASK_LIST",
                "找到 %d 个待办任务".formatted(tasks.size()),
                result
        );
    }
}
