package com.laker.admin.module.monitor.service;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JVM live diagnostics used by the runtime diagnosis page.
 *
 * <p>The service only exposes JVM management data already available inside the current process. Thread and
 * stack limits are enforced here instead of relying on callers, so a malformed request cannot create an
 * unbounded response.</p>
 */
@Service
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
public class JvmDiagnosticsService {
    static final int DEFAULT_MAX_THREADS = 200;
    static final int MAX_THREADS = 500;
    static final int DEFAULT_STACK_DEPTH = 64;
    static final int MAX_STACK_DEPTH = 128;
    static final int MAX_DEADLOCK_THREADS = 100;
    static final int DEFAULT_HISTOGRAM_CLASSES = 100;
    static final int MAX_HISTOGRAM_CLASSES = 500;

    private static final String DIAGNOSTIC_COMMAND_MBEAN = "com.sun.management:type=DiagnosticCommand";
    private static final String CLASS_HISTOGRAM_OPERATION = "gcClassHistogram";
    private static final String CLASS_HISTOGRAM_IMPACT = "高：耗时取决于 Java 堆大小和对象数量，执行期间可能产生 JVM 安全点停顿。";
    private static final Pattern HISTOGRAM_ROW = Pattern.compile("^\\s*(\\d+):\\s+(\\d+)\\s+(\\d+)\\s+(.+?)\\s*$");
    private static final Pattern HISTOGRAM_TOTAL = Pattern.compile("^\\s*Total\\s+(\\d+)\\s+(\\d+)\\s*$");

    private static final String HIKARI_PREFIX = "hikaricp.";
    private static final String JDBC_PREFIX = "jdbc.connections.";
    private static final String TOMCAT_PREFIX = "tomcat.threads.";
    private static final String EXECUTOR_PREFIX = "executor.";

    private final MeterRegistry meterRegistry;
    private final ThreadMXBean threadMxBean;
    private final com.sun.management.ThreadMXBean extendedThreadMxBean;
    private final HeapHistogramCommand heapHistogramCommand;
    private final AtomicBoolean heapHistogramRunning = new AtomicBoolean();

    @Autowired
    public JvmDiagnosticsService(MeterRegistry meterRegistry) {
        this(meterRegistry, ManagementFactory.getThreadMXBean(),
                new JmxHeapHistogramCommand(ManagementFactory.getPlatformMBeanServer()));
    }

    JvmDiagnosticsService(MeterRegistry meterRegistry, ThreadMXBean threadMxBean) {
        this(meterRegistry, threadMxBean, new JmxHeapHistogramCommand(ManagementFactory.getPlatformMBeanServer()));
    }

    JvmDiagnosticsService(MeterRegistry meterRegistry, ThreadMXBean threadMxBean,
                          HeapHistogramCommand heapHistogramCommand) {
        this.meterRegistry = meterRegistry;
        this.threadMxBean = threadMxBean;
        this.extendedThreadMxBean = threadMxBean instanceof com.sun.management.ThreadMXBean extended ? extended : null;
        this.heapHistogramCommand = heapHistogramCommand;
    }

