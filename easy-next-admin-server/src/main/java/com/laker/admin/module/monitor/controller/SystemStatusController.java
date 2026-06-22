package com.laker.admin.module.monitor.controller;

import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.monitor.dto.SystemStatusOverview;
import com.laker.admin.module.monitor.service.SystemStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "服务监控")
@RestController
@RequestMapping("/api/monitor/system")
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
public class SystemStatusController {
    private final SystemStatusService systemStatusService;

    public SystemStatusController(SystemStatusService systemStatusService) {
        this.systemStatusService = systemStatusService;
    }

    @GetMapping("/overview")
    @EasyPermission(EasyPermissions.Monitor.SERVER_VIEW)
    @Operation(summary = "轻量级系统状态概览", description = "返回 JVM、CPU、内存、线程、磁盘和 GC 基础指标。")
    public Response<SystemStatusOverview> overview() {
        return Response.ok(systemStatusService.overview());
    }
}
