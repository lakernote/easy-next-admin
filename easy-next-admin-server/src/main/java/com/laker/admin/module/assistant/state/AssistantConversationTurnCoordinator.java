package com.laker.admin.module.assistant.state;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.infrastructure.lock.IEasyLocker;
import com.laker.admin.infrastructure.lock.base.EasyLocker;
import com.laker.admin.infrastructure.security.support.EasySecurityToken;
import com.laker.admin.module.assistant.config.AssistantRuntimeProperties;
import com.laker.admin.module.assistant.context.AssistantRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 串行化“同一用户 + 同一会话”的 Agent Turn。
 *
 * <p>会话状态采用读取、推理、写回三段式流程。如果两个请求同时读取相同版本，后写入者会覆盖
 * Conversation State。这里使用项目已有的分布式锁覆盖整个状态事务边界，
 * 但采用非阻塞获取：冲突请求立即得到 409，不占用 SSE 线程等待上一轮模型调用。</p>
 *
 * <p>这个锁只保护 Agent 会话状态的一致性，不等于数据库事务，也不替代业务 Tool 自身的权限、
 * 幂等、乐观锁和审计。底层会定时续租；如果锁后端长时间不可用，业务写操作仍必须能抵抗重试和并发。</p>
 */
@Component
public class AssistantConversationTurnCoordinator {
    private static final Logger log = LoggerFactory.getLogger(AssistantConversationTurnCoordinator.class);
    private static final String LOCK_KEY_PREFIX = "assistant:turn:";
    private static final int LOCK_DIGEST_LENGTH = 32;

    private final IEasyLocker locker;
    private final Duration lockLease;

    public AssistantConversationTurnCoordinator(IEasyLocker locker,
                                                AssistantRuntimeProperties runtimeProperties) {
        this.locker = Objects.requireNonNull(locker, "locker must not be null");
        Objects.requireNonNull(runtimeProperties, "runtimeProperties must not be null");
        Duration configuredLease = Objects.requireNonNull(
                runtimeProperties.getConversationTurnLockLease(),
                "conversationTurnLockLease must not be null"
        );
        if (configuredLease.compareTo(AssistantRuntimeProperties.MIN_CONVERSATION_TURN_LOCK_LEASE) < 0) {
            throw new IllegalArgumentException("conversationTurnLockLease must be at least "
                    + AssistantRuntimeProperties.MIN_CONVERSATION_TURN_LOCK_LEASE);
        }
        this.lockLease = configuredLease;
    }

    /**
     * 非阻塞获取本轮会话租约。
     *
     * <p>返回非空租约后，调用方必须立即使用 try-with-resources 包裹从状态读取到状态写回的完整流程。</p>
     *
     * @throws BusinessException 同一用户的同一会话已有一轮正在执行时返回稳定的 409 冲突
     */
    public TurnLease acquire(AssistantRequestContext context) {
        Objects.requireNonNull(context, "context must not be null");
        String lockKey = lockKey(context);
        // tryAcquire 的 null 只表示“锁已被占用”。Redis/MySQL 异常不在这里伪装成并发冲突，
        // 而是继续向上失败，确保锁基础设施不可用时不会无锁执行 Agent Turn。
        EasyLocker lock = locker.tryAcquire(lockKey, lockLease);
        if (lock == null) {
            throw new BusinessException(
                    ErrorCode.CONCURRENT_OPERATION,
                    "当前会话正在处理上一条消息，请等待完成后再发送"
            );
        }
        return new TurnLease(locker, lock);
    }

    static String lockKey(AssistantRequestContext context) {
        // 原始状态键示例：1001:conversation:88202607140001。
        // MySQL 降级锁的 lock_key 长度是 50，因此取 SHA-256 的前 128 bit：既控制长度，
        // 也避免把 userId、sessionId、conversationId 明文写进 Redis Key 或基础设施表。
        String digest = EasySecurityToken.sha256(AssistantConversationStateKeys.stateKey(context));
        return LOCK_KEY_PREFIX + digest.substring(0, LOCK_DIGEST_LENGTH);
    }

    /**
     * try-with-resources 租约。释放失败只记日志：业务结果已经完成后，清理异常不能覆盖真实回复；
     * 底层租约到期仍会自动释放锁。
     */
    public static class TurnLease implements AutoCloseable {
        private final IEasyLocker locker;
        private final EasyLocker lock;
        private final AtomicBoolean closed = new AtomicBoolean();

        TurnLease(IEasyLocker locker, EasyLocker lock) {
            this.locker = locker;
            this.lock = lock;
        }

        @Override
        public void close() {
            // close 可能被正常流程、异常流程或框架清理重复调用，CAS 保证底层 release 最多执行一次。
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                if (!locker.release(lock)) {
                    log.warn("Assistant conversation turn lock was not released, key={}", lock.getKey());
                }
            } catch (RuntimeException ex) {
                log.warn("Assistant conversation turn lock release failed, key={}, failureType={}",
                        lock.getKey(), ex.getClass().getSimpleName());
            }
        }
    }
}
