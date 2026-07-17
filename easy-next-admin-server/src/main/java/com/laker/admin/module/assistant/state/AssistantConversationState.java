package com.laker.admin.module.assistant.state;

import java.time.Duration;
import java.time.Instant;

/**
 * 同一用户、同一 conversationId 的持久化会话快照。
 *
 * <p>它和 {@code AssistantRequestContext} 的区别：Request Context 只在当前请求内有效；
 * Conversation State 跨轮次保存。模型耗时、Token、Trace Step 等单轮观测数据不进入本对象。</p>
 *
 * <p>{@code version} 用于乐观并发校验。即使会话锁因基础设施异常失效，旧 Turn 也不能覆盖新状态。</p>
 *
 * <pre>{@code
 * {
 *   "version": 7,
 *   "chatMemory": {
 *     "recentTurns": [
 *       {
 *         "userMessage": "查一下我的待办",
 *         "assistantMessage": "你当前有 2 个待办。",
 *         "completedAt": "2026-07-14T02:30:00Z"
 *       }
 *     ]
 *   },
 *   "pendingAction": null,
 *   "lastResult": {
 *     "toolName": "workflow.task.list_my_pending",
 *     "artifactType": "TASK_LIST",
 *     "summary": "找到 2 个待办任务",
 *     "references": [],
 *     "updatedAt": "2026-07-14T02:30:00Z"
 *   },
 *   "updatedAt": "2026-07-14T02:30:00Z"
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code version}：持久化快照版本；保存下一版时必须满足 {@code next=expected+1}。</li>
 *   <li>{@code chatMemory}：最近完整用户/助手轮次，不包含中间 Tool Message 和原始 Tool 数据。</li>
 *   <li>{@code pendingAction}：待补字段或待确认写操作；没有暂停动作时为 {@code null}。</li>
 *   <li>{@code lastResult}：最近一次可跨轮引用的 Tool 摘要与稳定业务引用，不是完整执行历史。</li>
 *   <li>{@code updatedAt}：本快照成功提交时间，也是整段会话 TTL 的时间依据。</li>
 * </ul>
 *
 * <p>{@code conversationId} 不重复保存在 payload 中，而是和当前 userId 一起组成仓库 Key。
 * Pending Action 与 Last Result 属于短期业务上下文，30 分钟后清理；Chat Memory 的会话 TTL 为 2 小时。</p>
 */
public record AssistantConversationState(
        long version,
        AssistantChatMemory chatMemory,
        AssistantPendingAction pendingAction,
        AssistantLastResult lastResult,
        Instant updatedAt
) {
    private static final Duration TRANSIENT_STATE_TTL = Duration.ofMinutes(30);

    public AssistantConversationState {
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
        if (version > 0 && updatedAt == null) {
            throw new IllegalArgumentException("updatedAt is required for a persisted state");
        }
        chatMemory = chatMemory == null ? AssistantChatMemory.empty() : chatMemory;
    }

    public static AssistantConversationState empty() {
        return new AssistantConversationState(0, AssistantChatMemory.empty(), null, null, null);
    }

    public boolean hasPendingAction() {
        return pendingAction != null;
    }

    public boolean hasLastResult() {
        return lastResult != null;
    }

    /**
     * 过期的是短期业务上下文，不是整段 Chat Memory。清理后的快照仍保留原 version，
     * 下一次成功写入会把清理结果与新 Turn 一起原子提交。
     */
    public AssistantConversationState activeAt(Instant now) {
        if (now == null) {
            return this;
        }
        AssistantPendingAction activePendingAction = isExpired(
                pendingAction == null ? null : pendingAction.updatedAt(), now) ? null : pendingAction;
        AssistantLastResult activeLastResult = isExpired(
                lastResult == null ? null : lastResult.updatedAt(), now) ? null : lastResult;
        if (activePendingAction == pendingAction && activeLastResult == lastResult) {
            return this;
        }
        return new AssistantConversationState(
                version, chatMemory, activePendingAction, activeLastResult, updatedAt);
    }

    private static boolean isExpired(Instant stateUpdatedAt, Instant now) {
        return stateUpdatedAt != null
                && stateUpdatedAt.plus(TRANSIENT_STATE_TTL).isBefore(now);
    }
}
