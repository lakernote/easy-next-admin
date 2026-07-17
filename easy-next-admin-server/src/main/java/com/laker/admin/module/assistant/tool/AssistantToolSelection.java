package com.laker.admin.module.assistant.tool;

import java.util.List;

/**
 * 本轮工具选择结果。
 *
 * <p>它是模型原生 Tool Call 结果的脱敏快照，供参数校验、Trace 和调试响应使用；
 * 不包含第二套本地或 LLM Selector。</p>
 */
public record AssistantToolSelection(
        AssistantVisibleTool selectedTool,
        List<AssistantVisibleTool> availableTools,
        List<String> missingEvidence,
        String reason
) {
    public AssistantToolSelection {
        availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
        missingEvidence = missingEvidence == null ? List.of() : List.copyOf(missingEvidence);
    }

    public static AssistantToolSelection none(List<AssistantVisibleTool> availableTools,
                                              List<String> missingEvidence,
                                              String reason) {
        return new AssistantToolSelection(
                null,
                availableTools,
                missingEvidence,
                reason
        );
    }

    public static AssistantToolSelection selected(AssistantVisibleTool selectedTool,
                                                  List<AssistantVisibleTool> availableTools,
                                                  String reason) {
        return new AssistantToolSelection(
                selectedTool,
                availableTools,
                List.of(),
                reason
        );
    }

    public boolean hasSelectedTool() {
        return selectedTool != null;
    }

    public String toolName() {
        return selectedTool == null ? null : selectedTool.name();
    }
}
