package com.laker.admin.module.audit.service;

import com.laker.admin.infrastructure.audit.AuditLogCollector;
import com.laker.admin.module.audit.entity.AuditDataChangeLog;
import com.laker.admin.module.audit.entity.AuditOperationLog;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 企业敏感操作审计入口。
 *
 * <p>菜单、角色、流程等配置类操作统一写入数据变更审计，便于在审计中心的“敏感变更”页按人、时间和业务对象追溯。</p>
 */
@Service
public class SensitiveAuditService {
    private final AuditLogCollector auditLogCollector;

    public SensitiveAuditService(AuditLogCollector auditLogCollector) {
        this.auditLogCollector = auditLogCollector;
    }

    public void record(String module, String action, String bizType, String bizId, String detailJson) {
        AuditDataChangeLog log = new AuditDataChangeLog();
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setTableName(module);
        log.setChangeType(changeType(action, detailJson));
        log.setChangedFields(action);
        log.setAfterJson(payload(bizType, bizId, detailJson));
        log.setCreatedAt(LocalDateTime.now());
        auditLogCollector.recordDataChange(log);
    }

    public void recordFailure(String module, String action, String bizType, String bizId, String errorMessage) {
        AuditOperationLog log = new AuditOperationLog();
        log.setModule(module);
        log.setAction(action);
        log.setRequestParams(payload(bizType, bizId, null));
        log.setResponseStatus("FAILED");
        log.setErrorMessage(errorMessage);
        log.setCreatedAt(LocalDateTime.now());
        auditLogCollector.recordOperation(log);
    }

    private String payload(String bizType, String bizId, String detailJson) {
        String safeDetail = StringUtils.hasText(detailJson) ? detailJson : "{}";
        return """
                {"bizType":"%s","bizId":"%s","detail":%s}
                """.formatted(escape(bizType), escape(bizId), safeDetail).trim();
    }

    private String changeType(String action, String detailJson) {
        String text = ((action == null ? "" : action) + " " + (detailJson == null ? "" : detailJson)).toLowerCase();
        if (text.contains("删除") || text.contains("\"deleted\":true")) {
            return "DELETE";
        }
        if (text.contains("新增") || text.contains("创建")) {
            return "CREATE";
        }
        return "UPDATE";
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
