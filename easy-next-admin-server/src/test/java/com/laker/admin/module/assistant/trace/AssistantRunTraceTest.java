package com.laker.admin.module.assistant.trace;

import com.laker.admin.module.assistant.tool.ToolExecutionPhase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantRunTraceTest {

    @Test
    void shouldAttributeModelToolAndRuntimeLatencyWithoutOverlap() {
        AssistantRunTrace.Builder builder = AssistantRunTrace.builder(0);
        builder.addStep(
                "agent_run",
                "AgentLoop",
                200,
                "OK",
                "test",
                List.of(new AssistantModelCallRecord(
                        "agent_step",
                        "test-model",
                        "OK",
                        AssistantModelCallTiming.synchronous(100, 5, 90, 5),
                        new AssistantTokenUsage(20, 10, 30, 8, 2)
                )),
                List.of(new AssistantToolCallRecord("workflow.process.query", ToolExecutionPhase.READ, "EXECUTED", 25))
        );

        AssistantRunTrace trace = builder.build(200_000_000L);

        assertThat(trace.latency()).isEqualTo(new AssistantLatencyBreakdown(
                200,
                100,
                5,
                90,
                5,
                25,
                75,
                1,
                1
        ));
        assertThat(trace.tokenUsage()).isEqualTo(new AssistantTokenUsage(20, 10, 30, 8, 2));
    }
}
