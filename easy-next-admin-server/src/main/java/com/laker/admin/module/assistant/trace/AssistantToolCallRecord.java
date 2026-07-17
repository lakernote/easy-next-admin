package com.laker.admin.module.assistant.trace;

import com.laker.admin.module.assistant.tool.ToolExecutionPhase;

/**
 * 一次受控 ToolRunner 调用的耗时记录。
 */
public record AssistantToolCallRecord(
        String toolCallId,
        String name,
        ToolExecutionPhase phase,
        String status,
        long durationMs
) {
    public AssistantToolCallRecord(String name,
                                   ToolExecutionPhase phase,
                                   String status,
                                   long durationMs) {
        this("", name, phase, status, durationMs);
    }

    public AssistantToolCallRecord {
        toolCallId = toolCallId == null ? "" : toolCallId;
        name = name == null ? "" : name;
        phase = phase == null ? ToolExecutionPhase.READ : phase;
        status = status == null ? "UNKNOWN" : status;
        durationMs = Math.max(0, durationMs);
    }
}
