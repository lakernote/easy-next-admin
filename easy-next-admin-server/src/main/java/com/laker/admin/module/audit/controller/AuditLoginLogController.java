package com.laker.admin.module.audit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.infrastructure.observability.apilog.EasyApiAccessLog;
import com.laker.admin.infrastructure.persistence.mybatis.EasyPageSupport;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.audit.dto.AuditLoginLogView;
import com.laker.admin.module.audit.entity.AuditLoginLog;
import com.laker.admin.module.audit.service.IAuditLoginLogService;
import com.laker.admin.module.audit.support.AuditVisibilitySupport;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "登录审计日志")
@RestController
@RequestMapping("/api/audit/login-logs")
@EasyApiAccessLog
public class AuditLoginLogController {
    private final IAuditLoginLogService auditLoginLogService;
    private final AuditVisibilitySupport auditVisibilitySupport;

    public AuditLoginLogController(IAuditLoginLogService auditLoginLogService,
                                   AuditVisibilitySupport auditVisibilitySupport) {
        this.auditLoginLogService = auditLoginLogService;
        this.auditVisibilitySupport = auditVisibilitySupport;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.Audit.BEHAVIOR_VIEW)
    public PageResponse<AuditLoginLogView> pageAll(@RequestParam(required = false, defaultValue = "1") long page,
                                                   @RequestParam(required = false, defaultValue = "10") long limit,
                                                   @RequestParam(required = false) String keyWord,
                                                   @RequestParam(required = false) String loginResult) {
        LambdaQueryWrapper<AuditLoginLog> queryWrapper = Wrappers.<AuditLoginLog>lambdaQuery()
                .eq(StringUtils.hasText(loginResult), AuditLoginLog::getLoginResult, loginResult)
                .and(StringUtils.hasText(keyWord), wrapper -> wrapper
                        .like(AuditLoginLog::getUserName, keyWord)
                        .or()
                        .like(AuditLoginLog::getIp, keyWord)
                        .or()
                        .like(AuditLoginLog::getClientType, keyWord)
                        .or()
                        .like(AuditLoginLog::getFailReason, keyWord));
        auditVisibilitySupport.visibleUserIds().ifPresent(userIds -> {
            if (userIds.isEmpty()) {
                queryWrapper.apply("1 = 0");
            } else {
                queryWrapper.in(AuditLoginLog::getUserId, userIds);
            }
        });
        queryWrapper.orderByDesc(AuditLoginLog::getLoginTime);
        return EasyPageSupport.response(
                auditLoginLogService.page(EasyPageSupport.page(page, limit), queryWrapper),
                AuditLoginLogView::from);
    }
}
