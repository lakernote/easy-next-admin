package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import com.laker.admin.module.assistant.context.AssistantRequestContext.AssistantUserContext;
import com.laker.admin.module.assistant.tool.AssistantToolExecutionContext;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.DefaultToolResultValidator;
import com.laker.admin.module.assistant.tool.ToolExecutionPhase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.laker.admin.module.assistant.AssistantTestFixtures.requestContext;
import static org.assertj.core.api.Assertions.assertThat;

class ProcessQueryToolTest {

    @Test
    void shouldLimitToolToRealProcessRecordQueries() {
        ProcessQueryTool tool = new ProcessQueryTool((userId, keyword, limit) -> List.of());

        assertThat(tool.definition().description())
                .contains("用于", "不用于")
                .contains("当前用户自己发起的流程或申请")
                .contains("待办任务", "他人流程", "申请表中的时间、天数、原因", "制度知识")
                .contains("流程实例号", "业务单号", "当前节点", "状态")
                .contains("最近一笔申请单号", "最近一个流程进度", "不为列表项补查详情");
    }

    @Test
    void shouldReturnVisibleProcessesAsToolResult() {
        ProcessQueryTool tool = new ProcessQueryTool((userId, keyword, limit) -> List.of(
                new WorkflowProcessToolItem(
                        90001L,
                        "LEAVE",
                        "LV-20260709-000001",
                        "请假申请-张三",
                        "leader_approve",
                        "RUNNING",
                        LocalDateTime.of(2026, 7, 9, 10, 0),
                        "RUNTIME"
                )
        ));

        AssistantToolResult result = tool.execute(new AssistantToolExecutionContext(
                requestContext("流程单号多少啊"),
                new ToolExecutionPlan(
                        "run-agent-001",
                        "trace-agent-001",
                        ToolExecutionStatus.READY,
                        "workflow.process.query",
                        true,
                        List.of(),
                        List.of(),
                        List.of(),
                        "只读查询工具已准备好"
                )
        ));

        assertThat(result.status()).isEqualTo(AssistantToolRunStatus.EXECUTED);
        assertThat(result.toolName()).isEqualTo("workflow.process.query");
        assertThat(result.artifactType()).isEqualTo("PROCESS_LIST");
        assertThat(result.summary())
                .contains("流程实例号：90001")
                .contains("业务单号：LV-20260709-000001");
        assertThat(new DefaultToolResultValidator()
                .validate(tool.definition(), result, ToolExecutionPhase.READ)
                .valid()).isTrue();
        assertThat(result.data()).isInstanceOf(WorkflowProcessQueryResult.class);
        WorkflowProcessQueryResult data = (WorkflowProcessQueryResult) result.data();
        assertThat(data.total()).isEqualTo(1);
        assertThat(data.items()).extracting(WorkflowProcessToolItem::businessId)
                .containsExactly("LV-20260709-000001");
    }

    @Test
    void shouldPassCompletedStatusFilterToQueryService() {
        AtomicReference<WorkflowProcessStatusFilter> capturedStatus = new AtomicReference<>();
        WorkflowProcessToolQueryService queryService = new WorkflowProcessToolQueryService() {
            @Override
            public List<WorkflowProcessToolItem> myStartedProcesses(AssistantUserContext user, String keyword, int limit) {
                return List.of();
            }

            @Override
            public List<WorkflowProcessToolItem> myStartedProcesses(AssistantUserContext user,
                                                                    String keyword,
                                                                    WorkflowProcessStatusFilter statusFilter,
                                                                    int limit) {
                capturedStatus.set(statusFilter);
                return List.of();
            }
        };
        ProcessQueryTool tool = new ProcessQueryTool(queryService);
        ToolExecutionPlan plan = new ToolExecutionPlan(
                "run-status", "trace-status", ToolExecutionStatus.READY,
                ProcessQueryTool.TOOL_NAME, true,
                List.of(new com.laker.admin.module.assistant.tool.AssistantToolArgument(
                        "status", "流程状态", "COMPLETED", "已完成")),
                List.of(), List.of(), "查询已完成流程"
        );

        tool.execute(new AssistantToolExecutionContext(requestContext("查询我已完成的流程"), plan));

        assertThat(capturedStatus.get()).isEqualTo(WorkflowProcessStatusFilter.COMPLETED);
    }
}
