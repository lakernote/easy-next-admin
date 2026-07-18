package com.laker.admin.module.assistant.trace;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantAgentMetricsTest {

    @Test
    void shouldRecordLowCardinalityLatencyAndTokenMetrics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AssistantAgentMetrics metrics = new AssistantAgentMetrics(registry);

        AssistantTokenUsage usage = new AssistantTokenUsage(30, 10, 40, 12, 2);
        metrics.recordModelCall(new AssistantModelCallRecord(
                "agent_step",
                "test-model",
                "OK",
                AssistantModelCallTiming.synchronous(120, 5, 110, 5),
                usage
        ));
        metrics.recordTurn(
                "DIRECT_RESPONSE",
                "FINAL_RESPONSE",
                "SUCCESS",
                new AssistantRunTrace(120, usage, java.util.List.of())
        );

        assertThat(registry.find(AssistantAgentMetrics.MODEL_CALL_TIMER).timers()).hasSize(4);
        assertThat(registry.find(AssistantAgentMetrics.MODEL_TOKEN_DISTRIBUTION).summaries()).hasSize(5);
        assertThat(registry.find(AssistantAgentMetrics.TURN_TOKEN_DISTRIBUTION).summaries()).hasSize(5);
        assertThat(registry.find(AssistantAgentMetrics.MODEL_CALL_TIMER)
                .tag("phase", "provider_round_trip")
                .timer()
                .count()).isEqualTo(1);
        assertThat(registry.find(AssistantAgentMetrics.TURN_TIMER)
                .tag("termination", "final_response")
                .timer()
                .count()).isEqualTo(1);
    }
}
