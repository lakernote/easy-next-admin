package com.laker.admin.module.audit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.infrastructure.observability.apilog.EasyApiAccessLog;
import com.laker.admin.infrastructure.persistence.mybatis.EasyPageSupport;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.audit.dto.AuditOperationLogView;
import com.laker.admin.module.audit.entity.AuditOperationLog;
import com.laker.admin.module.audit.service.IAuditOperationLogService;
import com.laker.admin.module.audit.support.AuditVisibilitySupport;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "操作审计日志")
@RestController
@RequestMapping("/api/audit/operation-logs")
@EasyApiAccessLog
public class AuditOperationLogController {
    private final IAuditOperationLogService auditOperationLogService;
    private final AuditVisibilitySupport auditVisibilitySupport;

    public AuditOperationLogController(IAuditOperationLogService auditOperationLogService,
                                       AuditVisibilitySupport auditVisibilitySupport) {
        this.auditOperationLogService = auditOperationLogService;
        this.auditVisibilitySupport = auditVisibilitySupport;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.Audit.BEHAVIOR_VIEW)
    public PageResponse<AuditOperationLogView> pageAll(@RequestParam(required = false, defaultValue = "1") long page,
                                                       @RequestParam(required = false, defaultValue = "10") long limit,
                                                       @RequestParam(required = false) String keyWord,
                                                       @RequestParam(required = false) String responseStatus) {
        LambdaQueryWrapper<AuditOperationLog> queryWrapper = Wrappers.<AuditOperationLog>lambdaQuery()
                .eq(StringUtils.hasText(responseStatus), AuditOperationLog::getResponseStatus, responseStatus)
                .and(StringUtils.hasText(keyWord), wrapper -> wrapper
                        .like(AuditOperationLog::getModule, keyWord)
                        .or()
                        .like(AuditOperationLog::getAction, keyWord)
                        .or()
                        .like(AuditOperationLog::getOperatorName, keyWord)
                        .or()
                        .like(AuditOperationLog::getRequestUri, keyWord)
                        .or()
                        .like(AuditOperationLog::getIp, keyWord));
        auditVisibilitySupport.visibleUserIds().ifPresent(userIds -> {
            if (userIds.isEmpty()) {
                queryWrapper.apply("1 = 0");
            } else {
                queryWrapper.in(AuditOperationLog::getOperatorId, userIds);
            }
        });
        queryWrapper.orderByDesc(AuditOperationLog::getCreatedAt);
        return EasyPageSupport.response(
                auditOperationLogService.page(EasyPageSupport.page(page, limit), queryWrapper),
                AuditOperationLogView::from);
    }
}
