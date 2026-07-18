package com.laker.admin.module.assistant.runtime;

import com.laker.admin.module.assistant.config.AssistantModelProperties;
import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.response.ModelMessageBuilder;
import com.laker.admin.module.assistant.runtime.model.ModelFinishReason;
import com.laker.admin.module.assistant.runtime.model.ToolCall;
import com.laker.admin.module.assistant.runtime.model.ToolCallingMessage;
import com.laker.admin.module.assistant.runtime.model.ToolCallingModelClient;
import com.laker.admin.module.assistant.runtime.model.ToolCallingRequest;
import com.laker.admin.module.assistant.runtime.model.ToolCallingResponse;
import com.laker.admin.module.assistant.state.AssistantConversationState;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 模型、消息和 Tool 之间的最小 Agent 循环。
 *
 * <pre>{@code
 * response = model(messages, tools)
 * if (response.hasToolCalls()) {
 *     messages.add(response.assistantMessage())
 *     executeRequestedToolCalls(response.toolCalls())
 *     if (waitingForUser) return
 *     continue
 * }
 * if (response.hasContent()) return finalAnswer(response.content())
 * return invalidModelResponse()
 * }</pre>
 *
 * <p>参数协议错误可以在统一预算内修复一次，因此实现仍保留一个很小的循环；成功执行一个
 * Tool round 后 Tool 面会关闭，不允许模型无边界扩查。权限、参数、确认、幂等与 observation
 * 都在 {@link ToolCallProcessor}，会话加载和写回在上层 {@code AgentTurnService}。</p>
 */
@Component
public class AgentLoop {
    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private final ToolCallingModelClient modelClient;
    private final ModelMessageBuilder messageBuilder;
    private final ToolSetBuilder toolSetBuilder;
    private final ToolCallProcessor toolCallProcessor;
    private final int maxSteps;
    private final int maxToolCalls;

    public AgentLoop(ToolCallingModelClient modelClient,
                     AssistantModelProperties modelProperties,
                     ModelMessageBuilder messageBuilder,
                     ToolSetBuilder toolSetBuilder,
                     ToolCallProcessor toolCallProcessor) {
        this.modelClient = Objects.requireNonNull(modelClient, "modelClient must not be null");
        AssistantModelProperties properties = Objects.requireNonNull(
                modelProperties,
                "modelProperties must not be null"
        );
        this.messageBuilder = Objects.requireNonNull(messageBuilder, "messageBuilder must not be null");
        this.toolSetBuilder = Objects.requireNonNull(toolSetBuilder, "toolSetBuilder must not be null");
        this.toolCallProcessor = Objects.requireNonNull(toolCallProcessor, "toolCallProcessor must not be null");
        this.maxSteps = bounded(
                properties.getMaxModelCalls(),
                2,
                AssistantModelProperties.MAX_MODEL_CALLS_LIMIT
        );
        this.maxToolCalls = bounded(
                properties.getMaxToolCalls(),
                1,
                AssistantModelProperties.MAX_TOOL_CALLS_LIMIT
        );
    }

    public AgentResult run(AssistantRequestContext context,
                           AssistantConversationState conversationState,
                           List<AssistantVisibleTool> visibleTools) {
        return run(context, conversationState, visibleTools, AgentLoopListener.noop());
    }

