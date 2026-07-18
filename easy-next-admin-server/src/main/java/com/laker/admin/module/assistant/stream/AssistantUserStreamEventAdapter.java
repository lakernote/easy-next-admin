package com.laker.admin.module.assistant.stream;

import com.laker.admin.module.assistant.dto.AssistantChatStreamEvent;
import com.laker.admin.module.assistant.dto.AssistantEntryResponse;
import com.laker.admin.module.assistant.dto.AssistantAgentLifecycleEvent;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 把服务端内部 Agent 生命周期转换成普通用户可见的安全进度事件。
 *
 * <p>转换是单向的：内部事件携带的 Prompt、Tool 参数、Trace Step 和 payload 永远不会
 * 进入用户 SSE。每个内部步骤都会转换成独立、安全的用户步骤，便于前端实时反馈。</p>
 */
public final class AssistantUserStreamEventAdapter implements Consumer<AssistantAgentLifecycleEvent> {
    private final Consumer<AssistantChatStreamEvent> userEventSink;
    private int sequence;

    public AssistantUserStreamEventAdapter(Consumer<AssistantChatStreamEvent> userEventSink) {
        this.userEventSink = Objects.requireNonNull(userEventSink, "userEventSink must not be null");
    }

    @Override
    public void accept(AssistantAgentLifecycleEvent internalEvent) {
        if (internalEvent == null) {
            return;
        }
        switch (internalEvent.event()) {
            case CONTEXT_BUILT -> progress("context",
                    AssistantChatStreamEvent.AssistantChatPhase.PREPARING,
                    "整理本轮请求上下文"
            );
            case CONVERSATION_TURN_ACQUIRED -> progress("conversation_lock",
                    AssistantChatStreamEvent.AssistantChatPhase.PREPARING,
                    "进入当前会话执行队列"
            );
            case CONVERSATION_STATE_LOADED -> progress("conversation_state",
                    AssistantChatStreamEvent.AssistantChatPhase.PREPARING,
                    "读取最近对话和待处理动作"
            );
            case TOOL_VISIBILITY_READY -> progress("tool_visibility",
                    AssistantChatStreamEvent.AssistantChatPhase.PREPARING,
                    "准备当前账号可用的企业能力"
            );
            case MODEL_CALL_STARTED -> progress("model_call",
                    AssistantChatStreamEvent.AssistantChatPhase.THINKING,
                    "模型正在分析并决定下一步"
            );
            case MODEL_CALL_FINISHED -> progress("model_response",
                    AssistantChatStreamEvent.AssistantChatPhase.THINKING,
                    "模型已返回本轮决策"
            );
            case TOOL_SELECTION_READY -> progress("tool_selection",
                    AssistantChatStreamEvent.AssistantChatPhase.USING_TOOL,
                    "选择需要调用的企业能力"
            );
            case TOOL_ARGUMENTS_READY -> progress("tool_arguments",
                    AssistantChatStreamEvent.AssistantChatPhase.USING_TOOL,
                    "整理企业能力所需输入"
            );
            case TOOL_ARGUMENTS_VALIDATED -> progress("tool_validation",
                    AssistantChatStreamEvent.AssistantChatPhase.USING_TOOL,
                    "校验企业能力输入"
            );
            case TOOL_FINISHED -> progress("tool_result",
                    AssistantChatStreamEvent.AssistantChatPhase.USING_TOOL,
                    "企业能力已返回执行结果"
            );
            case DECISION_READY -> progress("decision",
                    AssistantChatStreamEvent.AssistantChatPhase.THINKING,
                    "确认本轮执行结论"
            );
            case PLAN_READY -> progress("plan",
                    AssistantChatStreamEvent.AssistantChatPhase.THINKING,
                    "整理下一步动作和用户提示"
            );
            case CONVERSATION_STATE_WRITTEN -> progress("conversation_write",
                    AssistantChatStreamEvent.AssistantChatPhase.SAVING,
                    "保存本轮会话状态"
            );
            case RESPONSE_READY -> progress("response",
                    AssistantChatStreamEvent.AssistantChatPhase.SAVING,
                    "准备向你展示最终回复"
            );
            case DONE -> emitDone(internalEvent.payload());
            case ERROR -> userEventSink.accept(AssistantChatStreamEvent.failed("助手执行失败，请稍后重试"));
        }
    }

    private void progress(String step,
                          AssistantChatStreamEvent.AssistantChatPhase phase,
                          String message) {
        userEventSink.accept(AssistantChatStreamEvent.progress(step, ++sequence, phase, message));
    }

    private void emitDone(Object payload) {
        if (payload instanceof AssistantEntryResponse response) {
            userEventSink.accept(AssistantChatStreamEvent.done(response));
            return;
        }
        userEventSink.accept(AssistantChatStreamEvent.failed("助手返回结果不完整，请稍后重试"));
    }
}
