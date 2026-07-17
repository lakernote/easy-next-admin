package com.laker.admin.module.assistant.turn;

import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.module.assistant.context.AssistantContextBuilder;
import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.dto.AssistantEntryResponse;
import com.laker.admin.module.assistant.dto.AssistantMessageRequest;
import com.laker.admin.module.assistant.dto.AssistantAgentLifecycleEvent;
import com.laker.admin.module.assistant.runtime.AgentLoop;
import com.laker.admin.module.assistant.runtime.AgentLoopListener;
import com.laker.admin.module.assistant.runtime.model.ToolCallingRequest;
import com.laker.admin.module.assistant.runtime.model.ToolCallingResponse;
import com.laker.admin.module.assistant.runtime.AgentResult;
import com.laker.admin.module.assistant.state.AssistantConversationState;
import com.laker.admin.module.assistant.state.AssistantConversationStateStore;
import com.laker.admin.module.assistant.state.AssistantConversationStateWriter;
import com.laker.admin.module.assistant.state.AssistantConversationTurnCoordinator;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentResolution;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolSelection;
import com.laker.admin.module.assistant.tool.AssistantToolVisibilityPlan;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ToolVisibilityPolicy;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.trace.AssistantModelCallRecord;
import com.laker.admin.module.assistant.trace.AssistantRunTrace;
import com.laker.admin.module.assistant.trace.AssistantRunTraceRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.laker.admin.module.assistant.turn.AgentTraceSnapshots.contextContent;
import static com.laker.admin.module.assistant.turn.AgentTraceSnapshots.contextMetadata;
import static com.laker.admin.module.assistant.turn.AgentTraceSnapshots.conversation;
import static com.laker.admin.module.assistant.turn.AgentTraceSnapshots.conversationMetadata;
import static com.laker.admin.module.assistant.turn.AgentTraceSnapshots.map;
import static com.laker.admin.module.assistant.turn.AgentTraceSnapshots.plan;
import static com.laker.admin.module.assistant.turn.AgentTraceSnapshots.planMetadata;
import static com.laker.admin.module.assistant.turn.AgentTraceSnapshots.toolResult;
import static com.laker.admin.module.assistant.turn.AgentTraceSnapshots.toolResultMetadata;

/**
 * 单轮企业 Agent 入口。
 *
 * <p>主链只有请求上下文、会话状态、权限过滤后的 Tool 面、企业助手主循环和状态写回。
 * toolName 只从模型的真实 Tool Call 派生，不做前置意图路由。</p>
 *
 * <pre>{@code
 * RequestContext
 *   -> acquire conversation lease
 *   -> load versioned ConversationState
 *   -> resolve visible Tools
 *   -> run AgentLoop.run
 *   -> atomically save the next ConversationState
 *   -> return response
 * }</pre>
 */
@Component
public class AgentTurnService {
    private static final Logger log = LoggerFactory.getLogger(AgentTurnService.class);
    private static final Consumer<AssistantAgentLifecycleEvent> NO_OP_EVENT_OBSERVER = ignored -> {
    };

    private final AssistantContextBuilder contextBuilder;
    private final ToolVisibilityPolicy toolVisibilityPolicy;
    private final AgentLoop agentLoop;
    private final AssistantRunTraceRecorder traceRecorder;
    private final AssistantConversationStateStore conversationStateStore;
    private final AssistantConversationStateWriter conversationStateWriter;
    private final AssistantConversationTurnCoordinator conversationTurnCoordinator;

    public AgentTurnService(AssistantContextBuilder contextBuilder,
                            ToolVisibilityPolicy toolVisibilityPolicy,
                            AgentLoop agentLoop,
                            AssistantRunTraceRecorder traceRecorder,
                            AssistantConversationStateStore conversationStateStore,
                            AssistantConversationStateWriter conversationStateWriter,
                            AssistantConversationTurnCoordinator conversationTurnCoordinator) {
        this.contextBuilder = contextBuilder;
        this.toolVisibilityPolicy = toolVisibilityPolicy;
        this.agentLoop = agentLoop;
        this.traceRecorder = traceRecorder;
        this.conversationStateStore = conversationStateStore;
        this.conversationStateWriter = conversationStateWriter;
        this.conversationTurnCoordinator = conversationTurnCoordinator;
    }

    public AssistantEntryResponse run(AssistantMessageRequest request) {
        return run(request, NO_OP_EVENT_OBSERVER);
    }

