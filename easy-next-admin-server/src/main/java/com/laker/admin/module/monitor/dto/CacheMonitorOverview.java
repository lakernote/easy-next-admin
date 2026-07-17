package com.laker.admin.module.monitor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CacheMonitorOverview", description = "缓存监控概览")
public class CacheMonitorOverview {
    private String provider;
    private String scope;
    private String sampleTime;
    private Boolean statisticsAvailable;
    private Integer cacheCount;
    private Long totalEstimatedSize;
    private Long totalMaximumSize;
    private Long totalRequestCount;
    private Long totalHitCount;
    private Long totalMissCount;
    private Long totalEvictionCount;
    private Integer hitRate;
    private Integer missRate;
    private Integer usageRate;
    private Integer warningCount;
    private String busiestCacheName;
    private String largestCacheName;
    private String weakestCacheName;
    private List<String> recommendations;
    private List<CacheItem> caches;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CacheItem {
        private String name;
        private String provider;
        private String nativeClass;
        private Long estimatedSize;
        private Long maximumSize;
        private Long ttlSeconds;
        private Long maxIdleSeconds;
        private Long requestCount;
        private Long hitCount;
        private Long missCount;
        private Long evictionCount;
        private Integer hitRate;
        private Integer missRate;
        private Integer usageRate;
        private Boolean statisticsAvailable;
        private String healthStatus;
        private String healthLabel;
        private String riskLevel;
        private String description;
    }
}
