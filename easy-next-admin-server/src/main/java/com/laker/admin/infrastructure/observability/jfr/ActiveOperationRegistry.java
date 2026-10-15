package com.laker.admin.infrastructure.observability.jfr;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks in-flight operations and samples per-thread CPU, allocation, contention, locks and stacks through JVM
 * management beans. This lightweight path remains available when continuous JFR recording is disabled.
 */
@Component
public class ActiveOperationRegistry {
    private static final List<String> KNOWN_TYPES = List.of("HTTP", "SQL", "TRANSACTION", "REMOTE", "JOB", "KAFKA");
    private static final List<String> METER_TYPES = List.of("HTTP", "SQL", "TRANSACTION", "REMOTE", "JOB", "KAFKA", "OTHER");
    private static final int MAX_STACK_DEPTH = 48;

    private final Map<Long, Entry> active = new ConcurrentHashMap<>();
    private final ThreadMXBean threadBean;
    private final com.sun.management.ThreadMXBean allocatedThreadBean;
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicInteger activeCount = new AtomicInteger();
    private final AtomicInteger activeSqlCount = new AtomicInteger();
    private final EasyNextAdminConfig config;
    private final boolean resourceSamplingEnabled;
    private final boolean sqlResourceSamplingEnabled;
    private final boolean contentionSamplingEnabled;
    private final boolean threadCpuTimeAvailable;
    private final boolean allocatedMemoryAvailable;
    private final int maxActiveEntries;
    private final int maxActiveSqlEntries;
    private final int maxDeepSamplesPerCycle;
    private final long deepSampleWindowNanos;
    private final Object deepSampleBudgetLock = new Object();
    private long deepSampleWindow = Long.MIN_VALUE;
    private int deepSamples;
    private final Map<String, Counter> droppedAdmissions;
    private final Counter deferredDeepSamples;

    @Autowired
    public ActiveOperationRegistry(MeterRegistry meterRegistry, EasyNextAdminConfig config) {
        this(meterRegistry, config, ManagementFactory.getThreadMXBean());
    }

