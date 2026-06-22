package com.laker.admin.module.audit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.audit.entity.AuditDataChangeLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 敏感数据变更审计对外响应模型。
 */
@Data
@Builder
public class AuditDataChangeLogView {
    private Long id;
    private String bizType;
    private String bizId;
    private String tableName;
    private String changeType;
    private String beforeJson;
    private String afterJson;
    private String changedFields;
    private Long operatorId;
    private AuditUserView operator;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    public static AuditDataChangeLogView from(AuditDataChangeLog log) {
        if (log == null) {
            return null;
        }
        return AuditDataChangeLogView.builder()
                .id(log.getId())
                .bizType(log.getBizType())
                .bizId(log.getBizId())
                .tableName(log.getTableName())
                .changeType(log.getChangeType())
                .beforeJson(log.getBeforeJson())
                .afterJson(log.getAfterJson())
                .changedFields(log.getChangedFields())
                .operatorId(log.getOperatorId())
                .operator(AuditUserView.from(log.getOperator()))
                .createdAt(log.getCreatedAt())
                .build();
    }

    public static List<AuditDataChangeLogView> fromList(List<AuditDataChangeLog> logs) {
        return logs == null ? List.of() : logs.stream().map(AuditDataChangeLogView::from).toList();
    }
}
