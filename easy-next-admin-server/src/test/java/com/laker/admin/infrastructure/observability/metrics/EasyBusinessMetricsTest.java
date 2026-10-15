package com.laker.admin.infrastructure.observability.metrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class EasyBusinessMetricsTest {

    @Test
    void monitorKillSwitchSkipsAllCustomMetricRegistration() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EasyBusinessMetrics metrics = new EasyBusinessMetrics(meterRegistry, false);

        metrics.recordApiAccess("Controller", "action", true, 1);
        metrics.recordRemoteCall("Client", "fetch", false, 1);
        metrics.recordRateLimitBlocked("rule", "IP");
        metrics.recordScheduleJob("job", true, 1);
        metrics.recordOutboxMessage("operation", "SENT");

        assertThat(meterRegistry.getMeters()).isEmpty();
    }

    @Test
    void recordsApiAccessByControllerActionAndResult() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EasyBusinessMetrics metrics = new EasyBusinessMetrics(meterRegistry);

        metrics.recordApiAccess("SysUserController", "pageAll", true, 37);

        Timer timer = meterRegistry.find(EasyBusinessMetrics.API_ACCESS_TIMER)
                .tags("controller", "SysUserController", "action", "pageAll", "result", "success")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(37.0);
        assertThat(timer.takeSnapshot().percentileValues()).isEmpty();
    }

    @Test
    void recordsRemoteCallsThroughTheSameBusinessMetricsModel() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EasyBusinessMetrics metrics = new EasyBusinessMetrics(meterRegistry);

        metrics.recordRemoteCall("WorkflowClient", "submit", false, TimeUnit.MILLISECONDS.toNanos(12));

        Timer timer = meterRegistry.find(EasyBusinessMetrics.REMOTE_CALL_TIMER)
                .tags("target", "WorkflowClient", "method", "submit", "result", "failure")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(12.0);
    }

    @Test
    void recordsRateLimitBlocksByRuleAndType() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EasyBusinessMetrics metrics = new EasyBusinessMetrics(meterRegistry);

        metrics.recordRateLimitBlocked("auth:login", "CLIENT_IP");

        Counter counter = meterRegistry.find(EasyBusinessMetrics.RATE_LIMIT_BLOCKED_COUNTER)
                .tags("rule", "auth:login", "type", "CLIENT_IP")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordsScheduleJobsByCodeAndResult() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EasyBusinessMetrics metrics = new EasyBusinessMetrics(meterRegistry);

        metrics.recordScheduleJob("infra_local_message_retry", false, 25);

        Timer timer = meterRegistry.find(EasyBusinessMetrics.SCHEDULE_JOB_TIMER)
                .tags("job", "infra_local_message_retry", "result", "failure")
                .timer();

        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(25.0);
    }

    @Test
    void recordsOutboxMessageStatusChanges() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EasyBusinessMetrics metrics = new EasyBusinessMetrics(meterRegistry);

        metrics.recordOutboxMessage("workflow_notify", "FAILED");

        Counter counter = meterRegistry.find(EasyBusinessMetrics.OUTBOX_MESSAGE_COUNTER)
                .tags("operation", "workflow_notify", "status", "FAILED")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void reusesRegisteredMeterOnTheHotPath() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AtomicInteger filterInvocations = new AtomicInteger();
        meterRegistry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if (EasyBusinessMetrics.API_ACCESS_TIMER.equals(id.getName())) {
                    filterInvocations.incrementAndGet();
                }
                return id;
            }
        });
        EasyBusinessMetrics metrics = new EasyBusinessMetrics(meterRegistry);

        for (int i = 0; i < 100; i++) {
            metrics.recordApiAccess("SysUserController", "pageAll", true, i);
        }

        assertThat(filterInvocations).hasValue(1);
        assertThat(meterRegistry.find(EasyBusinessMetrics.API_ACCESS_TIMER).timer().count()).isEqualTo(100);
    }

    @Test
    void boundsSeriesAndAggregatesOverflow() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        meterRegistry.config().meterFilter(new DiagnosisMeterFilterConfiguration()
                .diagnosisSeriesCardinalityLimit());
        EasyBusinessMetrics metrics = new EasyBusinessMetrics(meterRegistry);
        int samples = EasyBusinessMetrics.API_ACCESS_SERIES_LIMIT + 100;

        for (int i = 0; i < samples; i++) {
            metrics.recordApiAccess("Controller" + i, "action" + i, true, 1);
        }

        long series = meterRegistry.getMeters().stream()
                .filter(meter -> EasyBusinessMetrics.API_ACCESS_TIMER.equals(meter.getId().getName()))
                .count();
        Timer overflow = meterRegistry.find(EasyBusinessMetrics.API_ACCESS_TIMER)
                .tags("controller", "unknown", "action", "unknown", "result", "success")
                .timer();
        assertThat(series).isLessThanOrEqualTo(EasyBusinessMetrics.API_ACCESS_SERIES_LIMIT);
        assertThat(overflow).isNotNull();
        assertThat(overflow.count()).isEqualTo(samples - (EasyBusinessMetrics.API_ACCESS_SERIES_LIMIT - 2));
    }

    @Test
    void normalizesBlankAndLongTagValues() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        EasyBusinessMetrics metrics = new EasyBusinessMetrics(meterRegistry);
        String longAction = "😀".repeat(EasyBusinessMetrics.MAX_TAG_VALUE_LENGTH);

        metrics.recordApiAccess("  ", longAction, true, 1);

        Timer timer = meterRegistry.find(EasyBusinessMetrics.API_ACCESS_TIMER)
                .tag("controller", "unknown")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getTag("action"))
                .hasSizeLessThanOrEqualTo(EasyBusinessMetrics.MAX_TAG_VALUE_LENGTH)
                .doesNotEndWith("\uD83D");
    }

    @Test
    void keepsConcurrentHighCardinalityRecordingBoundedWithoutDroppingCounts() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        meterRegistry.config().meterFilter(new DiagnosisMeterFilterConfiguration()
                .diagnosisSeriesCardinalityLimit());
        EasyBusinessMetrics metrics = new EasyBusinessMetrics(meterRegistry);
        int samples = 2_000;

        IntStream.range(0, samples).parallel()
                .forEach(i -> metrics.recordRateLimitBlocked("rule-" + i, "TYPE-" + i));

        var counters = meterRegistry.find(EasyBusinessMetrics.RATE_LIMIT_BLOCKED_COUNTER).counters();
        assertThat(counters).hasSizeLessThanOrEqualTo(EasyBusinessMetrics.RATE_LIMIT_SERIES_LIMIT);
        assertThat(counters.stream().mapToDouble(Counter::count).sum()).isEqualTo(samples);
    }

    @Test
    void meterRegistrationFailureDoesNotEscapeOrRetryOnEveryCall() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AtomicInteger attempts = new AtomicInteger();
        meterRegistry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if (EasyBusinessMetrics.REMOTE_CALL_TIMER.equals(id.getName())) {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("registry unavailable");
                }
                return id;
            }
        });
        EasyBusinessMetrics metrics = new EasyBusinessMetrics(meterRegistry);

        assertThatCode(() -> {
            metrics.recordRemoteCall("RemoteClient", "fetch", true, 1);
            metrics.recordRemoteCall("RemoteClient", "fetch", true, 2);
        }).doesNotThrowAnyException();
        assertThat(attempts).hasValue(1);
    }
}
