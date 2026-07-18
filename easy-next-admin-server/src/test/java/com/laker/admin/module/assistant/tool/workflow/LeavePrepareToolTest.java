package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolExecutionContext;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.workflow.leave.support.LeavePeriodCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.laker.admin.module.assistant.AssistantTestFixtures.requestContext;
import static org.assertj.core.api.Assertions.assertThat;

class LeavePrepareToolTest {

    private final LeavePrepareTool tool = new LeavePrepareTool(new LeavePeriodCalculator());

    @Test
    void shouldOwnLeaveToolDefinitionAndParameters() {
        assertThat(tool.definition().name()).isEqualTo("workflow.leave.prepare");
        assertThat(tool.definition().readOnly()).isFalse();
        assertThat(tool.definition().inputSchema()).extracting(field -> field.name())
                .containsExactly("leaveType", "startTime", "endTime", "reason");
        assertThat(tool.definition().inputSchema())
                .filteredOn(field -> field.name().equals("endTime"))
                .singleElement()
                .satisfies(field -> assertThat(field.description())
                        .contains("下午/全天 18:00"));
        assertThat(tool.definition().description())
                .contains("用于", "不用于")
                .contains("仅填写用户明确提供的假别、起止时间和原因")
                .contains("假别必须在用户原文中出现")
                .contains("缺失字段省略");
        assertThat(tool.definition().inputSchema())
                .filteredOn(field -> field.name().equals("leaveType"))
                .singleElement()
                .satisfies(field -> assertThat(field.description())
                        .contains("用户原文明确出现", "未说明则省略"));
        assertThat(tool.definition().inputSchema())
                .filteredOn(field -> field.name().equals("reason"))
                .singleElement()
                .satisfies(field -> assertThat(field.description())
                        .contains("未说明则省略", "整句请假目标推断"));
    }

    @Test
    void shouldGenerateLeaveActionDraftWithoutSubmittingRequest() {
        AssistantToolResult result = tool.execute(new AssistantToolExecutionContext(
                requestContext("我想今天下午请事假，因为家里有事"),
                new ToolExecutionPlan(
                        "run-agent-001",
                        "trace-agent-001",
                        ToolExecutionStatus.APPROVAL_REQUIRED,
                        "workflow.leave.prepare",
                        false,
                        List.of(
                                new AssistantToolArgument("leaveType", "请假类型", "事假", "事假"),
                                new AssistantToolArgument("startTime", "开始时间", "2026-07-07T13:30", "2026-07-07 13:30"),
                                new AssistantToolArgument("endTime", "结束时间", "2026-07-07T18:00", "2026-07-07 18:00"),
                                new AssistantToolArgument("reason", "请假原因", "家里有事", "家里有事")
                        ),
                        List.of(),
                        List.of(),
                        "业务字段已齐全，可以生成待确认动作草稿"
                )
        ));

        assertThat(result.status()).isEqualTo(AssistantToolRunStatus.EXECUTED);
        assertThat(result.artifactType()).isEqualTo("LEAVE_ACTION_DRAFT");
        assertThat(result.data()).isInstanceOf(LeaveRequestActionDraft.class);
        LeaveRequestActionDraft draft = (LeaveRequestActionDraft) result.data();
        assertThat(draft.actionType()).isEqualTo("SUBMIT_LEAVE_REQUEST");
        assertThat(draft.leaveType()).isEqualTo("事假");
        assertThat(draft.days()).isEqualByComparingTo("0.5");
        assertThat(draft.reason()).isEqualTo("家里有事");
    }
}
