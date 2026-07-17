package com.laker.admin.module.assistant.runtime.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ModelFinishReasonTest {

    @ParameterizedTest
    @CsvSource(value = {
            "stop, STOP",
            "end_turn, STOP",
            "tool_calls, TOOL_CALLS",
            "tool-use, TOOL_CALLS",
            "length, MAX_TOKENS",
            "max_tokens, MAX_TOKENS",
            "content_filter, CONTENT_FILTER",
            "SAFETY, CONTENT_FILTER",
            "refusal, REFUSAL",
            "pause_turn, PAUSED",
            "malformed_function_call, ERROR",
            "future_provider_reason, UNKNOWN"
    })
    void shouldNormalizeProviderFinishReason(String providerValue, ModelFinishReason expected) {
        assertThat(ModelFinishReason.fromProvider(providerValue)).isEqualTo(expected);
    }
}
