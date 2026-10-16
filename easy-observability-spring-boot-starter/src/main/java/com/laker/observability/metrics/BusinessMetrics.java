package com.laker.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 业务指标统一入口。
 *
 * <p>仅维护低基数、长度有界的标签。Meter 句柄在首次出现标签组合时注册并永久复用；缓存达到上限后，
 * 新组合归入 {@code unknown} 汇总序列，避免缓存淘汰后 MeterRegistry 仍持续累积历史序列。</p>
 */
/** Records common application metrics with bounded tag values and bounded meter caches. */
public class BusinessMetrics {
    private static final Logger log = LoggerFactory.getLogger(BusinessMetrics.class);
    public static final String API_ACCESS_TIMER = "easy.api.requests";
    public static final String REMOTE_CALL_TIMER = "easy.remote.calls";
    public static final String RATE_LIMIT_BLOCKED_COUNTER = "easy.rate_limit.blocked";
    public static final String SCHEDULE_JOB_TIMER = "easy.schedule.jobs";
    public static final String OUTBOX_MESSAGE_COUNTER = "easy.outbox.messages";
    public static final String TRANSACTION_TIMER = "easy.transactions";

    static final int MAX_TAG_VALUE_LENGTH = 128;
    static final int API_ACCESS_SERIES_LIMIT = 512;
    static final int REMOTE_CALL_SERIES_LIMIT = 256;
    static final int RATE_LIMIT_SERIES_LIMIT = 128;
    static final int SCHEDULE_JOB_SERIES_LIMIT = 256;
    static final int OUTBOX_MESSAGE_SERIES_LIMIT = 128;

    private static final int RESULT_VARIANTS = 2;
    private static final String UNKNOWN = "unknown";
    private static final String SUCCESS = "success";
    private static final String FAILURE = "failure";

    private final MeterRegistry meterRegistry;
    private final boolean enabled;
    private final AtomicBoolean failureLogged = new AtomicBoolean();
    private final BoundedMeterCache<ApiKey, Timer> apiTimers;
    private final BoundedMeterCache<String, Timer> apiOverflowTimers;
    private final BoundedMeterCache<RemoteKey, Timer> remoteTimers;
    private final BoundedMeterCache<String, Timer> remoteOverflowTimers;
    private final BoundedMeterCache<RateLimitKey, Counter> rateLimitCounters;
    private final BoundedMeterCache<String, Counter> rateLimitOverflowCounters;
    private final BoundedMeterCache<ScheduleJobKey, Timer> scheduleJobTimers;
    private final BoundedMeterCache<String, Timer> scheduleJobOverflowTimers;
    private final BoundedMeterCache<OutboxKey, Counter> outboxCounters;
    private final BoundedMeterCache<String, Counter> outboxOverflowCounters;

    public BusinessMetrics(MeterRegistry meterRegistry) {
        this(meterRegistry, true);
    }

