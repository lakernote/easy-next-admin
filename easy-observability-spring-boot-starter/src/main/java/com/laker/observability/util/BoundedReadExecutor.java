package com.laker.observability.util;


import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Limits concurrent local-history scans without involving diagnostic writers.
 *
 * <p>A diagnostic query must never wait indefinitely for local disk admission: otherwise a burst of privileged
 * dashboard requests can occupy the whole servlet pool while only a few threads perform useful work. Nested reads
 * on the admitted thread share its permit, which lets a complete multi-file query hold one admission slot.</p>
 */
public final class BoundedReadExecutor {
    private static final Duration DEFAULT_ADMISSION_TIMEOUT = Duration.ofMillis(100);
    private static final String BUSY_MESSAGE = "本地诊断历史正在读取，请稍后重试";

    private final Semaphore permits;
    private final long admissionTimeoutNanos;
    private final ThreadLocal<Boolean> entered = ThreadLocal.withInitial(() -> false);

    public BoundedReadExecutor(int maxConcurrentReads) {
        this(maxConcurrentReads, DEFAULT_ADMISSION_TIMEOUT);
    }

    BoundedReadExecutor(int maxConcurrentReads, Duration admissionTimeout) {
        permits = new Semaphore(Math.max(1, maxConcurrentReads), true);
        Duration timeout = admissionTimeout == null || admissionTimeout.isNegative()
                ? DEFAULT_ADMISSION_TIMEOUT : admissionTimeout;
        admissionTimeoutNanos = timeout.toNanos();
    }

    public <T> T execute(Supplier<T> task) {
        Objects.requireNonNull(task, "task");
        if (entered.get()) return task.get();
        boolean admitted;
        try {
            admitted = permits.tryAcquire(admissionTimeoutNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(BUSY_MESSAGE);
        }
        if (!admitted) throw new IllegalStateException(BUSY_MESSAGE);
        entered.set(true);
        try {
            return task.get();
        } finally {
            entered.remove();
            permits.release();
        }
    }

}
