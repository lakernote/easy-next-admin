package com.laker.admin.module.assistant.state;

import java.util.Objects;

/**
 * 企业助手主循环返回给状态写入器的待处理动作更新，而不是让写入器从“最后一个执行计划”猜测。
 *
 * <pre>{@code
 * {"type":"UPSERT","action":{"toolName":"workflow.leave.prepare","status":"WAITING_FOR_CONFIRMATION"}}
 * {"type":"CLEAR","action":null}
 * }</pre>
 */
public record AssistantPendingActionUpdate(
        AssistantPendingActionUpdateType type,
        AssistantPendingAction action
) {
    public AssistantPendingActionUpdate {
        type = Objects.requireNonNull(type, "type must not be null");
        if (type == AssistantPendingActionUpdateType.UPSERT && action == null) {
            throw new IllegalArgumentException("UPSERT requires a pending action");
        }
        if (type != AssistantPendingActionUpdateType.UPSERT && action != null) {
            throw new IllegalArgumentException(type + " must not carry a pending action");
        }
    }

    public static AssistantPendingActionUpdate unchanged() {
        return new AssistantPendingActionUpdate(AssistantPendingActionUpdateType.UNCHANGED, null);
    }

    public static AssistantPendingActionUpdate upsert(AssistantPendingAction action) {
        return new AssistantPendingActionUpdate(
                AssistantPendingActionUpdateType.UPSERT,
                Objects.requireNonNull(action, "action must not be null")
        );
    }

    public static AssistantPendingActionUpdate clear() {
        return new AssistantPendingActionUpdate(AssistantPendingActionUpdateType.CLEAR, null);
    }

    /**
     * 按 Tool 实际执行顺序合并更新。后续显式更新覆盖前面的更新，UNCHANGED 不覆盖已有事实。
     */
    public AssistantPendingActionUpdate then(AssistantPendingActionUpdate next) {
        AssistantPendingActionUpdate safeNext = Objects.requireNonNull(next, "next must not be null");
        return safeNext.type == AssistantPendingActionUpdateType.UNCHANGED ? this : safeNext;
    }

    public AssistantPendingAction applyTo(AssistantPendingAction current) {
        return switch (type) {
            case UNCHANGED -> current;
            case UPSERT -> action;
            case CLEAR -> null;
        };
    }
}
