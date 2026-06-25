package com.laker.admin.infrastructure.message.local;

import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.json.EasyJsonException;
import com.laker.admin.infrastructure.message.local.entity.LocalMessage;
import com.laker.admin.infrastructure.message.local.mapper.LocalMessageMapper;
import com.laker.admin.infrastructure.observability.metrics.EasyBusinessMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.Date;
import java.util.Map;

/**
 * 本地消息模板：本地事务成功后先记录待发送消息，再执行远程/耗时操作。
 * 失败消息由 {@link LocalMessageRetryJob} 根据 {@link EasyLocalMessageOperation#name()} 路由回业务实现重试。
 */
@Component
@ConditionalOnProperty(prefix = "easy.features", name = "outbox", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class EasyLocalMessageTemplate {
    private final LocalMessageMapper localMessageMapper;
    private final EasyJsonCodec jsonCodec;
    private final PlatformTransactionManager transactionManager;
    private final EasyBusinessMetrics businessMetrics;

    /**
     * 本地事务成功后再执行远程操作；远程失败只标记消息失败，由重试任务接管。
     */
    public void execute(ILocalMessageOperation localMessageOperation, Map<String, Object> params) {

        LocalMessage localMessage = performTransactionalOperations(localMessageOperation, params);
        try {
            boolean success = localMessageOperation.remoteOperation(params);
            if (success) {
                updateMessageStatusSent(localMessage);
            } else {
                updateMessageStatusOnFailure(localMessage);
            }

        } catch (Exception e) {
            updateMessageStatusOnFailure(localMessage);
            throw e;
        }
    }


    private LocalMessage performTransactionalOperations(ILocalMessageOperation localMessageOperation, Map<String, Object> params) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        TransactionStatus status = transactionManager.getTransaction(definition);
        try {
            // 执行本地操作
            final boolean localOperation = localMessageOperation.localOperation(params);
            if (!localOperation) {
                throw new RuntimeException("Local operation failed");
            }
            LocalMessage localMessage = new LocalMessage();
            localMessage.setStatus("PENDING");
            localMessage.setCreateTime(new Date());
            localMessage.setUpdateTime(new Date());
            localMessage.setRetryCount(0);

            // 获取注解
            // 获取目标类
            Class<?> targetClass = AopUtils.getTargetClass(localMessageOperation);
            // 从目标类获取注解
            EasyLocalMessageOperation easyLocalMessageOperation = AnnotationUtils.findAnnotation(targetClass, EasyLocalMessageOperation.class);
            if (easyLocalMessageOperation == null) {
                throw new RuntimeException("No EasyLocalMessageOperation annotation found for bean: " + localMessageOperation.getClass().getName());
            }
            localMessage.setName(easyLocalMessageOperation.name());
            localMessage.setParam(toJson(params));
            localMessageMapper.insert(localMessage);
            transactionManager.commit(status);
            return localMessage;
        } catch (Exception e) {
            // 回滚事务
            transactionManager.rollback(status);
            throw e;
        }

    }

    private void updateMessageStatusSent(LocalMessage localMessage) {
        localMessage.setStatus("SENT");
        localMessage.setUpdateTime(new Date());
        localMessageMapper.updateById(localMessage);
        businessMetrics.recordOutboxMessage(localMessage.getName(), localMessage.getStatus());
    }

    private void updateMessageStatusOnFailure(LocalMessage localMessage) {
        localMessage.setStatus("FAILED");
        localMessage.setRetryCount(localMessage.getRetryCount() + 1);
        localMessage.setUpdateTime(new Date());
        localMessageMapper.updateById(localMessage);
        businessMetrics.recordOutboxMessage(localMessage.getName(), localMessage.getStatus());
    }

    private String toJson(Map<String, Object> params) {
        try {
            return jsonCodec.toJson(params);
        } catch (EasyJsonException e) {
            throw new IllegalStateException("本地消息参数序列化失败", e);
        }
    }
}
