package com.laker.admin.module.system.dto.workbench;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnterpriseWorkbenchOverview {
    private WorkflowOverview workflow;
    private List<ApplicationEntry> applications;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowOverview {
        private List<WorkflowMetric> metrics;
        private List<WorkflowTaskBrief> pendingTasks;
        private List<WorkflowInstanceBrief> startedInstances;
        private List<WorkflowCcBrief> ccItems;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowMetric {
        private String key;
        private String title;
        private String value;
        private String hint;
        private String tone;
        private String path;
        private String permission;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowTaskBrief {
        private Long id;
        private Long instanceId;
        private String title;
        private String nodeName;
        private String businessType;
        private String businessId;
        private String status;
        private LocalDateTime startedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowInstanceBrief {
        private Long id;
        private String title;
        private String businessType;
        private String businessId;
        private String status;
        private LocalDateTime startedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowCcBrief {
        private Long id;
        private Long instanceId;
        private String title;
        private String nodeKey;
        private String nodeName;
        private Integer readStatus;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicationEntry {
        private String title;
        private String description;
        private String path;
        private String permission;
        private String icon;
    }
}
