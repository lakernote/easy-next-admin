package com.laker.admin.module.audit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.infrastructure.observability.apilog.EasyApiAccessLog;
import com.laker.admin.infrastructure.persistence.mybatis.EasyPageSupport;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.audit.dto.AuditErrorLogView;
import com.laker.admin.module.audit.entity.AuditErrorLog;
import com.laker.admin.module.audit.service.IAuditErrorLogService;
import com.laker.admin.module.audit.support.AuditVisibilitySupport;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Tag(name = "异常审计日志")
@RestController
@RequestMapping("/api/audit/error-logs")
@EasyApiAccessLog
public class AuditErrorLogController {
    private final IAuditErrorLogService auditErrorLogService;
    private final ISysUserService sysUserService;
    private final AuditVisibilitySupport auditVisibilitySupport;

    public AuditErrorLogController(IAuditErrorLogService auditErrorLogService,
                                   ISysUserService sysUserService,
                                   AuditVisibilitySupport auditVisibilitySupport) {
        this.auditErrorLogService = auditErrorLogService;
        this.sysUserService = sysUserService;
        this.auditVisibilitySupport = auditVisibilitySupport;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.Audit.BEHAVIOR_VIEW)
    public PageResponse<AuditErrorLogView> pageAll(@RequestParam(required = false, defaultValue = "1") long page,
                                                   @RequestParam(required = false, defaultValue = "10") long limit,
                                                   @RequestParam(required = false) String keyWord,
                                                   @RequestParam(required = false) String errorType) {
        LambdaQueryWrapper<AuditErrorLog> queryWrapper = Wrappers.<AuditErrorLog>lambdaQuery()
                .like(StringUtils.hasText(errorType), AuditErrorLog::getErrorType, errorType)
                .and(StringUtils.hasText(keyWord), wrapper -> wrapper
                        .like(AuditErrorLog::getRequestUri, keyWord)
                        .or()
                        .like(AuditErrorLog::getRequestMethod, keyWord)
                        .or()
                        .like(AuditErrorLog::getErrorMessage, keyWord));
        auditVisibilitySupport.visibleUserIds().ifPresent(userIds -> {
            if (userIds.isEmpty()) {
                queryWrapper.apply("1 = 0");
            } else {
                queryWrapper.in(AuditErrorLog::getOperatorId, userIds);
            }
        });
        queryWrapper.orderByDesc(AuditErrorLog::getCreatedAt);
        Page<AuditErrorLog> pageResult = auditErrorLogService.page(EasyPageSupport.page(page, limit), queryWrapper);
        List<AuditErrorLog> records = pageResult.getRecords();
        Map<Long, SysUser> users = usersById(records);
        records.forEach(record -> record.setOperator(users.get(record.getOperatorId())));
        return EasyPageSupport.response(pageResult, AuditErrorLogView::from);
    }

    private Map<Long, SysUser> usersById(List<AuditErrorLog> records) {
        List<Long> userIds = records.stream()
                .map(AuditErrorLog::getOperatorId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return sysUserService.listByIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getUserId, Function.identity(), (a, b) -> a));
    }
}
