package com.laker.admin.module.assistant.runtime;

import com.laker.admin.module.assistant.config.AssistantModelProperties;
import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.response.ModelMessageBuilder;
import com.laker.admin.module.assistant.runtime.model.ModelCallException;
import com.laker.admin.module.assistant.runtime.model.ModelCallFailureType;
import com.laker.admin.module.assistant.runtime.model.ModelFinishReason;
import com.laker.admin.module.assistant.runtime.model.ToolCall;
import com.laker.admin.module.assistant.runtime.model.ToolCallingMessage;
import com.laker.admin.module.assistant.runtime.model.ToolCallingModelClient;
import com.laker.admin.module.assistant.runtime.model.ToolCallingRequest;
import com.laker.admin.module.assistant.runtime.model.ToolCallingResponse;
import com.laker.admin.module.assistant.runtime.model.ToolCallingOutputSchema;
import com.laker.admin.module.assistant.state.AssistantConversationState;
import com.laker.admin.module.assistant.tool.AssistantVisibleTool;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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
 * <p>参数协议错误可以在统一预算内修复；只读 Tool 可以在显式轮数和调用预算内根据 observation
 * 继续规划。权限、参数、确认、幂等与 observation 都在 {@link ToolCallProcessor}，会话加载和写回
 * 在上层 {@code AgentTurnService}。</p>
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
    private final int maxReadToolRounds;
    private final ToolCallingOutputSchema outputSchema;

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
        this.maxReadToolRounds = bounded(
                properties.getMaxReadToolRounds(),
                1,
                AssistantModelProperties.MAX_READ_TOOL_ROUNDS_LIMIT
        );
        this.outputSchema = properties.isStructuredOutputEnabled()
                ? ToolCallingOutputSchema.assistantReply()
                : null;
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
                    state.toolNameMappingDefinitions(),
                    finalResponseCall ? "final_response" : "agent_step",
                    null,
                    outputSchema
            );
            ToolCallingResponse response;
            try {
                AgentLoopEvents.modelCallStarted(context, safeListener, step, request);
                response = modelClient.call(request);
                AgentLoopEvents.modelCallFinished(context, safeListener, step, response);
            } catch (ModelCallException ex) {
                log.warn("Assistant model call failed, runId={}, traceId={}, failureType={}, retryable={}",
                        context.runId(), context.traceId(), ex.failureType(), ex.retryable());
                if (ex.retryable()
                        && step < maxSteps
                        && state.tryMarkModelCallRetry()
                        && waitBeforeRetry(ex)) {
                    continue;
                }
                if (ex.failureType() == ModelCallFailureType.PROTOCOL_ERROR) {
                    return AgentResultFactory.abnormalFinish(context, state, ModelFinishReason.STOP);
                }
                return AgentResultFactory.modelFailure(context, state);
            } catch (RuntimeException ex) {
                log.warn("Assistant model adapter failed without retry, runId={}, traceId={}, type={}",
                        context.runId(), context.traceId(), ex.getClass().getSimpleName());
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
                        maxToolCalls,
                        maxReadToolRounds
                );
                if (waitingForUser.isPresent()) {
                    return waitingForUser.get();
                }
                continue;
            }
            if (!response.hasContent()) {
                return AgentResultFactory.abnormalFinish(context, state, response.finishReason());
            }
            String finalReply = finalReply(response);
            if (!StringUtils.hasText(finalReply)) {
                return AgentResultFactory.abnormalFinish(context, state, ModelFinishReason.STOP);
            }
            return AgentResultFactory.finalAnswer(context, state, finalReply);
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

    private String finalReply(ToolCallingResponse response) {
        if (outputSchema == null) {
            return response.content();
        }
        if (response.structuredContent().size() != 1) {
            return "";
        }
        Object reply = response.structuredContent().get("reply");
        return reply instanceof String text ? text : "";
    }

    private static boolean waitBeforeRetry(ModelCallException exception) {
        long delayMillis = Math.min(1_000L, Math.max(0L, exception.retryDelay().toMillis()));
        if (delayMillis == 0L) {
            return true;
        }
        try {
            Thread.sleep(delayMillis);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
