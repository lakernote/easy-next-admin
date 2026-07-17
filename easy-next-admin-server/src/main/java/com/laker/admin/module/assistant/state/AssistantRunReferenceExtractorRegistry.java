package com.laker.admin.module.assistant.state;

import com.laker.admin.module.assistant.tool.AssistantToolResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Run Reference 提取器注册表。
 *
 * <p>一个工具结果可能没有任何可引用项；这时返回空列表即可，不影响 Chat Memory 和 Pending Action。</p>
 */
@Component
public class AssistantRunReferenceExtractorRegistry {
    private final List<AssistantRunReferenceExtractor> extractors;

    public AssistantRunReferenceExtractorRegistry(List<AssistantRunReferenceExtractor> extractors) {
        this.extractors = extractors == null ? List.of() : List.copyOf(extractors);
    }

    public List<AssistantRunReference> extract(AssistantToolResult toolResult) {
        if (toolResult == null) {
            return List.of();
        }
        List<AssistantRunReference> references = flatten(toolResult).stream()
                .flatMap(result -> extractors.stream()
                        .filter(Objects::nonNull)
                        .flatMap(extractor -> extractor.extract(result).stream()))
                .distinct()
                .toList();
        return normalizeIndexes(references);
    }

    /**
     * 聚合结果中的每个单项提取器都只能看到自己的结果，因此可能都生成 index=1。
     * 注册表在扁平化完成后按引用类型统一编号，给模型稳定的“第一个/第二个”事实边界。
     */
    private static List<AssistantRunReference> normalizeIndexes(List<AssistantRunReference> references) {
        Map<String, Integer> nextIndexByType = new HashMap<>();
        return references.stream()
                .map(reference -> {
                    int index = nextIndexByType.merge(reference.type(), 1, Integer::sum);
                    Map<String, Object> attributes = new LinkedHashMap<>(reference.attributes());
                    attributes.put("index", index);
                    return new AssistantRunReference(
                            reference.type(),
                            reference.label(),
                            reference.value(),
                            reference.displayValue(),
                            attributes
                    );
                })
                .toList();
    }

    private static List<AssistantToolResult> flatten(AssistantToolResult root) {
        List<AssistantToolResult> results = new ArrayList<>();
        Set<AssistantToolResult> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(root, results, visited, 0);
        return results;
    }

    private static void collect(AssistantToolResult result,
                                List<AssistantToolResult> results,
                                Set<AssistantToolResult> visited,
                                int depth) {
        if (result == null || depth > 4 || !visited.add(result)) {
            return;
        }
        results.add(result);
        if (!(result.data() instanceof Map<?, ?> map) || !(map.get("results") instanceof Iterable<?> nested)) {
            return;
        }
        for (Object item : nested) {
            if (item instanceof AssistantToolResult nestedResult) {
                collect(nestedResult, results, visited, depth + 1);
            }
        }
    }
}
