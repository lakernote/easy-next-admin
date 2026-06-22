package com.laker.admin.module.audit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.infrastructure.observability.metrics.EasyMetrics;
import com.laker.admin.infrastructure.persistence.mybatis.EasyPageSupport;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.audit.dto.AuditDataChangeLogView;
import com.laker.admin.module.audit.entity.AuditDataChangeLog;
import com.laker.admin.module.audit.service.IAuditDataChangeLogService;
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

@Tag(name = "数据变更审计日志")
@RestController
@RequestMapping("/api/audit/data-change-logs")
@EasyMetrics
public class AuditDataChangeLogController {
    private final IAuditDataChangeLogService auditDataChangeLogService;
    private final ISysUserService sysUserService;
    private final AuditVisibilitySupport auditVisibilitySupport;

    public AuditDataChangeLogController(IAuditDataChangeLogService auditDataChangeLogService,
                                        ISysUserService sysUserService,
                                        AuditVisibilitySupport auditVisibilitySupport) {
        this.auditDataChangeLogService = auditDataChangeLogService;
        this.sysUserService = sysUserService;
        this.auditVisibilitySupport = auditVisibilitySupport;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.Audit.BEHAVIOR_VIEW)
    public PageResponse<AuditDataChangeLogView> pageAll(@RequestParam(required = false, defaultValue = "1") long page,
                                                        @RequestParam(required = false, defaultValue = "10") long limit,
                                                        @RequestParam(required = false) String keyWord,
                                                        @RequestParam(required = false) String changeType) {
        LambdaQueryWrapper<AuditDataChangeLog> queryWrapper = Wrappers.<AuditDataChangeLog>lambdaQuery()
                .eq(StringUtils.hasText(changeType), AuditDataChangeLog::getChangeType, changeType)
                .and(StringUtils.hasText(keyWord), wrapper -> wrapper
                        .like(AuditDataChangeLog::getBizType, keyWord)
                        .or()
                        .like(AuditDataChangeLog::getBizId, keyWord)
                        .or()
                        .like(AuditDataChangeLog::getTableName, keyWord)
                        .or()
                        .like(AuditDataChangeLog::getChangedFields, keyWord));
        auditVisibilitySupport.visibleUserIds().ifPresent(userIds -> {
            if (userIds.isEmpty()) {
                queryWrapper.apply("1 = 0");
            } else {
                queryWrapper.in(AuditDataChangeLog::getOperatorId, userIds);
            }
        });
        queryWrapper.orderByDesc(AuditDataChangeLog::getCreatedAt);
        Page<AuditDataChangeLog> pageResult = auditDataChangeLogService.page(EasyPageSupport.page(page, limit), queryWrapper);
        List<AuditDataChangeLog> records = pageResult.getRecords();
        Map<Long, SysUser> users = usersById(records);
        records.forEach(record -> {
            Long operatorId = record.getOperatorId();
            record.setOperator(operatorId == null ? null : users.get(operatorId));
        });
        return EasyPageSupport.response(pageResult, AuditDataChangeLogView::from);
    }

    private Map<Long, SysUser> usersById(List<AuditDataChangeLog> records) {
        List<Long> userIds = records.stream()
                .map(AuditDataChangeLog::getOperatorId)
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
