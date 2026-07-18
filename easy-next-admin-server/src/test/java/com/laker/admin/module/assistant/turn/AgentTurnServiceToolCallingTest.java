package com.laker.admin.module.assistant.turn;

import com.laker.admin.module.assistant.context.AssistantContextBuilder;
import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.dto.AssistantEntryResponse;
import com.laker.admin.module.assistant.dto.AssistantMessageRequest;
import com.laker.admin.module.assistant.dto.AssistantAgentLifecycleEvent;
import com.laker.admin.module.assistant.dto.AssistantAgentLifecycleEventType;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.runtime.AgentLoop;
import com.laker.admin.module.assistant.runtime.AgentLoopListener;
import com.laker.admin.module.assistant.runtime.ToolSetBuilder;
import com.laker.admin.module.assistant.runtime.AgentResult;
import com.laker.admin.module.assistant.runtime.AgentTerminationReason;
import com.laker.admin.module.assistant.state.AssistantChatMemory;
import com.laker.admin.module.assistant.state.AssistantConversationState;
import com.laker.admin.module.assistant.state.AssistantConversationStateStore;
import com.laker.admin.module.assistant.state.AssistantConversationStateWriter;
import com.laker.admin.module.assistant.state.AssistantConversationTurnCoordinator;
import com.laker.admin.module.assistant.state.AssistantPendingAction;
import com.laker.admin.module.assistant.state.AssistantPendingActionUpdate;
import com.laker.admin.module.assistant.state.AssistantPendingActionStatus;
import com.laker.admin.module.assistant.tool.AssistantToolArgument;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentResolution;
import com.laker.admin.module.assistant.tool.AssistantToolSelection;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.AssistantToolVisibilityPlan;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import com.laker.admin.module.assistant.tool.ToolExecutionMode;
import com.laker.admin.module.assistant.tool.ToolInputField;
import com.laker.admin.module.assistant.tool.ToolRiskLevel;
import com.laker.admin.module.assistant.tool.ToolSchemaFormat;
import com.laker.admin.module.assistant.tool.ToolSchemaType;
import com.laker.admin.module.assistant.tool.ToolVisibilityPolicy;
import com.laker.admin.module.assistant.trace.AssistantRunTraceRecorder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.laker.admin.module.assistant.AssistantTestFixtures.requestContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentTurnServiceToolCallingTest {

    @Test
    void shouldReleaseTurnLeaseWhenObserverFailsImmediatelyAfterAcquire() {
        AssistantRequestContext context = requestContext("查询待办");
        AssistantContextBuilder contextBuilder = mock(AssistantContextBuilder.class);
        when(contextBuilder.build(any(), any(), any())).thenReturn(context);
        AssistantConversationTurnCoordinator coordinator = mock(AssistantConversationTurnCoordinator.class);
        AssistantConversationTurnCoordinator.TurnLease turnLease = mock(
                AssistantConversationTurnCoordinator.TurnLease.class
        );
        when(coordinator.acquire(context)).thenReturn(turnLease);
        AgentTurnService agentTurnService = new AgentTurnService(
                contextBuilder,
                mock(ToolVisibilityPolicy.class),
                mock(AgentLoop.class),
                new AssistantRunTraceRecorder(),
                mock(AssistantConversationStateStore.class),
                mock(AssistantConversationStateWriter.class),
                coordinator
        );

        assertThatThrownBy(() -> agentTurnService.run(
                new AssistantMessageRequest(context.normalizedMessage(), context.conversationId()),
                event -> {
                    if (event.event() == AssistantAgentLifecycleEventType.CONVERSATION_TURN_ACQUIRED) {
                        throw new AssertionError("模拟租约建立后的非普通观察者故障");
                    }
                }
        )).isInstanceOf(AssertionError.class);

        verify(turnLease).close();
    }

    @Test
    void shouldEnterAgentLoopAndKeepSyncAndSseTrace() {
        AssistantRequestContext context = requestContext("查询我的待办");
        AssistantConversationState state = AssistantConversationState.empty();
        ToolDefinition definition = new ToolDefinition(
                "workflow.task.list_my_pending", "查询我的待办", "查询当前用户待办",
                "assistant:chat", ToolExecutionMode.READ_ONLY, ToolRiskLevel.LOW,
                List.of(),
                List.of()
        );
        AssistantVisibleTool visibleTool = AssistantVisibleTool.from(definition);
        AssistantToolVisibilityPlan visibility = new AssistantToolVisibilityPlan(
                List.of(definition.name()), List.of(visibleTool), List.of(), "已按权限发现 Tool"
        );
        ToolExecutionPlan plan = new ToolExecutionPlan(
                context.runId(), context.traceId(), ToolExecutionStatus.READY, definition.name(), true,
                List.of(),
                List.of(), List.of(), "原生 Tool Call 已执行"
        );
        AssistantToolResult result = new AssistantToolResult(
                definition.name(), AssistantToolRunStatus.EXECUTED, "TASK_LIST", "找到 1 个待办", Map.of("total", 1)
        );

        AssistantContextBuilder contextBuilder = mock(AssistantContextBuilder.class);
        ToolVisibilityPolicy visibilityPolicy = mock(ToolVisibilityPolicy.class);
        AssistantConversationStateStore stateStore = mock(AssistantConversationStateStore.class);
        AssistantConversationStateWriter stateWriter = mock(AssistantConversationStateWriter.class);
        AgentLoop agentLoop = mock(AgentLoop.class);
        when(contextBuilder.build(any(), any(), any())).thenReturn(context);
        when(stateStore.load(context)).thenReturn(state);
        when(visibilityPolicy.selectVisibleTools(context)).thenReturn(visibility);
        when(agentLoop.run(eq(context), eq(state), eq(visibility.tools()), any(AgentLoopListener.class)))
                .thenAnswer(invocation -> {
                    AgentLoopListener listener = invocation.getArgument(3);
                    AssistantToolSelection selection = AssistantToolSelection.selected(
                            visibleTool,
                            visibility.tools(),
                            "测试模型真实选择"
                    );
                    AssistantToolArgumentResolution resolution = new AssistantToolArgumentResolution(
                            definition.name(), plan.filledArguments(), List.of()
                    );
                    listener.onToolSelected(selection);
                    listener.onArgumentsResolved(resolution);
                    listener.onArgumentsValidated(resolution);
                    listener.onToolFinished(result);
                    return new AgentResult(
                            plan,
                            result,
                            result.summary(),
                            AgentTerminationReason.FINAL_RESPONSE,
                            1,
                            1,
                            List.of(definition.name()),
                            AssistantPendingActionUpdate.unchanged()
                    );
                });
        when(stateWriter.writeTurn(
                eq(context), eq(state), eq(AssistantPendingActionUpdate.unchanged()),
                eq(result), eq(result.summary())
        )).thenReturn(state);

        AssistantConversationTurnCoordinator coordinator = mock(AssistantConversationTurnCoordinator.class);
        AssistantConversationTurnCoordinator.TurnLease turnLease = mock(
                AssistantConversationTurnCoordinator.TurnLease.class
        );
        when(coordinator.acquire(context)).thenReturn(turnLease);

        AgentTurnService agentTurnService = new AgentTurnService(
                contextBuilder,
                visibilityPolicy,
                agentLoop,
                new AssistantRunTraceRecorder(),
                stateStore,
                stateWriter,
                coordinator
        );
        List<AssistantAgentLifecycleEvent> events = new ArrayList<>();
        AssistantEntryResponse response = agentTurnService.run(
                new AssistantMessageRequest(context.normalizedMessage(), context.conversationId()),
                event -> {
                    events.add(event);
                    if (event.event() == AssistantAgentLifecycleEventType.TOOL_ARGUMENTS_READY) {
                        throw new IllegalStateException("断开的 SSE 观察者不应打断业务 Turn");
                    }
                }
        );

        assertThat(response.reply()).isEqualTo("找到 1 个待办");
        assertThat(response.terminationReason()).isEqualTo("FINAL_RESPONSE");
        assertThat(response.trace().steps()).extracting(AssistantEntryResponse.AssistantTraceStepSnapshot::name)
                .containsExactly(
                        "context_build",
                        "conversation_turn_lock",
                        "conversation_state",
                        "tool_visibility",
                        "agent_run",
                        "conversation_state_write"
                );
        assertThat(events).extracting(AssistantAgentLifecycleEvent::event)
                .containsSubsequence(
                        AssistantAgentLifecycleEventType.CONVERSATION_TURN_ACQUIRED,
                        AssistantAgentLifecycleEventType.TOOL_VISIBILITY_READY,
                        AssistantAgentLifecycleEventType.TOOL_SELECTION_READY,
                        AssistantAgentLifecycleEventType.TOOL_ARGUMENTS_READY,
                        AssistantAgentLifecycleEventType.TOOL_ARGUMENTS_VALIDATED,
                        AssistantAgentLifecycleEventType.TOOL_FINISHED,
                        AssistantAgentLifecycleEventType.DECISION_READY,
                        AssistantAgentLifecycleEventType.CONVERSATION_STATE_WRITTEN,
                        AssistantAgentLifecycleEventType.RESPONSE_READY,
                        AssistantAgentLifecycleEventType.DONE
                );
        verify(stateWriter).writeTurn(
                eq(context), eq(state), eq(AssistantPendingActionUpdate.unchanged()),
                eq(result), eq(result.summary())
        );
        verify(turnLease).close();
    }

    @Test
    void shouldPreservePendingActionWhenConfirmationCannotExecute() {
        AssistantRequestContext context = requestContext("确认提交");
        AssistantPendingAction pendingAction = new AssistantPendingAction(
                "source-run",
                "source-trace",
                "workflow.leave.prepare",
                "帮我请假",
                false,
                AssistantPendingActionStatus.COLLECTING,
                "action-1",
                "idem-1",
                List.of(new AssistantToolArgument("reason", "请假原因", "家中有事", "家中有事")),
                List.of(new ToolInputField(
                        "startTime", "开始时间", ToolSchemaType.STRING, ToolSchemaFormat.DATE_TIME,
                        "请假开始时间", true, List.of()
                )),
                Instant.parse("2026-07-13T06:00:00Z")
        );
        AssistantConversationState state = new AssistantConversationState(
                0, AssistantChatMemory.empty(), pendingAction, null, context.requestInstant()
        );
        AssistantToolVisibilityPlan visibility = new AssistantToolVisibilityPlan(
                List.of(), List.of(), List.of(), "当前无可见 Tool"
        );
        ToolExecutionPlan plan = new ToolExecutionPlan(
                context.runId(), context.traceId(), ToolExecutionStatus.BLOCKED,
                ToolSetBuilder.CONFIRM_PENDING_ACTION, false,
                List.of(), List.of(), List.of("pending_action.not_confirmable"),
                "当前动作缺少必填参数，不能确认"
        );
        AssistantToolResult result = AssistantToolResult.failed(
                ToolSetBuilder.CONFIRM_PENDING_ACTION,
                "当前动作缺少必填参数，不能确认"
        );

        AssistantContextBuilder contextBuilder = mock(AssistantContextBuilder.class);
        ToolVisibilityPolicy visibilityPolicy = mock(ToolVisibilityPolicy.class);
        AssistantConversationStateStore stateStore = mock(AssistantConversationStateStore.class);
        AssistantConversationStateWriter stateWriter = mock(AssistantConversationStateWriter.class);
        AgentLoop agentLoop = mock(AgentLoop.class);
        when(contextBuilder.build(any(), any(), any())).thenReturn(context);
        when(stateStore.load(context)).thenReturn(state);
        when(visibilityPolicy.selectVisibleTools(context)).thenReturn(visibility);
        when(agentLoop.run(eq(context), eq(state), eq(visibility.tools()), any(AgentLoopListener.class)))
                .thenReturn(new AgentResult(
                plan,
                result,
                result.summary(),
                AgentTerminationReason.BLOCKED,
                1,
                1,
                List.of(ToolSetBuilder.CONFIRM_PENDING_ACTION),
                AssistantPendingActionUpdate.unchanged()
        ));
        when(stateWriter.writeTurn(
                eq(context), eq(state), eq(AssistantPendingActionUpdate.unchanged()),
                eq(result), eq(result.summary())
        )).thenReturn(state);

        AgentTurnService agentTurnService = new AgentTurnService(
                contextBuilder,
                visibilityPolicy,
                agentLoop,
                new AssistantRunTraceRecorder(),
                stateStore,
                stateWriter,
                coordinator(context)
        );
        agentTurnService.run(new AssistantMessageRequest(context.normalizedMessage(), context.conversationId()));

        verify(stateWriter).writeTurn(
                eq(context), eq(state), eq(AssistantPendingActionUpdate.unchanged()),
                eq(result), eq(result.summary())
        );
    }

    @Test
    void shouldPreserveConfirmedDraftWhenBusinessExecutionFails() {
        AssistantRequestContext context = requestContext("确认审批");
        AssistantPendingAction pendingAction = new AssistantPendingAction(
                "source-run", "source-trace", "workflow.task.approve", "同意这个待办", false,
                AssistantPendingActionStatus.WAITING_FOR_CONFIRMATION, "action-1", "idem-1",
                List.of(new AssistantToolArgument("taskIdentifier", "待审批任务标识", "7001", "7001")),
                List.of(), Instant.parse("2026-07-13T06:00:00Z")
        );
        AssistantConversationState state = new AssistantConversationState(
                0, AssistantChatMemory.empty(), pendingAction, null, context.requestInstant()
        );
        AssistantToolVisibilityPlan visibility = new AssistantToolVisibilityPlan(
                List.of(), List.of(), List.of(), "测试"
        );
        ToolExecutionPlan plan = new ToolExecutionPlan(
                context.runId(), context.traceId(), ToolExecutionStatus.APPROVED,
                "workflow.task.approve", false, pendingAction.filledArguments(), List.of(), List.of(),
                "用户已确认", pendingAction.actionId(), pendingAction.idempotencyKey()
        );
        AssistantToolResult result = AssistantToolResult.failed("workflow.task.approve", "数据库暂时不可用");

        AssistantContextBuilder contextBuilder = mock(AssistantContextBuilder.class);
        ToolVisibilityPolicy visibilityPolicy = mock(ToolVisibilityPolicy.class);
        AssistantConversationStateStore stateStore = mock(AssistantConversationStateStore.class);
        AssistantConversationStateWriter stateWriter = mock(AssistantConversationStateWriter.class);
        AgentLoop agentLoop = mock(AgentLoop.class);
        when(contextBuilder.build(any(), any(), any())).thenReturn(context);
        when(stateStore.load(context)).thenReturn(state);
        when(visibilityPolicy.selectVisibleTools(context)).thenReturn(visibility);
        when(agentLoop.run(eq(context), eq(state), eq(visibility.tools()), any(AgentLoopListener.class)))
                .thenReturn(new AgentResult(
                plan,
                result,
                result.summary(),
                AgentTerminationReason.FINAL_RESPONSE,
                2,
                1,
                List.of(ToolSetBuilder.CONFIRM_PENDING_ACTION),
                AssistantPendingActionUpdate.unchanged()
        ));
        when(stateWriter.writeTurn(
                eq(context), eq(state), eq(AssistantPendingActionUpdate.unchanged()),
                eq(result), eq(result.summary())
        )).thenReturn(state);

        AgentTurnService agentTurnService = new AgentTurnService(
                contextBuilder,
                visibilityPolicy,
                agentLoop,
                new AssistantRunTraceRecorder(),
                stateStore,
                stateWriter,
                coordinator(context)
        );
        agentTurnService.run(new AssistantMessageRequest(context.normalizedMessage(), context.conversationId()));

        verify(stateWriter).writeTurn(
                eq(context), eq(state), eq(AssistantPendingActionUpdate.unchanged()),
                eq(result), eq(result.summary())
        );
    }

    private static AssistantConversationTurnCoordinator coordinator(AssistantRequestContext context) {
        AssistantConversationTurnCoordinator coordinator = mock(AssistantConversationTurnCoordinator.class);
        when(coordinator.acquire(context)).thenReturn(mock(AssistantConversationTurnCoordinator.TurnLease.class));
        return coordinator;
    }
}
