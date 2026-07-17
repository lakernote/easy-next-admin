package com.laker.admin.module.monitor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 轻量级系统状态概览。
 *
 * <p>只聚合 JDK 和本机可直接读取的运行态指标，适合作为后台管理系统的第一排障入口；
 * 更细的链路、慢 SQL 和调用栈分析仍交给日志或外部 APM。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SystemStatusOverview", description = "应用运行状态、JVM、CPU、内存、线程、磁盘和 GC 概览")
public class SystemStatusOverview {
    private String status;
    private Boolean healthy;
    private String applicationName;
    private String activeProfiles;
    private String startTime;
    private String sampleTime;
    private Long uptimeSeconds;
    private String uptime;
    private CpuMetrics cpu;
    private MemoryMetrics memory;
    private ThreadMetrics threads;
    private ServerInfo server;
    private JavaInfo java;
    private List<DiskMetrics> disks;
    private List<GarbageCollectorMetrics> garbageCollectors;
    private List<RuntimeInfo> runtime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CpuMetrics {
        private Integer processors;
        private Double systemLoadAverage;
        private Integer systemCpuUsagePercent;
        private Integer processCpuUsagePercent;
        private Integer idleCpuUsagePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryMetrics {
        private Long heapUsedBytes;
        private Long heapCommittedBytes;
        private Long heapMaxBytes;
        private Long heapFreeBytes;
        private Integer heapUsagePercent;
        private Long nonHeapUsedBytes;
        private Long nonHeapCommittedBytes;
        private Integer nonHeapUsagePercent;
        private Long physicalTotalBytes;
        private Long physicalFreeBytes;
        private Long physicalUsedBytes;
        private Integer physicalUsagePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ThreadMetrics {
        private Integer live;
        private Integer daemon;
        private Integer peak;
        private Long totalStarted;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiskMetrics {
        private String name;
        private String path;
        private String fileSystem;
        private String type;
        private Long totalBytes;
        private Long freeBytes;
        private Long usableBytes;
        private Long usedBytes;
        private Integer usagePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServerInfo {
        private String name;
        private String ip;
        private String osName;
        private String osArch;
        private String osVersion;
        private Integer processors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JavaInfo {
        private String name;
        private String version;
        private String vendor;
        private String home;
        private String projectDir;
        private String startTime;
        private String runTime;
        private String inputArguments;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GarbageCollectorMetrics {
        private String name;
        private Long collectionCount;
        private Long collectionTimeMillis;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RuntimeInfo {
        private String label;
        private String value;
    }
}
