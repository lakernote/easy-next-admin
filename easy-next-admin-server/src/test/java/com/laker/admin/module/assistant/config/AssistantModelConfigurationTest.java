package com.laker.admin.module.assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.module.assistant.runtime.model.SpringAiToolCallingModelClient;
import com.laker.admin.module.assistant.runtime.model.ToolCallingModelClient;
import com.laker.admin.module.assistant.trace.AssistantRunTraceRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantModelConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(EasyJsonCodec.class, () -> new EasyJsonCodec(new ObjectMapper()))
            .withBean(AssistantRunTraceRecorder.class, AssistantRunTraceRecorder::new)
            .withPropertyValues("easy.assistant.model.api-key=test-token")
            .withUserConfiguration(AssistantModelConfiguration.class);

    @Test
    void shouldRegisterOnlyOneSpringAiModelAndOneAgentProtocolAdapter() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ChatModel.class);
            assertThat(context).hasSingleBean(ToolCallingModelClient.class);
            assertThat(context.getBean(ToolCallingModelClient.class))
                    .isInstanceOf(SpringAiToolCallingModelClient.class);
        });
    }

    @Test
    void springAiInnerRetryIsDisabledSoAgentLoopOwnsTheRetryBudget() {
        contextRunner.run(context -> {
            OpenAiChatModel model = (OpenAiChatModel) context.getBean(ChatModel.class);
            RetryTemplate retryTemplate = (RetryTemplate) ReflectionTestUtils.getField(model, "retryTemplate");
            AtomicInteger attempts = new AtomicInteger();

            assertThatThrownBy(() -> retryTemplate.execute(retryContext -> {
                attempts.incrementAndGet();
                throw new TransientAiException("temporary");
            })).isInstanceOf(TransientAiException.class);
            assertThat(attempts).hasValue(1);
        });
    }

    @Test
    void shouldSupportLocalOpenAiCompatibleProviderWithoutApiKey() {
        new ApplicationContextRunner()
                .withBean(RestClient.Builder.class, RestClient::builder)
                .withBean(EasyJsonCodec.class, () -> new EasyJsonCodec(new ObjectMapper()))
                .withBean(AssistantRunTraceRecorder.class, AssistantRunTraceRecorder::new)
                .withUserConfiguration(AssistantModelConfiguration.class)
                .run(context -> assertThat(context).hasSingleBean(ChatModel.class));
    }

    @Test
    void shouldBindOpenAiCompatibleProviderProperties() {
        contextRunner
                .withPropertyValues(
                        "easy.assistant.model.base-url=https://llm.example.com/v1",
                        "easy.assistant.model.completions-path=/chat/completions",
                        "easy.assistant.model.model=qwen-plus",
                        "easy.assistant.model.max-tokens=1600",
                        "easy.assistant.model.max-model-calls=5",
                        "easy.assistant.model.max-tool-calls=8",
                        "easy.assistant.model.max-read-tool-rounds=4",
                        "easy.assistant.model.structured-output-enabled=true"
                )
                .run(context -> {
                    AssistantModelProperties properties = context.getBean(AssistantModelProperties.class);
                    assertThat(properties.getBaseUrl()).isEqualTo("https://llm.example.com/v1");
                    assertThat(properties.getModel()).isEqualTo("qwen-plus");
                    assertThat(properties.getMaxTokens()).isEqualTo(1600);
                    assertThat(properties.getMaxModelCalls()).isEqualTo(5);
                    assertThat(properties.getMaxToolCalls()).isEqualTo(8);
                    assertThat(properties.getMaxReadToolRounds()).isEqualTo(4);
                    assertThat(properties.isStructuredOutputEnabled()).isTrue();
                });
    }

    @Test
    void shouldFailFastWhenRunnerBudgetExceedsEnterpriseSafetyLimit() {
        contextRunner
                .withPropertyValues("easy.assistant.model.max-model-calls=17")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("maxModelCalls")
                            .hasMessageContaining("must be less than or equal to 16");
                });
    }

    @Test
    void shouldFailFastWhenModelBudgetCannotReserveToolStepAndFinalReply() {
        contextRunner
                .withPropertyValues("easy.assistant.model.max-model-calls=1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("maxModelCalls")
                            .hasMessageContaining("must be greater than or equal to 2");
                });
    }

    @Test
    void shouldFailFastWhenReadToolRoundBudgetExceedsSafetyLimit() {
        contextRunner
                .withPropertyValues("easy.assistant.model.max-read-tool-rounds=9")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("maxReadToolRounds")
                            .hasMessageContaining("must be less than or equal to 8");
                });
    }
}
