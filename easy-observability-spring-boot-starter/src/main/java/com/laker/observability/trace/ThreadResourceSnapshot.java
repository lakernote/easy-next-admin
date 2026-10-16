package com.laker.observability.trace;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

/** Captures bounded per-thread JVM counters without retaining application arguments or payloads. */
public record ThreadResourceSnapshot(long threadId, long cpuNanos, long allocatedBytes,
                                     long blockedCount, long blockedTimeMs,
                                     long waitedCount, long waitedTimeMs) {
    private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();
    private static final com.sun.management.ThreadMXBean HOTSPOT =
            THREADS instanceof com.sun.management.ThreadMXBean bean ? bean : null;

    public static ThreadResourceSnapshot capture(long threadId, boolean enabled) {
        if (!enabled) return new ThreadResourceSnapshot(threadId, -1, -1, -1, -1, -1, -1);
        long cpu = -1;
        long allocated = -1;
        ThreadInfo info = null;
        try {
            if (THREADS.isThreadCpuTimeSupported() && THREADS.isThreadCpuTimeEnabled()) {
                cpu = THREADS.getThreadCpuTime(threadId);
            }
            if (HOTSPOT != null && HOTSPOT.isThreadAllocatedMemorySupported()
                    && HOTSPOT.isThreadAllocatedMemoryEnabled()) {
                allocated = HOTSPOT.getThreadAllocatedBytes(threadId);
            }
            info = THREADS.getThreadInfo(threadId);
        } catch (Throwable ignored) { }
        return new ThreadResourceSnapshot(threadId, cpu, allocated,
                info == null ? -1 : info.getBlockedCount(), info == null ? -1 : info.getBlockedTime(),
                info == null ? -1 : info.getWaitedCount(), info == null ? -1 : info.getWaitedTime());
    }

    public long cpuTimeMsSince(ThreadResourceSnapshot start) {
        return start.cpuNanos < 0 || cpuNanos < 0 ? -1 : Math.max(0, (cpuNanos - start.cpuNanos) / 1_000_000);
    }

    public long allocatedBytesSince(ThreadResourceSnapshot start) {
        return start.allocatedBytes < 0 || allocatedBytes < 0 ? -1 : Math.max(0, allocatedBytes - start.allocatedBytes);
    }

    public long blockedCountSince(ThreadResourceSnapshot start) {
        return start.blockedCount < 0 || blockedCount < 0 ? -1 : Math.max(0, blockedCount - start.blockedCount);
    }

    public long blockedTimeMsSince(ThreadResourceSnapshot start) {
        return start.blockedTimeMs < 0 || blockedTimeMs < 0 ? -1 : Math.max(0, blockedTimeMs - start.blockedTimeMs);
    }

    public long waitedCountSince(ThreadResourceSnapshot start) {
        return start.waitedCount < 0 || waitedCount < 0 ? -1 : Math.max(0, waitedCount - start.waitedCount);
    }

    public long waitedTimeMsSince(ThreadResourceSnapshot start) {
        return start.waitedTimeMs < 0 || waitedTimeMs < 0 ? -1 : Math.max(0, waitedTimeMs - start.waitedTimeMs);
    }
}
