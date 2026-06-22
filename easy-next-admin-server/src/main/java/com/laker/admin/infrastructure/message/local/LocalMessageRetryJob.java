package com.laker.admin.infrastructure.message.local;

import com.fasterxml.jackson.core.type.TypeReference;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.message.local.entity.LocalMessage;
import com.laker.admin.infrastructure.message.local.mapper.LocalMessageMapper;
import com.laker.admin.module.schedule.core.EasyJob;
import com.laker.admin.module.schedule.core.EasyJobHandler;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;

@EasyJob(jobCode = "infra_local_message_retry", jobName = "本地消息失败重试", cron = "0 0/1 * * * ?")
@ConditionalOnProperty(prefix = "easy.features", name = "outbox", havingValue = "true", matchIfMissing = true)
@Slf4j
public class LocalMessageRetryJob implements EasyJobHandler {
    private final LocalMessageMapper localMessageMapper;
    private final ApplicationContext applicationContext;
    private final EasyJsonCodec jsonCodec;
    private final Map<String, ILocalMessageOperation> beansWithName = new java.util.HashMap<>();

    public LocalMessageRetryJob(LocalMessageMapper localMessageMapper,
                                ApplicationContext applicationContext,
                                EasyJsonCodec jsonCodec) {
        this.localMessageMapper = localMessageMapper;
        this.applicationContext = applicationContext;
        this.jsonCodec = jsonCodec;
    }

    @PostConstruct
    public void init() {
        final Map<String, ILocalMessageOperation> beansWithAnnotation = applicationContext.getBeansOfType(ILocalMessageOperation.class);
        // 本地消息按业务操作名分发，启动时先把所有处理器注册到内存索引，执行时避免反复扫描 Spring 容器。
        for (Map.Entry<String, ILocalMessageOperation> entry : beansWithAnnotation.entrySet()) {
            EasyLocalMessageOperation annotation = AnnotationUtils.findAnnotation(entry.getValue().getClass(), EasyLocalMessageOperation.class);
            if (annotation != null) {
                String name = annotation.name();
                if (beansWithName.containsKey(name)) {
                    throw new RuntimeException("Duplicate bean name: " + name);
                }
                beansWithName.put(name, entry.getValue());
                log.debug("Registered local message operation, name={}, class={}", name, entry.getValue().getClass().getName());
            }
        }
        log.info("Local message retry job initialized, operations={}", beansWithName.size());
    }


    @Override
    public void execute(Map map) throws Exception {
        List<LocalMessage> failedMessages = localMessageMapper.findByStatus("FAILED");
        if (failedMessages == null || failedMessages.isEmpty()) {
            log.debug("No failed local messages to process");
            return;
        }
        for (LocalMessage localMessage : failedMessages) {
            final ILocalMessageOperation bean = beansWithName.get(localMessage.getName());
            if (bean == null) {
                log.error("No bean found for name: {}", localMessage.getName());
                continue;
            }
            // 获取注解
            // 获取目标类
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            // 从目标类获取注解
            EasyLocalMessageOperation easyLocalMessageOperation = AnnotationUtils.findAnnotation(targetClass, EasyLocalMessageOperation.class);

            if (easyLocalMessageOperation == null) {
                log.error("No EasyLocalMessageOperation annotation found for bean: {}", bean.getClass().getName());
                continue;
            }
            if (localMessage.getRetryCount() < easyLocalMessageOperation.maxRetryCount()) {
                try {
                    Map<String, Object> params = jsonCodec.fromJson(localMessage.getParam(), new TypeReference<>() {
                    });
                    final boolean remoteOperation = bean.remoteOperation(params);
                    if (remoteOperation) {
                        // 远程操作成功，更新本地消息状态
                        localMessage.setStatus("SENT");
                    } else {
                        // 远程操作失败，更新本地消息状态
                        localMessage.setStatus("FAILED");
                        localMessage.setRetryCount(localMessage.getRetryCount() + 1);
                    }
                    localMessage.setUpdateTime(new Date());
                    localMessageMapper.updateById(localMessage);
                    log.info("Message sent successfully: {}", localMessage.getName());
                } catch (Exception e) {
                    localMessage.setRetryCount(localMessage.getRetryCount() + 1);
                    localMessage.setUpdateTime(new Date());
                    localMessageMapper.updateById(localMessage);
                    log.error("Failed to send message: {}. Retry count: {}", localMessage.getName(), localMessage.getRetryCount(), e);
                }
            } else {
                // 达到最大重试次数，进行人工处理
                localMessage.setStatus("ERROR");
                localMessage.setUpdateTime(new Date());
                localMessageMapper.updateById(localMessage);
                // 记录日志或发送通知
                // 人工处理
                log.error("Message failed after max retries: {}", localMessage.getName());
            }
        }
    }
}
