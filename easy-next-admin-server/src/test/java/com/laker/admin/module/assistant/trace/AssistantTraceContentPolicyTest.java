package com.laker.admin.module.assistant.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.security.masking.EasySensitiveDataMasker;
import com.laker.admin.module.assistant.config.AssistantTraceProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantTraceContentPolicyTest {

    @Test
    void shouldKeepOnlyMetadataByDefault() {
        AssistantTraceContentPolicy policy = policy(new AssistantTraceProperties());

        Object payload = policy.capture(
                Map.of("messageCount", 1),
                Map.of("message", "内部业务内容", "password", "secret")
        );

        assertThat(payload).isEqualTo(Map.of("messageCount", 1));
    }

    @Test
    void shouldMergeSanitizedContentForExplicitLocalDebugging() {
        AssistantTraceProperties properties = new AssistantTraceProperties();
        properties.setContentMode(AssistantTraceContentMode.SANITIZED_CONTENT);
        AssistantTraceContentPolicy policy = policy(properties);

        Object payload = policy.capture(
                Map.of("messageCount", 1),
                Map.of("message", "查询用户", "password", "secret", "phone", "13800000000")
        );

        assertThat(payload).isInstanceOfSatisfying(Map.class, value -> assertThat(value)
                .containsEntry("messageCount", 1)
                .containsEntry("message", "查询用户")
                .containsEntry("password", "******")
                .containsEntry("phone", "138****0000"));
    }

    private static AssistantTraceContentPolicy policy(AssistantTraceProperties properties) {
        ObjectMapper objectMapper = new ObjectMapper();
        return new AssistantTraceContentPolicy(
                properties,
                new EasySensitiveDataMasker(objectMapper),
                new EasyJsonCodec(objectMapper)
        );
    }
}
