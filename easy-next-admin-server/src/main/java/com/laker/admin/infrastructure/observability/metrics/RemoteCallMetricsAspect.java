package com.laker.admin.infrastructure.observability.metrics;

import com.laker.admin.infrastructure.observability.remote.entity.RemoteCallLog;
import com.laker.admin.infrastructure.observability.remote.service.RemoteCallLogService;
import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 远程调用运行态指标。
 *
 * <p>业务模块中的 Feign 接口、client/remote 包下的 RestClient 包装类会被记录为低基数指标，
 * 供监控页面查询真实外部调用次数、成功率和耗时。</p>
 */
@Aspect
@Component
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
@Slf4j
@RequiredArgsConstructor
public class RemoteCallMetricsAspect {
    private final EasyBusinessMetrics businessMetrics;
    private final ObjectProvider<RemoteCallLogService> remoteCallLogServiceProvider;

    @Pointcut("@within(org.springframework.cloud.openfeign.FeignClient) " +
            "|| execution(public * com.laker.admin.module..client..*.*(..)) " +
            "|| execution(public * com.laker.admin.module..remote..*.*(..))")
    public void remoteCall() {
        // pointcut only
    }

    @Around("remoteCall()")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        long startNanos = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            record(joinPoint, startNanos, true, null);
            return result;
        } catch (Throwable ex) {
            record(joinPoint, startNanos, false, ex);
            throw ex;
        }
    }

    private void record(ProceedingJoinPoint joinPoint, long startNanos, boolean success, Throwable error) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String target = signature.getDeclaringType().getSimpleName();
        String method = signature.getMethod().getName();
        long durationNanos = System.nanoTime() - startNanos;
        businessMetrics.recordRemoteCall(target, method, success, durationNanos);
        persist(target, method, success, durationNanos, error);
    }

    private void persist(String target, String method, boolean success, long durationNanos, Throwable error) {
        RemoteCallLogService remoteCallLogService = remoteCallLogServiceProvider.getIfAvailable();
        if (remoteCallLogService == null) {
            return;
        }
        RemoteCallLog remoteCallLog = new RemoteCallLog();
        remoteCallLog.setTraceId(EasyTraceIdContext.currentTraceId());
        remoteCallLog.setTarget(target);
        remoteCallLog.setMethod(method);
        remoteCallLog.setSuccess(success);
        remoteCallLog.setDurationMs(TimeUnit.NANOSECONDS.toMillis(durationNanos));
        remoteCallLog.setErrorMessage(error == null ? null : truncate(error.getMessage(), 500));
        remoteCallLog.setCreateTime(LocalDateTime.now());
        try {
            remoteCallLogService.save(remoteCallLog);
        } catch (RuntimeException ex) {
            // 允许老环境先上线代码后补 SQL；统计接口会退回 Micrometer 运行态数据。
            log.debug("远程调用日志持久化失败，可能尚未创建 observability_remote_call_log 表", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
