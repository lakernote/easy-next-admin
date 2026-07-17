package com.laker.admin.module.assistant.tool.workflow;

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

class WorkflowTaskApproveToolTest {

    @Test
    void shouldDeclareCanonicalConfirmedApprovalContract() {
        WorkflowTaskApproveTool tool = new WorkflowTaskApproveTool(new RecordingApprovalService());

        assertThat(tool.definition().name()).isEqualTo("workflow.task.approve");
        assertThat(tool.definition().executionMode()).isEqualTo(ToolExecutionMode.REQUIRES_CONFIRMATION);
        assertThat(tool.definition().riskLevel()).isEqualTo(ToolRiskLevel.HIGH);
        assertThat(tool.definition().requiredPermission()).isEqualTo("workflow:task:approve");
        assertThat(tool.definition().inputSchema()).extracting(field -> field.name())
                .containsExactly("taskIdentifier", "comment");
        assertThat(tool.definition().outputSchema()).extracting(field -> field.name())
                .containsExactly("taskId", "instanceId", "businessId", "currentNodeKey", "status");
        assertThat(tool.definition().description())
                .contains("用于", "不用于")
                .contains("同意草稿")
                .contains("确认后执行")
                .contains("驳回、退回或批量审批")
                .contains("唯一定位", "用户明确给出的意见");
    }

    @Test
    void shouldOnlyCreateDraftBeforeConfirmation() {
        RecordingApprovalService service = new RecordingApprovalService();
        WorkflowTaskApproveTool tool = new WorkflowTaskApproveTool(service);

        AssistantToolExecutionContext context = context(
                ToolExecutionStatus.APPROVAL_REQUIRED,
                new AssistantToolArgument("taskIdentifier", "待审批任务标识", "LV-20260713-000002", "LV-20260713-000002"),
                new AssistantToolArgument("comment", "审批意见", "同意", "同意")
        );
        AssistantToolResult result = tool.execute(context);

        assertThat(result.status()).isEqualTo(AssistantToolRunStatus.EXECUTED);
        assertThat(result.artifactType()).isEqualTo("WORKFLOW_TASK_APPROVAL_DRAFT");
        assertThat(result.data()).isInstanceOf(WorkflowTaskDecisionDraft.class);
        assertThat(service.approvals()).hasValue(0);
        WorkflowTaskDecisionDraft draft = (WorkflowTaskDecisionDraft) result.data();
        assertThat(draft.decision()).isEqualTo(WorkflowTaskDecision.APPROVE);
        assertThat(draft.taskId()).isEqualTo(7001L);
        assertThat(draft.businessId()).isEqualTo("LV-20260713-000002");
        assertThat(draft.comment()).isEqualTo("同意");
        assertThat(tool.confirmationArguments(context, result))
                .extracting(AssistantToolArgument::key, AssistantToolArgument::value)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("taskIdentifier", "7001"),
                        org.assertj.core.groups.Tuple.tuple("comment", "同意")
                );
        assertThat(result.summary()).contains("请确认后我再执行审批");
    }

    @Test
    void shouldExecuteSavedActionOnlyInConfirmedPhase() {
        RecordingApprovalService service = new RecordingApprovalService();
        WorkflowTaskApproveTool tool = new WorkflowTaskApproveTool(service);

        AssistantToolResult result = tool.execute(context(
                ToolExecutionStatus.APPROVED,
                new AssistantToolArgument("taskIdentifier", "待审批任务标识", "7001", "7001")
        ));

        assertThat(result.status()).isEqualTo(AssistantToolRunStatus.EXECUTED);
        assertThat(result.artifactType()).isEqualTo("WORKFLOW_TASK_APPROVED");
        assertThat(service.approvals()).hasValue(1);
        assertThat(result.data()).isEqualTo(new WorkflowTaskActionResult(
                WorkflowTaskDecision.APPROVE,
                7001L, 9001L, "LV-20260713-000002", "张三请假申请", null, "APPROVED"
        ));
    }

    private static AssistantToolExecutionContext context(ToolExecutionStatus nextStep,
                                                         AssistantToolArgument... arguments) {
        return new AssistantToolExecutionContext(
                requestContext("审批第一个"),
                new ToolExecutionPlan(
                        "run-agent-001",
                        "trace-agent-001",
                        nextStep,
                        WorkflowTaskApproveTool.TOOL_NAME,
                        false,
                        List.of(arguments),
                        List.of(),
                        List.of(),
                        "测试审批动作"
                )
        );
    }

    private static final class RecordingApprovalService implements WorkflowTaskActionToolService {
        private final AtomicInteger approvals = new AtomicInteger();

        @Override
        public WorkflowTaskActionTarget resolveTarget(
                com.laker.admin.module.assistant.context.AssistantRequestContext.AssistantUserContext user,
                String taskIdentifier) {
            return new WorkflowTaskActionTarget(
                    7001L,
                    9001L,
                    "LV-20260713-000002",
                    "张三请假申请",
                    "经理审批"
            );
        }

        @Override
        public WorkflowTaskActionResult approve(Long taskId, String comment) {
            approvals.incrementAndGet();
            return new WorkflowTaskActionResult(
                    WorkflowTaskDecision.APPROVE,
                    taskId,
                    9001L,
                    "LV-20260713-000002",
                    "张三请假申请",
                    null,
                    "APPROVED"
            );
        }

        @Override
        public WorkflowTaskActionResult reject(Long taskId, String comment) {
            throw new AssertionError("同意 Tool 不能调用驳回动作");
        }

        AtomicInteger approvals() {
            return approvals;
        }
    }
}
