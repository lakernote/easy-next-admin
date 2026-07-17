package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.context.AssistantRequestContext.AssistantUserContext;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolExecutionContext;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.ToolExecutionMode;
import com.laker.admin.module.assistant.tool.ToolRiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.laker.admin.module.assistant.AssistantTestFixtures.requestContext;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowTaskRejectToolTest {

    @Test
    void shouldDeclareIndependentConfirmedRejectionContract() {
        WorkflowTaskRejectTool tool = new WorkflowTaskRejectTool(new RecordingActionService());

        assertThat(tool.definition().name()).isEqualTo("workflow.task.reject");
        assertThat(tool.definition().executionMode()).isEqualTo(ToolExecutionMode.REQUIRES_CONFIRMATION);
        assertThat(tool.definition().riskLevel()).isEqualTo(ToolRiskLevel.HIGH);
        assertThat(tool.definition().requiredPermission()).isEqualTo("workflow:task:reject");
        assertThat(tool.definition().description())
                .contains("用于", "不用于")
                .contains("驳回草稿")
                .contains("确认后执行")
                .contains("同意、退回或批量审批")
                .contains("唯一定位", "用户明确给出的意见");
    }

    @Test
    void shouldCreateRejectionDraftWithoutTreatingActionWordAsComment() {
        RecordingActionService service = new RecordingActionService();
        WorkflowTaskRejectTool tool = new WorkflowTaskRejectTool(service);

        AssistantToolExecutionContext context = context(ToolExecutionStatus.APPROVAL_REQUIRED);
        AssistantToolResult result = tool.execute(context);

        assertThat(result.status()).isEqualTo(AssistantToolRunStatus.EXECUTED);
        assertThat(result.artifactType()).isEqualTo("WORKFLOW_TASK_REJECTION_DRAFT");
        assertThat(service.rejections()).hasValue(0);
        WorkflowTaskDecisionDraft draft = (WorkflowTaskDecisionDraft) result.data();
        assertThat(draft.decision()).isEqualTo(WorkflowTaskDecision.REJECT);
        assertThat(draft.comment()).isNull();
        assertThat(tool.confirmationArguments(context, result))
                .extracting(AssistantToolArgument::key, AssistantToolArgument::value)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("taskIdentifier", "7001"));
        assertThat(result.summary()).contains("请确认后我再执行驳回");
    }

    @Test
    void shouldOnlyRejectAfterConfirmedPhase() {
        RecordingActionService service = new RecordingActionService();
        WorkflowTaskRejectTool tool = new WorkflowTaskRejectTool(service);

        AssistantToolResult result = tool.execute(context(
                ToolExecutionStatus.APPROVED,
                new AssistantToolArgument("comment", "驳回意见", "信息不完整", "信息不完整")
        ));

        assertThat(service.rejections()).hasValue(1);
        assertThat(result.artifactType()).isEqualTo("WORKFLOW_TASK_REJECTED");
        assertThat(result.data()).isEqualTo(new WorkflowTaskActionResult(
                WorkflowTaskDecision.REJECT,
                7001L, 9001L, "LV-20260713-000002", "张三请假申请", null, "REJECTED"
        ));
    }

    private static AssistantToolExecutionContext context(ToolExecutionStatus nextStep,
                                                         AssistantToolArgument... additionalArguments) {
        List<AssistantToolArgument> arguments = new java.util.ArrayList<>();
        arguments.add(new AssistantToolArgument(
                "taskIdentifier", "待驳回任务标识", "LV-20260713-000002", "LV-20260713-000002"
        ));
        arguments.addAll(List.of(additionalArguments));
        return new AssistantToolExecutionContext(
                requestContext("驳回第一个"),
                new ToolExecutionPlan(
                        "run-agent-001", "trace-agent-001", nextStep, WorkflowTaskRejectTool.TOOL_NAME,
                        false, arguments, List.of(), List.of(), "测试驳回动作"
                )
        );
    }

    private static final class RecordingActionService implements WorkflowTaskActionToolService {
        private final AtomicInteger rejections = new AtomicInteger();

        @Override
        public WorkflowTaskActionTarget resolveTarget(AssistantUserContext user, String taskIdentifier) {
            return new WorkflowTaskActionTarget(
                    7001L, 9001L, "LV-20260713-000002", "张三请假申请", "经理审批"
            );
        }

        @Override
        public WorkflowTaskActionResult approve(Long taskId, String comment) {
            throw new AssertionError("驳回 Tool 不能调用同意动作");
        }

        @Override
        public WorkflowTaskActionResult reject(Long taskId, String comment) {
            rejections.incrementAndGet();
            return new WorkflowTaskActionResult(
                    WorkflowTaskDecision.REJECT,
                    taskId, 9001L, "LV-20260713-000002", "张三请假申请", null, "REJECTED"
            );
        }

        AtomicInteger rejections() {
            return rejections;
        }
    }
}
