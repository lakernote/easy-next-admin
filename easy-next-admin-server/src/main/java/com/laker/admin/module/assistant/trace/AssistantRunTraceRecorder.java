package com.laker.admin.module.assistant.trace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 当前线程内的助手调试记录器。
 *
 * <p>Orchestrator 负责在一次助手回合开始和结束时 reset/clear；模型客户端与 ToolRunner
 * 只记录脱敏的调用身份、分段耗时和 token usage。</p>
 */
@Component
public class AssistantRunTraceRecorder {
    private final ThreadLocal<List<AssistantModelCallRecord>> modelCalls = ThreadLocal.withInitial(ArrayList::new);
    private final ThreadLocal<List<AssistantToolCallRecord>> toolCalls = ThreadLocal.withInitial(ArrayList::new);
    private final AssistantAgentMetrics metrics;
    private final AssistantTraceContentPolicy contentPolicy;

    public AssistantRunTraceRecorder() {
        this.metrics = null;
        this.contentPolicy = AssistantTraceContentPolicy.metadataOnly();
    }

    @Autowired
    public AssistantRunTraceRecorder(AssistantAgentMetrics metrics,
                                     AssistantTraceContentPolicy contentPolicy) {
        this.metrics = metrics;
        this.contentPolicy = contentPolicy;
    }

    public void reset() {
        modelCalls.get().clear();
        toolCalls.get().clear();
    }

    public int markModelCalls() {
        return modelCalls.get().size();
    }

    public List<AssistantModelCallRecord> modelCallsSince(int mark) {
        List<AssistantModelCallRecord> calls = modelCalls.get();
        int safeMark = Math.max(0, Math.min(mark, calls.size()));
        return List.copyOf(calls.subList(safeMark, calls.size()));
    }

    public int markToolCalls() {
        return toolCalls.get().size();
    }

    public List<AssistantToolCallRecord> toolCallsSince(int mark) {
        List<AssistantToolCallRecord> calls = toolCalls.get();
        int safeMark = Math.max(0, Math.min(mark, calls.size()));
        return List.copyOf(calls.subList(safeMark, calls.size()));
    }

    public void recordModelCall(String name,
                                String model,
                                String status,
                                AssistantModelCallTiming timing,
                                AssistantTokenUsage tokenUsage) {
        recordModelCall(name, model, status, timing, tokenUsage, null, null, null);
    }

    public void recordModelCall(String name,
                                String model,
                                String status,
                                AssistantModelCallTiming timing,
                                AssistantTokenUsage tokenUsage,
                                Object requestPayload,
                                Object responsePayload,
                                String errorMessage) {
        AssistantModelCallRecord call = new AssistantModelCallRecord(
                name,
                model,
                status,
                timing,
                tokenUsage,
                requestPayload,
                responsePayload,
                errorMessage
        );
        modelCalls.get().add(call);
        if (metrics != null) {
            metrics.recordModelCall(call);
        }
    }

    public Object capturePayload(Object metadata, Object content) {
        return contentPolicy.capture(metadata, content);
    }

    public String sanitizeErrorMessage(String message) {
        return contentPolicy.sanitizeErrorMessage(message);
    }

    public void recordToolCall(AssistantToolCallRecord call) {
        if (call == null) {
            return;
        }
        toolCalls.get().add(call);
        if (metrics != null) {
            metrics.recordToolCall(call);
        }
    }

    public void recordTurn(String decision, String status, AssistantRunTrace trace) {
        if (metrics != null) {
            metrics.recordTurn(decision, status, trace);
        }
    }

    public void clear() {
        modelCalls.remove();
        toolCalls.remove();
    }
}
