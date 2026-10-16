package com.laker.observability.trace;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextTest {

    @AfterEach
    void clearContext() {
        TraceContext.clear();
    }

    @Test
    void shouldSkipRenderingForNormalOperationBelowThreshold() {
        TraceContext.startRoot("GET /api/users", SpanKind.HTTP, "", 8, 0);
        TraceContext.addSpan("UserService#list", SpanKind.SERVICE);
        TraceContext.stopSpan();
        AtomicReference<TraceContext.Completion> result = new AtomicReference<>();

        TraceContext.stopRoot(Long.MAX_VALUE, "test", null, result::set);

        assertThat(result.get().tree()).isEmpty();
        assertThat(result.get().slow()).isFalse();
        assertThat(result.get().error()).isFalse();
    }

    @Test
    void shouldRenderTreeWhenChildMarksTraceSlow() {
        TraceContext.startRoot("GET /api/users", SpanKind.HTTP, "", 8, 0);
        TraceContext.addSpan("UserService#list", SpanKind.SERVICE);
        TraceContext.stopSpan();
        TraceContext.markSlow("SLOW_SQL");
        AtomicReference<TraceContext.Completion> result = new AtomicReference<>();

        TraceContext.stopRoot(Long.MAX_VALUE, "test", null, result::set);

        assertThat(result.get().tree()).contains("GET /api/users", "UserService#list");
        assertThat(result.get().slow()).isTrue();
        assertThat(result.get().reasons()).containsExactly("SLOW_SQL");
    }

    @Test
    void shouldRenderTreeWhenChildMarksTraceAsError() {
        TraceContext.startRoot("GET /api/users", SpanKind.HTTP, "", 8, 0);
        IllegalStateException failure = new IllegalStateException("boom");
        TraceContext.addSpan("UserRepository#list", SpanKind.DATABASE);
        TraceContext.stopSpan();
        TraceContext.markError("SQL_ERROR", failure);
        AtomicReference<TraceContext.Completion> result = new AtomicReference<>();

        TraceContext.stopRoot(Long.MAX_VALUE, "test", null, result::set);

        assertThat(result.get().tree()).contains("GET /api/users", "UserRepository#list");
        assertThat(result.get().error()).isTrue();
        assertThat(result.get().errorType()).isEqualTo(IllegalStateException.class.getName());
        assertThat(result.get().reasons()).containsExactly("SQL_ERROR");
    }

    @Test
    void shouldTreatZeroThresholdAsDisabledForNormalOperations() {
        TraceContext.startRoot("diagnostic", SpanKind.HTTP, "", 8, 0);
        AtomicReference<TraceContext.Completion> result = new AtomicReference<>();

        TraceContext.stopRoot(0, "test", null, result::set);

        assertThat(result.get().tree()).isEmpty();
        assertThat(result.get().slow()).isFalse();
    }

    @Test
    void shouldRetainAsyncTreeWithoutMaterializingItUntilRequested() {
        TraceContext.startRoot("GET /api/stream", SpanKind.HTTP, "", 8, 0);
        TraceContext.addSpan("StreamService#open", SpanKind.SERVICE);
        TraceContext.stopSpan();
        AtomicReference<TraceContext.DeferredCompletion> result = new AtomicReference<>();

        TraceContext.stopRootDeferred(null, result::set);

        assertThat(result.get().slow()).isFalse();
        assertThat(result.get().error()).isFalse();
        assertThat(result.get().tree()).contains("GET /api/stream", "StreamService#open");
    }

    @Test
    void deferredCompletionMustKeepTheDomainErrorReasonWithoutAddingRootFallback() {
        IllegalStateException failure = new IllegalStateException("async boom");
        TraceContext.startRoot("GET /api/stream", SpanKind.HTTP, "", 8, 0);
        TraceContext.markError("HTTP_ERROR", failure);
        AtomicReference<TraceContext.DeferredCompletion> result = new AtomicReference<>();

        TraceContext.stopRootDeferred(failure, result::set);

        assertThat(result.get().error()).isTrue();
        assertThat(result.get().reasons()).containsExactly("HTTP_ERROR");
    }

    @Test
    void shouldKeepFullErrorEvidenceInCompletionButLogOnlyBoundedSummary() {
        Logger logger = (Logger) LoggerFactory.getLogger(TraceContext.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        boolean additive = logger.isAdditive();
        logger.setAdditive(false);
        logger.addAppender(appender);
        appender.start();
        try {
            IllegalStateException failure = new IllegalStateException("raw-error-message");
            TraceContext.startRoot("POST /orders", SpanKind.HTTP, "", 8, 0);
            TraceContext.addSpan("OrderService#create", SpanKind.SERVICE);
            TraceContext.stopSpan();
            AtomicReference<TraceContext.Completion> result = new AtomicReference<>();

            TraceContext.stopRoot(Long.MAX_VALUE, "root\nerror", failure, result::set);

            assertThat(result.get().tree()).contains("POST /orders", "OrderService#create");
            assertThat(result.get().stackTrace()).contains("raw-error-message");
            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getFormattedMessage())
                        .contains("运行诊断 incident 摘要", "root error", IllegalStateException.class.getName())
                        .doesNotContain("raw-error-message", "POST /orders", "OrderService#create", "\n", "\r");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
            logger.setAdditive(additive);
            appender.stop();
        }
    }

    @Test
    void shouldRateLimitIncidentLogSummariesAndReportPreviousSuppression() {
        TraceContext.IncidentLogLimiter limiter = new TraceContext.IncidentLogLimiter(2, 100);

        assertThat(limiter.tryAcquire(0)).isZero();
        assertThat(limiter.tryAcquire(1)).isZero();
        assertThat(limiter.tryAcquire(2)).isEqualTo(-1);
        assertThat(limiter.tryAcquire(100)).isEqualTo(1);
        assertThat(limiter.tryAcquire(101)).isZero();
        assertThat(limiter.tryAcquire(102)).isEqualTo(-1);
        // A stale caller cannot roll the limiter back into an older window.
        assertThat(limiter.tryAcquire(99)).isEqualTo(-1);
        assertThat(limiter.tryAcquire(200)).isEqualTo(2);
    }

    @Test
    void preformattedFailureEvidenceMustNotWalkTheThrowableStackAgain() {
        CountingFailure failure = new CountingFailure("boom");
        TraceContext.startRoot("POST /orders", SpanKind.HTTP, "", 8, 0);
        BoundedThrowableFormatter.FailureEvidence evidence = BoundedThrowableFormatter.capture(failure);
        TraceContext.markError("HTTP_ERROR", evidence);
        AtomicReference<TraceContext.Completion> result = new AtomicReference<>();

        TraceContext.stopRoot(Long.MAX_VALUE, "test", failure, result::set);

        assertThat(failure.stackReads).hasValue(1);
        assertThat(result.get().stackTrace()).contains("CountingFailure");
    }

    @Test
    void frozenRootBoundaryMustExcludeMonitoringFinalizationFromSlowDecision() {
        TraceContext.startRoot("GET /fast", SpanKind.HTTP, "", 8, 0);
        TraceContext.freezeRootBoundary();
        java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(75));
        AtomicReference<TraceContext.Completion> result = new AtomicReference<>();

        TraceContext.stopRoot(50, "test", null, result::set);

        assertThat(result.get().costMs()).isLessThan(50);
        assertThat(result.get().slow()).isFalse();
    }

    @Test
    void explicitStartBoundaryMustExcludeMonitoringSetupFromSlowDecision() {
        TraceContext.startRoot("GET /fast", SpanKind.HTTP, "", 8, 0);
        java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(75));
        TraceContext.beginCurrentSpanBoundary();
        TraceContext.freezeRootBoundary();
        AtomicReference<TraceContext.Completion> result = new AtomicReference<>();

        TraceContext.stopRoot(50, "test", null, result::set);

        assertThat(result.get().costMs()).isLessThan(50);
        assertThat(result.get().slow()).isFalse();
    }

    private static final class CountingFailure extends RuntimeException {
        private final AtomicInteger stackReads = new AtomicInteger();

        private CountingFailure(String message) {
            super(message);
        }

        @Override
        public StackTraceElement[] getStackTrace() {
            stackReads.incrementAndGet();
            return super.getStackTrace();
        }
    }
}
