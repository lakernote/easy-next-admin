package com.laker.admin.module.assistant.runtime;

import com.laker.admin.module.assistant.runtime.model.ToolCallingRequest;
import com.laker.admin.module.assistant.runtime.model.ToolCallingResponse;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentResolution;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolSelection;

/**
 * AgentLoop 的只读生命周期观察者。
 *
 * <p>它把模型调用和 Tool Calling 的真实执行时点交给 SSE/诊断层观察，但不参与任何决策。
 * Runtime 会隔离观察者异常，避免调试或进度展示改变业务结果。</p>
 */
public interface AgentLoopListener {
    AgentLoopListener NOOP = new AgentLoopListener() {
    };

    default void onModelCallStarted(int step, ToolCallingRequest request) {
    }

    default void onModelCallFinished(int step, ToolCallingResponse response) {
    }

    default void onToolSelected(AssistantToolSelection selection) {
    }

    default void onArgumentsResolved(AssistantToolArgumentResolution resolution) {
    }

    default void onArgumentsValidated(AssistantToolArgumentResolution resolution) {
    }

    default void onToolFinished(AssistantToolResult result) {
    }

    static AgentLoopListener noop() {
        return NOOP;
    }
}
