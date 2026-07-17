package com.laker.admin.module.assistant.stream;

import com.laker.admin.module.assistant.dto.AssistantChatStreamEvent;
import com.laker.admin.module.assistant.dto.AssistantEntryResponse;
import com.laker.admin.module.assistant.dto.AssistantAgentLifecycleEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AssistantUserStreamEventAdapterTest {

    @Test
    void shouldExposeEveryUserSafeProgressStepWithoutInternalPayload() {
        List<AssistantChatStreamEvent> events = new ArrayList<>();
        AssistantUserStreamEventAdapter adapter = new AssistantUserStreamEventAdapter(events::add);

        adapter.accept(internal("context_built", Map.of("prompt", "secret")));
        adapter.accept(internal("conversation_state_loaded", Map.of("pendingArguments", "secret")));
        adapter.accept(internal("model_call_started", Map.of("messages", "secret")));
        adapter.accept(internal("model_call_finished", Map.of("response", "secret")));
        adapter.accept(internal("tool_selection_ready", Map.of("toolArguments", "secret")));
        adapter.accept(internal("tool_arguments_ready", Map.of("toolArguments", "secret")));
        adapter.accept(internal("tool_finished", Map.of("toolResult", "secret")));
        adapter.accept(internal("conversation_state_written", Map.of("trace", "secret")));

        assertThat(events).extracting(AssistantChatStreamEvent::step)
                .containsExactly(
                        "context",
                        "conversation_state",
                        "model_call",
                        "model_response",
                        "tool_selection",
                        "tool_arguments",
                        "tool_result",
                        "conversation_write"
                );
        assertThat(events).extracting(AssistantChatStreamEvent::sequence)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.event()).isEqualTo("progress");
            assertThat(event.response()).isNull();
            assertThat(event.message()).doesNotContain("secret", "prompt", "trace", "Tool 参数");
        });
    }

    @Test
    void shouldMapDoneToSlimChatResponse() {
        List<AssistantChatStreamEvent> events = new ArrayList<>();
        AssistantUserStreamEventAdapter adapter = new AssistantUserStreamEventAdapter(events::add);
        AssistantEntryResponse response = mock(AssistantEntryResponse.class);
        AssistantEntryResponse.ToolExecutionPlanSnapshot plan = mock(
                AssistantEntryResponse.ToolExecutionPlanSnapshot.class
        );
        when(response.runId()).thenReturn("run-1");
        when(response.conversationId()).thenReturn(1001L);
        when(response.reply()).thenReturn("你有 2 条待办");
        when(response.plan()).thenReturn(plan);
        when(plan.status()).thenReturn("READY");

        adapter.accept(new AssistantAgentLifecycleEvent("done", "DONE", "run-1", "trace-secret", null, response));

        assertThat(events).hasSize(1);
        AssistantChatStreamEvent done = events.get(0);
        assertThat(done.event()).isEqualTo("done");
        assertThat(done.response().reply()).isEqualTo("你有 2 条待办");
        assertThat(done.response().conversationId()).isEqualTo(1001L);
        assertThat(done.toString()).doesNotContain("trace-secret");
    }

    private static AssistantAgentLifecycleEvent internal(String event, Object payload) {
        return new AssistantAgentLifecycleEvent(event, "RUNNING", "run-1", "trace-secret", null, payload);
    }
}
