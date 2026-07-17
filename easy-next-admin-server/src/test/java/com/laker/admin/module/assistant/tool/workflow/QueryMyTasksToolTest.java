package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import com.laker.admin.module.assistant.tool.AssistantToolExecutionContext;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.DefaultToolResultValidator;
import com.laker.admin.module.assistant.tool.ToolExecutionPhase;
import com.laker.admin.module.assistant.state.AssistantRunReference;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.laker.admin.module.assistant.AssistantTestFixtures.requestContext;
import static org.assertj.core.api.Assertions.assertThat;

class QueryMyTasksToolTest {

    @Test
    void shouldLimitToolToRealTaskQueries() {
        QueryMyTasksTool tool = new QueryMyTasksTool((userId, limit) -> List.of());

        assertThat(tool.definition().description())
                .contains("用于", "不用于")
                .contains("当前用户自己的待审批任务列表")
                .contains("已发起流程", "他人任务", "制度知识");
    }

    @Test
    void shouldReturnCurrentUsersPendingTasksAsToolResult() {
        QueryMyTasksTool tool = new QueryMyTasksTool((userId, limit) -> List.of(
                new WorkflowTaskToolItem(
                        1001L,
                        2001L,
                        "采购申请-显示器",
                        "负责人审批",
                        "PENDING",
                        LocalDateTime.of(2026, 7, 7, 10, 0)
                )
        ));

        AssistantToolResult result = tool.execute(new AssistantToolExecutionContext(
                requestContext("我有哪些待办"),
                new ToolExecutionPlan(
                        "run-agent-001",
                        "trace-agent-001",
                        ToolExecutionStatus.READY,
                        QueryMyTasksTool.TOOL_NAME,
                        true,
                        List.of(),
                        List.of(),
                        List.of(),
                        "只读查询工具已准备好"
                )
        ));

        assertThat(result.status()).isEqualTo(AssistantToolRunStatus.EXECUTED);
        assertThat(result.toolName()).isEqualTo(QueryMyTasksTool.TOOL_NAME);
        assertThat(result.artifactType()).isEqualTo("TASK_LIST");
        assertThat(result.summary()).isEqualTo("找到 1 个待办任务");
        assertThat(new DefaultToolResultValidator()
                .validate(tool.definition(), result, ToolExecutionPhase.READ)
                .valid()).isTrue();
        assertThat(result.data()).isInstanceOf(WorkflowTaskQueryResult.class);
        WorkflowTaskQueryResult data = (WorkflowTaskQueryResult) result.data();
        assertThat(data.total()).isEqualTo(1);
        assertThat(data.items()).extracting(WorkflowTaskToolItem::nodeName)
                .containsExactly("负责人审批");
    }

    @Test
    void shouldExposeTaskListItemsAsRunReferencesForFollowUpQuestions() {
        AssistantToolResult result = new AssistantToolResult(
                QueryMyTasksTool.TOOL_NAME,
                AssistantToolRunStatus.EXECUTED,
                "TASK_LIST",
                "找到 1 个待办任务",
                new WorkflowTaskQueryResult(1, List.of(
                        new WorkflowTaskToolItem(
                                1001L,
                                2001L,
                                "采购申请-显示器",
                                "负责人审批",
                                "PENDING",
                                LocalDateTime.of(2026, 7, 7, 10, 0)
                        )
                ))
        );

        List<AssistantRunReference> references = new WorkflowTaskRunReferenceExtractor().extract(result);

        assertThat(references).hasSize(1);
        AssistantRunReference reference = references.get(0);
        assertThat(reference.type()).isEqualTo("workflow.task");
        assertThat(reference.label()).isEqualTo("待办 1");
        assertThat(reference.value()).isEqualTo("1001");
        assertThat(reference.displayValue()).contains("采购申请-显示器", "负责人审批");
        assertThat(reference.attributes())
                .containsEntry("index", 1)
                .containsEntry("taskId", 1001L)
                .containsEntry("instanceId", 2001L)
                .containsEntry("instanceTitle", "采购申请-显示器")
                .containsEntry("nodeName", "负责人审批");
    }
}
