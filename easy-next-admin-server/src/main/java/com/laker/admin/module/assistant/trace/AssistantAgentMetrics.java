package com.laker.admin.module.assistant.trace;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * Agent 低基数聚合指标。Actuator 可查看 count/total/max，支持分位数的外部指标系统可查看 p50/p95/p99。
 */
@Component
public class AssistantAgentMetrics {
    public static final String MODEL_CALL_TIMER = "easy.assistant.model.calls";
    public static final String TOOL_CALL_TIMER = "easy.assistant.tool.calls";
    public static final String TURN_TIMER = "easy.assistant.turns";
    public static final String MODEL_TOKEN_DISTRIBUTION = "easy.assistant.model.tokens";
    public static final String TURN_TOKEN_DISTRIBUTION = "easy.assistant.turn.tokens";

    private static final double[] PERCENTILES = {0.5d, 0.95d, 0.99d};
    private static final String UNKNOWN = "unknown";

    private final MeterRegistry meterRegistry;

    public AssistantAgentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordModelCall(AssistantModelCallRecord call) {
        AssistantModelCallTiming timing = call.timing();
        recordModelPhase(call, "total", timing.totalDurationMs());
        recordModelPhase(call, "request_preparation", timing.requestPreparationDurationMs());
        recordModelPhase(call, "provider_round_trip", timing.providerRoundTripDurationMs());
        recordModelPhase(call, "response_processing", timing.responseProcessingDurationMs());
        recordTokens(call, "prompt", call.tokenUsage().promptTokens());
        recordTokens(call, "completion", call.tokenUsage().completionTokens());
        recordTokens(call, "total", call.tokenUsage().totalTokens());
        recordTokens(call, "cached_prompt", call.tokenUsage().cachedPromptTokens());
        recordTokens(call, "reasoning", call.tokenUsage().reasoningTokens());
    }

    public void recordToolCall(AssistantToolCallRecord call) {
        Timer.builder(TOOL_CALL_TIMER)
                .description("企业 Agent ToolRunner 调用耗时")
                .tag("tool", tagValue(call.name()))
                .tag("phase", call.phase().name().toLowerCase())
                .tag("result", tagValue(call.status()).toLowerCase())
                .publishPercentiles(PERCENTILES)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(call.durationMs(), TimeUnit.MILLISECONDS);
    }

    public void recordTurn(String decision,
                           String terminationReason,
                           String status,
                           AssistantRunTrace trace) {
        AssistantTokenUsage usage = trace == null ? AssistantTokenUsage.ZERO : trace.tokenUsage();
        Timer.builder(TURN_TIMER)
                .description("企业 Agent 单轮请求耗时")
                .tag("decision", tagValue(decision).toLowerCase())
                .tag("termination", tagValue(terminationReason).toLowerCase())
                .tag("result", tagValue(status).toLowerCase())
                .publishPercentiles(PERCENTILES)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(trace == null ? 0 : trace.totalDurationMs(), TimeUnit.MILLISECONDS);
        recordTurnTokens(decision, terminationReason, status, "prompt", usage.promptTokens());
        recordTurnTokens(decision, terminationReason, status, "completion", usage.completionTokens());
        recordTurnTokens(decision, terminationReason, status, "total", usage.totalTokens());
        recordTurnTokens(decision, terminationReason, status, "cached_prompt", usage.cachedPromptTokens());
        recordTurnTokens(decision, terminationReason, status, "reasoning", usage.reasoningTokens());
    }

    private void recordModelPhase(AssistantModelCallRecord call, String phase, long durationMs) {
        Timer.builder(MODEL_CALL_TIMER)
                .description("企业 Agent 模型调用分段耗时")
                .tag("operation", tagValue(call.name()))
                .tag("model", tagValue(call.model()))
                .tag("mode", call.timing().mode().name().toLowerCase())
                .tag("phase", phase)
                .tag("result", tagValue(call.status()).toLowerCase())
                .publishPercentiles(PERCENTILES)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Math.max(0, durationMs), TimeUnit.MILLISECONDS);
    }

    private void recordTokens(AssistantModelCallRecord call, String type, int tokens) {
        DistributionSummary.builder(MODEL_TOKEN_DISTRIBUTION)
                .description("企业 Agent 单次模型调用 Token 分布")
                .baseUnit("tokens")
                .tag("operation", tagValue(call.name()))
                .tag("model", tagValue(call.model()))
                .tag("type", type)
                .publishPercentiles(PERCENTILES)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Math.max(0, tokens));
    }

    private void recordTurnTokens(String decision,
                                  String terminationReason,
                                  String status,
                                  String type,
                                  int tokens) {
        DistributionSummary.builder(TURN_TOKEN_DISTRIBUTION)
                .description("企业 Agent 单轮总 Token 分布")
                .baseUnit("tokens")
                .tag("decision", tagValue(decision).toLowerCase())
                .tag("termination", tagValue(terminationReason).toLowerCase())
                .tag("result", tagValue(status).toLowerCase())
                .tag("type", type)
                .publishPercentiles(PERCENTILES)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Math.max(0, tokens));
    }

    private static String tagValue(String value) {
        return StringUtils.hasText(value) ? value : UNKNOWN;
    }
}
