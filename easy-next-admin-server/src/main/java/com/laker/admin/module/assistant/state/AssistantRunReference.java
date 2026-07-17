package com.laker.admin.module.assistant.state;

import java.util.Map;

/**
 * 上一轮工具结果中可被下一轮引用的结构化条目。
 *
 * <p>它不是 Chat Memory：Chat Memory 保存自然语言对话；Run Reference 保存工具结果里的稳定业务标识。
 * 例如流程列表里的第 1 条会保留 instanceId / businessId，下一轮 LLM 参数抽取看到“看第一个详情”时，
 * 可以从这些引用中选择精确标识并填入工具参数。</p>
 */
public record AssistantRunReference(
        String type,
        String label,
        String value,
        String displayValue,
        Map<String, Object> attributes
) {
    public AssistantRunReference {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
