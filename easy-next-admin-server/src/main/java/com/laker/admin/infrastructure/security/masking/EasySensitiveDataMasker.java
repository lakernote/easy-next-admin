package com.laker.admin.infrastructure.security.masking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.security.Principal;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 统一处理审计、接口日志、异常文本、URI 查询参数和 Map 参数等非 DTO 场景的脱敏。
 * DTO 字段输出优先使用 {@link EasyMask}，无法通过字段注解表达的边界统一走本组件。
 */
@Component
public class EasySensitiveDataMasker {
    public static final int DEFAULT_TEXT_LIMIT = 8000;
    private static final Set<String> FULL_MASK_KEYS = Set.of(
            "password", "passwd", "pwd", "token", "accesstoken", "refreshtoken",
            "authorization", "captcha", "captchacode", "captchaid", "secret",
            "credential", "credentials", "apikey", "appsecret"
    );
    private static final Set<String> PHONE_KEYS = Set.of("phone", "mobile", "tel", "telephone");
    private static final Set<String> EMAIL_KEYS = Set.of("email", "mail");
    private static final Set<String> NAME_KEYS = Set.of("realname");
    private static final Set<String> ID_CARD_KEYS = Set.of("idcard", "identityno", "identitynumber", "idnumber");
    private static final Set<String> BANK_CARD_KEYS = Set.of("bankcard", "cardno", "cardnumber", "bankaccount");

    private final ObjectMapper objectMapper;

    public EasySensitiveDataMasker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toSanitizedJson(Object value) {
        return toSanitizedJson(value, DEFAULT_TEXT_LIMIT);
    }

    public String toSanitizedCompactJson(Object value) {
        return toSanitizedCompactJson(value, DEFAULT_TEXT_LIMIT);
    }

    public String toSanitizedCompactJson(Object value, int maxLength) {
        try {
            JsonNode jsonNode = objectMapper.valueToTree(sanitizeValue(value));
            JsonNode masked = maskNode(null, jsonNode);
            JsonNode compact = compactNode(masked);
            if (compact == null || compact.isNull()) {
                return null;
            }
            return truncate(objectMapper.writeValueAsString(compact), maxLength);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            return truncate(maskText(String.valueOf(value)), maxLength);
        }
    }

    public String toSanitizedJson(Object value, int maxLength) {
        try {
            JsonNode jsonNode = objectMapper.valueToTree(sanitizeValue(value));
            JsonNode masked = maskNode(null, jsonNode);
            return truncate(objectMapper.writeValueAsString(masked), maxLength);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            return truncate(maskText(String.valueOf(value)), maxLength);
        }
    }

    public String sanitizeJsonText(String text) {
        return sanitizeJsonText(text, DEFAULT_TEXT_LIMIT);
    }

    public String sanitizeJsonText(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String trimmed = text.trim();
        if (!looksLikeJson(trimmed)) {
            return truncate(maskText(text), maxLength);
        }
        try {
            JsonNode jsonNode = objectMapper.readTree(trimmed);
            return truncate(objectMapper.writeValueAsString(maskNode(null, jsonNode)), maxLength);
        } catch (JsonProcessingException e) {
            return truncate(maskText(text), maxLength);
        }
    }

