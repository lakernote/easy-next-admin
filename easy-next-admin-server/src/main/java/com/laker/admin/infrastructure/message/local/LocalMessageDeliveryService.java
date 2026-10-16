package com.laker.admin.infrastructure.message.local;

import com.fasterxml.jackson.core.type.TypeReference;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.infrastructure.id.EasyIdGenerator;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.message.local.entity.LocalMessage;
import com.laker.admin.infrastructure.message.local.mapper.LocalMessageMapper;
import com.laker.observability.metrics.BusinessMetrics;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "easy.features", name = "outbox", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class LocalMessageDeliveryService {
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SENT = "SENT";
    private static final String STATUS_DEAD = "DEAD";
    private static final int ERROR_MESSAGE_LIMIT = 1000;
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final LocalMessageMapper messageMapper;
    private final ApplicationContext applicationContext;
    private final EasyJsonCodec jsonCodec;
    private final BusinessMetrics businessMetrics;
    private final Map<String, OperationDefinition> operations = new HashMap<>();

    @PostConstruct
    public void registerOperations() {
        applicationContext.getBeansOfType(ILocalMessageOperation.class).forEach((beanName, operation) -> {
            EasyLocalMessageOperation annotation = findAnnotation(operation);
            if (annotation == null) {
                return;
            }
            OperationDefinition duplicate = operations.putIfAbsent(annotation.name(),
                    new OperationDefinition(operation, annotation.maxRetryCount()));
            if (duplicate != null) {
                throw new IllegalStateException("本地消息处理器名称重复：" + annotation.name());
            }
        });
        log.info("Local message operations registered, count={}", operations.size());
    }

    @Transactional(rollbackFor = Exception.class)
    public List<LocalMessage> claimNext(String workerId, int limit, Duration leaseDuration) {
        LocalDateTime now = LocalDateTime.now();
        List<LocalMessage> messages = messageMapper.selectClaimable(now, limit);
        for (LocalMessage message : messages) {
            String token = EasyIdGenerator.uuid32();
            message.setStatus(STATUS_PROCESSING);
            message.setWorkerId(workerId);
            message.setLeaseToken(token);
            message.setLeaseUntil(now.plus(leaseDuration));
            message.setLastError(null);
            message.setUpdateTime(now);
            messageMapper.updateById(message);
        }
        return messages;
    }

    /**
     * 事务提交后的快速投递入口。条件 UPDATE 与广播 Worker 竞争同一条消息，只有领取成功者会发送。
     */
    public void claimAndDeliver(Long messageId, String workerId, Duration leaseDuration) {
        if (messageId == null || !StringUtils.hasText(workerId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        String token = EasyIdGenerator.uuid32();
        Duration actualLease = leaseDuration == null ? Duration.ofMinutes(5) : leaseDuration;
        int updated = messageMapper.update(null, Wrappers.<LocalMessage>lambdaUpdate()
                .eq(LocalMessage::getId, messageId)
                .eq(LocalMessage::getStatus, STATUS_PENDING)
                .le(LocalMessage::getNextRetryAt, now)
                .set(LocalMessage::getStatus, STATUS_PROCESSING)
                .set(LocalMessage::getWorkerId, workerId.trim())
                .set(LocalMessage::getLeaseToken, token)
                .set(LocalMessage::getLeaseUntil, now.plus(actualLease))
                .set(LocalMessage::getLastError, null)
                .set(LocalMessage::getUpdateTime, now));
        if (updated != 1) {
            return;
        }
        LocalMessage claimed = messageMapper.selectOne(Wrappers.<LocalMessage>lambdaQuery()
                .eq(LocalMessage::getId, messageId)
                .eq(LocalMessage::getStatus, STATUS_PROCESSING)
                .eq(LocalMessage::getLeaseToken, token));
        deliver(claimed);
    }

    public void deliver(LocalMessage message) {
        if (message == null || message.getId() == null || message.getLeaseToken() == null) {
            return;
        }
        OperationDefinition definition = operations.get(message.getOperationName());
        if (definition == null) {
            markDead(message, "未找到本地消息处理器：" + message.getOperationName());
            return;
        }
        try {
            Map<String, Object> params = jsonCodec.fromJson(message.getPayloadJson(), new TypeReference<>() {
            });
            if (definition.operation().remoteOperation(params)) {
                markSent(message);
            } else {
                markFailed(message, definition.maxRetryCount(), "远程操作返回失败");
            }
        } catch (Exception ex) {
            markFailed(message, definition.maxRetryCount(), exceptionMessage(ex));
        }
    }

    private void markSent(LocalMessage message) {
        LocalDateTime now = LocalDateTime.now();
        int updated = messageMapper.update(null, Wrappers.<LocalMessage>lambdaUpdate()
                .eq(LocalMessage::getId, message.getId())
                .eq(LocalMessage::getStatus, STATUS_PROCESSING)
                .eq(LocalMessage::getLeaseToken, message.getLeaseToken())
                .set(LocalMessage::getStatus, STATUS_SENT)
                .set(LocalMessage::getWorkerId, message.getWorkerId())
                .set(LocalMessage::getLeaseToken, null)
                .set(LocalMessage::getLeaseUntil, null)
                .set(LocalMessage::getLastError, null)
                .set(LocalMessage::getSentAt, now)
                .set(LocalMessage::getUpdateTime, now));
        if (updated == 1) {
            businessMetrics.recordOutboxMessage(message.getOperationName(), STATUS_SENT);
        }
    }

    private void markFailed(LocalMessage message, int maxRetryCount, String reason) {
        int retryCount = defaultRetryCount(message) + 1;
        if (retryCount >= maxRetryCount) {
            markDead(message, reason);
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = messageMapper.update(null, Wrappers.<LocalMessage>lambdaUpdate()
                .eq(LocalMessage::getId, message.getId())
                .eq(LocalMessage::getStatus, STATUS_PROCESSING)
                .eq(LocalMessage::getLeaseToken, message.getLeaseToken())
                .set(LocalMessage::getStatus, STATUS_PENDING)
                .set(LocalMessage::getRetryCount, retryCount)
                .set(LocalMessage::getNextRetryAt, now.plus(backoff(retryCount)))
                .set(LocalMessage::getLeaseToken, null)
                .set(LocalMessage::getLeaseUntil, null)
                .set(LocalMessage::getLastError, limit(reason))
                .set(LocalMessage::getUpdateTime, now));
        if (updated == 1) {
            businessMetrics.recordOutboxMessage(message.getOperationName(), STATUS_PENDING);
        }
    }

    private void markDead(LocalMessage message, String reason) {
        int retryCount = defaultRetryCount(message) + 1;
        LocalDateTime now = LocalDateTime.now();
        int updated = messageMapper.update(null, Wrappers.<LocalMessage>lambdaUpdate()
                .eq(LocalMessage::getId, message.getId())
                .eq(LocalMessage::getStatus, STATUS_PROCESSING)
                .eq(LocalMessage::getLeaseToken, message.getLeaseToken())
                .set(LocalMessage::getStatus, STATUS_DEAD)
                .set(LocalMessage::getRetryCount, retryCount)
                .set(LocalMessage::getLeaseToken, null)
                .set(LocalMessage::getLeaseUntil, null)
                .set(LocalMessage::getLastError, limit(reason))
                .set(LocalMessage::getUpdateTime, now));
        if (updated == 1) {
            businessMetrics.recordOutboxMessage(message.getOperationName(), STATUS_DEAD);
        }
    }

    private Duration backoff(int retryCount) {
        long seconds = Math.min(MAX_BACKOFF.toSeconds(), 60L << Math.min(5, Math.max(0, retryCount - 1)));
        return Duration.ofSeconds(seconds);
    }

    private int defaultRetryCount(LocalMessage message) {
        return message.getRetryCount() == null ? 0 : message.getRetryCount();
    }

    private EasyLocalMessageOperation findAnnotation(ILocalMessageOperation operation) {
        return AnnotationUtils.findAnnotation(AopUtils.getTargetClass(operation), EasyLocalMessageOperation.class);
    }

    private String exceptionMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private String limit(String value) {
        if (value == null || value.length() <= ERROR_MESSAGE_LIMIT) {
            return value;
        }
        return value.substring(0, ERROR_MESSAGE_LIMIT);
    }

    private record OperationDefinition(ILocalMessageOperation operation, int maxRetryCount) {
    }
}
