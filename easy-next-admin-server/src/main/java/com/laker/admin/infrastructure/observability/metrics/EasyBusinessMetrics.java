package com.laker.admin.infrastructure.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 业务指标统一入口。
 *
 * <p>这里仅维护低基数标签，避免把 URI、用户 ID、请求参数等高基数字段写入指标系统。</p>
 */
@Component
@RequiredArgsConstructor
public class EasyBusinessMetrics {
    public static final String API_ACCESS_TIMER = "easy.api.requests";
    public static final String REMOTE_CALL_TIMER = "easy.remote.calls";
    public static final String RATE_LIMIT_BLOCKED_COUNTER = "easy.rate_limit.blocked";
    public static final String SCHEDULE_JOB_TIMER = "easy.schedule.jobs";
    public static final String OUTBOX_MESSAGE_COUNTER = "easy.outbox.messages";

    private static final String UNKNOWN = "unknown";

    private final MeterRegistry meterRegistry;

    public void recordApiAccess(String controller, String action, boolean success, long durationMs) {
        Timer.builder(API_ACCESS_TIMER)
                .description("EasyNextAdmin API 访问耗时")
                .tag("controller", tagValue(controller))
                .tag("action", tagValue(action))
                .tag("result", result(success))
                .register(meterRegistry)
                .record(Math.max(durationMs, 0), TimeUnit.MILLISECONDS);
    }

    public void recordRemoteCall(String target, String method, boolean success, long durationNanos) {
        Timer.builder(REMOTE_CALL_TIMER)
                .description("EasyNextAdmin 远程调用耗时")
                .tag("target", tagValue(target))
                .tag("method", tagValue(method))
                .tag("result", result(success))
                .register(meterRegistry)
                .record(Math.max(durationNanos, 0), TimeUnit.NANOSECONDS);
    }

    public void recordRateLimitBlocked(String rule, String type) {
        Counter.builder(RATE_LIMIT_BLOCKED_COUNTER)
                .description("EasyNextAdmin 限流拦截次数")
                .tag("rule", tagValue(rule))
                .tag("type", tagValue(type))
                .register(meterRegistry)
                .increment();
    }

    public void recordScheduleJob(String jobCode, boolean success, Integer durationMs) {
        Timer.builder(SCHEDULE_JOB_TIMER)
                .description("EasyNextAdmin 调度任务执行耗时")
                .tag("job", tagValue(jobCode))
                .tag("result", result(success))
                .register(meterRegistry)
                .record(Math.max(durationMs == null ? 0 : durationMs, 0), TimeUnit.MILLISECONDS);
    }

    public void recordOutboxMessage(String operation, String status) {
        Counter.builder(OUTBOX_MESSAGE_COUNTER)
                .description("EasyNextAdmin 本地消息状态变更次数")
                .tag("operation", tagValue(operation))
                .tag("status", tagValue(status))
                .register(meterRegistry)
                .increment();
    }

    private String result(boolean success) {
        return success ? "success" : "failure";
    }

    private String tagValue(String value) {
        return StringUtils.hasText(value) ? value : UNKNOWN;
    }
}
