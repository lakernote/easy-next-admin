package com.laker.admin.module.assistant.tool;

import com.laker.admin.module.assistant.tool.workflow.LeavePrepareToolArgumentValidationRule;
import com.laker.admin.module.assistant.tool.workflow.LeavePrepareTool;
import com.laker.admin.module.assistant.tool.workflow.ProcessDetailTool;
import com.laker.admin.module.assistant.tool.workflow.WorkflowProcessDetailArgumentValidationRule;
import com.laker.admin.module.workflow.leave.support.LeavePeriodCalculator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.laker.admin.module.assistant.AssistantTestFixtures.requestContext;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultAssistantToolArgumentValidatorTest {

    @Test
    void shouldMoveInvalidLeaveTimeBackToMissingParameters() {
        AssistantVisibleTool tool = leaveTool();
        var context = requestContext("我想请明天下午病假，身体不舒服");
        var selection = AssistantToolSelection.selected(tool, List.of(tool), "test");
        var resolution = new AssistantToolArgumentResolution(
                tool.name(),
                List.of(
                        new AssistantToolArgument("leaveType", "请假类型", "病假", "病假"),
                        new AssistantToolArgument("startTime", "开始时间", "2026-07-08T18:00", "2026-07-08 18:00"),
                        new AssistantToolArgument("endTime", "结束时间", "2026-07-08T13:30", "2026-07-08 13:30"),
                        new AssistantToolArgument("reason", "请假原因", "身体不舒服", "身体不舒服")
                ),
                List.of()
        );

        AssistantToolArgumentResolution validated = new DefaultAssistantToolArgumentValidator(
                List.of(new LeavePrepareToolArgumentValidationRule())
        ).validate(context, selection, resolution);

        assertThat(validated.complete()).isFalse();
        assertThat(validated.arguments()).extracting(AssistantToolArgument::key)
                .doesNotContain("startTime", "endTime");
        assertThat(validated.missingRequiredParameters()).extracting(ToolInputField::name)
                .contains("startTime", "endTime");
    }

    @Test
    void shouldAllowLeaveStartTimeBeforeNowForRetrospectiveLeave() {
        AssistantVisibleTool tool = leaveTool();
        var context = requestContext("我想今天下午请病假，身体不舒服");
        var selection = AssistantToolSelection.selected(tool, List.of(tool), "test");
        var resolution = new AssistantToolArgumentResolution(
                tool.name(),
                List.of(
                        new AssistantToolArgument("leaveType", "请假类型", "病假", "病假"),
                        new AssistantToolArgument("startTime", "开始时间", "2026-07-07T13:30", "2026-07-07 13:30"),
                        new AssistantToolArgument("endTime", "结束时间", "2026-07-07T18:00", "2026-07-07 18:00"),
                        new AssistantToolArgument("reason", "请假原因", "身体不舒服", "身体不舒服")
                ),
                List.of()
        );

        AssistantToolArgumentResolution validated = new DefaultAssistantToolArgumentValidator(
                List.of(new LeavePrepareToolArgumentValidationRule())
        ).validate(context, selection, resolution);

        assertThat(validated.complete()).isTrue();
        assertThat(validated.argumentMap())
                .containsEntry("startTime", "2026-07-07T13:30")
                .containsEntry("endTime", "2026-07-07T18:00");
        assertThat(validated.missingRequiredParameters()).isEmpty();
    }

    @Test
    void shouldAllowRetrospectiveLeavePeriodThatHasCompletelyPassed() {
        AssistantVisibleTool tool = leaveTool();
        var context = requestContext("补一张昨天的病假单，原因是身体不舒服");
        var selection = AssistantToolSelection.selected(tool, List.of(tool), "test");
        var resolution = new AssistantToolArgumentResolution(
                tool.name(),
                List.of(
                        new AssistantToolArgument("leaveType", "请假类型", "病假", "病假"),
                        new AssistantToolArgument("startTime", "开始时间", "2026-07-06T13:30", "2026-07-06 13:30"),
                        new AssistantToolArgument("endTime", "结束时间", "2026-07-06T18:00", "2026-07-06 18:00"),
                        new AssistantToolArgument("reason", "请假原因", "身体不舒服", "身体不舒服")
                ),
                List.of()
        );

        AssistantToolArgumentResolution validated = new DefaultAssistantToolArgumentValidator(
                List.of(new LeavePrepareToolArgumentValidationRule())
        ).validate(context, selection, resolution);

        assertThat(validated.complete()).isTrue();
        assertThat(validated.argumentMap())
                .containsEntry("startTime", "2026-07-06T13:30")
                .containsEntry("endTime", "2026-07-06T18:00");
        assertThat(validated.missingRequiredParameters()).isEmpty();
    }

    @Test
    void shouldRejectInventedLeaveReasonWithoutCurrentUserEvidence() {
        AssistantVisibleTool tool = leaveTool();
        var context = requestContext("我明天下午请病假。");
        var selection = AssistantToolSelection.selected(tool, List.of(tool), "test");
        var resolution = new AssistantToolArgumentResolution(
                tool.name(),
                List.of(
                        new AssistantToolArgument("leaveType", "请假类型", "病假", "病假"),
                        new AssistantToolArgument("startTime", "开始时间", "2026-07-08T13:30", "2026-07-08 13:30"),
                        new AssistantToolArgument("endTime", "结束时间", "2026-07-08T18:00", "2026-07-08 18:00"),
                        new AssistantToolArgument("reason", "请假原因", "身体不舒服", "身体不舒服")
                ),
                List.of()
        );

        AssistantToolArgumentResolution validated = new DefaultAssistantToolArgumentValidator(
                List.of(new LeavePrepareToolArgumentValidationRule())
        ).validate(context, selection, resolution);

        assertThat(validated.complete()).isFalse();
        assertThat(validated.argumentMap()).doesNotContainKey("reason");
        assertThat(validated.missingRequiredParameters())
                .extracting(ToolInputField::name)
                .containsExactly("reason");
        assertThat(validated.providedArgumentKeys()).doesNotContain("reason");
    }

    @Test
    void shouldKeepReasonRestoredFromPendingActionWithoutTreatingItAsCurrentInput() {
        AssistantVisibleTool tool = leaveTool();
        var context = requestContext("确认提交");
        var selection = AssistantToolSelection.selected(tool, List.of(tool), "test");
        var resolution = new AssistantToolArgumentResolution(
                tool.name(),
                List.of(
                        new AssistantToolArgument("leaveType", "请假类型", "病假", "病假"),
                        new AssistantToolArgument("startTime", "开始时间", "2026-07-08T13:30", "2026-07-08 13:30"),
                        new AssistantToolArgument("endTime", "结束时间", "2026-07-08T18:00", "2026-07-08 18:00"),
                        new AssistantToolArgument("reason", "请假原因", "身体不舒服", "身体不舒服")
                ),
                List.of(),
                Set.of()
        );

        AssistantToolArgumentResolution validated = new DefaultAssistantToolArgumentValidator(
                List.of(new LeavePrepareToolArgumentValidationRule())
        ).validate(context, selection, resolution);

        assertThat(validated.complete()).isTrue();
        assertThat(validated.argumentMap()).containsEntry("reason", "身体不舒服");
        assertThat(validated.providedArgumentKeys()).isEmpty();
    }

    @Test
    void shouldValidateProcessIdentifierFormatWithoutInterpretingNaturalLanguage() {
        AssistantVisibleTool tool = AssistantVisibleTool.from(
                new ProcessDetailTool((user, keyword, limit) -> List.of()).definition()
        );
        var context = requestContext("看第一个流程详情");
        var selection = AssistantToolSelection.selected(tool, List.of(tool), "test");
        var resolution = new AssistantToolArgumentResolution(
                tool.name(),
                List.of(new AssistantToolArgument(
                        "processIdentifier", "流程标识", "第一个流程", "第一个流程"
                )),
                List.of()
        );

        AssistantToolArgumentResolution validated = new DefaultAssistantToolArgumentValidator(
                List.of(new WorkflowProcessDetailArgumentValidationRule())
        ).validate(context, selection, resolution);

        assertThat(validated.complete()).isFalse();
        assertThat(validated.arguments()).isEmpty();
        assertThat(validated.missingRequiredParameters())
                .extracting(ToolInputField::name)
                .containsExactly("processIdentifier");
    }

    private static AssistantVisibleTool leaveTool() {
        return AssistantVisibleTool.from(new LeavePrepareTool(new LeavePeriodCalculator()).definition());
    }
}
