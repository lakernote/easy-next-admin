package com.laker.admin.module.assistant.trace;

import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.security.masking.EasySensitiveDataMasker;
import com.laker.admin.module.assistant.config.AssistantTraceProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Agent Trace 的统一内容边界。
 *
 * <p>业务组件同时提供低敏元数据和可选内容；生产默认只保留元数据，本地调试才输出脱敏内容。
 * 这样 Trace 是否记录 Prompt、Tool 参数和结果由一个策略决定，不散落在模型适配器或前端。</p>
 */
@Component
public class AssistantTraceContentPolicy {
    private final AssistantTraceProperties properties;
    private final EasySensitiveDataMasker masker;
    private final EasyJsonCodec jsonCodec;

    public AssistantTraceContentPolicy(AssistantTraceProperties properties,
                                       EasySensitiveDataMasker masker,
                                       EasyJsonCodec jsonCodec) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.masker = masker;
        this.jsonCodec = jsonCodec;
    }

    static AssistantTraceContentPolicy metadataOnly() {
        return new AssistantTraceContentPolicy(new AssistantTraceProperties(), null, null);
    }

    public Object capture(Object metadata, Object content) {
        Object safeMetadata = sanitize(metadata);
        if (properties.getContentMode() == AssistantTraceContentMode.METADATA_ONLY || content == null) {
            return safeMetadata;
        }
        Object safeContent = sanitize(content);
        if (safeMetadata instanceof Map<?, ?> metadataMap && safeContent instanceof Map<?, ?> contentMap) {
            Map<String, Object> merged = new LinkedHashMap<>();
            contentMap.forEach((key, value) -> merged.put(String.valueOf(key), value));
            metadataMap.forEach((key, value) -> merged.put(String.valueOf(key), value));
            return merged;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("metadata", safeMetadata);
        payload.put("content", safeContent);
        return payload;
    }

    public String sanitizeErrorMessage(String message) {
        if (message == null) {
            return null;
        }
        if (message.isBlank()) {
            return "步骤执行失败";
        }
        if (masker == null) {
            return "步骤执行失败";
        }
        return masker.truncate(masker.maskText(message), Math.min(properties.getMaxPayloadLength(), 1_000));
    }

    private Object sanitize(Object value) {
        if (value == null || masker == null || jsonCodec == null) {
            return value;
        }
        String json = masker.toSanitizedJson(value, properties.getMaxPayloadLength());
        try {
            return jsonCodec.fromJson(json, Object.class);
        } catch (RuntimeException ignored) {
            // 超长 JSON 被截断后不再是合法 JSON；保留已脱敏、已限长的文本即可。
            return json;
        }
    }
}
