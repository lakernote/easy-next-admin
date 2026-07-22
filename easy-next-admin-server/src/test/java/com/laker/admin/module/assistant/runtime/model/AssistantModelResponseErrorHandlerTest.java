package com.laker.admin.module.assistant.runtime.model;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssistantModelResponseErrorHandlerTest {
    private final AssistantModelResponseErrorHandler handler = new AssistantModelResponseErrorHandler();

    @Test
    void shouldRetryOnlyTransientHttpFailures() {
        assertFailure(HttpStatus.INTERNAL_SERVER_ERROR, "server failed", ModelCallFailureType.SERVER_ERROR, true);
        assertFailure(HttpStatus.TOO_MANY_REQUESTS, "rate limited", ModelCallFailureType.RATE_LIMIT, true);
        assertFailure(HttpStatus.UNAUTHORIZED, "bad key", ModelCallFailureType.AUTHENTICATION, false);
        assertFailure(HttpStatus.BAD_REQUEST, "invalid request", ModelCallFailureType.CLIENT_ERROR, false);
    }

    @Test
    void quotaExhaustionIsNotTreatedAsRetryableRateLimiting() {
        assertFailure(
                HttpStatus.TOO_MANY_REQUESTS,
                "{\"error\":{\"code\":\"insufficient_quota\"}}",
                ModelCallFailureType.CLIENT_ERROR,
                false
        );
    }

    private void assertFailure(HttpStatus status,
                               String body,
                               ModelCallFailureType expectedType,
                               boolean retryable) {
        MockClientHttpResponse response = new MockClientHttpResponse(body.getBytes(), status);
        assertThatThrownBy(() -> handler.handleError(
                URI.create("https://llm.example.com/chat/completions"),
                HttpMethod.POST,
                response
        )).isInstanceOfSatisfying(ModelCallException.class, failure -> {
            assertThat(failure.failureType()).isEqualTo(expectedType);
            assertThat(failure.retryable()).isEqualTo(retryable);
        });
    }
}
