package com.laker.admin.infrastructure.message.local;

import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.json.EasyJsonException;
import com.laker.admin.infrastructure.message.local.entity.LocalMessage;
import com.laker.admin.infrastructure.message.local.mapper.LocalMessageMapper;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.Map;

/**
 * 本地消息模板：本地事务成功后先记录待发送消息，再执行远程/耗时操作。
 * 失败消息由 {@link LocalMessageRetryJob} 根据 {@link EasyLocalMessageOperation#name()} 路由回业务实现重试。
 */
@Component
@ConditionalOnProperty(prefix = "easy.features", name = "outbox", havingValue = "true", matchIfMissing = true)
public class EasyLocalMessageTemplate {
    @Autowired
    private LocalMessageMapper localMessageMapper;
    @Autowired
    private EasyJsonCodec jsonCodec;

    /**
     * 高级抽象，对事务管理的常见操作进行了封装，提供了简洁的 API 来处理事务。开发者无需手动处理事务的开启、提交和回滚等细节，只需关注业务逻辑即可。
     * transactionTemplate.execute(status -> {
     * // 业务逻辑
     * return null;
     * });
     */
    @Autowired
    TransactionTemplate transactionTemplate;

    /**
     * 低级抽象，提供了对事务管理的底层操作，允许开发者手动控制事务的开启、提交和回滚等细节。适用于需要更细粒度控制事务的场景。
     */
    @Autowired
    PlatformTransactionManager transactionManager;

    /**
     *
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
    }

    private void updateMessageStatusOnFailure(LocalMessage localMessage) {
        localMessage.setStatus("FAILED");
        localMessage.setRetryCount(localMessage.getRetryCount() + 1);
        localMessage.setUpdateTime(new Date());
        localMessageMapper.updateById(localMessage);
    }

    private String toJson(Map<String, Object> params) {
        try {
            return jsonCodec.toJson(params);
        } catch (EasyJsonException e) {
            throw new IllegalStateException("本地消息参数序列化失败", e);
        }
    }
}
