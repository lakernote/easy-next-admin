package com.laker.admin.infrastructure.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 平台 JSON 编解码门面。
 *
 * <p>业务和基础设施代码通过该类使用 JSON，避免散落依赖 ObjectMapper 和 Jackson 异常。
 * Jackson 仍由 {@code EasyJacksonCustomizer} 统一配置。</p>
 */
@Component
public class EasyJsonCodec {

    private final ObjectMapper objectMapper;

    public EasyJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new EasyJsonException("JSON 序列化失败", e);
        }
    }

    public <T> T fromJson(String json, Class<T> valueType) {
        try {
            return objectMapper.readValue(json, valueType);
        } catch (JsonProcessingException e) {
            throw new EasyJsonException("JSON 反序列化失败", e);
        }
    }

    public <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new EasyJsonException("JSON 反序列化失败", e);
        }
    }

    public JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new EasyJsonException("JSON 树解析失败", e);
        }
    }
}
