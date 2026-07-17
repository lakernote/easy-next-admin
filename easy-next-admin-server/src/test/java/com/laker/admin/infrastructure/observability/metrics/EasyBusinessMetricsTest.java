package com.laker.admin.infrastructure.observability.metrics;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class EasyBusinessMetricsTest {

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
}
