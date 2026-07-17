package com.laker.admin.module.assistant.tool;

/**
 * 工具执行后的脱敏结果。
 *
 * <p>data 只放可以返回给当前用户看的结构化数据；管理员完整 Run Record 后续单独落库。</p>
 */
public record AssistantToolResult(
        String toolName,
        AssistantToolRunStatus status,
        String artifactType,
        String summary,
        Object data
) {
    public AssistantToolResult {
        toolName = text(toolName);
        artifactType = text(artifactType);
        summary = text(summary);
        if (status == null) {
            throw new IllegalArgumentException("AssistantToolResult.status must not be null");
        }
        if (status != AssistantToolRunStatus.SKIPPED && toolName.isEmpty()) {
            throw new IllegalArgumentException("Non-skipped tool result must declare toolName");
        }
        if (summary.isEmpty()) {
            throw new IllegalArgumentException("AssistantToolResult.summary must not be blank");
        }
    }

    public static AssistantToolResult skipped(String summary) {
        return new AssistantToolResult(null, AssistantToolRunStatus.SKIPPED, null, summary, null);
    }

    public static AssistantToolResult failed(String toolName, String summary) {
        return new AssistantToolResult(toolName, AssistantToolRunStatus.FAILED, null, summary, null);
    }

    public static AssistantToolResult indeterminate(String toolName, String summary) {
        return new AssistantToolResult(toolName, AssistantToolRunStatus.INDETERMINATE, null, summary, null);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
