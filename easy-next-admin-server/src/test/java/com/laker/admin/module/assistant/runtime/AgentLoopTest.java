package com.laker.admin.module.assistant.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.infrastructure.idempotency.idempotent.IdempotentHandler;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.module.assistant.config.AssistantModelProperties;
import com.laker.admin.module.assistant.response.ModelMessageBuilder;
import com.laker.admin.module.assistant.runtime.model.ModelCallException;
import com.laker.admin.module.assistant.runtime.model.ModelFinishReason;
import com.laker.admin.module.assistant.runtime.model.ToolCall;
import com.laker.admin.module.assistant.runtime.model.ToolCallingMessage;
import com.laker.admin.module.assistant.runtime.model.ToolCallingMessageRole;
import com.laker.admin.module.assistant.runtime.model.ToolCallingModelClient;
import com.laker.admin.module.assistant.runtime.model.ToolCallingRequest;
import com.laker.admin.module.assistant.runtime.model.ToolCallingResponse;
import com.laker.admin.module.assistant.state.AssistantChatMemory;
import com.laker.admin.module.assistant.state.AssistantConversationState;
import com.laker.admin.module.assistant.state.AssistantPendingAction;
import com.laker.admin.module.assistant.state.AssistantPendingActionStatus;
import com.laker.admin.module.assistant.state.AssistantPendingActionUpdateType;
import com.laker.admin.module.assistant.state.PendingActionArgumentMerger;
import com.laker.admin.module.assistant.tool.AssistantTool;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentResolution;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentResolver;
import com.laker.admin.module.assistant.tool.AssistantToolExecutionContext;
import com.laker.admin.module.assistant.tool.AssistantToolRegistry;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.AssistantToolRunner;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ConfirmableTool;
import com.laker.admin.module.assistant.tool.DefaultAssistantToolArgumentValidator;
import com.laker.admin.module.assistant.tool.DefaultToolExecutionPolicy;
import com.laker.admin.module.assistant.tool.DefaultToolResultValidator;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import com.laker.admin.module.assistant.tool.ToolExecutionMode;
import com.laker.admin.module.assistant.tool.ToolInputField;
import com.laker.admin.module.assistant.tool.ToolOutputField;
import com.laker.admin.module.assistant.tool.ToolRiskLevel;
import com.laker.admin.module.assistant.tool.ToolSchemaFormat;
import com.laker.admin.module.assistant.tool.ToolSchemaType;
import com.laker.admin.module.assistant.tool.execution.DefaultToolExecutionPlanFactory;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import com.laker.admin.module.assistant.trace.AssistantRunTraceRecorder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.laker.admin.module.assistant.AssistantTestFixtures.requestContext;
import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopTest {

    @Test
    void shouldAnswerSimpleQuestionWithOneModelCall() {
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse("4", List.of(), "stop")
        ));
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of());

        AgentResult result = agentLoop(model, registry).run(
                requestContext("2+2 等于多少"),
                AssistantConversationState.empty(),
                List.of()
        );

        assertThat(result.reply()).isEqualTo("4");
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.FINAL_RESPONSE);
        assertThat(result.modelCallCount()).isEqualTo(1);
        assertThat(result.toolCallCount()).isZero();
        assertThat(model.requests()).hasSize(1);
        assertThat(model.requests().get(0).operationName()).isEqualTo("agent_step");
        assertThat(model.requests().get(0).messages().get(0).content())
                .contains("普通常识和简单计算直接回答")
                .doesNotContain("输出守卫", "关键词分类");
    }

    @Test
    void readToolFeedsObservationBackToTheSameModel() {
        AtomicInteger executions = new AtomicInteger();
        AssistantTool readTool = tool(
                definition("workflow.task.list", "查询当前用户待办", List.of()),
                context -> {
                    executions.incrementAndGet();
                    return new AssistantToolResult(
                            "workflow.task.list",
                            AssistantToolRunStatus.EXECUTED,
                            "TASK_LIST",
                            "找到 2 个待办",
                            Map.of("total", 2)
                    );
                }
        );
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of(readTool));
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse(null, List.of(
                        new ToolCall("call_1", "workflow.task.list", Map.of())
                ), "tool_calls"),
                new ToolCallingResponse("你当前有 2 个待办。", List.of(), "stop")
        ));

        AgentResult result = agentLoop(model, registry).run(
                requestContext("我有几个待办"),
                AssistantConversationState.empty(),
                List.of(AssistantVisibleTool.from(readTool.definition()))
        );

        assertThat(result.reply()).isEqualTo("你当前有 2 个待办。");
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.FINAL_RESPONSE);
        assertThat(result.modelCallCount()).isEqualTo(2);
        assertThat(result.toolCallCount()).isEqualTo(1);
        assertThat(executions).hasValue(1);
        assertThat(model.requests().get(1).messages()).anySatisfy(message -> {
            assertThat(message.role()).isEqualTo(ToolCallingMessageRole.TOOL);
            assertThat(message.content())
                    .contains("\"status\":\"EXECUTED\"", "\"complete\":true", "\"total\":2")
                    .doesNotContain("找到 2 个待办", "artifactType", "planStatus");
        });
        assertThat(model.requests().get(1).tools())
                .as("只读 Tool round 后模型仍可基于 observation 继续规划")
                .extracting(ToolDefinition::name)
                .containsExactly("workflow.task.list");
        assertThat(model.requests().get(1).operationName()).isEqualTo("agent_step");
        assertThat(model.requests().get(1).messages())
                .extracting(ToolCallingMessage::role)
                .endsWith(ToolCallingMessageRole.ASSISTANT, ToolCallingMessageRole.TOOL);
        assertThat(model.requests().get(1).messages().get(3).toolCalls())
                .singleElement()
                .satisfies(call -> assertThat(call.id()).isEqualTo("call_1"));
        assertThat(model.requests().get(1).messages().get(4).toolCallId()).isEqualTo("call_1");
    }

    @Test
    void dependentReadOnlyToolsCanUsePreviousObservationBeforeFinalResponse() {
        AtomicInteger listExecutions = new AtomicInteger();
        AtomicInteger detailExecutions = new AtomicInteger();
        AssistantTool listTool = tool(
                definition("test.item.list", "查询项目列表", List.of()),
                context -> {
                    listExecutions.incrementAndGet();
                    return new AssistantToolResult(
                            "test.item.list",
                            AssistantToolRunStatus.EXECUTED,
                            "ITEM_LIST",
                            "找到第一个项目",
                            Map.of("items", List.of(Map.of("id", "42")))
                    );
                }
        );
        AssistantTool detailTool = tool(
                definition("test.item.detail", "查询项目详情", List.of(new ToolInputField(
                        "value", "项目 ID", ToolSchemaType.STRING, ToolSchemaFormat.NONE,
                        "列表 observation 中的项目 ID", true, List.of()
                ))),
                context -> {
                    detailExecutions.incrementAndGet();
                    return new AssistantToolResult(
                            "test.item.detail",
                            AssistantToolRunStatus.EXECUTED,
                            "ITEM_DETAIL",
                            "项目 42 详情",
                            Map.of("id", context.plan().argumentMap().get("value"), "name", "示例项目")
                    );
                }
        );
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of(listTool, detailTool));
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse(null, List.of(
                        new ToolCall("list", listTool.definition().name(), Map.of())
                ), "tool_calls"),
                new ToolCallingResponse(null, List.of(
                        new ToolCall("detail", detailTool.definition().name(), Map.of("value", "42"))
                ), "tool_calls"),
                new ToolCallingResponse("第一个项目是示例项目。", List.of(), "stop")
        ));

        AgentResult result = agentLoop(model, registry, 6, 6, 2, false).run(
                requestContext("先查询列表，再查看第一个项目详情"),
                AssistantConversationState.empty(),
                List.of(
                        AssistantVisibleTool.from(listTool.definition()),
                        AssistantVisibleTool.from(detailTool.definition())
                )
        );

        assertThat(result.reply()).isEqualTo("第一个项目是示例项目。");
        assertThat(result.executedToolNames())
                .containsExactly("test.item.list", "test.item.detail");
        assertThat(listExecutions).hasValue(1);
        assertThat(detailExecutions).hasValue(1);
        assertThat(model.requests()).hasSize(3);
        assertThat(model.requests().get(1).tools()).isNotEmpty();
        assertThat(model.requests().get(2).tools()).isEmpty();
        assertThat(model.requests().get(2).operationName()).isEqualTo("final_response");
    }

    @Test
    void readRoundLimitAllowsOneWriteDraftHandoffWithoutFurtherReads() {
        AtomicInteger prepared = new AtomicInteger();
        AtomicInteger confirmed = new AtomicInteger();
        AssistantTool firstRead = tool(
                definition("test.item.list", "查询项目列表", List.of()),
                context -> new AssistantToolResult(
                        "test.item.list",
                        AssistantToolRunStatus.EXECUTED,
                        "ITEM_LIST",
                        "找到两个项目",
                        Map.of("items", List.of(Map.of("id", "1"), Map.of("id", "2")))
                )
        );
        AssistantTool secondRead = tool(
                definition("test.item.detail", "查询第二个项目详情", List.of()),
                context -> new AssistantToolResult(
                        "test.item.detail",
                        AssistantToolRunStatus.EXECUTED,
                        "ITEM_DETAIL",
                        "第二个项目可作为申请参考",
                        Map.of("date", "2026-07-18", "reason", "客户支持")
                )
        );
        ConfirmableTool writeTool = confirmableTool(prepared, confirmed);
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of(firstRead, secondRead, writeTool));
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse(null, List.of(new ToolCall(
                        "list", firstRead.definition().name(), Map.of()
                )), "tool_calls"),
                new ToolCallingResponse(null, List.of(new ToolCall(
                        "detail", secondRead.definition().name(), Map.of()
                )), "tool_calls"),
                new ToolCallingResponse(null, List.of(new ToolCall(
                        "draft",
                        writeTool.definition().name(),
                        Map.of("date", "2026-07-18", "reason", "客户支持")
                )), "tool_calls")
        ));
        List<AssistantVisibleTool> visibleTools = List.of(
                AssistantVisibleTool.from(firstRead.definition()),
                AssistantVisibleTool.from(secondRead.definition()),
                AssistantVisibleTool.from(writeTool.definition())
        );

        AgentResult result = agentLoop(model, registry, 6, 6, 2, false).run(
                requestContext("查询两个项目，参考第二个项目新建申请"),
                AssistantConversationState.empty(),
                visibleTools
        );

        assertThat(result.plan().status()).isEqualTo(ToolExecutionStatus.APPROVAL_REQUIRED);
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.WAITING_FOR_CONFIRMATION);
        assertThat(result.executedToolNames())
                .containsExactly("test.item.list", "test.item.detail", "workflow.request.prepare");
        assertThat(prepared).hasValue(1);
        assertThat(confirmed).hasValue(0);
        assertThat(model.requests()).hasSize(3);
        assertThat(model.requests().get(2).operationName()).isEqualTo("agent_step");
        assertThat(model.requests().get(2).tools())
                .extracting(ToolDefinition::name)
                .containsExactly("workflow.request.prepare");
    }

    @Test
    void emptyCompletedResponseIsRejectedInsteadOfMarkedAsDirectAnswer() {
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse(" ", List.of(), "stop")
        ));

        AgentResult result = agentLoop(model, new AssistantToolRegistry(List.of())).run(
                requestContext("你好"),
                AssistantConversationState.empty(),
                List.of()
        );

        assertThat(result.plan().status()).isEqualTo(ToolExecutionStatus.BLOCKED);
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.MODEL_PROTOCOL_ERROR);
        assertThat(result.plan().reason()).isEqualTo("模型返回了不一致的结束状态");
        assertThat(result.reply()).contains("未能可靠完成");
    }

    @Test
    void toolCallsFinishReasonWithoutAnyCallIsRejected() {
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse("我准备调用工具", List.of(), "tool_calls")
        ));

        AgentResult result = agentLoop(model, new AssistantToolRegistry(List.of())).run(
                requestContext("查询待办"),
                AssistantConversationState.empty(),
                List.of()
        );

        assertThat(result.plan().status()).isEqualTo(ToolExecutionStatus.BLOCKED);
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.MODEL_PROTOCOL_ERROR);
        assertThat(result.plan().reason()).isEqualTo("模型返回了不一致的结束状态");
        assertThat(result.toolCallCount()).isZero();
    }

    @Test
    void truncatedToolCallIsNeverExecuted() {
        AssistantTool readTool = echoTool();
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of(readTool));
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse(
                        null,
                        List.of(new ToolCall("call_1", readTool.definition().name(), Map.of("value", "42"))),
                        ModelFinishReason.MAX_TOKENS,
                        "length"
                )
        ));

        AgentResult result = agentLoop(model, registry).run(
                requestContext("查询值 42"),
                AssistantConversationState.empty(),
                List.of(AssistantVisibleTool.from(readTool.definition()))
        );

        assertThat(result.plan().status()).isEqualTo(ToolExecutionStatus.BLOCKED);
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.MODEL_MAX_TOKENS);
        assertThat(result.toolCallCount()).isZero();
        assertThat(result.executedToolNames()).isEmpty();
    }

    @Test
    void malformedArgumentsBecomeAnObservationAndCanBeRepaired() {
        AssistantTool readTool = echoTool();
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of(readTool));
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse(null, List.of(
                        new ToolCall("bad", readTool.definition().name(), Map.of(), "arguments is not an object")
                ), "tool_calls"),
                new ToolCallingResponse(null, List.of(
                        new ToolCall("fixed", readTool.definition().name(), Map.of("value", "42"))
                ), "tool_calls"),
                new ToolCallingResponse("42", List.of(), "stop")
        ));

        AgentResult result = agentLoop(model, registry).run(
                requestContext("查询值 42"),
                AssistantConversationState.empty(),
                List.of(AssistantVisibleTool.from(readTool.definition()))
        );

        assertThat(result.reply()).isEqualTo("42");
        assertThat(result.modelCallCount()).isEqualTo(3);
        assertThat(result.executedToolNames()).containsExactly(readTool.definition().name());
        assertThat(result.toolResult().status()).isEqualTo(AssistantToolRunStatus.EXECUTED);
    }

    @Test
    void duplicateToolCallStopsWithAnExplicitReasonWithoutReplayingTheTool() {
        AtomicInteger executions = new AtomicInteger();
        AssistantTool readTool = tool(echoTool().definition(), context -> {
            executions.incrementAndGet();
            return new AssistantToolResult(
                    "test.echo",
                    AssistantToolRunStatus.EXECUTED,
                    "TEST_VALUE",
                    "值为 42",
                    Map.of("value", "42")
            );
        });
        ToolCall duplicated = new ToolCall("first", readTool.definition().name(), Map.of("value", "42"));
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse(null, List.of(
                        duplicated,
                        new ToolCall("second", duplicated.name(), duplicated.arguments())
                ), "tool_calls"),
                new ToolCallingResponse("已使用第一次查询结果。", List.of(), "stop")
        ));

        AgentResult result = agentLoop(model, new AssistantToolRegistry(List.of(readTool))).run(
                requestContext("查询值 42"),
                AssistantConversationState.empty(),
                List.of(AssistantVisibleTool.from(readTool.definition()))
        );

        assertThat(executions).hasValue(1);
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.DUPLICATE_TOOL_CALL);
        assertThat(result.reply()).isEqualTo("已使用第一次查询结果。");
    }

    @Test
    void toolCallBudgetStopsWithAnExplicitReason() {
        AssistantTool readTool = echoTool();
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse(null, List.of(
                        new ToolCall("first", readTool.definition().name(), Map.of("value", "41")),
                        new ToolCall("second", readTool.definition().name(), Map.of("value", "42"))
                ), "tool_calls"),
                new ToolCallingResponse("只保留预算内的第一次查询。", List.of(), "stop")
        ));

        AgentResult result = agentLoop(
                model,
                new AssistantToolRegistry(List.of(readTool)),
                6,
                1
        ).run(
                requestContext("查询两个值"),
                AssistantConversationState.empty(),
                List.of(AssistantVisibleTool.from(readTool.definition()))
        );

        assertThat(result.toolCallCount()).isEqualTo(1);
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.TOOL_CALL_LIMIT);
    }

    @Test
    void modelCallBudgetStopsWithAnExplicitReason() {
        AssistantTool readTool = echoTool();
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse(null, List.of(
                        new ToolCall("bad-1", readTool.definition().name(), Map.of(), "invalid json")
                ), "tool_calls"),
                new ToolCallingResponse(null, List.of(
                        new ToolCall("bad-2", readTool.definition().name(), Map.of(), "invalid json")
                ), "tool_calls")
        ));

        AgentResult result = agentLoop(
                model,
                new AssistantToolRegistry(List.of(readTool)),
                2,
                6
        ).run(
                requestContext("查询值"),
                AssistantConversationState.empty(),
                List.of(AssistantVisibleTool.from(readTool.definition()))
        );

        assertThat(result.modelCallCount()).isEqualTo(2);
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.MODEL_CALL_LIMIT);
    }

    @Test
    void writeRequestStopsAtServerSavedDraft() {
        AtomicInteger prepared = new AtomicInteger();
        AtomicInteger confirmed = new AtomicInteger();
        ConfirmableTool writeTool = confirmableTool(prepared, confirmed);
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of(writeTool));
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse(null, List.of(new ToolCall(
                        "draft",
                        writeTool.definition().name(),
                        Map.of("date", "2026-07-18", "reason", "客户支持")
                )), "tool_calls")
        ));

        AgentResult result = agentLoop(model, registry).run(
                requestContext("帮我申请 7 月 18 日请假，原因客户支持"),
                AssistantConversationState.empty(),
                List.of(AssistantVisibleTool.from(writeTool.definition()))
        );

        assertThat(result.modelCallCount()).isEqualTo(1);
        assertThat(result.plan().status()).isEqualTo(ToolExecutionStatus.APPROVAL_REQUIRED);
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.WAITING_FOR_CONFIRMATION);
        assertThat(result.pendingActionUpdate().type()).isEqualTo(AssistantPendingActionUpdateType.UPSERT);
        assertThat(result.reply()).contains("草稿", "确认");
        assertThat(prepared).hasValue(1);
        assertThat(confirmed).hasValue(0);
    }

    @Test
    void missingToolInputHasAnExplicitWaitingTermination() {
        AssistantTool readTool = echoTool();
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of(readTool));
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse(null, List.of(
                        new ToolCall("missing", readTool.definition().name(), Map.of())
                ), "tool_calls")
        ));

        AgentResult result = agentLoop(model, registry).run(
                requestContext("查询一个值"),
                AssistantConversationState.empty(),
                List.of(AssistantVisibleTool.from(readTool.definition()))
        );

        assertThat(result.plan().status()).isEqualTo(ToolExecutionStatus.INPUT_REQUIRED);
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.WAITING_FOR_INPUT);
        assertThat(result.reply()).contains("还需要补充", "值");
    }

    @Test
    void confirmationExecutesOnlyTheSavedSnapshot() {
        AtomicInteger prepared = new AtomicInteger();
        AtomicInteger confirmed = new AtomicInteger();
        ConfirmableTool writeTool = confirmableTool(prepared, confirmed);
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of(writeTool));
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse(null, List.of(new ToolCall(
                        "confirm", ToolSetBuilder.CONFIRM_PENDING_ACTION, Map.of()
                )), "tool_calls"),
                new ToolCallingResponse("申请已提交。", List.of(), "stop")
        ));

        AgentResult result = agentLoop(model, registry).run(
                requestContext("确认提交"),
                state(completePendingAction()),
                List.of(AssistantVisibleTool.from(writeTool.definition()))
        );

        assertThat(result.reply()).isEqualTo("申请已提交。");
        assertThat(result.modelCallCount()).isEqualTo(2);
        assertThat(result.pendingActionUpdate().type()).isEqualTo(AssistantPendingActionUpdateType.CLEAR);
        assertThat(prepared).hasValue(0);
        assertThat(confirmed).hasValue(1);
    }

    @Test
    void finalResponseRetryNeverReplaysAConfirmedWriteTool() {
        AtomicInteger prepared = new AtomicInteger();
        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger modelCalls = new AtomicInteger();
        List<ToolCallingRequest> requests = new ArrayList<>();
        ConfirmableTool writeTool = confirmableTool(prepared, confirmed);
        AssistantToolRegistry registry = new AssistantToolRegistry(List.of(writeTool));
        ToolCallingModelClient model = request -> {
            requests.add(request);
            return switch (modelCalls.incrementAndGet()) {
                case 1 -> new ToolCallingResponse(null, List.of(new ToolCall(
                        "confirm", ToolSetBuilder.CONFIRM_PENDING_ACTION, Map.of()
                )), "tool_calls");
                case 2 -> throw ModelCallException.serverError("temporary final response failure");
                default -> new ToolCallingResponse("申请已提交。", List.of(), "stop");
            };
        };

        AgentResult result = agentLoop(model, registry).run(
                requestContext("确认提交"),
                state(completePendingAction()),
                List.of(AssistantVisibleTool.from(writeTool.definition()))
        );

        assertThat(result.reply()).isEqualTo("申请已提交。");
        assertThat(confirmed).hasValue(1);
        assertThat(prepared).hasValue(0);
        assertThat(modelCalls).hasValue(3);
        assertThat(requests.get(1).tools()).isEmpty();
        assertThat(requests.get(2).tools()).isEmpty();
        assertThat(requests.get(1).messages()).isEqualTo(requests.get(2).messages());
    }

    @Test
    void modelRefusalIsNotRetried() {
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse(
                        "",
                        List.of(),
                        ModelFinishReason.REFUSAL,
                        "refusal"
                )
        ));

        AgentResult result = agentLoop(model, new AssistantToolRegistry(List.of())).run(
                requestContext("你好"),
                AssistantConversationState.empty(),
                List.of()
        );

        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.MODEL_REFUSED);
        assertThat(result.modelCallCount()).isEqualTo(1);
        assertThat(model.requests()).hasSize(1);
    }

    @Test
    void retryableGatewayFailureRetriesAtMostOnce() {
        AtomicInteger attempts = new AtomicInteger();
        ToolCallingModelClient model = request -> {
            if (attempts.getAndIncrement() == 0) {
                throw ModelCallException.serverError("temporary gateway failure");
            }
            return new ToolCallingResponse("恢复成功", List.of(), "stop");
        };

        AgentResult result = agentLoop(model, new AssistantToolRegistry(List.of())).run(
                requestContext("你好"),
                AssistantConversationState.empty(),
                List.of()
        );

        assertThat(result.reply()).isEqualTo("恢复成功");
        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.FINAL_RESPONSE);
        assertThat(result.modelCallCount()).isEqualTo(2);
        assertThat(attempts).hasValue(2);
    }

    @Test
    void unknownRuntimeFailureIsNotRetried() {
        AtomicInteger attempts = new AtomicInteger();
        ToolCallingModelClient model = request -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("adapter bug");
        };

        AgentResult result = agentLoop(model, new AssistantToolRegistry(List.of())).run(
                requestContext("你好"),
                AssistantConversationState.empty(),
                List.of()
        );

        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.MODEL_ERROR);
        assertThat(result.modelCallCount()).isEqualTo(1);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void protocolFailureIsNotRetriedAndKeepsItsTerminationReason() {
        AtomicInteger attempts = new AtomicInteger();
        ToolCallingModelClient model = request -> {
            attempts.incrementAndGet();
            throw ModelCallException.protocol("invalid provider payload");
        };

        AgentResult result = agentLoop(model, new AssistantToolRegistry(List.of())).run(
                requestContext("你好"),
                AssistantConversationState.empty(),
                List.of()
        );

        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.MODEL_PROTOCOL_ERROR);
        assertThat(result.modelCallCount()).isEqualTo(1);
        assertThat(attempts).hasValue(1);
    }

    @Test
    void structuredOutputIsUnwrappedWithoutChangingTheUserReplyContract() {
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse(
                        "{\"reply\":\"结构化回答\"}",
                        List.of(),
                        ModelFinishReason.STOP,
                        "stop",
                        Map.of("reply", "结构化回答")
                )
        ));

        AgentResult result = agentLoop(
                model,
                new AssistantToolRegistry(List.of()),
                6,
                6,
                3,
                true
        ).run(requestContext("你好"), AssistantConversationState.empty(), List.of());

        assertThat(result.reply()).isEqualTo("结构化回答");
        assertThat(model.requests()).singleElement().satisfies(request ->
                assertThat(request.outputSchema()).isNotNull()
        );
    }

    @Test
    void structuredOutputWithUnexpectedFieldsFailsAsProtocolErrorWithoutRetry() {
        RecordingModelClient model = new RecordingModelClient(List.of(
                new ToolCallingResponse(
                        "{\"answer\":\"错误字段\"}",
                        List.of(),
                        ModelFinishReason.STOP,
                        "stop",
                        Map.of("answer", "错误字段")
                )
        ));

        AgentResult result = agentLoop(
                model,
                new AssistantToolRegistry(List.of()),
                6,
                6,
                3,
                true
        ).run(requestContext("你好"), AssistantConversationState.empty(), List.of());

        assertThat(result.terminationReason()).isEqualTo(AgentTerminationReason.MODEL_PROTOCOL_ERROR);
        assertThat(result.modelCallCount()).isEqualTo(1);
        assertThat(model.requests()).hasSize(1);
    }

    private static AgentLoop agentLoop(ToolCallingModelClient model,
                                       AssistantToolRegistry registry) {
        return agentLoop(model, registry, 6, 6, 3, false);
    }

    private static AgentLoop agentLoop(ToolCallingModelClient model,
                                       AssistantToolRegistry registry,
                                       int maxModelCalls,
                                       int maxToolCalls) {
        return agentLoop(model, registry, maxModelCalls, maxToolCalls, 3, false);
    }

    private static AgentLoop agentLoop(ToolCallingModelClient model,
                                       AssistantToolRegistry registry,
                                       int maxModelCalls,
                                       int maxToolCalls,
                                       int maxReadToolRounds,
                                       boolean structuredOutputEnabled) {
        EasyJsonCodec jsonCodec = new EasyJsonCodec(new ObjectMapper());
        AssistantToolRunner toolRunner = new AssistantToolRunner(
                registry,
                alwaysAvailableIdempotentHandler(),
                new DefaultToolResultValidator(),
                new DefaultToolExecutionPolicy(),
                new AssistantRunTraceRecorder()
        );
        DefaultAssistantToolArgumentValidator argumentValidator =
                new DefaultAssistantToolArgumentValidator(List.of());
        DefaultToolExecutionPlanFactory planFactory = new DefaultToolExecutionPlanFactory();
        BusinessToolCallHandler businessToolCallHandler = new BusinessToolCallHandler(
                registry,
                nativeArgumentResolver(),
                argumentValidator,
                planFactory,
                toolRunner,
                new PendingActionArgumentMerger()
        );
        ToolCallProcessor toolCallProcessor = new ToolCallProcessor(
                businessToolCallHandler,
                new PendingActionHandler(registry, argumentValidator, planFactory, toolRunner),
                jsonCodec,
                new ToolObservationFormatter(jsonCodec)
        );
        AssistantModelProperties properties = new AssistantModelProperties();
        properties.setMaxModelCalls(maxModelCalls);
        properties.setMaxToolCalls(maxToolCalls);
        properties.setMaxReadToolRounds(maxReadToolRounds);
        properties.setStructuredOutputEnabled(structuredOutputEnabled);
        return new AgentLoop(
                model,
                properties,
                new ModelMessageBuilder(),
                new ToolSetBuilder(registry),
                toolCallProcessor
        );
    }

    private static AssistantToolArgumentResolver nativeArgumentResolver() {
        return (context, tool, arguments, conversationState) -> {
            Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
            List<AssistantToolArgument> resolved = tool.inputSchema().stream()
                    .filter(field -> safeArguments.get(field.name()) != null)
                    .map(field -> new AssistantToolArgument(
                            field.name(),
                            field.displayName(),
                            String.valueOf(safeArguments.get(field.name())),
                            String.valueOf(safeArguments.get(field.name()))
                    ))
                    .toList();
            List<String> keys = resolved.stream().map(AssistantToolArgument::key).toList();
            return new AssistantToolArgumentResolution(
                    tool.name(),
                    resolved,
                    tool.inputSchema().stream()
                            .filter(field -> field.required() && !keys.contains(field.name()))
                            .toList()
            );
        };
    }

    private static IdempotentHandler alwaysAvailableIdempotentHandler() {
        return new IdempotentHandler() {
            @Override
            public boolean checkAndSet(String key, long expireTime) {
                return true;
            }

            @Override
            public void remove(String key) {
                // Confirmed actions are fail-closed; the runner never removes this key.
            }
        };
    }

    private static ToolDefinition definition(String name,
                                             String description,
                                             List<ToolInputField> inputSchema) {
        return new ToolDefinition(
                name,
                description,
                description,
                "",
                ToolExecutionMode.READ_ONLY,
                ToolRiskLevel.LOW,
                inputSchema,
                List.of()
        );
    }

    private static AssistantTool tool(
            ToolDefinition definition,
            java.util.function.Function<AssistantToolExecutionContext, AssistantToolResult> execution) {
        return new AssistantTool() {
            @Override
            public ToolDefinition definition() {
                return definition;
            }

            @Override
            public AssistantToolResult execute(AssistantToolExecutionContext context) {
                return execution.apply(context);
            }
        };
    }

    private static AssistantTool echoTool() {
        ToolDefinition definition = definition(
                "test.echo",
                "回显模型明确给出的测试值",
                List.of(new ToolInputField(
                        "value", "值", ToolSchemaType.STRING, ToolSchemaFormat.NONE,
                        "需要回显的值", true, List.of()
                ))
        );
        return tool(definition, context -> new AssistantToolResult(
                definition.name(),
                AssistantToolRunStatus.EXECUTED,
                "TEST_VALUE",
                "值为 " + context.plan().argumentMap().get("value"),
                Map.of("value", context.plan().argumentMap().get("value"))
        ));
    }

    private static ConfirmableTool confirmableTool(AtomicInteger prepared, AtomicInteger confirmed) {
        ToolDefinition definition = new ToolDefinition(
                "workflow.request.prepare",
                "准备企业申请",
                "按日期和原因生成待确认申请草稿。",
                "",
                ToolExecutionMode.REQUIRES_CONFIRMATION,
                ToolRiskLevel.MEDIUM,
                List.of(
                        new ToolInputField("date", "日期", ToolSchemaType.STRING, ToolSchemaFormat.DATE,
                                "申请日期", true, List.of()),
                        new ToolInputField("reason", "原因", ToolSchemaType.STRING, ToolSchemaFormat.NONE,
                                "申请原因", true, List.of())
                ),
                List.of(new ToolOutputField("requestNo", ToolSchemaType.STRING, true))
        );
        return new ConfirmableTool() {
            @Override
            public ToolDefinition definition() {
                return definition;
            }

            @Override
            public AssistantToolResult prepare(AssistantToolExecutionContext context) {
                prepared.incrementAndGet();
                return new AssistantToolResult(
                        definition.name(),
                        AssistantToolRunStatus.EXECUTED,
                        "ACTION_DRAFT",
                        "已生成申请草稿",
                        Map.of()
                );
            }

            @Override
            public AssistantToolResult executeConfirmed(AssistantToolExecutionContext context) {
                confirmed.incrementAndGet();
                return new AssistantToolResult(
                        definition.name(),
                        AssistantToolRunStatus.EXECUTED,
                        "ACTION_SUBMITTED",
                        "已提交申请 REQ-1001",
                        Map.of("requestNo", "REQ-1001")
                );
            }
        };
    }

    private static AssistantPendingAction completePendingAction() {
        return new AssistantPendingAction(
                "source-run",
                "source-trace",
                "workflow.request.prepare",
                "帮我申请请假",
                false,
                AssistantPendingActionStatus.WAITING_FOR_CONFIRMATION,
                "action-1001",
                "idem-1001",
                List.of(
                        new AssistantToolArgument("date", "日期", "2026-07-18", "2026-07-18"),
                        new AssistantToolArgument("reason", "原因", "客户支持", "客户支持")
                ),
                List.of(),
                Instant.parse("2026-07-16T02:00:00Z")
        );
    }

    private static AssistantConversationState state(AssistantPendingAction action) {
        return new AssistantConversationState(0, AssistantChatMemory.empty(), action, null, null);
    }

    private static final class RecordingModelClient implements ToolCallingModelClient {
        private final List<ToolCallingResponse> responses;
        private final List<ToolCallingRequest> requests = new ArrayList<>();

        private RecordingModelClient(List<ToolCallingResponse> responses) {
            this.responses = List.copyOf(responses);
        }

        @Override
        public ToolCallingResponse call(ToolCallingRequest request) {
            requests.add(request);
            return responses.get(requests.size() - 1);
        }

        private List<ToolCallingRequest> requests() {
            return List.copyOf(requests);
        }
    }
}
