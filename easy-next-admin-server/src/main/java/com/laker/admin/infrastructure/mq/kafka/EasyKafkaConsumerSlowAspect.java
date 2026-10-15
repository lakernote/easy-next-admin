package com.laker.admin.infrastructure.mq.kafka;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.trace.EasyMdcContext;
import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import com.laker.admin.infrastructure.observability.trace.BoundedThrowableFormatter;
import com.laker.admin.infrastructure.observability.trace.SpanType;
import com.laker.admin.infrastructure.observability.trace.TraceContext;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
@ConditionalOnClass(KafkaListener.class)
@ConditionalOnProperty(prefix = "easy.features", name = "kafka", havingValue = "true")
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
public class EasyKafkaConsumerSlowAspect {
    private final EasyNextAdminConfig easyNextAdminConfig;
    private final RecentTraceStore recentTraceStore;
    private final OperationObservation operationObservation;

    @Autowired
    public EasyKafkaConsumerSlowAspect(EasyNextAdminConfig easyNextAdminConfig, RecentTraceStore recentTraceStore,
                                       OperationObservation operationObservation) {
        this.easyNextAdminConfig = easyNextAdminConfig;
        this.recentTraceStore = recentTraceStore;
        this.operationObservation = operationObservation;
    }

    public EasyKafkaConsumerSlowAspect(EasyNextAdminConfig easyNextAdminConfig) {
        this(easyNextAdminConfig, null, null);
    }