    public ThreadDump threads(int requestedMaxThreads, int requestedStackDepth) {
        int maxThreads = clamp(requestedMaxThreads, DEFAULT_MAX_THREADS, MAX_THREADS);
        int stackDepth = clamp(requestedStackDepth, DEFAULT_STACK_DEPTH, MAX_STACK_DEPTH);
        Set<Long> deadlockedIds = deadlockedThreadIds();
        long[] allThreadIds = threadMxBean.getAllThreadIds();
        ThreadInfo[] shallowInfos = threadMxBean.getThreadInfo(allThreadIds, 0);
        if (shallowInfos == null) shallowInfos = new ThreadInfo[0];
        Map<Long, Long> cpuTimes = Arrays.stream(shallowInfos)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toMap(ThreadInfo::getThreadId,
                        info -> cpuTime(info.getThreadId()), (left, right) -> left));
        List<ThreadInfo> selected = Arrays.stream(shallowInfos)
                .filter(info -> info != null)
                .sorted(threadOrder(deadlockedIds, cpuTimes))
                .limit(maxThreads)
                .toList();
        long[] selectedIds = selected.stream().mapToLong(ThreadInfo::getThreadId).toArray();
        ThreadInfo[] detailedInfos = selectedIds.length == 0 ? new ThreadInfo[0]
                : threadMxBean.getThreadInfo(selectedIds, true, true, stackDepth);
        if (detailedInfos == null) detailedInfos = new ThreadInfo[0];
        List<ThreadSnapshot> snapshots = Arrays.stream(detailedInfos)
                .filter(Objects::nonNull)
                .map(info -> toSnapshot(info, deadlockedIds.contains(info.getThreadId()), stackDepth))
                .toList();
        int totalThreads = (int) Arrays.stream(shallowInfos).filter(Objects::nonNull).count();
        return new ThreadDump(Instant.now().toString(), totalThreads, snapshots.size(),
                totalThreads > snapshots.size(), stackDepth, cpuTimeEnabled(), allocatedMemoryEnabled(),
                contentionMonitoringEnabled(), snapshots);
    }

    public DeadlockReport deadlocks(int requestedMaxThreads, int requestedStackDepth) {
        int maxThreads = clamp(requestedMaxThreads, MAX_DEADLOCK_THREADS, MAX_DEADLOCK_THREADS);
        int stackDepth = clamp(requestedStackDepth, DEFAULT_STACK_DEPTH, MAX_STACK_DEPTH);
        Set<Long> deadlockedIds = deadlockedThreadIds();
        if (deadlockedIds.isEmpty()) {
            return new DeadlockReport(Instant.now().toString(), false, 0, false, List.of());
        }
        long[] ids = deadlockedIds.stream().mapToLong(Long::longValue).toArray();
        ThreadInfo[] infos = threadMxBean.getThreadInfo(ids, true, true, stackDepth);
        List<ThreadSnapshot> threads = Arrays.stream(infos)
                .filter(info -> info != null)
                .sorted(Comparator.comparingLong(ThreadInfo::getThreadId))
                .limit(maxThreads)
                .map(info -> toSnapshot(info, true, stackDepth))
                .toList();
        return new DeadlockReport(Instant.now().toString(), true, deadlockedIds.size(),
                deadlockedIds.size() > threads.size(), threads);
    }

    public PoolDiagnostics pools() {
        Map<PoolKey, List<PoolMetric>> grouped = new LinkedHashMap<>();
        meterRegistry.getMeters().stream()
                .filter(meter -> poolType(meter.getId().getName()) != null)
                .sorted(Comparator.comparing(meter -> meter.getId().getName()))
                .forEach(meter -> {
                    PoolType type = poolType(meter.getId().getName());
                    String resourceName = resourceName(type, meter.getId());
                    grouped.computeIfAbsent(new PoolKey(type, resourceName), unused -> new ArrayList<>())
                            .add(toPoolMetric(meter));
                });
        List<PoolView> pools = grouped.entrySet().stream()
                .map(entry -> toPoolView(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(PoolView::type).thenComparing(PoolView::name))
                .toList();
        return new PoolDiagnostics(Instant.now().toString(), pools);
    }

    /**
     * Produces a bounded, on-demand class histogram using the JDK DiagnosticCommand MBean.
     *
     * <p>{@code -all=true} includes unreachable objects and avoids requesting a live-only histogram, which can
     * otherwise trigger a full collection before inspection. Heap inspection itself is still a high-impact JVM
     * operation, so callers must trigger it explicitly and concurrent captures are rejected.</p>
     */
    public HeapHistogram heapHistogram(int requestedLimit) {
        int limit = clamp(requestedLimit, DEFAULT_HISTOGRAM_CLASSES, MAX_HISTOGRAM_CLASSES);
        HeapHistogramCapability capability = heapHistogramCapability();
        if (!capability.supported()) {
            return HeapHistogram.unavailable(capability.message(), limit);
        }
        if (!heapHistogramRunning.compareAndSet(false, true)) {
            return HeapHistogram.busy(limit);
        }
        try {
            return parseHeapHistogram(heapHistogramCommand.execute(), limit);
        } catch (Exception exception) {
            String reason = exception.getMessage();
            String message = reason == null || reason.isBlank()
                    ? "JVM 拒绝执行类直方图命令，请检查运行时与管理权限。"
                    : "类直方图抓取失败：" + reason;
            return HeapHistogram.failed(message, limit);
        } finally {
            heapHistogramRunning.set(false);
        }
    }

    public HeapHistogramCapability heapHistogramCapability() {
        try {
            if (!heapHistogramCommand.available()) {
                return new HeapHistogramCapability(false, "UNAVAILABLE", CLASS_HISTOGRAM_IMPACT,
                        "当前 JVM 未提供 DiagnosticCommand.gcClassHistogram，类直方图不可用。");
            }
            return new HeapHistogramCapability(true, "AVAILABLE", CLASS_HISTOGRAM_IMPACT,
                    "类直方图可按需抓取；该操作开销较高，不会后台周期执行。");
        } catch (Exception exception) {
            String reason = exception.getMessage();
            return new HeapHistogramCapability(false, "UNAVAILABLE", CLASS_HISTOGRAM_IMPACT,
                    reason == null || reason.isBlank()
                            ? "无法探测 JVM 类直方图能力。"
                            : "无法探测 JVM 类直方图能力：" + reason);
        }
    }

    static HeapHistogram parseHeapHistogram(String output, int requestedLimit) {
        int limit = clamp(requestedLimit, DEFAULT_HISTOGRAM_CLASSES, MAX_HISTOGRAM_CLASSES);
        if (output == null || output.isBlank()) {
            return HeapHistogram.failed("JVM 返回了空的类直方图。", limit);
        }
        List<HeapClassHistogramRow> rows = new ArrayList<>();
        int totalClasses = 0;
        long totalInstances = 0;
        long totalBytes = 0;
        for (String line : output.split("\\R")) {
            Matcher rowMatcher = HISTOGRAM_ROW.matcher(line);
            if (rowMatcher.matches()) {
                totalClasses++;
                if (rows.size() < limit) {
                    String[] classAndModule = splitClassAndModule(rowMatcher.group(4));
                    rows.add(new HeapClassHistogramRow(
                            Integer.parseInt(rowMatcher.group(1)),
                            Long.parseLong(rowMatcher.group(2)),
                            Long.parseLong(rowMatcher.group(3)),
                            classAndModule[0], classAndModule[1]));
                }
                continue;
            }
            Matcher totalMatcher = HISTOGRAM_TOTAL.matcher(line);
            if (totalMatcher.matches()) {
                totalInstances = Long.parseLong(totalMatcher.group(1));
                totalBytes = Long.parseLong(totalMatcher.group(2));
            }
        }
        if (totalClasses == 0) {
            return HeapHistogram.failed("无法解析 JVM 返回的类直方图。", limit);
        }
        if (totalInstances == 0) totalInstances = rows.stream().mapToLong(HeapClassHistogramRow::instances).sum();
        if (totalBytes == 0) totalBytes = rows.stream().mapToLong(HeapClassHistogramRow::bytes).sum();
        return new HeapHistogram(Instant.now().toString(), "READY", true, true, CLASS_HISTOGRAM_IMPACT,
                "类直方图抓取完成。", limit, totalClasses, rows.size(), totalClasses > rows.size(),
                totalInstances, totalBytes, List.copyOf(rows));
    }

    private static String[] splitClassAndModule(String label) {
        int moduleStart = label.lastIndexOf(" (");
        if (moduleStart > 0 && label.endsWith(")")) {
            return new String[]{label.substring(0, moduleStart),
                    label.substring(moduleStart + 2, label.length() - 1)};
        }
        return new String[]{label, null};
    }

    private Comparator<ThreadInfo> threadOrder(Set<Long> deadlockedIds, Map<Long, Long> cpuTimes) {
        return Comparator.<ThreadInfo, Boolean>comparing(info -> deadlockedIds.contains(info.getThreadId())).reversed()
                .thenComparing(info -> info.getThreadState() == Thread.State.BLOCKED ? 0 : 1)
                .thenComparing(Comparator.comparingLong((ThreadInfo info) ->
                        cpuTimes.getOrDefault(info.getThreadId(), -1L)).reversed())
                .thenComparingLong(ThreadInfo::getThreadId);
    }

    private ThreadSnapshot toSnapshot(ThreadInfo info, boolean deadlocked, int stackDepth) {
        List<StackFrame> stack = Arrays.stream(info.getStackTrace())
                .limit(stackDepth)
                .map(JvmDiagnosticsService::toStackFrame)
                .toList();
        List<HeldLock> monitors = Arrays.stream(info.getLockedMonitors())
                .map(JvmDiagnosticsService::toHeldMonitor)
                .toList();
        List<HeldLock> synchronizers = Arrays.stream(info.getLockedSynchronizers())
                .map(lock -> toHeldLock("SYNCHRONIZER", lock, null))
                .toList();
        LockView waitingOn = toLockView(info.getLockInfo());
        ThreadOwner owner = info.getLockOwnerId() < 0 ? null
                : new ThreadOwner(info.getLockOwnerId(), info.getLockOwnerName());
        return new ThreadSnapshot(info.getThreadId(), info.getThreadName(), info.getThreadState().name(),
                info.isDaemon(), info.getPriority(), optional(cpuTime(info.getThreadId())),
                optional(allocatedBytes(info.getThreadId())), info.getBlockedCount(), optional(info.getBlockedTime()),
                info.getWaitedCount(), optional(info.getWaitedTime()), waitingOn, owner, monitors, synchronizers,
                stack, info.getStackTrace().length > stack.size(), deadlocked);
    }

    private long cpuTime(long threadId) {
        if (!cpuTimeEnabled()) return -1;
        try {
            return threadMxBean.getThreadCpuTime(threadId);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private long allocatedBytes(long threadId) {
        if (!allocatedMemoryEnabled()) return -1;
        try {
            return extendedThreadMxBean.getThreadAllocatedBytes(threadId);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private boolean cpuTimeEnabled() {
        try {
            return threadMxBean.isThreadCpuTimeSupported() && threadMxBean.isThreadCpuTimeEnabled();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean contentionMonitoringEnabled() {
        try {
            return threadMxBean.isThreadContentionMonitoringSupported()
                    && threadMxBean.isThreadContentionMonitoringEnabled();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean allocatedMemoryEnabled() {
        try {
            return extendedThreadMxBean != null && extendedThreadMxBean.isThreadAllocatedMemorySupported()
                    && extendedThreadMxBean.isThreadAllocatedMemoryEnabled();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private Set<Long> deadlockedThreadIds() {
        long[] ids = null;
        try {
            if (threadMxBean.isSynchronizerUsageSupported()) ids = threadMxBean.findDeadlockedThreads();
        } catch (RuntimeException ignored) {
            // Fall back to monitor-only deadlock detection below.
        }
        if (ids == null) {
            try {
                ids = threadMxBean.findMonitorDeadlockedThreads();
            } catch (RuntimeException ignored) {
                ids = null;
            }
        }
        Set<Long> result = new HashSet<>();
        if (ids != null) Arrays.stream(ids).forEach(result::add);
        return result;
    }

    private static StackFrame toStackFrame(StackTraceElement frame) {
        return new StackFrame(frame.getClassName(), frame.getMethodName(), frame.getFileName(), frame.getLineNumber(),
                frame.isNativeMethod());
    }

    private static HeldLock toHeldMonitor(MonitorInfo monitor) {
        String lockedAt = monitor.getLockedStackFrame() == null ? null : monitor.getLockedStackFrame().toString();
        return toHeldLock("MONITOR", monitor, lockedAt);
    }

    private static HeldLock toHeldLock(String type, LockInfo lock, String lockedAt) {
        return new HeldLock(type, lock.getClassName(), lock.getIdentityHashCode(), lockLabel(lock), lockedAt);
    }

    private static LockView toLockView(LockInfo lock) {
        return lock == null ? null
                : new LockView(lock.getClassName(), lock.getIdentityHashCode(), lockLabel(lock));
    }

    private static String lockLabel(LockInfo lock) {
        return lock.getClassName() + '@' + Integer.toHexString(lock.getIdentityHashCode());
    }

    private static Long optional(long value) {
        return value < 0 ? null : value;
    }

    private static PoolType poolType(String meterName) {
        if (meterName.startsWith(HIKARI_PREFIX)) return PoolType.HIKARI;
        if (meterName.startsWith(JDBC_PREFIX)) return PoolType.JDBC;
        if (meterName.startsWith(TOMCAT_PREFIX)) return PoolType.TOMCAT;
        if (meterName.startsWith(EXECUTOR_PREFIX)) return PoolType.EXECUTOR;
        return null;
    }

    private static String resourceName(PoolType type, Meter.Id id) {
        String preferredTag = type == PoolType.HIKARI ? "pool" : "name";
        String value = id.getTag(preferredTag);
        if (value == null || value.isBlank()) value = id.getTag("pool");
        return value == null || value.isBlank() ? "default" : value;
    }

    private static PoolMetric toPoolMetric(Meter meter) {
        Map<String, String> tags = new LinkedHashMap<>();
        meter.getId().getTags().forEach(tag -> tags.put(tag.getKey(), tag.getValue()));
        Map<String, Double> measurements = new LinkedHashMap<>();
        for (Measurement measurement : meter.measure()) {
            double value = measurement.getValue();
            if (Double.isFinite(value)) {
                measurements.put(measurement.getStatistic().name().toLowerCase(Locale.ROOT), value);
            }
        }
        return new PoolMetric(meter.getId().getName(), meter.getId().getType().name(), meter.getId().getBaseUnit(),
                meter.getId().getDescription(), tags, measurements);
    }

    private static PoolView toPoolView(PoolKey key, List<PoolMetric> metrics) {
        Double active = switch (key.type) {
            case HIKARI -> gauge(metrics, "hikaricp.connections.active");
            case JDBC -> gauge(metrics, "jdbc.connections.active");
            case TOMCAT -> gauge(metrics, "tomcat.threads.busy");
            case EXECUTOR -> gauge(metrics, "executor.active");
        };
        Double idle = switch (key.type) {
            case HIKARI -> gauge(metrics, "hikaricp.connections.idle");
            case JDBC -> gauge(metrics, "jdbc.connections.idle");
            default -> null;
        };
        Double pending = switch (key.type) {
            case HIKARI -> gauge(metrics, "hikaricp.connections.pending");
            case EXECUTOR -> gauge(metrics, "executor.queued");
            default -> null;
        };
        Double current = switch (key.type) {
            case TOMCAT -> gauge(metrics, "tomcat.threads.current");
            case EXECUTOR -> gauge(metrics, "executor.pool.size");
            default -> active != null && idle != null ? active + idle : null;
        };
        Double max = switch (key.type) {
            case HIKARI -> gauge(metrics, "hikaricp.connections.max");
            case JDBC -> gauge(metrics, "jdbc.connections.max");
            case TOMCAT -> gauge(metrics, "tomcat.threads.config.max");
            case EXECUTOR -> gauge(metrics, "executor.pool.max");
        };
        Double completed = key.type == PoolType.EXECUTOR ? gauge(metrics, "executor.completed") : null;
        Double utilization = active == null || max == null || max <= 0 ? null : active * 100d / max;
        return new PoolView(key.type.name(), key.name, active, idle, pending, current, max, completed,
                utilization, List.copyOf(metrics));
    }

    private static Double gauge(List<PoolMetric> metrics, String meterName) {
        return metrics.stream().filter(metric -> meterName.equals(metric.name()))
                .map(metric -> metric.measurements().get("value"))
                .filter(value -> value != null && Double.isFinite(value))
                .findFirst().orElse(null);
    }

    private static int clamp(int requested, int defaultValue, int max) {
        int value = requested <= 0 ? defaultValue : requested;
        return Math.min(value, max);
    }

    private enum PoolType { HIKARI, JDBC, TOMCAT, EXECUTOR }

    private record PoolKey(PoolType type, String name) { }

    public record ThreadDump(String sampledAt, int totalThreads, int returnedThreads, boolean truncated,
                             int stackDepth, boolean cpuTimeEnabled, boolean allocatedMemoryEnabled,
                             boolean contentionMonitoringEnabled, List<ThreadSnapshot> threads) { }

    public record DeadlockReport(String sampledAt, boolean detected, int count, boolean truncated,
                                 List<ThreadSnapshot> threads) { }

    public record ThreadSnapshot(long id, String name, String state, boolean daemon, int priority,
                                 Long cpuTimeNanos, Long allocatedBytes, long blockedCount, Long blockedTimeMs,
                                 long waitedCount, Long waitedTimeMs, LockView waitingOn, ThreadOwner lockOwner,
                                 List<HeldLock> lockedMonitors, List<HeldLock> lockedSynchronizers,
                                 List<StackFrame> stackTrace, boolean stackTruncated, boolean deadlocked) { }

    public record ThreadOwner(long id, String name) { }

    public record LockView(String className, int identityHashCode, String label) { }

    public record HeldLock(String type, String className, int identityHashCode, String label, String lockedAt) { }

    public record StackFrame(String className, String methodName, String fileName, int lineNumber,
                             boolean nativeMethod) { }

    public record PoolDiagnostics(String sampledAt, List<PoolView> pools) { }

    public record PoolView(String type, String name, Double active, Double idle, Double pending, Double current,
                           Double max, Double completed, Double utilizationPercent, List<PoolMetric> metrics) { }

    public record PoolMetric(String name, String meterType, String baseUnit, String description,
                             Map<String, String> tags, Map<String, Double> measurements) { }

    public record HeapHistogramCapability(boolean supported, String status, String impact, String message) { }

    public record HeapHistogram(String sampledAt, String status, boolean supported, boolean allObjects,
                                String impact, String message, int limit, int totalClasses, int returnedClasses,
                                boolean truncated, long totalInstances, long totalBytes,
                                List<HeapClassHistogramRow> classes) {
        private static HeapHistogram unavailable(String message, int limit) {
            return empty("UNAVAILABLE", false, message, limit);
        }

        private static HeapHistogram busy(int limit) {
            return empty("BUSY", true, "已有类直方图正在抓取，请等待当前操作完成。", limit);
        }

        private static HeapHistogram failed(String message, int limit) {
            return empty("FAILED", true, message, limit);
        }

        private static HeapHistogram empty(String status, boolean supported, String message, int limit) {
            return new HeapHistogram(Instant.now().toString(), status, supported, true,
                    CLASS_HISTOGRAM_IMPACT, message, limit, 0, 0, false, 0, 0, List.of());
        }
    }

    public record HeapClassHistogramRow(int rank, long instances, long bytes, String className, String module) { }

    interface HeapHistogramCommand {
        boolean available() throws Exception;

        String execute() throws Exception;
    }

    private static final class JmxHeapHistogramCommand implements HeapHistogramCommand {
        private final MBeanServerConnection mBeanServer;
        private final ObjectName objectName;

        private JmxHeapHistogramCommand(MBeanServerConnection mBeanServer) {
            this.mBeanServer = mBeanServer;
            try {
                this.objectName = new ObjectName(DIAGNOSTIC_COMMAND_MBEAN);
            } catch (Exception exception) {
                throw new IllegalStateException("DiagnosticCommand MBean 名称无效", exception);
            }
        }

        @Override
        public boolean available() throws Exception {
            if (!mBeanServer.isRegistered(objectName)) return false;
            MBeanInfo info = mBeanServer.getMBeanInfo(objectName);
            for (MBeanOperationInfo operation : info.getOperations()) {
                if (!CLASS_HISTOGRAM_OPERATION.equals(operation.getName())) continue;
                Object enabled = operation.getDescriptor().getFieldValue("dcmd.enabled");
                return enabled == null || Boolean.parseBoolean(String.valueOf(enabled));
            }
            return false;
        }

        @Override
        public String execute() throws Exception {
            return (String) mBeanServer.invoke(objectName, CLASS_HISTOGRAM_OPERATION,
                    new Object[]{new String[]{"-all=true"}}, new String[]{String[].class.getName()});
        }
    }
}