    public Object sanitizeValue(Object value) {
        if (value == null || isSimpleValue(value)) {
            return value;
        }
        if (isSkippedValue(value)) {
            return skippedSummary(value);
        }
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> sanitized = new LinkedHashMap<>(source.size());
            source.forEach((key, mapValue) -> {
                String keyText = String.valueOf(key);
                sanitized.put(keyText, maskByKeyOrSanitize(keyText, mapValue));
            });
            return sanitized;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> sanitized = new ArrayList<>(collection.size());
            collection.forEach(item -> sanitized.add(sanitizeValue(item)));
            return sanitized;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> sanitized = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                sanitized.add(sanitizeValue(Array.get(value, i)));
            }
            return sanitized;
        }
        return value;
    }

    public boolean isRequestInfrastructureValue(Object value) {
        return value != null && isSkippedValue(value);
    }

    public String maskText(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String result = text;
        for (String key : FULL_MASK_KEYS) {
            result = maskTextValue(result, key);
        }
        for (String key : PHONE_KEYS) {
            result = result.replaceAll("(?i)(\"" + key + "\"\\s*:\\s*\")([^\"]*)(\")", matchPhoneJson(key));
            result = result.replaceAll("(?i)(\\b" + key + "\\b\\s*=\\s*)([^&\\s,;]+)", "$1" + EasyMaskingSupport.MASK);
        }
        for (String key : EMAIL_KEYS) {
            result = result.replaceAll("(?i)(\"" + key + "\"\\s*:\\s*\")([^\"]*)(\")", matchEmailJson(key));
            result = result.replaceAll("(?i)(\\b" + key + "\\b\\s*=\\s*)([^&\\s,;]+)", "$1" + EasyMaskingSupport.MASK);
        }
        for (String key : NAME_KEYS) {
            result = maskTextValue(result, key);
        }
        for (String key : ID_CARD_KEYS) {
            result = maskTextValue(result, key);
        }
        for (String key : BANK_CARD_KEYS) {
            result = maskTextValue(result, key);
        }
        return result;
    }

    public String maskUri(String uri) {
        if (!StringUtils.hasText(uri)) {
            return uri;
        }
        int index = uri.indexOf('?');
        if (index < 0 || index == uri.length() - 1) {
            return uri;
        }
        String path = uri.substring(0, index + 1);
        String query = uri.substring(index + 1);
        String[] pairs = query.split("&", -1);
        for (int i = 0; i < pairs.length; i++) {
            int eqIndex = pairs[i].indexOf('=');
            if (eqIndex <= 0) {
                continue;
            }
            String key = pairs[i].substring(0, eqIndex);
            if (isSensitiveKey(key)) {
                pairs[i] = key + "=" + EasyMaskingSupport.MASK;
            }
        }
        return path + String.join("&", pairs);
    }

    public String changedFields(String beforeJson, String afterJson) {
        if (!StringUtils.hasText(beforeJson) || !StringUtils.hasText(afterJson)) {
            return null;
        }
        try {
            JsonNode before = objectMapper.readTree(beforeJson);
            JsonNode after = objectMapper.readTree(afterJson);
            if (!before.isObject() || !after.isObject()) {
                return Objects.equals(before, after) ? "" : "*";
            }
            Set<String> fieldNames = new LinkedHashSet<>();
            before.fieldNames().forEachRemaining(fieldNames::add);
            after.fieldNames().forEachRemaining(fieldNames::add);
            List<String> changed = fieldNames.stream()
                    .filter(field -> !Objects.equals(before.get(field), after.get(field)))
                    .toList();
            return truncate(String.join(",", changed), 1000);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 14)) + "...(truncated)";
    }

    private Object maskByKeyOrSanitize(String key, Object value) {
        if (!isSensitiveKey(key)) {
            return sanitizeValue(value);
        }
        return maskSensitiveValue(key, value == null ? null : String.valueOf(value));
    }

    private JsonNode maskNode(String fieldName, JsonNode node) {
        if (node == null || node.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (StringUtils.hasText(fieldName) && isSensitiveKey(fieldName)) {
            return TextNode.valueOf(maskSensitiveValue(fieldName, node.isTextual() ? node.asText() : node.toString()));
        }
        if (node.isObject()) {
            ObjectNode objectNode = node.deepCopy();
            List<Map.Entry<String, JsonNode>> entries = new ArrayList<>(objectNode.properties());
            for (Map.Entry<String, JsonNode> entry : entries) {
                objectNode.set(entry.getKey(), maskNode(entry.getKey(), entry.getValue()));
            }
            return objectNode;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = node.deepCopy();
            for (int i = 0; i < arrayNode.size(); i++) {
                arrayNode.set(i, maskNode(null, arrayNode.get(i)));
            }
            return arrayNode;
        }
        return node;
    }

    private JsonNode compactNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return NullNode.getInstance();
        }
        if (node.isTextual() && !StringUtils.hasText(node.asText())) {
            return NullNode.getInstance();
        }
        if (node.isObject()) {
            ObjectNode compact = JsonNodeFactory.instance.objectNode();
            node.properties().forEach(entry -> {
                JsonNode child = compactNode(entry.getValue());
                if (child != null && !child.isNull()) {
                    compact.set(entry.getKey(), child);
                }
            });
            return compact;
        }
        if (node.isArray()) {
            ArrayNode compact = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> {
                JsonNode child = compactNode(item);
                if (child != null && !child.isNull()) {
                    compact.add(child);
                }
            });
            return compact;
        }
        return node;
    }

    private String maskSensitiveValue(String key, String value) {
        if (value == null) {
            return null;
        }
        String normalized = normalizeKey(key);
        if (PHONE_KEYS.stream().anyMatch(normalized::contains)) {
            return EasyMaskingSupport.mask(EasyMaskType.PHONE, value, 0, 0);
        }
        if (EMAIL_KEYS.stream().anyMatch(normalized::contains)) {
            return EasyMaskingSupport.mask(EasyMaskType.EMAIL, value, 0, 0);
        }
        if (NAME_KEYS.stream().anyMatch(normalized::contains)) {
            return EasyMaskingSupport.mask(EasyMaskType.NAME, value, 0, 0);
        }
        if (ID_CARD_KEYS.stream().anyMatch(normalized::contains)) {
            return EasyMaskingSupport.mask(EasyMaskType.ID_CARD, value, 0, 0);
        }
        if (BANK_CARD_KEYS.stream().anyMatch(normalized::contains)) {
            return EasyMaskingSupport.mask(EasyMaskType.BANK_CARD, value, 0, 0);
        }
        return EasyMaskingSupport.MASK;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = normalizeKey(key);
        return FULL_MASK_KEYS.stream().anyMatch(normalized::contains)
                || PHONE_KEYS.stream().anyMatch(normalized::contains)
                || EMAIL_KEYS.stream().anyMatch(normalized::contains)
                || NAME_KEYS.stream().anyMatch(normalized::contains)
                || ID_CARD_KEYS.stream().anyMatch(normalized::contains)
                || BANK_CARD_KEYS.stream().anyMatch(normalized::contains);
    }

    private String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        return key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private boolean isSimpleValue(Object value) {
        return value instanceof CharSequence
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Enum<?>
                || value instanceof TemporalAccessor;
    }

    private boolean isSkippedValue(Object value) {
        return value instanceof ServletRequest
                || value instanceof ServletResponse
                || value instanceof MultipartFile
                || value instanceof BindingResult
                || value instanceof InputStream
                || value instanceof OutputStream
                || value instanceof Principal;
    }

    private String skippedSummary(Object value) {
        if (value instanceof MultipartFile multipartFile) {
            return "MultipartFile(" + multipartFile.getOriginalFilename() + ")";
        }
        return value.getClass().getSimpleName();
    }

    private boolean looksLikeJson(String text) {
        return (text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"));
    }

    private String matchPhoneJson(String key) {
        return "\"" + key + "\":\"" + EasyMaskingSupport.MASK + "\"";
    }

    private String matchEmailJson(String key) {
        return "\"" + key + "\":\"" + EasyMaskingSupport.MASK + "\"";
    }

    private String maskTextValue(String text, String key) {
        String result = text.replaceAll("(?i)(\"" + key + "\"\\s*:\\s*\")([^\"]*)(\")", "$1" + EasyMaskingSupport.MASK + "$3");
        return result.replaceAll("(?i)(\\b" + key + "\\b\\s*=\\s*)([^&\\s,;]+)", "$1" + EasyMaskingSupport.MASK);
    }
}
