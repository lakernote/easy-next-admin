package com.laker.admin.module.monitor.service;

import com.laker.admin.module.monitor.dto.SystemStatusOverview;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 本机运行态指标采集服务。
 *
 * <p>这里刻意不依赖外部 Agent，保证企业脚手架在最小部署形态下也能查看基础状态。</p>
 */
@Service
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
public class SystemStatusService {
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Environment environment;
    private final HealthEndpoint healthEndpoint;

    @Autowired
    public SystemStatusService(Environment environment,
                               ObjectProvider<HealthEndpoint> healthEndpointProvider) {
        this(environment, healthEndpointProvider.getIfAvailable());
    }

    SystemStatusService(Environment environment) {
        this(environment, (HealthEndpoint) null);
    }

    private SystemStatusService(Environment environment, HealthEndpoint healthEndpoint) {
        this.environment = environment;
        this.healthEndpoint = healthEndpoint;
    }

    public SystemStatusOverview overview() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        long uptimeMillis = runtime.getUptime();
        String status = healthStatus();
        return SystemStatusOverview.builder()
                .status(status)
                .healthy("UP".equalsIgnoreCase(status))
                .applicationName(environment.getProperty("spring.application.name", "easy-next-admin"))
                .activeProfiles(activeProfiles())
                .startTime(DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(runtime.getStartTime())))
                .sampleTime(DATE_TIME_FORMATTER.format(Instant.now()))
                .uptimeSeconds(uptimeMillis / 1000)
                .uptime(formatDuration(uptimeMillis))
                .cpu(cpuMetrics())
                .memory(memoryMetrics())
                .threads(threadMetrics())
                .server(serverInfo())
                .java(javaInfo(runtime, uptimeMillis))
                .disks(diskMetrics())
                .garbageCollectors(garbageCollectorMetrics())
                .runtime(runtimeInfo(runtime))
                .build();
    }

    private String healthStatus() {
        if (healthEndpoint == null) {
            return "UP";
        }
        HealthComponent health = healthEndpoint.health();
        return health.getStatus().getCode();
    }

    private SystemStatusOverview.CpuMetrics cpuMetrics() {
        java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double systemCpuLoad = -1;
        double processCpuLoad = -1;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            systemCpuLoad = sunOsBean.getCpuLoad();
            processCpuLoad = sunOsBean.getProcessCpuLoad();
        }
        return SystemStatusOverview.CpuMetrics.builder()
                .processors(osBean.getAvailableProcessors())
                .systemLoadAverage(round(osBean.getSystemLoadAverage()))
                .systemCpuUsagePercent(percent(systemCpuLoad))
                .processCpuUsagePercent(percent(processCpuLoad))
                .idleCpuUsagePercent(idlePercent(systemCpuLoad))
                .build();
    }

    private SystemStatusOverview.MemoryMetrics memoryMetrics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
        long physicalTotal = -1;
        long physicalFree = -1;
        java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            physicalTotal = sunOsBean.getTotalMemorySize();
            physicalFree = sunOsBean.getFreeMemorySize();
        }
        return SystemStatusOverview.MemoryMetrics.builder()
                .heapUsedBytes(heap.getUsed())
                .heapCommittedBytes(heap.getCommitted())
                .heapMaxBytes(heap.getMax())
                .heapFreeBytes(freeBytes(heap.getMax(), heap.getUsed()))
                .heapUsagePercent(percent(heap.getUsed(), heap.getMax()))
                .nonHeapUsedBytes(nonHeap.getUsed())
                .nonHeapCommittedBytes(nonHeap.getCommitted())
                .nonHeapUsagePercent(percent(nonHeap.getUsed(), nonHeap.getCommitted()))
                .physicalTotalBytes(physicalTotal)
                .physicalFreeBytes(physicalFree)
                .physicalUsedBytes(physicalTotal < 0 || physicalFree < 0 ? -1 : Math.max(physicalTotal - physicalFree, 0))
                .physicalUsagePercent(percent(physicalTotal - physicalFree, physicalTotal))
                .build();
    }

    private SystemStatusOverview.ThreadMetrics threadMetrics() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        return SystemStatusOverview.ThreadMetrics.builder()
                .live(threadBean.getThreadCount())
                .daemon(threadBean.getDaemonThreadCount())
                .peak(threadBean.getPeakThreadCount())
                .totalStarted(threadBean.getTotalStartedThreadCount())
                .build();
    }

    private List<SystemStatusOverview.DiskMetrics> diskMetrics() {
        return Arrays.stream(File.listRoots())
                .map(this::diskMetric)
                .toList();
    }

    private SystemStatusOverview.DiskMetrics diskMetric(File root) {
        long total = root.getTotalSpace();
        long usable = root.getUsableSpace();
        long free = root.getFreeSpace();
        String fileSystem = root.getAbsolutePath();
        String type = "-";
        try {
            Path path = root.toPath();
            FileStore store = Files.getFileStore(path);
            fileSystem = store.name();
            type = store.type();
            total = store.getTotalSpace();
            usable = store.getUsableSpace();
            free = store.getUnallocatedSpace();
        } catch (IOException | RuntimeException ignored) {
            // FileStore 在部分容器或受限目录下不可读，回退 File 基础指标。
        }
        return SystemStatusOverview.DiskMetrics.builder()
                .name(root.getAbsolutePath())
                .path(root.getAbsolutePath())
                .fileSystem(fileSystem)
                .type(type)
                .totalBytes(total)
                .freeBytes(free)
                .usableBytes(usable)
                .usedBytes(Math.max(total - usable, 0))
                .usagePercent(percent(total - usable, total))
                .build();
    }

    private List<SystemStatusOverview.GarbageCollectorMetrics> garbageCollectorMetrics() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(this::garbageCollectorMetric)
                .toList();
    }

    private SystemStatusOverview.GarbageCollectorMetrics garbageCollectorMetric(GarbageCollectorMXBean bean) {
        return SystemStatusOverview.GarbageCollectorMetrics.builder()
                .name(bean.getName())
                .collectionCount(bean.getCollectionCount())
                .collectionTimeMillis(bean.getCollectionTime())
                .build();
    }

    private List<SystemStatusOverview.RuntimeInfo> runtimeInfo(RuntimeMXBean runtime) {
        java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        return List.of(
                item("Java 版本", runtime.getVmName() + " " + runtime.getSpecVersion()),
                item("JVM 进程", runtime.getName()),
                item("操作系统", osBean.getName() + " " + osBean.getVersion() + " / " + osBean.getArch()),
                item("工作目录", System.getProperty("user.dir", "-"))
        );
    }

    private SystemStatusOverview.RuntimeInfo item(String label, String value) {
        return SystemStatusOverview.RuntimeInfo.builder()
                .label(label)
                .value(value)
                .build();
    }

    private SystemStatusOverview.ServerInfo serverInfo() {
        java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        HostInfo host = hostInfo();
        return SystemStatusOverview.ServerInfo.builder()
                .name(host.name())
                .ip(host.ip())
                .osName(osBean.getName())
                .osArch(osBean.getArch())
                .osVersion(osBean.getVersion())
                .processors(osBean.getAvailableProcessors())
                .build();
    }

    private SystemStatusOverview.JavaInfo javaInfo(RuntimeMXBean runtime, long uptimeMillis) {
        return SystemStatusOverview.JavaInfo.builder()
                .name(runtime.getVmName())
                .version(System.getProperty("java.version", runtime.getSpecVersion()))
                .vendor(System.getProperty("java.vendor", "-"))
                .home(System.getProperty("java.home", "-"))
                .projectDir(System.getProperty("user.dir", "-"))
                .startTime(DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(runtime.getStartTime())))
                .runTime(formatDuration(uptimeMillis))
                .inputArguments(String.join(" ", runtime.getInputArguments()))
                .build();
    }

    private HostInfo hostInfo() {
        try {
            InetAddress address = InetAddress.getLocalHost();
            return new HostInfo(address.getHostName(), address.getHostAddress());
        } catch (UnknownHostException e) {
            return new HostInfo("-", "-");
        }
    }

    private String activeProfiles() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 0 ? "default" : String.join(", ", profiles);
    }

    private int percent(double ratio) {
        if (Double.isNaN(ratio) || ratio < 0) {
            return -1;
        }
        return (int) Math.max(0, Math.min(100, Math.round(ratio * 100)));
    }

    private int idlePercent(double cpuLoad) {
        int used = percent(cpuLoad);
        return used < 0 ? -1 : Math.max(0, 100 - used);
    }

    private int percent(long used, long total) {
        if (used < 0 || total <= 0) {
            return -1;
        }
        return (int) Math.max(0, Math.min(100, Math.round(used * 100.0 / total)));
    }

    private long freeBytes(long total, long used) {
        if (total < 0 || used < 0) {
            return -1;
        }
        return Math.max(total - used, 0);
    }

    private double round(double value) {
        if (Double.isNaN(value) || value < 0) {
            return -1;
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        if (days > 0) {
            return "%d天 %d小时 %d分钟".formatted(days, hours, minutes);
        }
        if (hours > 0) {
            return "%d小时 %d分钟".formatted(hours, minutes);
        }
        if (minutes > 0) {
            return "%d分钟 %d秒".formatted(minutes, remainingSeconds);
        }
        return "%d秒".formatted(remainingSeconds);
    }

    private record HostInfo(String name, String ip) {
    }
}
