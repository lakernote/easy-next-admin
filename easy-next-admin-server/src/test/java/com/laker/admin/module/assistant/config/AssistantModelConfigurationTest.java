package com.laker.admin.module.assistant.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.module.assistant.runtime.model.SpringAiToolCallingModelClient;
import com.laker.admin.module.assistant.runtime.model.ToolCallingModelClient;
import com.laker.admin.module.assistant.trace.AssistantRunTraceRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

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
                        "easy.assistant.model.max-tool-calls=8"
                )
                .run(context -> {
                    AssistantModelProperties properties = context.getBean(AssistantModelProperties.class);
                    assertThat(properties.getBaseUrl()).isEqualTo("https://llm.example.com/v1");
                    assertThat(properties.getModel()).isEqualTo("qwen-plus");
                    assertThat(properties.getMaxTokens()).isEqualTo(1600);
                    assertThat(properties.getMaxModelCalls()).isEqualTo(5);
                    assertThat(properties.getMaxToolCalls()).isEqualTo(8);
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
}
