package com.laker.admin.module.audit.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.observability.metrics.EasyMetrics;
import com.laker.admin.infrastructure.persistence.mybatis.EasyPageSupport;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.audit.dto.AuditApiDailyVisitView;
import com.laker.admin.module.audit.dto.AuditApiLogView;
import com.laker.admin.module.audit.dto.AuditApiTopIpView;
import com.laker.admin.module.audit.entity.AuditApiLog;
import com.laker.admin.module.audit.mapper.AuditApiLogMapper;
import com.laker.admin.module.audit.service.IAuditApiLogService;
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

/**
 * 行为审计日志接口。
 *
 * <p>用于后台查询接口访问、耗时、请求结果等审计数据。写入动作由观测切面统一完成，
 * 这里不承载业务写日志逻辑。</p>
 */
@Tag(name = "行为审计日志")
@RestController
@RequestMapping("/api/audit/api-logs")
@EasyMetrics
public class AuditApiLogController {
    final IAuditApiLogService auditApiLogService;

    final AuditApiLogMapper auditApiLogMapper;

    final ISysUserService sysUserService;
    private final AuditVisibilitySupport auditVisibilitySupport;

    public AuditApiLogController(IAuditApiLogService auditApiLogService,
                                 AuditApiLogMapper auditApiLogMapper,
                                 ISysUserService sysUserService,
                                 AuditVisibilitySupport auditVisibilitySupport) {
        this.auditApiLogService = auditApiLogService;
        this.auditApiLogMapper = auditApiLogMapper;
        this.sysUserService = sysUserService;
        this.auditVisibilitySupport = auditVisibilitySupport;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.Audit.BEHAVIOR_VIEW)
    public PageResponse<AuditApiLogView> pageAll(@RequestParam(required = false, defaultValue = "1") long page,
                                                 @RequestParam(required = false, defaultValue = "10") long limit,
                                                 String keyWord) {
        LambdaQueryWrapper<AuditApiLog> queryWrapper = new QueryWrapper<AuditApiLog>().lambda();
        if (StringUtils.hasText(keyWord)) {
            queryWrapper.and(wrapper -> wrapper
                    .like(AuditApiLog::getUri, keyWord)
                    .or()
                    .like(AuditApiLog::getIp, keyWord)
                    .or()
                    .like(AuditApiLog::getMethod, keyWord)
                    .or()
                    .like(AuditApiLog::getRequest, keyWord));
        }
        auditVisibilitySupport.visibleUserIds().ifPresent(userIds -> {
            if (userIds.isEmpty()) {
                queryWrapper.apply("1 = 0");
            } else {
                queryWrapper.in(AuditApiLog::getUserId, userIds);
            }
        });
        queryWrapper.orderByDesc(AuditApiLog::getCreateTime);
        Page<AuditApiLog> pageList = auditApiLogService.page(EasyPageSupport.page(page, limit), queryWrapper);
        List<AuditApiLog> records = pageList.getRecords();
        Map<Long, SysUser> users = usersById(records);
        records.forEach(auditLog -> auditLog.setUser(users.get(auditLog.getUserId())));
        return EasyPageSupport.response(pageList, AuditApiLogView::from);
    }

    private Map<Long, SysUser> usersById(List<AuditApiLog> records) {
        List<Long> userIds = records.stream()
                .map(AuditApiLog::getUserId)
                .filter(userId -> userId != null)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return sysUserService.listByIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getUserId, Function.identity(), (left, right) -> left));
    }


    @GetMapping("/visits7day")
    @EasyPermission(EasyPermissions.Audit.BEHAVIOR_VIEW)
    public Response<List<AuditApiDailyVisitView>> visits7Day() {
        if (!auditVisibilitySupport.canReadAllUsers()) {
            return Response.ok(List.of());
        }
        List<AuditApiDailyVisitView> logStatisticsVo = auditApiLogMapper.selectStatistics7Day();
        return Response.ok(logStatisticsVo);
    }

    @GetMapping("/visitsTop10IP")
    @EasyPermission(EasyPermissions.Audit.BEHAVIOR_VIEW)
    public PageResponse<AuditApiTopIpView> visitsTop10Ip() {
        if (!auditVisibilitySupport.canReadAllUsers()) {
            return PageResponse.ok(List.of(), 0);
        }
        List<AuditApiTopIpView> logStatisticsVo = auditApiLogMapper.selectStatisticsVisitsTop10IP();
        return PageResponse.ok(logStatisticsVo, logStatisticsVo.size());
    }
}
