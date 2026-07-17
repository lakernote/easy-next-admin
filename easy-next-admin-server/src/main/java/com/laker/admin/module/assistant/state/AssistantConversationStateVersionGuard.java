package com.laker.admin.module.assistant.state;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;

/** Conversation State Store 实现共享的版本合同。 */
final class AssistantConversationStateVersionGuard {
    private AssistantConversationStateVersionGuard() {
    }

    static void validateNextVersion(long expectedVersion, AssistantConversationState newState) {
        if (expectedVersion < 0 || newState == null || newState.version() != expectedVersion + 1) {
            throw new IllegalArgumentException("newState.version must equal expectedVersion + 1");
        }
    }

    static BusinessException concurrentStateChange() {
        return new BusinessException(
                ErrorCode.CONCURRENT_OPERATION,
                "会话状态已更新，请重试当前消息"
        );
    }
}
