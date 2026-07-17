package com.laker.admin.infrastructure.message.local;

import com.laker.admin.infrastructure.id.EasyIdGenerator;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.message.local.entity.LocalMessage;
import com.laker.admin.infrastructure.message.local.mapper.LocalMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 本地消息模板：业务写入和待投递消息处于同一事务，事务提交后立即走本机 Fast Path 投递。
 * 广播重试任务只处理失败消息和过期租约，因此首次投递不需要等待 Cron。
 */
@Component
@ConditionalOnProperty(prefix = "easy.features", name = "outbox", havingValue = "true", matchIfMissing = true)
@Slf4j
public class EasyLocalMessageTemplate {
    private static final Duration FAST_PATH_LEASE = Duration.ofMinutes(5);
    private static final String FAST_PATH_WORKER_PREFIX = "outbox-fast:";

    private final LocalMessageMapper messageMapper;
    private final EasyJsonCodec jsonCodec;
    private final PlatformTransactionManager transactionManager;
    private final LocalMessageDeliveryService deliveryService;
    private final TaskExecutor taskExecutor;
    private final String fastPathWorkerId = FAST_PATH_WORKER_PREFIX + EasyIdGenerator.uuid32().substring(0, 12);

    public EasyLocalMessageTemplate(LocalMessageMapper messageMapper,
                                    EasyJsonCodec jsonCodec,
                                    PlatformTransactionManager transactionManager,
                                    LocalMessageDeliveryService deliveryService,
                                    @Qualifier("easyThreadPool") TaskExecutor taskExecutor) {
        this.messageMapper = messageMapper;
        this.jsonCodec = jsonCodec;
        this.transactionManager = transactionManager;
        this.deliveryService = deliveryService;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 执行业务本地事务并返回消息幂等键。远程投递在真实事务提交后立即异步执行。
     */
    public String execute(ILocalMessageOperation operation, Map<String, Object> params) {
        EasyLocalMessageOperation annotation = findAnnotation(operation);
        if (annotation == null) {
            throw new IllegalStateException("本地消息处理器缺少 EasyLocalMessageOperation 注解");
        }

        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        TransactionStatus transaction = transactionManager.getTransaction(definition);
        try {
            if (!operation.localOperation(params)) {
                throw new IllegalStateException("本地业务操作执行失败");
            }
            LocalMessage message = createPendingMessage(annotation.name(), params);
            messageMapper.insert(message);
            registerAfterCommit(message);
            transactionManager.commit(transaction);
            return message.getMessageKey();
        } catch (Exception ex) {
            if (!transaction.isCompleted()) {
                transactionManager.rollback(transaction);
            }
            throw ex;
        }
    }

    private LocalMessage createPendingMessage(String operationName, Map<String, Object> params) {
        LocalDateTime now = LocalDateTime.now();
        LocalMessage message = new LocalMessage();
        message.setMessageKey(EasyIdGenerator.uuid32());
        message.setOperationName(operationName);
        message.setPayloadJson(jsonCodec.toJson(params));
        message.setStatus("PENDING");
        message.setRetryCount(0);
        message.setNextRetryAt(now);
        message.setCreateTime(now);
        message.setUpdateTime(now);
        return message;
    }

    private void registerAfterCommit(LocalMessage message) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("本地消息必须在受 Spring 管理的事务中提交");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    taskExecutor.execute(() -> deliveryService.claimAndDeliver(
                            message.getId(), fastPathWorkerId, FAST_PATH_LEASE));
                } catch (RuntimeException ex) {
                    log.warn("Local message fast-path dispatch rejected, messageKey={}", message.getMessageKey(), ex);
                }
            }
        });
    }

    private EasyLocalMessageOperation findAnnotation(ILocalMessageOperation operation) {
        if (operation == null) {
            return null;
        }
        return AnnotationUtils.findAnnotation(AopUtils.getTargetClass(operation), EasyLocalMessageOperation.class);
    }
}
