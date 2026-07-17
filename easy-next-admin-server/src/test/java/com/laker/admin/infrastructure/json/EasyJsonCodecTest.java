package com.laker.admin.infrastructure.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EasyJsonCodecTest {

    private final EasyJsonCodec codec = new EasyJsonCodec(new ObjectMapper());

    @Test
    void shouldSerializeAndDeserializeObject() {
        String json = codec.toJson(Map.of("name", "easy-next-admin", "enabled", true));
        Map<String, Object> value = codec.fromJson(json, new TypeReference<>() {
        });

        assertThat(json).contains("\"name\":\"easy-next-admin\"");
        assertThat(value).containsEntry("name", "easy-next-admin").containsEntry("enabled", true);
    }

    @Test
    void shouldWrapInvalidJsonException() {
        assertThatThrownBy(() -> codec.fromJson("{", new TypeReference<Map<String, Object>>() {
                }))
                .isInstanceOf(EasyJsonException.class)
                .hasMessageContaining("JSON 反序列化失败");
    }
}