    ActiveOperationRegistry(MeterRegistry meterRegistry, EasyNextAdminConfig config, ThreadMXBean threadBean) {
        this.config = config;
        this.threadBean = threadBean;
        this.allocatedThreadBean = threadBean instanceof com.sun.management.ThreadMXBean bean ? bean : null;
        this.resourceSamplingEnabled = config.getFeatures().isMonitor()
                && config.getMonitor().getDiagnosis().isEnabled()
                && config.getMonitor().getDiagnosis().isDetailSamplingEnabled();
        this.sqlResourceSamplingEnabled = config.getMonitor().getDiagnosis().isSqlResourceSamplingEnabled();
        this.contentionSamplingEnabled = config.getMonitor().getDiagnosis().isContentionTimeEnabled();
        this.maxActiveEntries = Math.max(1, config.getMonitor().getDiagnosis().getActiveMaxEntries());
        this.maxActiveSqlEntries = Math.max(0, Math.min(maxActiveEntries,
                config.getMonitor().getDiagnosis().getActiveMaxSqlEntries()));
        this.maxDeepSamplesPerCycle = Math.max(1,
                config.getMonitor().getDiagnosis().getActiveMaxSamplesPerCycle());
        this.deepSampleWindowNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(1L,
                config.getMonitor().getDiagnosis().getActiveSampleIntervalMs()));
        if (resourceSamplingEnabled) {
            enableJvmResourceMonitoring();
        }
        this.threadCpuTimeAvailable = resourceSamplingEnabled && threadCpuTimeAvailable();
        this.allocatedMemoryAvailable = resourceSamplingEnabled && allocatedMemoryAvailable();
        KNOWN_TYPES.forEach(type -> Gauge.builder("easy.operations.active", active,
                        values -> values.values().stream().filter(entry -> type.equals(entry.type)).count())
                .description("EasyNextAdmin 当前执行中的操作数")
                .tag("type", type)
                .register(meterRegistry));
        Map<String, Counter> admissionMeters = new HashMap<>();
        METER_TYPES.forEach(type -> admissionMeters.put(type,
                Counter.builder("easy.diagnosis.active.admission.dropped")
                        .description("运行诊断活跃操作达到容量上限后的拒绝数")
                        .tag("type", type)
                        .register(meterRegistry)));
        this.droppedAdmissions = Map.copyOf(admissionMeters);
        this.deferredDeepSamples = Counter.builder("easy.diagnosis.active.deep.sample.deferred")
                .description("因单轮深诊断线程上限而顺延的慢操作数")
                .register(meterRegistry);
    }

    public Scope start(String type, String traceId, String name, String detail) {
        if (!resourceSamplingEnabled) return new Scope(-1);
        String operationType = safe(type);
        if (!reserve(operationType)) {
            droppedAdmissions.get(meterType(operationType)).increment();
            return new Scope(-1);
        }
        long id = sequence.incrementAndGet();
        Thread thread = Thread.currentThread();
        boolean sampleResources = !"SQL".equals(operationType) || sqlResourceSamplingEnabled;
        boolean stored = false;
        try {
            ThreadInfo baseline = sampleResources && contentionSamplingEnabled ? threadInfo(thread.getId()) : null;
            Entry entry = new Entry(id, operationType, safe(traceId), safe(name), safe(detail), thread.getId(),
                    thread.getName(), System.nanoTime(), sampleResources,
                    sampleResources ? cpuTime(thread.getId()) : -1,
                    sampleResources ? allocatedBytes(thread.getId()) : -1,
                    blockedCount(baseline), blockedTime(baseline), waitedCount(baseline), waitedTime(baseline));
            active.put(id, entry);
            stored = true;
            return new Scope(id);
        } finally {
            if (!stored) releaseReservation(operationType);
        }
    }

    @Scheduled(fixedDelayString = "${easy.monitor.diagnosis.active-sample-interval-ms:500}",
            initialDelayString = "500")
    public void captureSlowOperations() {
        if (!resourceSamplingEnabled || active.isEmpty()) return;
        long now = System.nanoTime();
        List<SlowSampleCandidate> eligible = new ArrayList<>(Math.min(activeCount.get(), maxActiveEntries));
        active.values().forEach(entry -> {
            long durationMs = Math.max(0, (now - entry.startedNanos) / 1_000_000);
            if (durationMs < threshold(entry.type)) return;

            synchronized (entry) {
                if (entry.threadId < 0 || entry.diagnostic.hasEvidence()
                        || entry.sampled) {
                    return;
                }
                eligible.add(new SlowSampleCandidate(entry, entry.threadId, entry.threadBindingVersion));
            }
        });
        if (eligible.isEmpty()) return;

        eligible.sort(java.util.Comparator.comparingLong((SlowSampleCandidate candidate) ->
                        candidate.entry().startedNanos)
                .thenComparingLong(candidate -> candidate.entry().id));
        Map<Long, List<SlowSampleCandidate>> selectedByThread = new java.util.LinkedHashMap<>();
        long deferred = 0;
        for (SlowSampleCandidate candidate : eligible) {
            List<SlowSampleCandidate> sameThread = selectedByThread.get(candidate.threadId());
            if (sameThread != null) {
                sameThread.add(candidate);
            } else if (tryAcquireDeepSample()) {
                List<SlowSampleCandidate> selected = new ArrayList<>();
                selected.add(candidate);
                selectedByThread.put(candidate.threadId(), selected);
            } else {
                deferred++;
            }
        }
        if (deferred > 0) deferredDeepSamples.increment(deferred);

        List<SlowSampleCandidate> candidates = new ArrayList<>();
        selectedByThread.values().forEach(group -> group.forEach(candidate -> {
            Entry entry = candidate.entry();
            synchronized (entry) {
                if (active.get(entry.id) != entry || entry.threadId != candidate.threadId()
                        || entry.threadBindingVersion != candidate.threadBindingVersion()
                        || entry.threadId < 0 || entry.diagnostic.hasEvidence() || entry.sampled) {
                    return;
                }
                entry.sampled = true;
                candidates.add(candidate);
            }
        }));
        if (candidates.isEmpty()) return;

        long[] threadIds = candidates.stream()
                .mapToLong(SlowSampleCandidate::threadId)
                .distinct()
                .toArray();
        Map<Long, ThreadDiagnostic> diagnosticsByThread = inspectThreads(threadIds);

        candidates.forEach(candidate -> {
            Entry entry = candidate.entry();
            ThreadDiagnostic diagnostic = diagnosticsByThread.getOrDefault(
                    candidate.threadId(), ThreadDiagnostic.EMPTY);
            synchronized (entry) {
                boolean sameBinding = active.get(entry.id) == entry
                        && entry.threadId == candidate.threadId()
                        && entry.threadBindingVersion == candidate.threadBindingVersion();
                if (sameBinding && !entry.diagnostic.hasEvidence() && diagnostic.hasEvidence()) {
                    entry.diagnostic = diagnostic;
                }
                if (!entry.diagnostic.hasEvidence()) {
                    entry.sampled = false;
                }
            }
        });
    }

    private Map<Long, ThreadDiagnostic> inspectThreads(long[] threadIds) {
        if (threadIds.length == 0) return Map.of();
        try {
            ThreadInfo[] infos = threadBean.getThreadInfo(threadIds, true, true, MAX_STACK_DEPTH);
            if (infos == null) return Map.of();
            LocalDateTime sampledAt = LocalDateTime.now();
            Map<Long, ThreadDiagnostic> diagnostics = new HashMap<>(threadIds.length);
            int resultCount = Math.min(threadIds.length, infos.length);
            for (int index = 0; index < resultCount; index++) {
                ThreadDiagnostic diagnostic = diagnostic(infos[index], sampledAt);
                if (diagnostic.hasEvidence()) {
                    diagnostics.put(threadIds[index], diagnostic);
                }
            }
            return diagnostics;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    public List<ActiveOperation> snapshot() {
        return snapshot(Integer.MAX_VALUE);
    }

    public List<ActiveOperation> snapshot(int limit) {
        if (limit <= 0) return List.of();
        long now = System.nanoTime();
        LocalDateTime snapshotAt = LocalDateTime.now();
        List<Entry> entries = active.values().stream()
                .sorted(java.util.Comparator.comparingLong((Entry entry) -> entry.startedNanos)
                        .thenComparingLong(entry -> entry.id))
                .limit(limit)
                .toList();
        long[] threadIds = entries.stream()
                .mapToLong(entry -> entry.threadId)
                .filter(threadId -> threadId >= 0)
                .distinct()
                .toArray();
        Map<Long, LiveThreadSnapshot> liveThreads = inspectLiveThreads(threadIds);
        return entries.stream()
                .map(entry -> view(entry, now, snapshotAt, liveThreads.get(entry.threadId)))
                .toList();
    }

    private void enableJvmResourceMonitoring() {
        if (config.getMonitor().getDiagnosis().isContentionTimeEnabled()
                && threadBean.isThreadContentionMonitoringSupported()
                && !threadBean.isThreadContentionMonitoringEnabled()) {
            try { threadBean.setThreadContentionMonitoringEnabled(true); }
            catch (RuntimeException ignored) { }
        }
        if (threadBean.isThreadCpuTimeSupported() && !threadBean.isThreadCpuTimeEnabled()) {
            try { threadBean.setThreadCpuTimeEnabled(true); }
            catch (RuntimeException ignored) { }
        }
        if (allocatedThreadBean != null && allocatedThreadBean.isThreadAllocatedMemorySupported()
                && !allocatedThreadBean.isThreadAllocatedMemoryEnabled()) {
            try { allocatedThreadBean.setThreadAllocatedMemoryEnabled(true); }
            catch (RuntimeException ignored) { }
        }
    }

    public Map<String, Long> countsByType() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        KNOWN_TYPES.forEach(type -> counts.put(type,
                active.values().stream().filter(entry -> type.equals(entry.type)).count()));
        active.values().stream().map(entry -> entry.type).filter(type -> !counts.containsKey(type)).distinct()
                .forEach(type -> counts.put(type,
                        active.values().stream().filter(entry -> type.equals(entry.type)).count()));
        return counts;
    }

    private ActiveOperation view(Entry entry, long now, LocalDateTime snapshotAt, LiveThreadSnapshot liveThread) {
        long durationNanos = Math.max(0, now - entry.startedNanos);
        boolean asyncWaiting = entry.threadId < 0;
        ThreadDiagnostic live = asyncWaiting || liveThread == null
                ? ThreadDiagnostic.EMPTY : liveThread.diagnostic();
        ThreadDiagnostic diagnostic = asyncWaiting
                ? new ThreadDiagnostic("ASYNC_WAITING", entry.diagnostic.lockInfo(),
                entry.diagnostic.stackTrace(), entry.diagnostic.sampledAt())
                : live.stackTrace().isBlank() ? entry.diagnostic : live;
        ResourceUsage resources = resourceUsage(entry, liveThread);
        return new ActiveOperation(Long.toString(entry.id), entry.type, entry.traceId, entry.name, entry.detail,
                entry.threadId, entry.threadName, diagnostic.threadState(),
                durationNanos / 1_000_000, resources.cpuTimeMs(),
                resources.allocatedBytes(), resources.blockedCount(), resources.blockedTimeMs(),
                resources.waitedCount(), resources.waitedTimeMs(), diagnostic.lockInfo(), diagnostic.stackTrace(),
                snapshotAt.minusNanos(durationNanos), diagnostic.sampledAt());
    }

    /**
     * The active page can contain nested HTTP/transaction/SQL entries bound to the same thread. Capture that
     * thread once per refresh so opening the page does not multiply MXBean stack walks and counter reads by the
     * number of nested operations.
     */
    private Map<Long, LiveThreadSnapshot> inspectLiveThreads(long[] threadIds) {
        if (threadIds.length == 0) return Map.of();
        Map<Long, CounterSnapshot> counters = new HashMap<>(threadIds.length);
        for (long threadId : threadIds) {
            counters.put(threadId, new CounterSnapshot(cpuTime(threadId), allocatedBytes(threadId)));
        }
        try {
            ThreadInfo[] infos = threadBean.getThreadInfo(threadIds, false, false, MAX_STACK_DEPTH);
            if (infos == null) return Map.of();
            LocalDateTime sampledAt = LocalDateTime.now();
            Map<Long, LiveThreadSnapshot> snapshots = new HashMap<>(threadIds.length);
            int resultCount = Math.min(threadIds.length, infos.length);
            for (int index = 0; index < resultCount; index++) {
                ThreadInfo info = infos[index];
                CounterSnapshot counter = counters.getOrDefault(threadIds[index], CounterSnapshot.EMPTY);
                snapshots.put(threadIds[index], new LiveThreadSnapshot(threadIds[index], info,
                        diagnostic(info, sampledAt), counter.cpuNanos(), counter.allocatedBytes()));
            }
            return snapshots;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private ThreadDiagnostic inspect(long threadId, boolean includeLockedObjects) {
        try {
            ThreadInfo info = includeLockedObjects
                    ? threadBean.getThreadInfo(new long[]{threadId}, true, true, MAX_STACK_DEPTH)[0]
                    : threadBean.getThreadInfo(threadId, MAX_STACK_DEPTH);
            return diagnostic(info, LocalDateTime.now());
        } catch (RuntimeException ignored) {
            return ThreadDiagnostic.EMPTY;
        }
    }

    private static ThreadDiagnostic diagnostic(ThreadInfo info, LocalDateTime sampledAt) {
        if (info == null) return ThreadDiagnostic.EMPTY;
        try {
            String stack = Arrays.stream(info.getStackTrace()).limit(MAX_STACK_DEPTH)
                    .map(StackTraceElement::toString)
                    .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
            return new ThreadDiagnostic(info.getThreadState().name(), lockInfo(info), stack, sampledAt);
        } catch (RuntimeException ignored) {
            return ThreadDiagnostic.EMPTY;
        }
    }

    private ThreadInfo threadInfo(long threadId) {
        if (threadId < 0) return null;
        try {
            return threadBean.getThreadInfo(threadId, 0);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private long cpuTime(long threadId) {
        try {
            return threadId >= 0 && threadCpuTimeAvailable
                    ? threadBean.getThreadCpuTime(threadId) : -1;
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private long allocatedBytes(long threadId) {
        try {
            return threadId >= 0 && allocatedThreadBean != null && allocatedMemoryAvailable
                    ? allocatedThreadBean.getThreadAllocatedBytes(threadId) : -1;
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private ResourceUsage resourceUsage(Entry entry, LiveThreadSnapshot liveThread) {
        if (!entry.resourceSampling) return ResourceUsage.EMPTY;
        synchronized (entry) {
            if (liveThread == null || entry.threadId != liveThread.threadId()) {
                return accumulatedResourceUsage(entry);
            }
            ThreadInfo current = liveThread.threadInfo();
            return new ResourceUsage(
                    nanosToMillis(addAvailable(entry.accumulatedCpuNanos,
                            delta(entry.cpuStart, liveThread.cpuNanos()))),
                    addAvailable(entry.accumulatedAllocatedBytes,
                            delta(entry.allocatedStart, liveThread.allocatedBytes())),
                    addAvailable(entry.accumulatedBlockedCount,
                            delta(entry.blockedCountStart, blockedCount(current))),
                    addAvailable(entry.accumulatedBlockedTimeMs,
                            delta(entry.blockedTimeStart, blockedTime(current))),
                    addAvailable(entry.accumulatedWaitedCount,
                            delta(entry.waitedCountStart, waitedCount(current))),
                    addAvailable(entry.accumulatedWaitedTimeMs,
                            delta(entry.waitedTimeStart, waitedTime(current))));
        }
    }

    private boolean threadCpuTimeAvailable() {
        try {
            return threadBean.isThreadCpuTimeSupported() && threadBean.isThreadCpuTimeEnabled();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean allocatedMemoryAvailable() {
        try {
            return allocatedThreadBean != null
                    && allocatedThreadBean.isThreadAllocatedMemorySupported()
                    && allocatedThreadBean.isThreadAllocatedMemoryEnabled();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private ResourceUsage accumulatedResourceUsage(Entry entry) {
        if (!entry.resourceSampling) return ResourceUsage.EMPTY;
        return new ResourceUsage(
                nanosToMillis(entry.accumulatedCpuNanos),
                entry.accumulatedAllocatedBytes,
                entry.accumulatedBlockedCount,
                entry.accumulatedBlockedTimeMs,
                entry.accumulatedWaitedCount,
                entry.accumulatedWaitedTimeMs);
    }

    private void finishCurrentThreadSegment(Entry entry) {
        if (entry.threadId < 0 || !entry.resourceSampling) return;
        long currentCpuTime = cpuTime(entry.threadId);
        long currentAllocatedBytes = allocatedBytes(entry.threadId);
        ThreadInfo current = contentionSamplingEnabled ? threadInfo(entry.threadId) : null;
        entry.accumulatedCpuNanos = addAvailable(entry.accumulatedCpuNanos,
                delta(entry.cpuStart, currentCpuTime));
        entry.accumulatedAllocatedBytes = addAvailable(entry.accumulatedAllocatedBytes,
                delta(entry.allocatedStart, currentAllocatedBytes));
        entry.accumulatedBlockedCount = addAvailable(entry.accumulatedBlockedCount,
                delta(entry.blockedCountStart, blockedCount(current)));
        entry.accumulatedBlockedTimeMs = addAvailable(entry.accumulatedBlockedTimeMs,
                delta(entry.blockedTimeStart, blockedTime(current)));
        entry.accumulatedWaitedCount = addAvailable(entry.accumulatedWaitedCount,
                delta(entry.waitedCountStart, waitedCount(current)));
        entry.accumulatedWaitedTimeMs = addAvailable(entry.accumulatedWaitedTimeMs,
                delta(entry.waitedTimeStart, waitedTime(current)));
    }

    private void bindCurrentThread(Entry entry) {
        Thread thread = Thread.currentThread();
        entry.threadId = thread.getId();
        entry.threadName = thread.getName();
        if (!entry.resourceSampling) return;
        ThreadInfo baseline = contentionSamplingEnabled ? threadInfo(thread.getId()) : null;
        entry.cpuStart = cpuTime(entry.threadId);
        entry.allocatedStart = allocatedBytes(entry.threadId);
        entry.blockedCountStart = blockedCount(baseline);
        entry.blockedTimeStart = blockedTime(baseline);
        entry.waitedCountStart = waitedCount(baseline);
        entry.waitedTimeStart = waitedTime(baseline);
    }

    private static long blockedCount(ThreadInfo info) { return info == null ? -1 : info.getBlockedCount(); }
    private static long blockedTime(ThreadInfo info) { return info == null ? -1 : info.getBlockedTime(); }
    private static long waitedCount(ThreadInfo info) { return info == null ? -1 : info.getWaitedCount(); }
    private static long waitedTime(ThreadInfo info) { return info == null ? -1 : info.getWaitedTime(); }
    private static long delta(long start, long end) { return start < 0 || end < start ? -1 : end - start; }
    private static long addAvailable(long accumulated, long value) {
        if (accumulated < 0) return value;
        if (value < 0) return accumulated;
        try { return Math.addExact(accumulated, value); }
        catch (ArithmeticException ignored) { return Long.MAX_VALUE; }
    }
    private static long nanosToMillis(long nanos) { return nanos < 0 ? -1 : nanos / 1_000_000; }

    private static String lockInfo(ThreadInfo info) {
        if (info == null) return "";
        StringBuilder result = new StringBuilder();
        if (info.getLockInfo() != null) {
            String owner = info.getLockOwnerName() == null ? "" :
                    ", owner=" + info.getLockOwnerName() + '#' + info.getLockOwnerId();
            result.append("waitingOn=").append(info.getLockInfo()).append(owner);
        }
        appendHeldLocks(result, "heldMonitors", Arrays.stream(info.getLockedMonitors())
                .map(monitor -> monitor + (monitor.getLockedStackFrame() == null
                        ? "" : " at " + monitor.getLockedStackFrame()))
                .toList());
        appendHeldLocks(result, "heldSynchronizers", Arrays.stream(info.getLockedSynchronizers())
                .map(Object::toString)
                .toList());
        return result.toString();
    }

    private static void appendHeldLocks(StringBuilder result, String label, List<String> locks) {
        if (locks.isEmpty()) return;
        if (!result.isEmpty()) result.append("; ");
        result.append(label).append('=').append(locks);
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private boolean reserve(String type) {
        boolean sql = "SQL".equals(type);
        if (sql && !incrementBelow(activeSqlCount, maxActiveSqlEntries)) return false;
        if (incrementBelow(activeCount, maxActiveEntries)) return true;
        if (sql) activeSqlCount.decrementAndGet();
        return false;
    }

    private void releaseReservation(String type) {
        activeCount.decrementAndGet();
        if ("SQL".equals(type)) activeSqlCount.decrementAndGet();
    }

    private static boolean incrementBelow(AtomicInteger counter, int limit) {
        while (true) {
            int current = counter.get();
            if (current >= limit) return false;
            if (counter.compareAndSet(current, current + 1)) return true;
        }
    }

    /** Shares one per-window budget between scheduled slow sampling and finishing slow/error sampling. */
    private boolean tryAcquireDeepSample() {
        long currentWindow = System.nanoTime() / deepSampleWindowNanos;
        synchronized (deepSampleBudgetLock) {
            if (deepSampleWindow != currentWindow) {
                deepSampleWindow = currentWindow;
                deepSamples = 0;
            }
            if (deepSamples >= maxDeepSamplesPerCycle) return false;
            deepSamples++;
            return true;
        }
    }

    private static String meterType(String type) {
        return METER_TYPES.contains(type) ? type : "OTHER";
    }

    private long threshold(String type) {
        long value = switch (type) {
            case "SQL" -> config.getTrace().getSqlSlowThresholdMs();
            case "TRANSACTION" -> config.getTrace().getTransactionSlowThresholdMs();
            case "REMOTE" -> config.getTrace().getRemoteSlowThresholdMs();
            case "JOB" -> config.getTrace().getScheduleSlowThresholdMs();
            case "KAFKA" -> config.getTrace().getKafkaConsumerSlowThresholdMs();
            default -> config.getTrace().getHttpSlowThresholdMs();
        };
        return value <= 0 ? Long.MAX_VALUE : value;
    }

    private static final class Entry {
        private final long id;
        private final String type;
        private final String traceId;
        private final String name;
        private final String detail;
        private final boolean resourceSampling;
        private volatile long threadId;
        private volatile String threadName;
        private long threadBindingVersion;
        private volatile long startedNanos;
        /** Guarded by this entry's monitor. */
        private boolean sampled;
        private volatile ThreadDiagnostic diagnostic = ThreadDiagnostic.EMPTY;
        private long cpuStart;
        private long allocatedStart;
        private long blockedCountStart;
        private long blockedTimeStart;
        private long waitedCountStart;
        private long waitedTimeStart;
        private long accumulatedCpuNanos;
        private long accumulatedAllocatedBytes;
        private long accumulatedBlockedCount;
        private long accumulatedBlockedTimeMs;
        private long accumulatedWaitedCount;
        private long accumulatedWaitedTimeMs;

        private Entry(long id, String type, String traceId, String name, String detail, long threadId,
                      String threadName, long startedNanos, boolean resourceSampling,
                      long cpuStart,
                      long allocatedStart, long blockedCountStart, long blockedTimeStart,
                      long waitedCountStart, long waitedTimeStart) {
            this.id = id;
            this.type = type;
            this.traceId = traceId;
            this.name = name;
            this.detail = detail;
            this.resourceSampling = resourceSampling;
            this.threadId = threadId;
            this.threadName = threadName;
            this.startedNanos = startedNanos;
            this.cpuStart = cpuStart;
            this.allocatedStart = allocatedStart;
            this.blockedCountStart = blockedCountStart;
            this.blockedTimeStart = blockedTimeStart;
            this.waitedCountStart = waitedCountStart;
            this.waitedTimeStart = waitedTimeStart;
            this.accumulatedCpuNanos = cpuStart < 0 ? -1 : 0;
            this.accumulatedAllocatedBytes = allocatedStart < 0 ? -1 : 0;
            this.accumulatedBlockedCount = blockedCountStart < 0 ? -1 : 0;
            this.accumulatedBlockedTimeMs = blockedTimeStart < 0 ? -1 : 0;
            this.accumulatedWaitedCount = waitedCountStart < 0 ? -1 : 0;
            this.accumulatedWaitedTimeMs = waitedTimeStart < 0 ? -1 : 0;
        }
    }

    private record SlowSampleCandidate(Entry entry, long threadId, long threadBindingVersion) { }

    private record CounterSnapshot(long cpuNanos, long allocatedBytes) {
        private static final CounterSnapshot EMPTY = new CounterSnapshot(-1, -1);
    }

    private record LiveThreadSnapshot(long threadId, ThreadInfo threadInfo, ThreadDiagnostic diagnostic,
                                      long cpuNanos, long allocatedBytes) { }

    public final class Scope implements AutoCloseable {
        private final long id;
        /** Guarded by the matching entry's monitor. */
        private boolean closed;

        private Scope(long id) { this.id = id; }

        ThreadDiagnostic capturedDiagnostic() {
            Entry entry = active.get(id);
            return entry == null ? ThreadDiagnostic.EMPTY : entry.diagnostic;
        }

        /** Captures the finishing thread when the periodic sampler has not yet seen this slow/error operation. */
        ThreadDiagnostic captureDiagnosticIfAbsent() {
            Entry entry = active.get(id);
            if (entry == null) return ThreadDiagnostic.EMPTY;
            long sampledThreadId;
            long sampledBindingVersion;
            synchronized (entry) {
                if (entry.diagnostic.hasEvidence() || entry.threadId < 0) return entry.diagnostic;
                sampledThreadId = entry.threadId;
                sampledBindingVersion = entry.threadBindingVersion;
            }

            ThreadDiagnostic diagnostic = inspect(sampledThreadId, true);

            synchronized (entry) {
                if (entry.diagnostic.hasEvidence()) return entry.diagnostic;
                if (active.get(id) != entry || entry.threadId != sampledThreadId
                        || entry.threadBindingVersion != sampledBindingVersion) {
                    return entry.diagnostic;
                }
                if (diagnostic.hasEvidence()) {
                    entry.diagnostic = diagnostic;
                    entry.sampled = true;
                }
                return entry.diagnostic;
            }
        }

        void markAsyncWaiting() {
            Entry entry = active.get(id);
            if (entry == null) return;
            synchronized (entry) {
                if (closed || active.get(id) != entry) return;
                finishCurrentThreadSegment(entry);
                entry.threadId = -1;
                entry.threadName = "async-waiting";
                entry.threadBindingVersion++;
            }
        }

        void rebindCurrentThread() {
            Entry entry = active.get(id);
            if (entry == null) return;
            synchronized (entry) {
                if (closed || active.get(id) != entry) return;
                finishCurrentThreadSegment(entry);
                bindCurrentThread(entry);
                entry.threadBindingVersion++;
            }
        }

        /**
         * Resets the current segment immediately before user code starts. Observation/trace setup happens before
         * this boundary and therefore cannot inflate the operation's CPU, allocation or elapsed measurements.
         */
        void rebaseCurrentThread() {
            Entry entry = active.get(id);
            if (entry == null) return;
            synchronized (entry) {
                if (closed || active.get(id) != entry || entry.threadId != Thread.currentThread().getId()) return;
                ThreadInfo baseline = entry.resourceSampling && contentionSamplingEnabled
                        ? threadInfo(entry.threadId) : null;
                entry.cpuStart = entry.resourceSampling ? cpuTime(entry.threadId) : -1;
                entry.allocatedStart = entry.resourceSampling ? allocatedBytes(entry.threadId) : -1;
                entry.blockedCountStart = blockedCount(baseline);
                entry.blockedTimeStart = blockedTime(baseline);
                entry.waitedCountStart = waitedCount(baseline);
                entry.waitedTimeStart = waitedTime(baseline);
                entry.accumulatedCpuNanos = entry.cpuStart < 0 ? -1 : 0;
                entry.accumulatedAllocatedBytes = entry.allocatedStart < 0 ? -1 : 0;
                entry.accumulatedBlockedCount = entry.blockedCountStart < 0 ? -1 : 0;
                entry.accumulatedBlockedTimeMs = entry.blockedTimeStart < 0 ? -1 : 0;
                entry.accumulatedWaitedCount = entry.waitedCountStart < 0 ? -1 : 0;
                entry.accumulatedWaitedTimeMs = entry.waitedTimeStart < 0 ? -1 : 0;
                entry.startedNanos = System.nanoTime();
                entry.diagnostic = ThreadDiagnostic.EMPTY;
                entry.sampled = false;
            }
        }

        ResourceUsage finishAndClose() {
            return finishAndClose(false).resources();
        }

        /** Removes an observation after its higher-level finalizer failed; no counters or stacks are sampled. */
        void discard() {
            Entry entry = active.get(id);
            if (entry == null) return;
            boolean owner = false;
            synchronized (entry) {
                if (closed || active.get(id) != entry) return;
                closed = true;
                owner = true;
                entry.threadId = -1;
                entry.threadName = "discarded";
                entry.threadBindingVersion++;
            }
            if (owner && active.remove(id, entry)) releaseReservation(entry.type);
        }

        /**
         * Freezes the observed thread counters before taking an optional finishing stack/lock snapshot. The
         * diagnostic MXBean call allocates and consumes CPU on the finishing thread, so taking it first would make
         * monitoring overhead look like application work in the persisted resource usage.
         */
        FinishedOperation finishAndClose(boolean captureDiagnostic) {
            return finishAndClose(captureDiagnostic, null);
        }

        /**
         * Freezes counters, then runs an optional boundary action before the potentially expensive diagnostic
         * snapshot. OperationObservation uses this to end its JFR event at the same business boundary without
         * charging the JFR call to CPU/allocation or extending the event through stack/lock inspection.
         */
        FinishedOperation finishAndClose(boolean captureDiagnostic, Runnable afterCountersFrozen) {
            Entry entry = active.get(id);
            if (entry == null) return FinishedOperation.EMPTY;
            boolean owner = false;
            try {
                ResourceUsage resources;
                ThreadDiagnostic diagnostic;
                long finishingThreadId;
                synchronized (entry) {
                    if (closed || active.get(id) != entry) return FinishedOperation.EMPTY;
                    closed = true;
                    owner = true;
                    finishCurrentThreadSegment(entry);
                    resources = accumulatedResourceUsage(entry);
                    diagnostic = entry.diagnostic;
                    boolean needsDiagnostic = captureDiagnostic && !diagnostic.hasEvidence() && entry.threadId >= 0;
                    boolean admittedDiagnostic = needsDiagnostic && tryAcquireDeepSample();
                    if (needsDiagnostic && !admittedDiagnostic) deferredDeepSamples.increment();
                    finishingThreadId = admittedDiagnostic ? entry.threadId : -1;
                    entry.threadId = -1;
                    entry.threadName = "completed";
                    entry.threadBindingVersion++;
                }
                if (afterCountersFrozen != null) afterCountersFrozen.run();
                if (finishingThreadId >= 0) {
                    ThreadDiagnostic captured = inspect(finishingThreadId, true);
                    if (captured.hasEvidence()) diagnostic = captured;
                }
                return new FinishedOperation(resources, diagnostic);
            } finally {
                if (owner && active.remove(id, entry)) releaseReservation(entry.type);
            }
        }

        @Override
        public void close() { finishAndClose(); }
    }

    public record ActiveOperation(String id, String type, String traceId, String name, String detail,
                                  long threadId, String threadName, String threadState, long durationMs,
                                  long cpuTimeMs, long allocatedBytes, long blockedCount, long blockedTimeMs,
                                  long waitedCount, long waitedTimeMs, String lockInfo, String stackTrace,
                                  LocalDateTime startedAt, LocalDateTime sampledAt) { }

    record ThreadDiagnostic(String threadState, String lockInfo, String stackTrace, LocalDateTime sampledAt) {
        private static final ThreadDiagnostic EMPTY = new ThreadDiagnostic("UNKNOWN", "", "", null);

        private boolean hasEvidence() {
            return sampledAt != null || !lockInfo.isBlank() || !stackTrace.isBlank() || !"UNKNOWN".equals(threadState);
        }
    }

    record ResourceUsage(long cpuTimeMs, long allocatedBytes, long blockedCount, long blockedTimeMs,
                         long waitedCount, long waitedTimeMs) {
        private static final ResourceUsage EMPTY = new ResourceUsage(-1, -1, -1, -1, -1, -1);
    }

    record FinishedOperation(ResourceUsage resources, ThreadDiagnostic diagnostic) {
        private static final FinishedOperation EMPTY = new FinishedOperation(ResourceUsage.EMPTY,
                ThreadDiagnostic.EMPTY);
    }
}
