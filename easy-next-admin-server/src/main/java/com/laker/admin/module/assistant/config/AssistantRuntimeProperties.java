package com.laker.admin.module.assistant.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Assistant 运行时资源配置。
 *
 * <p>模型参数和运行时资源分开配置：模型超时描述一次供应商调用，SSE 超时和线程池描述
 * 一次完整 Agent Turn。二者不能混用，否则多次 Tool Calling 很容易被单次模型超时误伤。</p>
 */
@ConfigurationProperties(prefix = "easy.assistant.runtime")
@Validated
public class AssistantRuntimeProperties {
    public static final Duration MIN_CONVERSATION_TURN_LOCK_LEASE = Duration.ofSeconds(3);

    @NotNull
    private Duration streamTimeout = Duration.ofMinutes(3);

    @NotNull
    private Duration heartbeatInterval = Duration.ofSeconds(15);

    /**
     * 同一用户会话单轮执行锁的租期。底层锁会自动续租，因此它不是 Agent Turn 的硬超时。
     */
    @NotNull
    private Duration conversationTurnLockLease = Duration.ofSeconds(30);

    @Min(1)
    @Max(16)
    private int heartbeatSchedulerPoolSize = 2;

    @Valid
    @NotNull
    private Executor executor = new Executor();

    @AssertTrue(message = "easy.assistant.runtime.stream-timeout 必须大于 0")
    public boolean isStreamTimeoutPositive() {
        return streamTimeout != null && !streamTimeout.isZero() && !streamTimeout.isNegative();
    }

    @AssertTrue(message = "easy.assistant.runtime.heartbeat-interval 必须大于 0 且小于 stream-timeout")
    public boolean isHeartbeatIntervalValid() {
        return heartbeatInterval != null
                && !heartbeatInterval.isZero()
                && !heartbeatInterval.isNegative()
                && streamTimeout != null
                && heartbeatInterval.compareTo(streamTimeout) < 0;
    }

    @AssertTrue(message = "easy.assistant.runtime.conversation-turn-lock-lease 不能小于 3 秒")
    public boolean isConversationTurnLockLeaseValid() {
        return conversationTurnLockLease != null
                && conversationTurnLockLease.compareTo(MIN_CONVERSATION_TURN_LOCK_LEASE) >= 0;
    }

    public Duration getStreamTimeout() {
        return streamTimeout;
    }

    public void setStreamTimeout(Duration streamTimeout) {
        this.streamTimeout = streamTimeout;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public Duration getConversationTurnLockLease() {
        return conversationTurnLockLease;
    }

    public void setConversationTurnLockLease(Duration conversationTurnLockLease) {
        this.conversationTurnLockLease = conversationTurnLockLease;
    }

    public int getHeartbeatSchedulerPoolSize() {
        return heartbeatSchedulerPoolSize;
    }

    public void setHeartbeatSchedulerPoolSize(int heartbeatSchedulerPoolSize) {
        this.heartbeatSchedulerPoolSize = heartbeatSchedulerPoolSize;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public static class Executor {
        @Min(1)
        private int coreSize = 8;

        @Min(1)
        private int maxSize = 32;

        @Min(0)
        private int queueCapacity = 32;

        @Min(0)
        private int awaitTerminationSeconds = 60;

        @AssertTrue(message = "easy.assistant.runtime.executor.max-size 不能小于 core-size")
        public boolean isPoolSizeValid() {
            return maxSize >= coreSize;
        }

        public int getCoreSize() {
            return coreSize;
        }

        public void setCoreSize(int coreSize) {
            this.coreSize = coreSize;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getAwaitTerminationSeconds() {
            return awaitTerminationSeconds;
        }

        public void setAwaitTerminationSeconds(int awaitTerminationSeconds) {
            this.awaitTerminationSeconds = awaitTerminationSeconds;
        }
    }
}
