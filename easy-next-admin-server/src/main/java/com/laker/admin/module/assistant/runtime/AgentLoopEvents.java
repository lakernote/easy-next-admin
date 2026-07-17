package com.laker.admin.module.assistant.runtime;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.runtime.model.ToolCallingRequest;
import com.laker.admin.module.assistant.runtime.model.ToolCallingResponse;
import com.laker.admin.module.assistant.tool.AssistantToolArgumentResolution;
import com.laker.admin.module.assistant.tool.AssistantToolResult;
import com.laker.admin.module.assistant.tool.AssistantToolSelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 隔离 AgentLoop 观察者；进度展示失败不能改变 Agent 结果。 */
final class AgentLoopEvents {
    private static final Logger log = LoggerFactory.getLogger(AgentLoopEvents.class);

    private AgentLoopEvents() {
    }

    static void modelCallStarted(AssistantRequestContext context,
                                 AgentLoopListener listener,
                                 int step,
                                 ToolCallingRequest request) {
        notify(context, "model_started", () -> listener.onModelCallStarted(step, request));
    }

    static void modelCallFinished(AssistantRequestContext context,
                                  AgentLoopListener listener,
                                  int step,
                                  ToolCallingResponse response) {
        notify(context, "model_finished", () -> listener.onModelCallFinished(step, response));
    }

    static void toolSelected(AssistantRequestContext context,
                             AgentLoopListener listener,
                             AssistantToolSelection selection) {
        notify(context, "tool_selected", () -> listener.onToolSelected(selection));
    }

    static void argumentsResolved(AssistantRequestContext context,
                                  AgentLoopListener listener,
                                  AssistantToolArgumentResolution resolution) {
        notify(context, "arguments_resolved", () -> listener.onArgumentsResolved(resolution));
    }

    static void argumentsValidated(AssistantRequestContext context,
                                   AgentLoopListener listener,
                                   AssistantToolArgumentResolution resolution) {
        notify(context, "arguments_validated", () -> listener.onArgumentsValidated(resolution));
    }

    static void toolFinished(AssistantRequestContext context,
                             AgentLoopListener listener,
                             AssistantToolResult result) {
        notify(context, "tool_finished", () -> listener.onToolFinished(result));
    }

    private static void notify(AssistantRequestContext context, String stage, Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException ex) {
            log.warn("Agent loop listener failed, runId={}, traceId={}, stage={}, type={}",
                    context.runId(), context.traceId(), stage, ex.getClass().getSimpleName());
        }
    }
}
