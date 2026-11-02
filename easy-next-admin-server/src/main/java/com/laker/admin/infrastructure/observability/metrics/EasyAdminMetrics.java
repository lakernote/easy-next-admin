package com.laker.admin.infrastructure.observability.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/** Metrics owned by the admin application, separate from the reusable trace starter. */
public class EasyAdminMetrics {
    public static final String API_REQUEST_TIMER = "easy.api.requests";
    public static final String RATE_LIMIT_BLOCKED = "easy.rate.limit.blocked";
    public static final String OUTBOX_MESSAGES = "easy.outbox.messages";
    public static final String SCHEDULE_JOBS = "easy.schedule.jobs";

    private final MeterRegistry meterRegistry;
    private final boolean enabled;

    public EasyAdminMetrics(MeterRegistry meterRegistry) {
        this(meterRegistry, true);
    }

    public EasyAdminMetrics(MeterRegistry meterRegistry, boolean enabled) {
        this.meterRegistry = meterRegistry;
        this.enabled = enabled && meterRegistry != null;
    }

    public void recordApiAccess(String controller, String action, boolean success, long durationMs) {
        if (!enabled) return;
        Timer.builder(API_REQUEST_TIMER)
                .tag("controller", safe(controller))
                .tag("action", safe(action))
                .tag("outcome", success ? "success" : "failure")
                .register(meterRegistry)
                .record(Math.max(0, durationMs), TimeUnit.MILLISECONDS);
    }

    public void recordRateLimitBlocked(String key, String type) {
        if (enabled) meterRegistry.counter(RATE_LIMIT_BLOCKED,
                "key", safe(key), "type", safe(type)).increment();
    }

    public void recordOutboxMessage(String operation, String status) {
        if (enabled) meterRegistry.counter(OUTBOX_MESSAGES,
                "operation", safe(operation), "status", safe(status)).increment();
    }

    public void recordScheduleJob(String jobCode, boolean success, long durationMs) {
        if (!enabled) return;
        Timer.builder(SCHEDULE_JOBS)
                .tag("job", safe(jobCode))
                .tag("outcome", success ? "success" : "failure")
                .register(meterRegistry)
                .record(Math.max(0, durationMs), TimeUnit.MILLISECONDS);
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "unknown";
        return value.length() > 128 ? value.substring(0, 128) : value;
    }
}
