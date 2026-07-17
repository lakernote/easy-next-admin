package com.laker.admin.module.assistant.state;

import com.laker.admin.module.assistant.context.AssistantRequestContext;

/**
 * Conversation State 的原子快照仓库。
 *
 * <p>读取和写入都以“用户 + conversationId”为边界。写入必须比较 expectedVersion，
 * 不能把 Chat Memory、Pending Action、Last Result 拆成多个独立提交。</p>
 */
public interface AssistantConversationStateStore {

    /**
     * 原子读取当前用户与 conversationId 对应的一份完整快照。
     *
     * <p>不存在时返回 {@link AssistantConversationState#empty()}（version=0），不能返回 {@code null}；
     * 实现需要在一次存储命令中同时得到 version 和 payload，并在返回前清理已过期的短期状态。</p>
     */
    AssistantConversationState load(AssistantRequestContext context);

    /**
     * 原子保存下一版快照。版本不匹配表示存在旧 Turn 覆盖风险，实现必须拒绝写入。
     */
    void save(AssistantRequestContext context,
              long expectedVersion,
              AssistantConversationState newState);
}
