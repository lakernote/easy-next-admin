package com.laker.admin.module.assistant.stream;

import com.laker.admin.module.assistant.dto.AssistantChatStreamEvent;
import com.laker.admin.module.assistant.dto.AssistantEntryResponse;
import com.laker.admin.module.assistant.dto.AssistantAgentLifecycleEvent;
import com.laker.admin.module.assistant.dto.AssistantAgentLifecycleEventType;
import com.laker.admin.module.assistant.dto.AssistantAgentLifecyclePhase;
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

        adapter.accept(internal(AssistantAgentLifecycleEventType.CONTEXT_BUILT, Map.of("prompt", "secret")));
        adapter.accept(internal(AssistantAgentLifecycleEventType.CONVERSATION_STATE_LOADED,
                Map.of("pendingArguments", "secret")));
        adapter.accept(internal(AssistantAgentLifecycleEventType.MODEL_CALL_STARTED, Map.of("messages", "secret")));
        adapter.accept(internal(AssistantAgentLifecycleEventType.MODEL_CALL_FINISHED, Map.of("response", "secret")));
        adapter.accept(internal(AssistantAgentLifecycleEventType.TOOL_SELECTION_READY,
                Map.of("toolArguments", "secret")));
        adapter.accept(internal(AssistantAgentLifecycleEventType.TOOL_ARGUMENTS_READY,
                Map.of("toolArguments", "secret")));
        adapter.accept(internal(AssistantAgentLifecycleEventType.TOOL_FINISHED, Map.of("toolResult", "secret")));
        adapter.accept(internal(AssistantAgentLifecycleEventType.CONVERSATION_STATE_WRITTEN,
                Map.of("trace", "secret")));

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

        adapter.accept(new AssistantAgentLifecycleEvent(
                AssistantAgentLifecycleEventType.DONE,
                AssistantAgentLifecyclePhase.DONE,
                "run-1",
                "trace-secret",
                null,
                response
        ));

        assertThat(events).hasSize(1);
        AssistantChatStreamEvent done = events.get(0);
        assertThat(done.event()).isEqualTo("done");
        assertThat(done.response().reply()).isEqualTo("你有 2 条待办");
        assertThat(done.response().conversationId()).isEqualTo(1001L);
        assertThat(done.toString()).doesNotContain("trace-secret");
    }

    private static AssistantAgentLifecycleEvent internal(AssistantAgentLifecycleEventType event, Object payload) {
        return new AssistantAgentLifecycleEvent(
                event,
                AssistantAgentLifecyclePhase.RUNNING,
                "run-1",
                "trace-secret",
                null,
                payload
        );
    }
}
