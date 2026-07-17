package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolExecutionContext;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.DefaultToolResultValidator;
import com.laker.admin.module.assistant.tool.ToolExecutionPhase;
import com.laker.admin.module.workflow.leave.dto.LeaveRequestView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static com.laker.admin.module.assistant.AssistantTestFixtures.requestContext;
import static org.assertj.core.api.Assertions.assertThat;

class LeaveRequestDetailToolTest {

    @Test
    void shouldDeclareFocusedLeaveBusinessDetailContract() {
        LeaveRequestDetailTool tool = new LeaveRequestDetailTool((user, identifier) -> java.util.Optional.empty());

        assertThat(tool.definition().name()).isEqualTo("workflow.leave.detail");
        assertThat(tool.definition().description())
                .contains("用于", "不用于")
                .contains("申请人、假别、时间、天数或原因")
                .contains("请假单号或记录 ID")
                .contains("审批节点", "休假制度")
                .contains("仅出现请假单号不构成调用理由")
                .doesNotContain("workflow.process.detail");
        assertThat(tool.definition().inputSchema())
                .singleElement()
                .satisfies(field -> {
                    assertThat(field.name()).isEqualTo("requestIdentifier");
                    assertThat(field.required()).isTrue();
                });
    }

    @Test
    void shouldReturnExactLeaveRequestAsStableToolContract() {
        LeaveRequestView view = leaveRequest();
        LeaveRequestDetailTool tool = new LeaveRequestDetailTool(
                (user, identifier) -> "LV-20260714-000001".equals(identifier)
                        ? java.util.Optional.of(view)
                        : java.util.Optional.empty()
        );

        AssistantToolResult result = tool.execute(new AssistantToolExecutionContext(
                requestContext("核对请假单号 LV-20260714-000001"),
                new ToolExecutionPlan(
                        "run-agent-001",
                        "trace-agent-001",
                        ToolExecutionStatus.READY,
                        LeaveRequestDetailTool.TOOL_NAME,
                        true,
                        List.of(new AssistantToolArgument(
                                "requestIdentifier",
                                "请假单标识",
                                "LV-20260714-000001",
                                "LV-20260714-000001"
                        )),
                        List.of(),
                        List.of(),
                        "只读查询工具已准备好"
                )
        ));

        assertThat(result.status()).isEqualTo(AssistantToolRunStatus.EXECUTED);
        assertThat(result.summary())
                .contains("请假单号：LV-20260714-000001")
                .contains("申请人：超级管理员")
                .contains("开始时间：2026-07-15 13:30:00")
                .contains("原因：智能 Agent 端到端回归测试")
                .contains("业务状态：APPROVING");
        assertThat(result.data()).isEqualTo(new LeaveRequestToolItem(
                "LV-20260714-000001",
                "超级管理员",
                "病假",
                "2026-07-15 13:30:00",
                "2026-07-15 18:00:00",
                new BigDecimal("0.5"),
                "智能 Agent 端到端回归测试",
                "APPROVING",
                "RUNNING",
                2076874607883907074L
        ));
        assertThat(new DefaultToolResultValidator().validate(
                tool.definition(),
                result,
                ToolExecutionPhase.READ
        ).valid()).isTrue();
    }

    private static LeaveRequestView leaveRequest() {
        LeaveRequestView view = new LeaveRequestView();
        view.setRequestNo("LV-20260714-000001");
        view.setApplicantName("超级管理员");
        view.setLeaveType("病假");
        view.setStartTime(LocalDateTime.of(2026, 7, 15, 13, 30));
        view.setEndTime(LocalDateTime.of(2026, 7, 15, 18, 0));
        view.setDays(new BigDecimal("0.5"));
        view.setReason("智能 Agent 端到端回归测试");
        view.setStatus("APPROVING");
        view.setWorkflowStatus("RUNNING");
        view.setWorkflowInstanceId(2076874607883907074L);
        return view;
    }
}
