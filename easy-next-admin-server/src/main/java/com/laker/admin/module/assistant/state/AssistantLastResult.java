package com.laker.admin.module.assistant.state;

import com.laker.admin.module.assistant.tool.AssistantToolResult;

import java.time.Instant;
import java.util.List;

/**
 * 最近一次有业务意义的 Tool 结果摘要，供下一轮解析“第一个”“刚才那个流程”等指代。
 *
 * <p>它不是 Run State，也不能恢复执行；恢复待确认写操作由 {@link AssistantPendingAction} 负责。
 * Trace、耗时、模型调用次数等观测字段只进入 {@code AssistantRunTrace}。</p>
 *
 * <pre>{@code
 * {
 *   "toolName": "workflow.task.list_my_pending",
 *   "artifactType": "TASK_LIST",
 *   "summary": "找到 2 个待办任务",
 *   "references": [
 *     {
 *       "type": "workflow.task",
 *       "label": "第1个待办",
 *       "value": "90001",
 *       "displayValue": "请假审批 REQ-20260714-001",
 *       "attributes": {"instanceId": "80001", "index": 1}
 *     }
 *   ],
 *   "updatedAt": "2026-07-14T02:30:00Z"
 * }
 * }</pre>
 */
public record AssistantLastResult(
        String toolName,
        String artifactType,
        String summary,
        List<AssistantRunReference> references,
        Instant updatedAt
) {
    public AssistantLastResult {
        toolName = text(toolName);
        artifactType = text(artifactType);
        summary = text(summary);
        references = references == null ? List.of() : List.copyOf(references);
    }

    public static AssistantLastResult from(AssistantToolResult toolResult,
                                           List<AssistantRunReference> references,
                                           Instant updatedAt) {
        return new AssistantLastResult(
                toolResult == null ? "" : toolResult.toolName(),
                toolResult == null ? "" : toolResult.artifactType(),
                toolResult == null ? "" : toolResult.summary(),
                references,
                updatedAt
        );
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
