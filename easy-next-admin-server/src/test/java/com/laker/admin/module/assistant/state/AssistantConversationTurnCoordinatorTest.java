package com.laker.admin.module.assistant.state;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.infrastructure.lock.IEasyLocker;
import com.laker.admin.infrastructure.lock.base.EasyLocker;
import com.laker.admin.module.assistant.config.AssistantRuntimeProperties;
import com.laker.admin.module.assistant.context.AssistantRequestContext;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.laker.admin.module.assistant.AssistantTestFixtures.requestContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantConversationTurnCoordinatorTest {

    @Test
    void shouldAcquireShortOpaqueDistributedLockAndReleaseOnlyOnce() {
        AssistantRequestContext context = requestContext("查询待办");
        IEasyLocker locker = mock(IEasyLocker.class);
        EasyLocker lock = EasyLocker.builder().key("stored-key").token("token").build();
        when(locker.tryAcquire(anyString(), eq(Duration.ofSeconds(30)))).thenReturn(lock);
        when(locker.release(lock)).thenReturn(true);

        AssistantConversationTurnCoordinator coordinator = new AssistantConversationTurnCoordinator(
                locker,
                new AssistantRuntimeProperties()
        );
        String lockKey = AssistantConversationTurnCoordinator.lockKey(context);

        assertThat(lockKey).startsWith("assistant:turn:").hasSizeLessThanOrEqualTo(50);
        assertThat(lockKey.substring("assistant:turn:".length())).matches("[0-9a-f]{32}");

        AssistantConversationTurnCoordinator.TurnLease lease = coordinator.acquire(context);
        lease.close();
        lease.close();

        verify(locker).tryAcquire(lockKey, Duration.ofSeconds(30));
        verify(locker).release(lock);
    }

    @Test
    void shouldRejectConcurrentTurnWithoutWaiting() {
        AssistantRequestContext context = requestContext("第二条并发消息");
        IEasyLocker locker = mock(IEasyLocker.class);
        when(locker.tryAcquire(anyString(), eq(Duration.ofSeconds(30)))).thenReturn(null);
        AssistantConversationTurnCoordinator coordinator = new AssistantConversationTurnCoordinator(
                locker,
                new AssistantRuntimeProperties()
        );

        assertThatThrownBy(() -> coordinator.acquire(context))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONCURRENT_OPERATION);
                    assertThat(ex.getMessage()).contains("当前会话正在处理上一条消息");
                });
    }

    @Test
    void shouldNotReplaceBusinessResultWhenLockReleaseFails() {
        AssistantRequestContext context = requestContext("查询流程");
        IEasyLocker locker = mock(IEasyLocker.class);
        EasyLocker lock = EasyLocker.builder().key("stored-key").token("token").build();
        when(locker.tryAcquire(anyString(), eq(Duration.ofSeconds(30)))).thenReturn(lock);
        when(locker.release(lock)).thenThrow(new IllegalStateException("redis unavailable"));
        AssistantConversationTurnCoordinator coordinator = new AssistantConversationTurnCoordinator(
                locker,
                new AssistantRuntimeProperties()
        );

        AssistantConversationTurnCoordinator.TurnLease lease = coordinator.acquire(context);

        assertThatCode(lease::close).doesNotThrowAnyException();
    }

    @Test
    void shouldFailClosedWhenLockInfrastructureIsUnavailable() {
        AssistantRequestContext context = requestContext("查询流程");
        IEasyLocker locker = mock(IEasyLocker.class);
        IllegalStateException infrastructureFailure = new IllegalStateException("redis unavailable");
        when(locker.tryAcquire(anyString(), eq(Duration.ofSeconds(30)))).thenThrow(infrastructureFailure);
        AssistantConversationTurnCoordinator coordinator = new AssistantConversationTurnCoordinator(
                locker,
                new AssistantRuntimeProperties()
        );

        assertThatThrownBy(() -> coordinator.acquire(context)).isSameAs(infrastructureFailure);
    }

    @Test
    void shouldRejectLeaseTooShortForSafeRenewal() {
        AssistantRuntimeProperties properties = new AssistantRuntimeProperties();
        properties.setConversationTurnLockLease(Duration.ofSeconds(2));

        assertThatThrownBy(() -> new AssistantConversationTurnCoordinator(mock(IEasyLocker.class), properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("conversationTurnLockLease");
    }
}