    public BusinessMetrics(MeterRegistry meterRegistry, boolean enabled) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.enabled = enabled;
        this.apiTimers = new BoundedMeterCache<>(API_ACCESS_SERIES_LIMIT - RESULT_VARIANTS,
                this::registerApiTimer);
        this.apiOverflowTimers = new BoundedMeterCache<>(RESULT_VARIANTS,
                result -> registerApiTimer(new ApiKey(UNKNOWN, UNKNOWN, result)));
        this.remoteTimers = new BoundedMeterCache<>(REMOTE_CALL_SERIES_LIMIT - RESULT_VARIANTS,
                this::registerRemoteTimer);
        this.remoteOverflowTimers = new BoundedMeterCache<>(RESULT_VARIANTS,
                result -> registerRemoteTimer(new RemoteKey(UNKNOWN, UNKNOWN, result)));
        this.rateLimitCounters = new BoundedMeterCache<>(RATE_LIMIT_SERIES_LIMIT - 1,
                this::registerRateLimitCounter);
        this.rateLimitOverflowCounters = new BoundedMeterCache<>(1,
                ignored -> registerRateLimitCounter(new RateLimitKey(UNKNOWN, UNKNOWN)));
        this.scheduleJobTimers = new BoundedMeterCache<>(SCHEDULE_JOB_SERIES_LIMIT - RESULT_VARIANTS,
                this::registerScheduleJobTimer);
        this.scheduleJobOverflowTimers = new BoundedMeterCache<>(RESULT_VARIANTS,
                result -> registerScheduleJobTimer(new ScheduleJobKey(UNKNOWN, result)));
        this.outboxCounters = new BoundedMeterCache<>(OUTBOX_MESSAGE_SERIES_LIMIT - 1,
                this::registerOutboxCounter);
        this.outboxOverflowCounters = new BoundedMeterCache<>(1,
                ignored -> registerOutboxCounter(new OutboxKey(UNKNOWN, UNKNOWN)));
    }

    /** Records one API access duration in milliseconds. */
    public void recordApiAccess(String controller, String action, boolean success, long durationMs) {
        if (!enabled) return;
        String result = result(success);
        MeterHandle<Timer> handle = apiTimers.get(new ApiKey(tagValue(controller), tagValue(action), result));
        if (handle == null) {
            handle = apiOverflowTimers.get(result);
        }
        record(handle, Math.max(durationMs, 0), TimeUnit.MILLISECONDS);
    }

    /** Records one remote call duration in nanoseconds. */
    public void recordRemoteCall(String target, String method, boolean success, long durationNanos) {
        if (!enabled) return;
        String result = result(success);
        MeterHandle<Timer> handle = remoteTimers.get(new RemoteKey(tagValue(target), tagValue(method), result));
        if (handle == null) {
            handle = remoteOverflowTimers.get(result);
        }
        record(handle, Math.max(durationNanos, 0), TimeUnit.NANOSECONDS);
    }

    /** Increments the counter for a rejected request. */
    public void recordRateLimitBlocked(String rule, String type) {
        if (!enabled) return;
        MeterHandle<Counter> handle = rateLimitCounters.get(new RateLimitKey(tagValue(rule), tagValue(type)));
        if (handle == null) {
            handle = rateLimitOverflowCounters.get(UNKNOWN);
        }
        increment(handle);
    }

    /** Records one scheduled job duration in milliseconds. */
    public void recordScheduleJob(String jobCode, boolean success, Integer durationMs) {
        if (!enabled) return;
        String result = result(success);
        MeterHandle<Timer> handle = scheduleJobTimers.get(new ScheduleJobKey(tagValue(jobCode), result));
        if (handle == null) {
            handle = scheduleJobOverflowTimers.get(result);
        }
        record(handle, Math.max(durationMs == null ? 0 : durationMs, 0), TimeUnit.MILLISECONDS);
    }

    /** Increments the counter for an outbox operation and resulting status. */
    public void recordOutboxMessage(String operation, String status) {
        if (!enabled) return;
        MeterHandle<Counter> handle = outboxCounters.get(new OutboxKey(tagValue(operation), tagValue(status)));
        if (handle == null) {
            handle = outboxOverflowCounters.get(UNKNOWN);
        }
        increment(handle);
    }

    private MeterHandle<Timer> registerApiTimer(ApiKey key) {
        return register(() -> Timer.builder(API_ACCESS_TIMER)
                .description("API access duration")
                .tag("controller", key.controller())
                .tag("action", key.action())
                .tag("result", key.result())
                .register(meterRegistry));
    }

    private MeterHandle<Timer> registerRemoteTimer(RemoteKey key) {
        return register(() -> Timer.builder(REMOTE_CALL_TIMER)
                .description("Remote call duration")
                .tag("target", key.target())
                .tag("method", key.method())
                .tag("result", key.result())
                .register(meterRegistry));
    }

    private MeterHandle<Counter> registerRateLimitCounter(RateLimitKey key) {
        return register(() -> Counter.builder(RATE_LIMIT_BLOCKED_COUNTER)
                .description("Rate limit rejections")
                .tag("rule", key.rule())
                .tag("type", key.type())
                .register(meterRegistry));
    }

    private MeterHandle<Timer> registerScheduleJobTimer(ScheduleJobKey key) {
        return register(() -> Timer.builder(SCHEDULE_JOB_TIMER)
                .description("Scheduled job duration")
                .tag("job", key.job())
                .tag("result", key.result())
                .register(meterRegistry));
    }

    private MeterHandle<Counter> registerOutboxCounter(OutboxKey key) {
        return register(() -> Counter.builder(OUTBOX_MESSAGE_COUNTER)
                .description("Outbox message state changes")
                .tag("operation", key.operation())
                .tag("status", key.status())
                .register(meterRegistry));
    }

    private <M> MeterHandle<M> register(Supplier<M> supplier) {
        try {
            return new MeterHandle<>(supplier.get());
        } catch (RuntimeException exception) {
            reportFailure("业务指标注册失败，已停用对应指标序列", exception);
            return new MeterHandle<>(null);
        }
    }

    private void record(MeterHandle<Timer> handle, long duration, TimeUnit unit) {
        if (handle == null) {
            return;
        }
        Timer timer = handle.meter();
        if (timer == null) {
            return;
        }
        try {
            timer.record(duration, unit);
        } catch (RuntimeException exception) {
            handle.disable();
            reportFailure("业务耗时指标记录失败，已停用对应指标序列", exception);
        }
    }

    private void increment(MeterHandle<Counter> handle) {
        if (handle == null) {
            return;
        }
        Counter counter = handle.meter();
        if (counter == null) {
            return;
        }
        try {
            counter.increment();
        } catch (RuntimeException exception) {
            handle.disable();
            reportFailure("业务计数指标记录失败，已停用对应指标序列", exception);
        }
    }

    private void reportFailure(String message, RuntimeException exception) {
        if (failureLogged.compareAndSet(false, true)) {
            log.warn("{}: {}", message, exception.toString());
            log.debug(message, exception);
        }
    }

    private String result(boolean success) {
        return success ? SUCCESS : FAILURE;
    }

    private String tagValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            return UNKNOWN;
        }
        if (normalized.length() <= MAX_TAG_VALUE_LENGTH) {
            return normalized;
        }
        int endIndex = MAX_TAG_VALUE_LENGTH;
        if (Character.isHighSurrogate(normalized.charAt(endIndex - 1))) {
            endIndex--;
        }
        return normalized.substring(0, endIndex);
    }

    private record ApiKey(String controller, String action, String result) {
    }

    private record RemoteKey(String target, String method, String result) {
    }

    private record RateLimitKey(String rule, String type) {
    }

    private record ScheduleJobKey(String job, String result) {
    }

    private record OutboxKey(String operation, String status) {
    }

    private static final class MeterHandle<M> {
        private volatile M meter;

        private MeterHandle(M meter) {
            this.meter = meter;
        }

        private M meter() {
            return meter;
        }

        private void disable() {
            meter = null;
        }
    }

    /**
     * MeterRegistry 本身不会在本地缓存淘汰后删除 meter，因此这里达到上限后停止接纳新 key。
     * 已存在 key 无锁读取，只有首次注册进入同步区。
     */
    private static final class BoundedMeterCache<K, M> {
        private final int maximumSize;
        private final Function<K, MeterHandle<M>> factory;
        private final ConcurrentMap<K, MeterHandle<M>> entries = new ConcurrentHashMap<>();
        private final Object registrationMonitor = new Object();
        private volatile boolean full;

        private BoundedMeterCache(int maximumSize, Function<K, MeterHandle<M>> factory) {
            this.maximumSize = maximumSize;
            this.factory = factory;
        }

        private MeterHandle<M> get(K key) {
            if (full) {
                return entries.get(key);
            }
            MeterHandle<M> existing = entries.get(key);
            if (existing != null) {
                return existing;
            }
            synchronized (registrationMonitor) {
                existing = entries.get(key);
                if (existing != null) {
                    return existing;
                }
                if (entries.size() >= maximumSize) {
                    full = true;
                    return null;
                }
                MeterHandle<M> created = factory.apply(key);
                entries.put(key, created);
                if (entries.size() >= maximumSize) {
                    full = true;
                }
                return created;
            }
        }
    }
}
