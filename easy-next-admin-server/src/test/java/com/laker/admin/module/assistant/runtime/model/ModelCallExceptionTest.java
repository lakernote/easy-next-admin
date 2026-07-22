package com.laker.admin.module.assistant.runtime.model;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCallExceptionTest {

    @Test
    void nestedTimeoutIsRetryable() {
        ModelCallException failure = ModelCallException.classify(new IllegalStateException(
                "provider call failed",
                new HttpTimeoutException("request timed out")
        ));

        assertThat(failure.failureType()).isEqualTo(ModelCallFailureType.TIMEOUT);
        assertThat(failure.retryable()).isTrue();
    }

    @Test
    void timeoutMessageFallbackIsRetryable() {
        ModelCallException failure = ModelCallException.classify(
                new IllegalStateException("Timeout on blocking read for 60000 MILLISECONDS")
        );

        assertThat(failure.failureType()).isEqualTo(ModelCallFailureType.TIMEOUT);
        assertThat(failure.retryable()).isTrue();
    }

    @Test
    void nestedConnectFailureIsRetryable() {
        ModelCallException failure = ModelCallException.classify(new IllegalStateException(
                "provider call failed",
                new ConnectException("connection refused")
        ));

        assertThat(failure.failureType()).isEqualTo(ModelCallFailureType.NETWORK);
        assertThat(failure.retryable()).isTrue();
    }

    @Test
    void unknownRuntimeFailureIsNotRetryable() {
        ModelCallException failure = ModelCallException.classify(new IllegalStateException("adapter bug"));

        assertThat(failure.failureType()).isEqualTo(ModelCallFailureType.UNKNOWN);
        assertThat(failure.retryable()).isFalse();
    }
}