    /**
     * 执行一个完整 Agent Turn，并把有序生命周期事件同步通知观察者。
     * 观察者只用于 SSE、调试或审计旁路；其异常会被隔离，不得改变 Agent 的业务结果。
     */
    public AssistantEntryResponse run(AssistantMessageRequest request,
                                      Consumer<AssistantAgentLifecycleEvent> eventObserver) {
        Objects.requireNonNull(eventObserver, "eventObserver");
        AssistantRunTrace.Builder traceBuilder = AssistantRunTrace.builder(System.nanoTime());
        traceRecorder.reset();
        String runStatus = "UNKNOWN";
        boolean turnMetricRecorded = false;
        try {
            AssistantRequestContext context = traceStep(
                    traceBuilder,
                    "context_build",
                    "AssistantContextBuilder",
                    "构建不可变请求上下文",
                    tracePayload(
                            map(
                                    "conversationId", request.conversationId(),
                                    "messageLength", request.message() == null ? 0 : request.message().length(),
                                    "multiline", request.message() != null && request.message().contains("\n")
                            ),
                            map("rawMessage", request.message())
                    ),
                    () -> contextBuilder.build(
                            EasySecurityContext.getPrincipal(),
                            request.message(),
                            request.conversationId()
                    ),
                    value -> tracePayload(contextMetadata(value), contextContent(value))
            );
            emit(eventObserver, AssistantAgentLifecycleEvent.contextBuilt(context, traceBuilder.lastStep()));

            AssistantEntryResponse response;
            AssistantRunTrace trace;
            // 锁必须在读取 Conversation State 之前获得，并一直持有到状态写回完成。
            // 这里直接把 acquire 放进 try-with-resources，避免“已拿锁但尚未进入 try”时发生异常而延迟释放。
            try (AssistantConversationTurnCoordinator.TurnLease ignored1 = traceStep(
                    traceBuilder,
                    "conversation_turn_lock",
                    "AssistantConversationTurnCoordinator",
                    "串行化同一用户会话的状态事务",
                    map("conversationId", context.conversationId()),
                    () -> conversationTurnCoordinator.acquire(context),
                    ignored -> map("acquired", true)
            )) {
                emit(eventObserver, AssistantAgentLifecycleEvent.conversationTurnAcquired(context, traceBuilder.lastStep()));
                AssistantConversationState conversationState = traceStep(
                        traceBuilder,
                        "conversation_state",
                        "AssistantConversationStateStore",
                        "原子读取带版本的会话快照",
                        contextMetadata(context),
                        () -> conversationStateStore.load(context),
                        value -> tracePayload(
                                conversationMetadata(value),
                                conversation(value)
                        )
                );
                emit(eventObserver, AssistantAgentLifecycleEvent.conversationStateLoaded(
                        context, conversationState, traceBuilder.lastStep()));

                AssistantToolVisibilityPlan toolVisibilityPlan = traceStep(
                        traceBuilder,
                        "tool_visibility",
                        "ToolVisibilityPolicy",
                        "按当前用户权限构建模型可见 Tool 面",
                        contextMetadata(context),
                        () -> toolVisibilityPolicy.selectVisibleTools(context),
                        AgentTraceSnapshots::toolVisibilityMetadata
                );
                emit(eventObserver, AssistantAgentLifecycleEvent.toolVisibilityReady(
                        context, toolVisibilityPlan, traceBuilder.lastStep()));

                AgentLoopListener lifecycleListener = lifecycleListener(context, eventObserver);
                AgentResult runResult = traceStep(
                        traceBuilder,
                        "agent_run",
                        "AgentLoop",
                        "由同一个模型选择真实 Tool、消费 observation 并完成目标",
                        tracePayload(
                                map(
                                        "messageLength", context.normalizedMessage().length(),
                                        "visibleToolNames", toolVisibilityPlan.tools().stream()
                                                .map(AssistantVisibleTool::name)
                                                .toList(),
                                        "conversationState", conversationMetadata(conversationState)
                                ),
                                map(
                                        "message", context.normalizedMessage(),
                                        "conversationState", conversation(conversationState)
                                )
                        ),
                        () -> agentLoop.run(
                                context,
                                conversationState,
                                toolVisibilityPlan.tools(),
                                lifecycleListener
                        ),
                        result -> tracePayload(
                                map(
                                        "planStatus", result.plan() == null ? "" : result.plan().status(),
                                        "plan", planMetadata(result.plan()),
                                        "toolResult", toolResultMetadata(result.toolResult()),
                                        "replyLength", result.reply() == null ? 0 : result.reply().length(),
                                        "modelCallCount", result.modelCallCount(),
                                        "toolCallCount", result.toolCallCount(),
                                        "calledToolNames", result.executedToolNames()
                                ),
                                map(
                                        "plan", plan(result.plan()),
                                        "toolResult", toolResult(result.toolResult()),
                                        "reply", result.reply()
                                )
                        )
                );
                AssistantRunTrace.AssistantTraceStep agentRunStep = traceBuilder.lastStep();

                ToolExecutionPlan plan = runResult.plan();
                runStatus = plan.status().name();
                AssistantToolResult toolResult = runResult.toolResult();
                emit(eventObserver, AssistantAgentLifecycleEvent.decisionReady(context, plan, traceBuilder.lastStep()));
                emit(eventObserver, AssistantAgentLifecycleEvent.planReady(context, plan, traceBuilder.lastStep()));

                String reply = runResult.reply();

                traceStep(
                        traceBuilder,
                        "conversation_state_write",
                        "AssistantConversationStateWriter",
                        "在会话锁内原子写回完整 Conversation State",
                        map(
                                "conversationId", context.conversationId(),
                                "expectedVersion", conversationState.version(),
                                "pendingActionUpdate", runResult.pendingActionUpdate().type()
                        ),
                        () -> conversationStateWriter.writeTurn(
                                context,
                                conversationState,
                                runResult.pendingActionUpdate(),
                                toolResult,
                                reply
                        ),
                        writtenState -> map(
                                "written", true,
                                "newVersion", writtenState.version()
                        )
                );
                emit(eventObserver, AssistantAgentLifecycleEvent.conversationStateWritten(context, traceBuilder.lastStep()));
                emit(eventObserver, AssistantAgentLifecycleEvent.responseReady(context, reply, agentRunStep));
                trace = traceBuilder.build(System.nanoTime());
                response = AssistantEntryResponse.turnReady(
                        context, toolVisibilityPlan, plan, toolResult, reply, trace);
            }
            traceRecorder.recordTurn(runStatus, "OK", trace);
            turnMetricRecorded = true;
            emit(eventObserver, AssistantAgentLifecycleEvent.done(response));
            return response;
        } catch (RuntimeException ex) {
            if (!turnMetricRecorded) {
                traceRecorder.recordTurn(runStatus, "FAILED", traceBuilder.build(System.nanoTime()));
            }
            throw ex;
        } finally {
            traceRecorder.clear();
        }
    }