    public AgentResult run(AssistantRequestContext context,
                           AssistantConversationState conversationState,
                           List<AssistantVisibleTool> visibleTools,
                           AgentLoopListener listener) {
        Objects.requireNonNull(context, "context must not be null");
        AssistantConversationState safeConversationState = conversationState == null
                ? AssistantConversationState.empty()
                : conversationState;
        AgentLoopListener safeListener = listener == null
                ? AgentLoopListener.noop()
                : listener;
        AgentLoopState state = start(
                context,
                safeConversationState,
                visibleTools == null ? List.of() : List.copyOf(visibleTools)
        );

        for (int step = 1; step <= maxSteps; step++) {
            boolean allowTools = step < maxSteps && !state.activeToolDefinitions().isEmpty();
            boolean finalResponseCall = !allowTools && state.currentPlan() != null;
            int modelCallNumber = state.recordModelCall();
            ToolCallingRequest request = new ToolCallingRequest(
                    state.messageHistory(),
                    allowTools ? state.activeToolDefinitions() : List.of(),
                    finalResponseCall ? "final_response" : "agent_step",
                    null
            );
            ToolCallingResponse response;
            try {
                AgentLoopEvents.modelCallStarted(context, safeListener, step, request);
                response = modelClient.call(request);
                AgentLoopEvents.modelCallFinished(context, safeListener, step, response);
            } catch (RuntimeException ex) {
                log.warn("Assistant model call failed, runId={}, traceId={}, type={}",
                        context.runId(), context.traceId(), ex.getClass().getSimpleName());
                if (step < maxSteps && state.tryMarkModelCallRetry()) {
                    continue;
                }
                return AgentResultFactory.modelFailure(context, state);
            }

            if (response == null) {
                return AgentResultFactory.abnormalFinish(context, state, ModelFinishReason.ERROR);
            }
            switch (response.finishReason()) {
                case MAX_TOKENS, CONTENT_FILTER, REFUSAL, PAUSED, ERROR, UNKNOWN -> {
                    return AgentResultFactory.abnormalFinish(context, state, response.finishReason());
                }
                case TOOL_CALLS -> {
                    if (!response.hasToolCalls()) {
                        return AgentResultFactory.abnormalFinish(context, state, response.finishReason());
                    }
                }
                case STOP, UNSPECIFIED -> {
                    // 部分兼容供应商会把完整 Tool Call 标成 stop，继续以响应结构为准。
                }
            }
            if (response.hasToolCalls()) {
                List<ToolCall> requestedToolCalls = toolCallProcessor.normalizeRequestedToolCalls(
                        response.toolCalls(),
                        modelCallNumber
                );

                // 标准消息顺序：先保留模型提出的 assistant Tool Call，随后再追加匹配 callId 的 Tool Result。
                state.addMessage(ToolCallingMessage.assistant(
                        response.content(),
                        requestedToolCalls
                ));

                if (!allowTools) {
                    AgentTerminationReason terminationReason = step == maxSteps
                            && !state.activeToolDefinitions().isEmpty()
                            ? AgentTerminationReason.MODEL_CALL_LIMIT
                            : AgentTerminationReason.BLOCKED;
                    return toolCallProcessor.rejectCallsWithoutToolSurface(
                            context,
                            state,
                            requestedToolCalls,
                            safeListener,
                            terminationReason
                    );
                }

                Optional<AgentResult> waitingForUser = toolCallProcessor.executeRequestedToolCalls(
                        context,
                        safeConversationState,
                        state,
                        safeListener,
                        requestedToolCalls,
                        maxToolCalls
                );
                if (waitingForUser.isPresent()) {
                    return waitingForUser.get();
                }
                continue;
            }
            if (!response.hasContent()) {
                return AgentResultFactory.abnormalFinish(context, state, response.finishReason());
            }
            return AgentResultFactory.finalAnswer(context, state, response.content());
        }
        return AgentResultFactory.modelCallLimit(context, state);
    }

    private AgentLoopState start(AssistantRequestContext context,
                                 AssistantConversationState conversationState,
                                 List<AssistantVisibleTool> visibleTools) {
        List<ToolDefinition> businessTools = toolSetBuilder.buildBusinessTools(visibleTools);
        return AgentLoopState.start(
                visibleTools,
                businessTools,
                toolSetBuilder.buildInitialTools(businessTools, conversationState),
                messageBuilder.buildInitialMessages(context, conversationState)
        );
    }

    private static int bounded(int value, int lowerBound, int upperBound) {
        return Math.min(upperBound, Math.max(lowerBound, value));
    }
}
