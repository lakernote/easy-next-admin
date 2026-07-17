package com.laker.admin.module.assistant.state;

/**
 * 待处理动作的生命周期状态。
 *
 * <p>命名参考 Agent workflow / human-in-the-loop 中常见的 checkpoint 状态：
 * 先收集字段，字段齐全后等待人工确认，随后确认、取消或过期。</p>
 */
public enum AssistantPendingActionStatus {
    COLLECTING,
    WAITING_FOR_CONFIRMATION,
    CONFIRMED,
    CANCELED,
    EXPIRED
}
