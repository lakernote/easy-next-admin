package com.laker.admin.module.assistant.state;

import com.laker.admin.module.assistant.context.AssistantRequestContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单节点开发和测试使用的会话状态仓库。
 *
 * <p>它提供进程内原子 CAS，但不能在多实例之间共享状态。生产环境开启 Redis 后会优先注入
 * {@link RedisAssistantConversationStateStore}。</p>
 */
@Component
public class InMemoryAssistantConversationStateStore implements AssistantConversationStateStore {
    private static final Duration CONVERSATION_TTL = Duration.ofHours(2);

    private final Map<String, AssistantConversationState> states = new ConcurrentHashMap<>();

    @Override
    public AssistantConversationState load(AssistantRequestContext context) {
        String key = AssistantConversationStateKeys.stateKey(context);
        AssistantConversationState state = states.get(key);
        if (state == null) {
            return AssistantConversationState.empty();
        }
        if (state.updatedAt() != null
                && state.updatedAt().plus(CONVERSATION_TTL).isBefore(context.requestInstant())) {
            states.remove(key, state);
            return AssistantConversationState.empty();
        }
        return state.activeAt(context.requestInstant());
    }

    @Override
    public void save(AssistantRequestContext context,
                     long expectedVersion,
                     AssistantConversationState newState) {
        AssistantConversationStateVersionGuard.validateNextVersion(expectedVersion, newState);
        AtomicBoolean saved = new AtomicBoolean(false);
        states.compute(AssistantConversationStateKeys.stateKey(context), (key, current) -> {
            long currentVersion = current == null ? 0 : current.version();
            if (currentVersion != expectedVersion) {
                return current;
            }
            saved.set(true);
            return newState;
        });
        if (!saved.get()) {
            throw AssistantConversationStateVersionGuard.concurrentStateChange();
        }
    }
}