    private static AgentLoopListener lifecycleListener(
            AssistantRequestContext context,
            Consumer<AssistantAgentLifecycleEvent> eventObserver) {
        return new AgentLoopListener() {
            @Override
            public void onModelCallStarted(int step, ToolCallingRequest request) {
                emit(eventObserver, AssistantAgentLifecycleEvent.modelCallStarted(context, step, request));
            }

            @Override
            public void onModelCallFinished(int step, ToolCallingResponse response) {
                emit(eventObserver, AssistantAgentLifecycleEvent.modelCallFinished(context, step, response));
            }

            @Override
            public void onToolSelected(AssistantToolSelection selection) {
                emit(eventObserver, AssistantAgentLifecycleEvent.toolSelectionReady(context, selection, null));
            }

            @Override
            public void onArgumentsResolved(AssistantToolArgumentResolution resolution) {
                emit(eventObserver, AssistantAgentLifecycleEvent.toolArgumentsReady(context, resolution, null));
            }

            @Override
            public void onArgumentsValidated(AssistantToolArgumentResolution resolution) {
                emit(eventObserver, AssistantAgentLifecycleEvent.toolArgumentsValidated(context, resolution, null));
            }

            @Override
            public void onToolFinished(AssistantToolResult result) {
                emit(eventObserver, AssistantAgentLifecycleEvent.toolFinished(context, result, null));
            }
        };
    }

    private static void emit(Consumer<AssistantAgentLifecycleEvent> eventObserver, AssistantAgentLifecycleEvent event) {
        try {
            eventObserver.accept(event);
        } catch (RuntimeException ex) {
            log.warn("Assistant event observer failed, event={}, failureType={}",
                    event == null ? "unknown" : event.event(), ex.getClass().getSimpleName());
        }
    }

    private Object tracePayload(Object metadata, Object content) {
        return traceRecorder.capturePayload(metadata, content);
    }

    private <T> T traceStep(AssistantRunTrace.Builder traceBuilder,
                            String name,
                            String component,
                            String summary,
                            Object inputPayload,
                            Supplier<T> action,
                            Function<T, Object> outputMapper) {
        int modelCallMark = traceRecorder.markModelCalls();
        int toolCallMark = traceRecorder.markToolCalls();
        long startedNanos = System.nanoTime();
        try {
            T result = action.get();
            traceBuilder.addStep(
                    name,
                    component,
                    AssistantRunTrace.toMillis(System.nanoTime() - startedNanos),
                    "OK",
                    summary,
                    inputPayload,
                    outputMapper == null ? null : outputMapper.apply(result),
                    traceRecorder.modelCallsSince(modelCallMark),
                    traceRecorder.toolCallsSince(toolCallMark)
            );
            return result;
        } catch (RuntimeException ex) {
            List<AssistantModelCallRecord> modelCalls = traceRecorder.modelCallsSince(modelCallMark);
            String failureType = ex.getClass().getSimpleName();
            traceBuilder.addStep(
                    name,
                    component,
                    AssistantRunTrace.toMillis(System.nanoTime() - startedNanos),
                    "FAILED",
                    "步骤执行失败：" + failureType,
                    inputPayload,
                    tracePayload(
                            map("failureType", failureType),
                            map("errorMessage", traceRecorder.sanitizeErrorMessage(ex.getMessage()))
                    ),
                    modelCalls,
                    traceRecorder.toolCallsSince(toolCallMark)
            );
            throw ex;
        }
    }

}
