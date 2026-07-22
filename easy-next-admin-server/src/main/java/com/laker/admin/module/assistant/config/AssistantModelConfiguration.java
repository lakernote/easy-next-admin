package com.laker.admin.module.assistant.config;

import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.module.assistant.runtime.model.AssistantModelResponseErrorHandler;
import com.laker.admin.module.assistant.runtime.model.SpringAiToolCallingModelClient;
import com.laker.admin.module.assistant.runtime.model.ToolCallingModelClient;
import com.laker.admin.module.assistant.trace.AssistantRunTraceRecorder;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({AssistantModelProperties.class, AssistantTraceProperties.class})
public class AssistantModelConfiguration {

    @Bean
    public ChatModel assistantChatModel(RestClient.Builder restClientBuilder,
                                        AssistantModelProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        Duration timeout = properties.getTimeout() == null
                || properties.getTimeout().isZero()
                || properties.getTimeout().isNegative()
                ? Duration.ofSeconds(20)
                : properties.getTimeout();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        OpenAiApi.Builder apiBuilder = OpenAiApi.builder()
                .baseUrl(properties.getBaseUrl())
                .completionsPath(properties.getCompletionsPath())
                .responseErrorHandler(new AssistantModelResponseErrorHandler())
                .restClientBuilder(restClientBuilder.requestFactory(requestFactory));
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            apiBuilder.apiKey(new NoopApiKey());
        } else {
            apiBuilder.apiKey(properties.getApiKey());
        }
        OpenAiApi openAiApi = apiBuilder.build();
        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(properties.getModel())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .internalToolExecutionEnabled(false)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                // 重试由 AgentLoop 的统一预算负责，避免 Spring AI 内层形成不可见的第二套重试。
                .retryTemplate(RetryTemplate.builder().maxAttempts(1).build())
                .build();
    }

    @Bean
    public ToolCallingModelClient toolCallingModelClient(ChatModel assistantChatModel,
                                                         AssistantModelProperties properties,
                                                         AssistantRunTraceRecorder traceRecorder,
                                                         EasyJsonCodec jsonCodec) {
        return new SpringAiToolCallingModelClient(
                assistantChatModel,
                properties,
                traceRecorder,
                jsonCodec
        );
    }

}
