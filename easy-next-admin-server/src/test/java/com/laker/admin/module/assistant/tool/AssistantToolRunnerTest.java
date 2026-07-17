package com.laker.admin.module.assistant.tool;

import com.laker.admin.infrastructure.idempotency.idempotent.IdempotentHandler;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.trace.AssistantRunTraceRecorder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.laker.admin.module.assistant.AssistantTestFixtures.requestContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantToolRunnerTest {

    @Test
    void shouldExecuteReadToolInsideDeterministicPolicyBoundary() {
        AssistantTool tool = tool(readDefinition(), context -> new AssistantToolResult(
                "customer.query",
                AssistantToolRunStatus.EXECUTED,
                "CUSTOMERS",
                "找到 1 个客户",
                Map.of("count", 1)
        ));
        AssistantToolRunResult outcome = runner(List.of(tool), idempotentHandler()).executePlan(
                requestContext("查我的客户"),
                plan(ToolExecutionStatus.READY, "customer.query", true, null, null)
        );

        assertThat(outcome.result().status()).isEqualTo(AssistantToolRunStatus.EXECUTED);
        assertThat(outcome.result().summary()).contains("1 个客户");
        assertThat(outcome.plan().steps()).allMatch(step -> step.status() != null);
    }

    @Test
    void shouldCorrelateExecutedToolWithModelToolCallId() {
        AssistantTool tool = tool(readDefinition(), context -> new AssistantToolResult(
                "customer.query", AssistantToolRunStatus.EXECUTED, "CUSTOMERS", "查询完成", Map.of()
        ));
        AssistantRunTraceRecorder traceRecorder = new AssistantRunTraceRecorder();
        AssistantToolRunner runner = runner(List.of(tool), idempotentHandler(), traceRecorder);

        runner.executePlan(
                requestContext("查我的客户"),
                plan(ToolExecutionStatus.READY, "customer.query", true, null, null)
                        .withToolCallId("call-customer-1")
        );

        assertThat(traceRecorder.toolCallsSince(0)).singleElement().satisfies(call -> {
            assertThat(call.toolCallId()).isEqualTo("call-customer-1");
            assertThat(call.name()).isEqualTo("customer.query");
        });
    }

    @Test
    void shouldSeparateWriteDraftFromConfirmedExecution() {
        AtomicInteger prepared = new AtomicInteger();
        AtomicInteger executed = new AtomicInteger();
        ConfirmableTool tool = new ConfirmableTool() {
            @Override
            public ToolDefinition definition() {
                return writeDefinition();
            }

            @Override
            public AssistantToolResult prepare(AssistantToolExecutionContext context) {
                prepared.incrementAndGet();
                return executed("ACTION_DRAFT", "已生成草稿");
            }

            @Override
            public AssistantToolResult executeConfirmed(AssistantToolExecutionContext context) {
                executed.incrementAndGet();
                return executed("ACTION_DONE", "已提交");
            }
        };
        IdempotentHandler idempotentHandler = mock(IdempotentHandler.class);
        when(idempotentHandler.checkAndSet("idem-1", 1800L)).thenReturn(true);
        AssistantToolRunner runner = runner(List.of(tool), idempotentHandler);

        runner.executePlan(
                requestContext("提交申请"),
                plan(ToolExecutionStatus.APPROVAL_REQUIRED, "workflow.request.prepare", false, "action-1", "idem-1")
        );
        runner.executePlan(
                requestContext("确认提交"),
                plan(ToolExecutionStatus.APPROVED, "workflow.request.prepare", false, "action-1", "idem-1")
        );

        assertThat(prepared).hasValue(1);
        assertThat(executed).hasValue(1);
    }

    @Test
    void shouldNotLeaveFailedWritePreparationAsConfirmableDraft() {
        ConfirmableTool tool = new ConfirmableTool() {
            @Override
            public ToolDefinition definition() {
                return writeDefinition();
            }

            @Override
            public AssistantToolResult prepare(AssistantToolExecutionContext context) {
                return AssistantToolResult.failed(definition().name(), "没有找到可审批的流程任务");
            }

            @Override
            public AssistantToolResult executeConfirmed(AssistantToolExecutionContext context) {
                throw new AssertionError("失败草稿不能进入确认执行");
            }
        };

        AssistantToolRunResult outcome = runner(List.of(tool), idempotentHandler()).executePlan(
                requestContext("审批第一个"),
                plan(ToolExecutionStatus.APPROVAL_REQUIRED, "workflow.request.prepare", false, "action-1", "idem-1")
        );

        assertThat(outcome.result().status()).isEqualTo(AssistantToolRunStatus.FAILED);
        assertThat(outcome.plan().status()).isEqualTo(ToolExecutionStatus.BLOCKED);
        assertThat(outcome.plan().steps()).allMatch(step -> step.status() ==
                com.laker.admin.module.assistant.tool.execution.ToolExecutionStepStatus.FAILED);
    }

    @Test
    void shouldPersistCanonicalArgumentsForLaterConfirmation() {
        ToolDefinition definition = new ToolDefinition(
                "workflow.task.approve", "同意流程待办", "准备同意流程待办。",
                "", ToolExecutionMode.REQUIRES_CONFIRMATION, ToolRiskLevel.HIGH,
                List.of(new ToolInputField(
                        "taskIdentifier", "待办标识", ToolSchemaType.STRING, ToolSchemaFormat.NONE,
                        "待办任务号、流程实例号或业务单号", true, List.of()
                )),
                List.of()
        );
        ConfirmableTool tool = new ConfirmableTool() {
            @Override
            public ToolDefinition definition() {
                return definition;
            }

            @Override
            public AssistantToolResult prepare(AssistantToolExecutionContext context) {
                return new AssistantToolResult(
                        definition.name(), AssistantToolRunStatus.EXECUTED,
                        "ACTION_DRAFT", "已生成草稿", Map.of()
                );
            }

            @Override
            public List<AssistantToolArgument> confirmationArguments(AssistantToolExecutionContext context,
                                                                    AssistantToolResult preparedResult) {
                return List.of(new AssistantToolArgument("taskIdentifier", "待办标识", "7001", "7001"));
            }

            @Override
            public AssistantToolResult executeConfirmed(AssistantToolExecutionContext context) {
                throw new AssertionError("草稿阶段不能执行确认动作");
            }
        };
        ToolExecutionPlan draftPlan = new ToolExecutionPlan(
                "run-1", "trace-1", ToolExecutionStatus.APPROVAL_REQUIRED, definition.name(), false,
                List.of(new AssistantToolArgument(
                        "taskIdentifier", "待办标识", "LV-20260713-000002", "LV-20260713-000002"
                )),
                List.of(), List.of(), "test"
        );

        AssistantToolRunResult outcome = runner(List.of(tool), idempotentHandler())
                .executePlan(requestContext("审批业务单 LV-20260713-000002"), draftPlan);

        assertThat(outcome.result().status()).isEqualTo(AssistantToolRunStatus.EXECUTED);
        assertThat(outcome.plan().argumentMap()).containsEntry("taskIdentifier", "7001");
    }

    @Test
    void shouldNotExposeToolRuntimeExceptionToUser() {
        AssistantTool tool = tool(readDefinition(), context -> {
            throw new IllegalStateException("database password must not leak");
        });

        AssistantToolResult result = runner(List.of(tool), idempotentHandler()).executePlan(
                requestContext("查询"),
                plan(ToolExecutionStatus.READY, "customer.query", true, null, null)
        ).result();

        assertThat(result.status()).isEqualTo(AssistantToolRunStatus.FAILED);
        assertThat(result.summary()).doesNotContain("password");
    }

    @Test
    void shouldRejectConfirmedWriteWithoutIdempotencyKey() {
        AtomicInteger executed = new AtomicInteger();
        ConfirmableTool tool = new ConfirmableTool() {
            @Override
            public ToolDefinition definition() {
                return writeDefinition();
            }

            @Override
            public AssistantToolResult prepare(AssistantToolExecutionContext context) {
                return executed("ACTION_DRAFT", "已生成草稿");
            }

            @Override
            public AssistantToolResult executeConfirmed(AssistantToolExecutionContext context) {
                executed.incrementAndGet();
                return executed("ACTION_DONE", "已提交");
            }
        };

        AssistantToolResult result = runner(List.of(tool), idempotentHandler()).executePlan(
                requestContext("确认提交"),
                plan(ToolExecutionStatus.APPROVED, "workflow.request.prepare", false, "action-1", null)
        ).result();

        assertThat(executed).hasValue(0);
        assertThat(result.status()).isEqualTo(AssistantToolRunStatus.FAILED);
        assertThat(result.summary()).contains("幂等标识");
    }

    @Test
    void shouldKeepIdempotencyMarkerWhenOutputValidationFailsAfterConfirmedWrite() {
        AtomicInteger executed = new AtomicInteger();
        ToolDefinition definition = new ToolDefinition(
                "workflow.request.prepare", "准备申请", "生成待确认申请。",
                "", ToolExecutionMode.REQUIRES_CONFIRMATION, ToolRiskLevel.MEDIUM,
                List.of(), List.of(new ToolOutputField("requestNo", ToolSchemaType.STRING, true))
        );
        ConfirmableTool tool = new ConfirmableTool() {
            @Override
            public ToolDefinition definition() {
                return definition;
            }

            @Override
            public AssistantToolResult prepare(AssistantToolExecutionContext context) {
                return executed("ACTION_DRAFT", "已生成草稿");
            }

            @Override
            public AssistantToolResult executeConfirmed(AssistantToolExecutionContext context) {
                executed.incrementAndGet();
                // 模拟业务已经提交，但 Tool 返回合同缺少 requestNo。
                return new AssistantToolResult(
                        definition.name(), AssistantToolRunStatus.EXECUTED,
                        "ACTION_DONE", "业务已提交但返回结构异常", Map.of()
                );
            }
        };
        IdempotentHandler handler = mock(IdempotentHandler.class);
        when(handler.checkAndSet("idem-1", 1800L)).thenReturn(true);

        AssistantToolResult result = runner(List.of(tool), handler).executePlan(
                requestContext("确认提交"),
                plan(ToolExecutionStatus.APPROVED, definition.name(), false, "action-1", "idem-1")
        ).result();

        assertThat(executed).hasValue(1);
        assertThat(result.status()).isEqualTo(AssistantToolRunStatus.FAILED);
        verify(handler, never()).remove("idem-1");
    }

    @Test
    void shouldReportUnknownOutcomeInsteadOfClaimingDuplicateWriteSucceeded() {
        AtomicInteger executed = new AtomicInteger();
        ConfirmableTool tool = new ConfirmableTool() {
            @Override
            public ToolDefinition definition() {
                return writeDefinition();
            }

            @Override
            public AssistantToolResult prepare(AssistantToolExecutionContext context) {
                return executed("ACTION_DRAFT", "已生成草稿");
            }

            @Override
            public AssistantToolResult executeConfirmed(AssistantToolExecutionContext context) {
                executed.incrementAndGet();
                throw new IllegalStateException("模拟提交边界内连接中断");
            }
        };
        IdempotentHandler handler = mock(IdempotentHandler.class);
        when(handler.checkAndSet("idem-1", 1800L)).thenReturn(true, false);
        AssistantToolRunner runner = runner(List.of(tool), handler);
        ToolExecutionPlan confirmedPlan = plan(
                ToolExecutionStatus.APPROVED,
                "workflow.request.prepare",
                false,
                "action-1",
                "idem-1"
        );

        AssistantToolResult firstResult = runner.executePlan(requestContext("确认提交"), confirmedPlan).result();
        AssistantToolResult retryResult = runner.executePlan(requestContext("再次确认"), confirmedPlan).result();

        assertThat(executed).hasValue(1);
        assertThat(firstResult.status()).isEqualTo(AssistantToolRunStatus.FAILED);
        assertThat(retryResult.status()).isEqualTo(AssistantToolRunStatus.INDETERMINATE);
        assertThat(retryResult.summary())
                .contains("无法判断上次是否成功")
                .contains("查询业务状态或人工核对");
    }

    private static AssistantToolRunner runner(List<AssistantTool> tools,
                                              IdempotentHandler idempotentHandler) {
        return runner(tools, idempotentHandler, new AssistantRunTraceRecorder());
    }

    private static AssistantToolRunner runner(List<AssistantTool> tools,
                                              IdempotentHandler idempotentHandler,
                                              AssistantRunTraceRecorder traceRecorder) {
        return new AssistantToolRunner(
                new AssistantToolRegistry(tools),
                idempotentHandler,
                new DefaultToolResultValidator(),
                new DefaultToolExecutionPolicy(),
                traceRecorder
        );
    }

    private static IdempotentHandler idempotentHandler() {
        IdempotentHandler handler = mock(IdempotentHandler.class);
        when(handler.checkAndSet(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
        return handler;
    }

    private static ToolExecutionPlan plan(ToolExecutionStatus decision,
                                      String toolName,
                                      boolean readOnly,
                                      String actionId,
                                      String idempotencyKey) {
        return new ToolExecutionPlan(
                "run-1",
                "trace-1",
                decision,
                toolName,
                readOnly,
                List.of(),
                List.of(),
                List.of(),
                "test",
                actionId,
                idempotencyKey
        );
    }

    private static ToolDefinition readDefinition() {
        return new ToolDefinition(
                "customer.query", "查询客户", "查询当前用户可见客户。",
                "", ToolExecutionMode.READ_ONLY, ToolRiskLevel.LOW,
                List.of(), List.of(new ToolOutputField("count", ToolSchemaType.INTEGER, true))
        );
    }

    private static ToolDefinition writeDefinition() {
        return new ToolDefinition(
                "workflow.request.prepare", "准备申请", "生成待确认申请。",
                "", ToolExecutionMode.REQUIRES_CONFIRMATION, ToolRiskLevel.MEDIUM,
                List.of(), List.of()
        );
    }

    private static AssistantTool tool(ToolDefinition definition,
                                      java.util.function.Function<AssistantToolExecutionContext, AssistantToolResult> executor) {
        return new AssistantTool() {
            @Override
            public ToolDefinition definition() {
                return definition;
            }

            @Override
            public AssistantToolResult execute(AssistantToolExecutionContext context) {
                return executor.apply(context);
            }
        };
    }

    private static AssistantToolResult executed(String artifactType, String summary) {
        return new AssistantToolResult(
                "workflow.request.prepare",
                AssistantToolRunStatus.EXECUTED,
                artifactType,
                summary,
                Map.of()
        );
    }
}
