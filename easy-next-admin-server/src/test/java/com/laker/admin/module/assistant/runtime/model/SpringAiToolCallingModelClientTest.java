package com.laker.admin.module.assistant.runtime.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.security.masking.EasySensitiveDataMasker;
import com.laker.admin.module.assistant.config.AssistantModelProperties;
import com.laker.admin.module.assistant.config.AssistantTraceProperties;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import com.laker.admin.module.assistant.tool.ToolExecutionMode;
import com.laker.admin.module.assistant.tool.ToolRiskLevel;
import com.laker.admin.module.assistant.trace.AssistantRunTraceRecorder;
import com.laker.admin.module.assistant.trace.AssistantModelCallMode;
import com.laker.admin.module.assistant.trace.AssistantTraceContentMode;
import com.laker.admin.module.assistant.trace.AssistantTraceContentPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiToolCallingModelClientTest {

    @Test
    void shouldUseSpringAiProtocolWhileKeepingExecutionUserControlled() {
        ChatModel chatModel = mock(ChatModel.class);
        OpenAiApi.Usage nativeUsage = new OpenAiApi.Usage(
                18,
                42,
                60,
                new OpenAiApi.Usage.PromptTokensDetails(0, 12),
                new OpenAiApi.Usage.CompletionTokenDetails(3, 0, 0, 0)
        );
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(
                        AssistantMessage.builder()
                                .content("")
                                .toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "call-1", "function", "workflow_process_query", "{\"status\":\"RUNNING\"}"
                                )))
                                .build(),
                        ChatGenerationMetadata.builder().finishReason("tool_calls").build()
                )
        ), ChatResponseMetadata.builder()
                .model("provider-model")
                .usage(new DefaultUsage(42, 18, 60, nativeUsage))
                .build()));
        AssistantModelProperties properties = new AssistantModelProperties();
        AssistantRunTraceRecorder traceRecorder = contentTraceRecorder();
        SpringAiToolCallingModelClient client = new SpringAiToolCallingModelClient(
                chatModel,
                properties,
                traceRecorder,
                new EasyJsonCodec(new ObjectMapper())
        );

        ToolCallingResponse response = client.call(new ToolCallingRequest(
                List.of(ToolCallingMessage.system("你是企业 Agent"), ToolCallingMessage.user("查进行中流程")),
                List.of(definition()),
                "agent_step",
                200
        ));

        assertThat(response.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("workflow.process.query");
            assertThat(call.arguments()).containsEntry("status", "RUNNING");
        });
        assertThat(response.finishReason()).isEqualTo(ModelFinishReason.TOOL_CALLS);
        assertThat(response.providerFinishReason()).isEqualTo("tool_calls");
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        OpenAiChatOptions options = (OpenAiChatOptions) promptCaptor.getValue().getOptions();
        assertThat(options.getInternalToolExecutionEnabled()).isFalse();
        assertThat(options.getMaxTokens()).isEqualTo(200);
        assertThat(options.getToolCallbacks()).singleElement().satisfies(callback ->
                assertThat(callback.getToolDefinition().name()).isEqualTo("workflow_process_query")
        );
        assertThat(traceRecorder.modelCallsSince(0)).singleElement().satisfies(call -> {
            assertThat(call.status()).isEqualTo("OK");
            assertThat(call.timing().mode()).isEqualTo(AssistantModelCallMode.SYNCHRONOUS);
            assertThat(call.timing().providerRoundTripDurationMs()).isGreaterThanOrEqualTo(0);
            assertThat(call.timing().timeToFirstTokenDurationMs()).isNull();
            assertThat(call.timing().generationDurationMs()).isNull();
            assertThat(call.tokenUsage().cachedPromptTokens()).isEqualTo(12);
            assertThat(call.tokenUsage().reasoningTokens()).isEqualTo(3);
            assertThat(call.requestPayload()).isInstanceOfSatisfying(Map.class, payload -> assertThat(payload)
                    .containsEntry("messageCount", 2)
                    .containsEntry("toolCount", 1)
                    .containsEntry("maxOutputTokens", 200)
                    .containsKeys("messages", "tools", "options"));
            assertThat(call.requestPayload().toString())
                    .contains("你是企业 Agent", "查进行中流程", "workflow_process_query", "maxOutputTokens=200");
            assertThat(call.responsePayload()).isInstanceOfSatisfying(Map.class, payload -> {
                assertThat(payload)
                        .containsEntry("toolCallCount", 1)
                        .containsEntry("finishReason", "TOOL_CALLS")
                        .containsEntry("providerFinishReason", "tool_calls")
                        .containsKeys("providerOutput", "runtimeNormalizedResponse");
                assertThat(payload.get("toolCalls").toString()).contains("call-1", "workflow.process.query");
                assertThat(payload.get("providerOutput").toString())
                        .contains("call-1", "workflow_process_query", "{\"status\":\"RUNNING\"}");
                assertThat(payload.get("runtimeNormalizedResponse").toString())
                        .contains("workflow.process.query", "status=RUNNING");
            });
        });
    }

    @Test
    void shouldRecordEmptyModelResponseOnceAsFailure() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of()));
        AssistantRunTraceRecorder traceRecorder = new AssistantRunTraceRecorder();
        AssistantModelProperties properties = new AssistantModelProperties();
        SpringAiToolCallingModelClient client = new SpringAiToolCallingModelClient(
                chatModel,
                properties,
                traceRecorder,
                new EasyJsonCodec(new ObjectMapper())
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.call(new ToolCallingRequest(
                        List.of(ToolCallingMessage.user("你好")),
                        List.of()
                )))
                .isInstanceOf(IllegalStateException.class);

        assertThat(traceRecorder.modelCallsSince(0))
                .singleElement()
                .satisfies(call -> {
                    assertThat(call.status()).isEqualTo("FAILED");
                    assertThat(call.requestPayload()).isInstanceOfSatisfying(Map.class, payload -> assertThat(payload)
                            .containsEntry("maxOutputTokens", properties.getMaxTokens()));
                });
    }

    @Test
    void shouldPreserveMalformedToolArgumentsAsRecoverableProtocolError() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(AssistantMessage.builder()
                        .content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall(
                                "call-invalid", "function", "workflow_process_query", "{invalid-json"
                        )))
                        .build())
        )));
        SpringAiToolCallingModelClient client = new SpringAiToolCallingModelClient(
                chatModel,
                new AssistantModelProperties(),
                new AssistantRunTraceRecorder(),
                new EasyJsonCodec(new ObjectMapper())
        );

        ToolCallingResponse response = client.call(new ToolCallingRequest(
                List.of(ToolCallingMessage.user("查询流程")),
                List.of(definition())
        ));

        assertThat(response.toolCalls()).singleElement().satisfies(call -> {
            assertThat(call.name()).isEqualTo("workflow.process.query");
            assertThat(call.arguments()).isEmpty();
            assertThat(call.hasArgumentError()).isTrue();
            assertThat(call.argumentError()).contains("不是合法 JSON 对象");
        });
    }

    @Test
    void shouldExposeUniqueProviderSafeNamesWhenCanonicalNamesCollideAfterMapping() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(AssistantMessage.builder().content("无需调用工具").build())
        )));
        SpringAiToolCallingModelClient client = new SpringAiToolCallingModelClient(
                chatModel,
                new AssistantModelProperties(),
                new AssistantRunTraceRecorder(),
                new EasyJsonCodec(new ObjectMapper())
        );
        ToolDefinition underscoredDefinition = new ToolDefinition(
                "workflow_process_query",
                "查询流程",
                "查询当前用户可见流程。",
                "",
                ToolExecutionMode.READ_ONLY,
                ToolRiskLevel.LOW,
                List.of(),
                List.of()
        );

        client.call(new ToolCallingRequest(
                List.of(ToolCallingMessage.system("你是企业 Agent"), ToolCallingMessage.user("你好")),
                List.of(definition(), underscoredDefinition)
        ));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        OpenAiChatOptions options = (OpenAiChatOptions) promptCaptor.getValue().getOptions();
        assertThat(options.getToolCallbacks())
                .extracting(callback -> callback.getToolDefinition().name())
                .hasSize(2)
                .doesNotHaveDuplicates()
                .allMatch(name -> name.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void shouldAllowParallelCallsOnlyWhenAllVisibleToolsAreReadOnly() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(org.mockito.ArgumentMatchers.any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(AssistantMessage.builder().content("完成").build())
        )));
        SpringAiToolCallingModelClient client = new SpringAiToolCallingModelClient(
                chatModel,
                new AssistantModelProperties(),
                new AssistantRunTraceRecorder(),
                new EasyJsonCodec(new ObjectMapper())
        );

        client.call(new ToolCallingRequest(
                List.of(ToolCallingMessage.user("查询")),
                List.of(definition())
        ));
        client.call(new ToolCallingRequest(
                List.of(ToolCallingMessage.user("审批")),
                List.of(definition(), writeDefinition())
        ));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(promptCaptor.capture());
        assertThat(promptCaptor.getAllValues())
                .extracting(prompt -> ((OpenAiChatOptions) prompt.getOptions()).getParallelToolCalls())
                .containsExactly(true, false);
    }

    private static AssistantRunTraceRecorder contentTraceRecorder() {
        ObjectMapper objectMapper = new ObjectMapper();
        AssistantTraceProperties traceProperties = new AssistantTraceProperties();
        traceProperties.setContentMode(AssistantTraceContentMode.SANITIZED_CONTENT);
        traceProperties.setMaxPayloadLength(100_000);
        return new AssistantRunTraceRecorder(
                null,
                new AssistantTraceContentPolicy(
                        traceProperties,
                        new EasySensitiveDataMasker(objectMapper),
                        new EasyJsonCodec(objectMapper)
                )
        );
    }

    private static ToolDefinition definition() {
        return new ToolDefinition(
                "workflow.process.query",
                "查询流程",
                "查询当前用户可见流程。",
                "",
                ToolExecutionMode.READ_ONLY,
                ToolRiskLevel.LOW,
                List.of(),
                List.of()
        );
    }

    private static ToolDefinition writeDefinition() {
        return new ToolDefinition(
                "workflow.task.approve",
                "审批待办",
                "准备审批当前用户可处理的待办，确认后执行。",
                "workflow:task:approve",
                ToolExecutionMode.REQUIRES_CONFIRMATION,
                ToolRiskLevel.HIGH,
                List.of(),
                List.of()
        );
    }
}