    @Around(value = "@annotation(kafkaListener)", argNames = "joinPoint,kafkaListener")
    public Object aroundKafkaListener(ProceedingJoinPoint joinPoint, KafkaListener kafkaListener) throws Throwable {
        Object[] args = joinPoint.getArgs();
        try (EasyMdcContext.Scope ignored = EasyMdcContext.scope()) {
            EasyTraceIdContext.putOrCreateTraceId(resolveTraceId(args));
            EasyMdcContext.putOrRemove(EasyNextAdminConstants.USER_ID, resolveUserId(args));
            String traceId = EasyTraceIdContext.currentTraceId();
            String operationName = joinPoint.getSignature() == null ? "KafkaListener" : joinPoint.getSignature().toShortString();
            String topic = listenerTopics(kafkaListener);
            OperationObservation.Scope observation = operationObservation == null ? null : operationObservation.start("KAFKA", operationName);
            boolean traceEnabled = easyNextAdminConfig.getTrace().isEnabled();
            long thresholdMs = easyNextAdminConfig.getTrace().getKafkaConsumerSlowThresholdMs();
            boolean traceStarted = false;
            if (traceEnabled) {
                try {
                    TraceContext.startRoot(operationName, SpanType.Kafka,
                            topic,
                            easyNextAdminConfig.getTrace().getMaxDepth(),
                            easyNextAdminConfig.getTrace().getMinNodeCostMs());
                    traceStarted = true;
                } catch (Throwable monitoringFailure) {
                    TraceContext.clear();
                }
            }
            if (observation != null) observation.beginBusinessBoundary();
            if (traceStarted) TraceContext.beginCurrentSpanBoundary();
            long startedNanos = System.nanoTime();
            Throwable failure = null;
            try {
                return joinPoint.proceed();
            } catch (Throwable ex) {
                failure = ex;
                throw ex;
            } finally {
                try {
                    // Freeze the business boundary before trace rendering, exception formatting, JSONL, or logging.
                    long durationMs = Math.max(0, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos));
                    if (observation != null) observation.endBusinessBoundary();
                    if (traceStarted) TraceContext.freezeRootBoundary();
                    if (observation != null) observation.finish(failure == null ? "success" : "failure", failure);
                    OperationObservation.Completion operationCompletion =
                            observation == null ? null : observation.completion();
                    BoundedThrowableFormatter.FailureEvidence failureEvidence =
                            observation == null ? null : observation.failureEvidence();
                    TraceContext.Completion[] traceCompletion = new TraceContext.Completion[1];
                    boolean rootSlow = thresholdMs > 0 && durationMs >= thresholdMs;
                    if (traceStarted) {
                        if (rootSlow) TraceContext.markSlow("SLOW_KAFKA");
                        if (failure != null) {
                            if (failureEvidence != null && failureEvidence.present()) {
                                TraceContext.markError("KAFKA_ERROR", failureEvidence);
                            } else {
                                TraceContext.markError("KAFKA_ERROR", failure);
                            }
                        }
                        TraceContext.stopRoot(thresholdMs, "Kafka consumer trace tree", failure,
                                (TraceContext.Completion completion) -> traceCompletion[0] = completion);
                    } else {
                        TraceContext.clear();
                    }
                    boolean rootError = failure != null;
                    if (recentTraceStore != null && isIncident(traceCompletion[0], operationCompletion,
                            rootSlow, rootError)) {
                        IncidentEvidence evidence = incidentEvidence(traceCompletion[0], operationCompletion,
                                rootSlow, rootError, failure);
                        recentTraceStore.recordIncident("KAFKA", traceId, operationName, topic,
                                rootError ? 500 : 200, evidence.tree(), durationMs, evidence.slow(), evidence.error(),
                                evidence.reasons(), evidence.errorType(), evidence.errorMessage(), evidence.stackTrace(),
                                evidence.primaryCause(), evidence.slowCause(), evidence.errorCause(),
                                observation == null ? null : observation.finishedAt());
                    }
                } catch (Throwable monitoringFailure) {
                    if (observation != null) {
                        try { observation.discard(); } catch (Throwable ignoredDiscardFailure) { }
                    }
                    TraceContext.clear();
                    retainOriginalFailure(failure, monitoringFailure);
                }
            }
        }
    }

    private static boolean isIncident(TraceContext.Completion trace, OperationObservation.Completion operation,
                                      boolean rootSlow, boolean rootError) {
        return rootSlow || rootError
                || trace != null && (trace.slow() || trace.error())
                || operation != null && (operation.slow() || operation.error());
    }

    private static String resolveTraceId(Object[] args) {
        return resolveHeader(args, EasyNextAdminConstants.TRACE_ID_HEADER);
    }

    private static String resolveUserId(Object[] args) {
        return resolveHeader(args, EasyNextAdminConstants.USER_ID);
    }

    private static String resolveHeader(Object[] args, String headerName) {
        for (Object arg : args) {
            if (arg instanceof ConsumerRecord<?, ?> record) {
                Header header = record.headers().lastHeader(headerName);
                if (header != null && header.value() != null) {
                    String value = new String(header.value(), StandardCharsets.UTF_8);
                    if (StringUtils.hasText(value)) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private static String listenerTopics(KafkaListener kafkaListener) {
        String[] topics = kafkaListener.topics();
        if (topics != null && topics.length > 0) {
            return String.join(",", topics);
        }
        if (StringUtils.hasText(kafkaListener.topicPattern())) {
            return kafkaListener.topicPattern();
        }
        return "-";
    }

    private static IncidentEvidence incidentEvidence(TraceContext.Completion trace,
                                                     OperationObservation.Completion operation,
                                                     boolean rootSlow, boolean rootError, Throwable failure) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        if (trace != null) reasons.addAll(trace.reasons());
        if (operation != null) reasons.addAll(operation.reasons());
        if (rootSlow) reasons.add("SLOW_KAFKA");
        if (rootError) reasons.add("KAFKA_ERROR");
        String errorType = first(trace == null ? "" : trace.errorType(),
                operation == null ? "" : operation.errorType());
        String errorMessage = first(trace == null ? "" : trace.errorMessage(),
                operation == null ? "" : operation.errorMessage());
        String stackTrace = first(trace == null ? "" : trace.stackTrace(),
                operation == null ? "" : operation.stackTrace());
        if (failure != null) {
            if (errorType.isBlank()) errorType = failure.getClass().getName();
            if (errorMessage.isBlank()) errorMessage = BoundedThrowableFormatter.message(failure);
            if (stackTrace.isBlank()) stackTrace = BoundedThrowableFormatter.format(failure);
        }
        return new IncidentEvidence(trace == null ? "" : trace.tree(),
                rootSlow || trace != null && trace.slow() || operation != null && operation.slow(),
                rootError || trace != null && trace.error() || operation != null && operation.error(),
                List.copyOf(reasons), errorType, errorMessage, stackTrace,
                operation == null ? null : operation.primaryCause(),
                operation == null ? null : operation.slowCause(),
                operation == null ? null : operation.errorCause());
    }

    private static String first(String first, String second) {
        return first != null && !first.isBlank() ? first : second == null ? "" : second;
    }

    private static void retainOriginalFailure(Throwable businessFailure, Throwable monitoringFailure) {
        if (businessFailure != null && !(businessFailure instanceof VirtualMachineError)
                && !(monitoringFailure instanceof VirtualMachineError)) {
            try { businessFailure.addSuppressed(monitoringFailure); }
            catch (Throwable ignored) { }
        }
        try {
            if (!(monitoringFailure instanceof VirtualMachineError)) {
                log.warn("Kafka consumer 运行诊断收尾失败，已保留原业务结果: {}",
                        monitoringFailure.getClass().getName());
            }
        } catch (Throwable ignored) { }
    }

    private record IncidentEvidence(String tree, boolean slow, boolean error, List<String> reasons,
                                    String errorType, String errorMessage, String stackTrace,
                                    RecentTraceStore.IncidentCause primaryCause,
                                    RecentTraceStore.IncidentCause slowCause,
                                    RecentTraceStore.IncidentCause errorCause) { }
}
