package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolExecutionContext;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.laker.admin.module.assistant.AssistantTestFixtures.requestContext;
import static org.assertj.core.api.Assertions.assertThat;

class ProcessDetailToolTest {

    @Test
    void shouldDeclareFocusedProcessDetailBoundary() {
        ProcessDetailTool tool = new ProcessDetailTool((userId, keyword, limit) -> List.of());

        assertThat(tool.definition().description())
                .contains("用于", "不用于")
                .contains("用户明确给出的", "会话中的唯一流程引用")
                .contains("流程实例号", "业务单号", "当前节点", "状态")
                .contains("先定位最近流程", "流程列表", "待办任务", "申请单业务字段")
                .contains("完整进度答案", "不需要扩查申请单")
                .doesNotContain("attributes", "taskId", "workflow.process.query");
        assertThat(tool.definition().outputSchema())
                .extracting(field -> field.name())
                .containsExactly(
                        "instanceId", "businessType", "businessId", "title",
                        "status", "currentNodeKey"
                );
    }

    @Test
    void shouldReturnProcessDetailByIdentifier() {
        ProcessDetailTool tool = new ProcessDetailTool((userId, keyword, limit) -> List.of(
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
                requestContext("查一下 LV-20260709-000001 的流程详情"),
                new ToolExecutionPlan(
                        "run-agent-001",
                        "trace-agent-001",
                        ToolExecutionStatus.READY,
                        ProcessDetailTool.TOOL_NAME,
                        true,
                        List.of(new AssistantToolArgument(
                                "processIdentifier",
                                "流程标识",
                                "LV-20260709-000001",
                                "LV-20260709-000001"
                        )),
                        List.of(),
                        List.of(),
                        "只读查询工具已准备好"
                )
        ));

        assertThat(result.status()).isEqualTo(AssistantToolRunStatus.EXECUTED);
        assertThat(result.toolName()).isEqualTo(ProcessDetailTool.TOOL_NAME);
        assertThat(result.artifactType()).isEqualTo("PROCESS_DETAIL");
        assertThat(result.summary()).contains("当前节点：leader_approve");
        assertThat(result.data()).isInstanceOf(WorkflowProcessToolItem.class);
    }
}
