package com.laker.admin.module.monitor.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.system.dto.OnlineSessionView;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 监控统计聚合结果。
 *
 * <p>数据来自审计日志、任务日志、在线会话和 Micrometer 指标，页面不得再填充假数据。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "MonitorStatisticsOverview", description = "接口、在线用户、远程调用和任务执行统计")
public class MonitorStatisticsOverview {
    private ApiStatistics api;
    private OnlineUserStatistics onlineUsers;
    private List<RemoteCallStatistics> remoteCalls;
    private JobStatistics jobs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiStatistics {
        private Long totalCount;
        private Long successCount;
        private Long failureCount;
        private Long uniqueIpCount;
        private Integer slowCount;
        private Double avgCostMs;
        private Integer maxCostMs;
        private Integer successRate;
        private List<ApiEndpointStatistics> endpoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiEndpointStatistics {
        private String uri;
        private String method;
        private Long totalCount;
        private Long successCount;
        private Long failureCount;
        private Double avgCostMs;
        private Integer maxCostMs;
        private Integer successRate;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private LocalDateTime lastTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OnlineUserStatistics {
        private Long totalCount;
        private List<OnlineSessionView> records;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemoteCallStatistics {
        private String target;
        private String method;
        private Long totalCount;
        private Long successCount;
        private Long failureCount;
        private Double avgCostMs;
        private Double maxCostMs;
        private Integer successRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobStatistics {
        private Long totalJobs;
        private Long enabledJobs;
        private Long executionCount24h;
        private Long failureCount24h;
        private Double avgCostMs24h;
        private Integer maxCostMs24h;
        private Integer successRate24h;
        private List<JobExecutionStatistics> executions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobExecutionStatistics {
        private String jobCode;
        private Long totalCount;
        private Long successCount;
        private Long failureCount;
        private Double avgCostMs;
        private Integer maxCostMs;
        private Integer lastStatus;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
        private LocalDateTime lastStartTime;
    }
}
