package com.laker.admin.module.assistant.response;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import com.laker.admin.module.assistant.runtime.model.ToolCallingMessage;
import com.laker.admin.module.assistant.state.ConversationHistorySelector;
import com.laker.admin.module.assistant.state.AssistantConversationState;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 构建发给企业助手主模型的初始消息数组。
 *
 * <p>顺序固定为：稳定 System Prompt、逐条历史 user/assistant 消息、本轮可信运行上下文、
 * 当前 user 消息。稳定内容在前、变化内容在后，既保留消息角色语义，也便于模型网关复用
 * 相同的 Prompt 前缀。Tool observation 由运行时在后续模型调用中继续追加。</p>
 */
@Component
public class ModelMessageBuilder {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_PENDING_ACTION_LENGTH = 1_400;
    private static final int MAX_LAST_RESULT_LENGTH = 1_800;

    private final ConversationHistorySelector conversationHistorySelector;

    public ModelMessageBuilder() {
        this(new ConversationHistorySelector());
    }

    @Autowired
    public ModelMessageBuilder(ConversationHistorySelector conversationHistorySelector) {
        this.conversationHistorySelector = Objects.requireNonNull(
                conversationHistorySelector,
                "conversationHistorySelector must not be null"
        );
    }

    public List<ToolCallingMessage> buildInitialMessages(AssistantRequestContext context) {
        return buildInitialMessages(context, null);
    }

    public List<ToolCallingMessage> buildInitialMessages(AssistantRequestContext context,
                                                         AssistantConversationState conversationState) {
        Objects.requireNonNull(context, "context must not be null");
        List<ToolCallingMessage> messages = new ArrayList<>();
        messages.add(ToolCallingMessage.system(AgentSystemPrompt.instructionsForMainModel()));
        conversationHistorySelector.selectRecentTurns(conversationState).forEach(turn -> {
            messages.add(ToolCallingMessage.user(turn.userMessage()));
            messages.add(ToolCallingMessage.assistant(turn.assistantMessage(), List.of()));
        });
        messages.add(ToolCallingMessage.system(buildTrustedRuntimeContextMessage(context, conversationState)));
        messages.add(ToolCallingMessage.user(context.normalizedMessage()));
        return List.copyOf(messages);
    }

    private static String buildTrustedRuntimeContextMessage(AssistantRequestContext context,
                                                            AssistantConversationState conversationState) {
        StringBuilder trustedRuntimeContext = new StringBuilder("本轮可信运行上下文（不能代替权限校验）：\n")
                .append("time=").append(context.now().format(DATE_TIME_FORMATTER)).append(System.lineSeparator())
                .append("zone=").append(context.zoneId().getId());
        if (conversationState != null && conversationState.lastResult() != null) {
            trustedRuntimeContext.append("\n\n最近业务结果（仅供明确承接时引用）：\n")
                    .append(lastResultText(conversationState))
                    .append(System.lineSeparator());
        }
        if (conversationState != null && conversationState.pendingAction() != null) {
            trustedRuntimeContext.append("\n待处理动作（服务端草稿）：\n")
                    .append(pendingActionText(conversationState))
                    .append(System.lineSeparator());
        }
        return trustedRuntimeContext.toString().trim();
    }

    private static String pendingActionText(AssistantConversationState conversationState) {
        if (conversationState == null || conversationState.pendingAction() == null) {
            return "无";
        }
        var pendingAction = conversationState.pendingAction();
        String filled = pendingAction.filledArguments().isEmpty()
                ? "无"
                : pendingAction.filledArguments().stream()
                .limit(8)
                .map(argument -> inline(argument.label(), 80) + "="
                        + (StringUtils.hasText(argument.displayValue())
                        ? inline(argument.displayValue(), 200)
                        : inline(argument.value(), 200)))
                .collect(java.util.stream.Collectors.joining("，"));
        String missing = pendingAction.missingParameters().isEmpty()
                ? "无"
                : pendingAction.missingParameters().stream()
                .limit(8)
                .map(parameter -> inline(parameter.displayName(), 80))
                .collect(java.util.stream.Collectors.joining("、"));
        String text = "status=" + pendingAction.status().name()
                + System.lineSeparator()
                + "toolName=" + inline(pendingAction.toolName(), 128)
                + System.lineSeparator()
                + "originatingUserMessage=" + inline(pendingAction.originatingUserMessage(), 400)
                + System.lineSeparator()
                + "filledArguments=" + filled
                + System.lineSeparator()
                + "missingParameters=" + missing;
        return truncate(text, MAX_PENDING_ACTION_LENGTH);
    }

    private static String lastResultText(AssistantConversationState conversationState) {
        if (conversationState == null || conversationState.lastResult() == null) {
            return "无";
        }
        var lastResult = conversationState.lastResult();
        StringBuilder builder = new StringBuilder();
        builder.append("toolName=").append(inline(lastResult.toolName(), 128)).append(System.lineSeparator());
        builder.append("artifactType=").append(inline(lastResult.artifactType(), 128)).append(System.lineSeparator());
        builder.append("summary=").append(inline(lastResult.summary(), 400)).append(System.lineSeparator());
        if (lastResult.references().isEmpty()) {
            builder.append("references=[]");
            return builder.toString();
        }
        builder.append("references=").append(System.lineSeparator());
        lastResult.references().stream().limit(5).forEach(reference -> builder.append("- type=")
                .append(inline(reference.type(), 80))
                .append(" | label=")
                .append(inline(reference.label(), 100))
                .append(" | value=")
                .append(inline(reference.value(), 120))
                .append(" | displayValue=")
                .append(inline(reference.displayValue(), 160))
                .append(" | attributes=")
                .append(inline(String.valueOf(reference.attributes()), 300))
                .append(System.lineSeparator()));
        return truncate(builder.toString().trim(), MAX_LAST_RESULT_LENGTH);
    }

    private static String inline(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(value.length());
        boolean previousWasWhitespace = true;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isWhitespace(current)) {
                if (!previousWasWhitespace) {
                    normalized.append(' ');
                    previousWasWhitespace = true;
                }
            } else {
                normalized.append(current);
                previousWasWhitespace = false;
            }
        }
        String result = normalized.toString().trim();
        return truncate(result, maxLength);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "…";
    }
}
