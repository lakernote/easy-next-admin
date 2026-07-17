package com.laker.admin.module.assistant.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolRunStatus;
import com.laker.admin.module.assistant.tool.ToolDefinition;
import com.laker.admin.module.assistant.tool.ToolOutputField;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionPlan;
import com.laker.admin.module.assistant.tool.execution.ToolExecutionStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 把完整 Tool Result 转成模型需要的紧凑 observation。
 *
 * <p>Run Record 仍保留完整结果；这里只控制回填模型的字段、列表数量和文本长度。</p>
 */
@Component
public class ToolObservationFormatter {
    private static final int MAX_OBSERVATION_LENGTH = 6_000;
    private static final CompactPolicy DEFAULT_POLICY = new CompactPolicy(5, 320, 12, 5);
    private static final CompactPolicy TIGHT_POLICY = new CompactPolicy(2, 160, 8, 4);

    private final EasyJsonCodec jsonCodec;

    public ToolObservationFormatter(EasyJsonCodec jsonCodec) {
        this.jsonCodec = Objects.requireNonNull(jsonCodec, "jsonCodec must not be null");
    }

    public String format(AssistantToolResult result,
                         ToolExecutionPlan plan,
                         ToolDefinition definition) {
        String observation = encode(result, plan, definition, DEFAULT_POLICY);
        if (observation.length() <= MAX_OBSERVATION_LENGTH) {
            return observation;
        }
        observation = encode(result, plan, definition, TIGHT_POLICY);
        if (observation.length() <= MAX_OBSERVATION_LENGTH) {
            return observation;
        }

        Map<String, Object> fallback = basePayload(result);
        fallback.put("message", boundedSummary(result));
        fallback.put("truncated", true);
        addNextStep(fallback, plan);
        return jsonCodec.toJson(fallback);
    }

    private String encode(AssistantToolResult result,
                          ToolExecutionPlan plan,
                          ToolDefinition definition,
                          CompactPolicy policy) {
        Map<String, Object> payload = basePayload(result);
        CompactState compactState = new CompactState();
        JsonNode data = compactData(result, definition, policy, compactState);
        boolean hasData = hasData(data);
        if (hasData) {
            payload.put("data", data);
        }
        if (!hasData || result == null || result.status() != AssistantToolRunStatus.EXECUTED) {
            payload.put("message", boundedSummary(result));
        }
        if (compactState.truncated) {
            payload.put("truncated", true);
        }
        addNextStep(payload, plan);
        return jsonCodec.toJson(payload);
    }

    private static Map<String, Object> basePayload(AssistantToolResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", result == null ? AssistantToolRunStatus.FAILED.name() : result.status().name());
        if (result != null && result.status() == AssistantToolRunStatus.EXECUTED) {
            payload.put("complete", true);
        }
        return payload;
    }

    private JsonNode compactData(AssistantToolResult result,
                                 ToolDefinition definition,
                                 CompactPolicy policy,
                                 CompactState state) {
        if (result == null || result.data() == null) {
            return null;
        }
        try {
            JsonNode source = jsonCodec.readTree(jsonCodec.toJson(result.data()));
            JsonNode contracted = selectContractFields(source, definition, state);
            return compact(contracted, policy, state, 0);
        } catch (RuntimeException ignored) {
            state.truncated = true;
            return null;
        }
    }

    private static JsonNode selectContractFields(JsonNode source,
                                                 ToolDefinition definition,
                                                 CompactState state) {
        if (source == null || !source.isObject() || definition == null || definition.outputSchema().isEmpty()) {
            return source;
        }
        Set<String> allowed = definition.outputSchema().stream()
                .map(ToolOutputField::name)
                .collect(Collectors.toUnmodifiableSet());
        ObjectNode selected = JsonNodeFactory.instance.objectNode();
        source.properties().forEach(entry -> {
            if (allowed.contains(entry.getKey())) {
                selected.set(entry.getKey(), entry.getValue());
            } else {
                state.truncated = true;
            }
        });
        return selected;
    }

    private static JsonNode compact(JsonNode source,
                                    CompactPolicy policy,
                                    CompactState state,
                                    int depth) {
        if (source == null || source.isNull() || source.isMissingNode()) {
            return null;
        }
        if (depth >= policy.maxDepth()) {
            state.truncated = true;
            return JsonNodeFactory.instance.textNode("…");
        }
        if (source.isTextual()) {
            String value = normalizeWhitespace(source.asText());
            if (!StringUtils.hasText(value)) {
                return null;
            }
            if (value.length() > policy.maxTextLength()) {
                state.truncated = true;
                value = value.substring(0, policy.maxTextLength()) + "…";
            }
            return JsonNodeFactory.instance.textNode(value);
        }
        if (source.isArray()) {
            ArrayNode compacted = JsonNodeFactory.instance.arrayNode();
            int included = 0;
            for (JsonNode item : source) {
                if (included >= policy.maxArrayItems()) {
                    state.truncated = true;
                    break;
                }
                JsonNode compactedItem = compact(item, policy, state, depth + 1);
                if (compactedItem != null) {
                    compacted.add(compactedItem);
                    included++;
                }
            }
            return compacted;
        }
        if (source.isObject()) {
            ObjectNode compacted = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = source.properties().iterator();
            int included = 0;
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (included >= policy.maxObjectFields()) {
                    state.truncated = true;
                    break;
                }
                JsonNode compactedValue = compact(field.getValue(), policy, state, depth + 1);
                if (compactedValue != null) {
                    compacted.set(field.getKey(), compactedValue);
                    included++;
                }
            }
            return compacted;
        }
        return source.deepCopy();
    }

    private static void addNextStep(Map<String, Object> payload, ToolExecutionPlan plan) {
        if (plan == null || (plan.status() != ToolExecutionStatus.INPUT_REQUIRED
                && plan.status() != ToolExecutionStatus.APPROVAL_REQUIRED)) {
            return;
        }
        Map<String, Object> next = new LinkedHashMap<>();
        next.put("state", plan.status().name());
        if (!plan.missingParameters().isEmpty()) {
            next.put("missing", plan.missingParameters().stream()
                    .map(field -> field.displayName())
                    .toList());
        }
        if (plan.status() == ToolExecutionStatus.APPROVAL_REQUIRED) {
            next.put("confirmationRequired", true);
        }
        payload.put("next", next);
    }

    private static boolean hasData(JsonNode data) {
        return data != null
                && !(data.isContainerNode() && data.isEmpty());
    }

    private static String boundedSummary(AssistantToolResult result) {
        String summary = result == null ? "工具没有返回结果" : result.summary();
        String normalized = normalizeWhitespace(summary);
        return normalized.length() <= 320 ? normalized : normalized.substring(0, 320) + "…";
    }

    private static String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(value.length());
        boolean previousWasWhitespace = true;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isWhitespace(current)) {
                if (!previousWasWhitespace) {
                    normalized.append(' ');
                    previousWasWhitespace = true;
                }
            } else {
                normalized.append(current);
                previousWasWhitespace = false;
            }
        }
        int length = normalized.length();
        if (length > 0 && normalized.charAt(length - 1) == ' ') {
            normalized.setLength(length - 1);
        }
        return normalized.toString();
    }

    private record CompactPolicy(int maxArrayItems,
                                 int maxTextLength,
                                 int maxObjectFields,
                                 int maxDepth) {
    }

    private static final class CompactState {
        private boolean truncated;
    }
}
