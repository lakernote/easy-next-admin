package com.laker.admin.module.monitor.controller;

import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.observability.apilog.EasyApiAccessLog;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.monitor.dto.MonitorStatisticsOverview;
import com.laker.admin.module.monitor.service.MonitorStatisticsService;
import com.laker.admin.module.system.dto.OnlineSessionView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "监控统计")
@RestController
@RequestMapping("/api/monitor/statistics")
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
@EasyApiAccessLog
public class MonitorStatisticsController {
    private final MonitorStatisticsService monitorStatisticsService;

    public MonitorStatisticsController(MonitorStatisticsService monitorStatisticsService) {
        this.monitorStatisticsService = monitorStatisticsService;
    }

    @GetMapping("/overview")
    @EasyPermission(EasyPermissions.Monitor.SERVER_VIEW)
    @Operation(summary = "监控统计总览", description = "聚合接口、在线用户、远程调用和任务执行统计。")
    public Response<MonitorStatisticsOverview> overview() {
        return Response.ok(monitorStatisticsService.overview());
    }

    @GetMapping("/apis")
    @EasyPermission(EasyPermissions.Audit.BEHAVIOR_VIEW)
    @Operation(summary = "接口访问统计", description = "从 audit_api_log 聚合接口调用量、成功率、耗时和高耗时请求数量。")
    public Response<MonitorStatisticsOverview.ApiStatistics> apiStatistics(
            @RequestParam(required = false, defaultValue = "7") int days,
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return Response.ok(monitorStatisticsService.apiStatistics(days, limit));
    }

    @GetMapping("/online-users")
    @EasyPermission(EasyPermissions.Monitor.ONLINE_VIEW)
    @Operation(summary = "在线用户", description = "复用认证会话存储返回当前在线用户。")
    public PageResponse<OnlineSessionView> onlineUsers(@RequestParam(required = false, defaultValue = "1") int page,
                                                       @RequestParam(required = false, defaultValue = "10") int limit) {
        return monitorStatisticsService.onlineUsers(page, limit);
    }

    @GetMapping("/remote-calls")
    @EasyPermission(EasyPermissions.Audit.BEHAVIOR_VIEW)
    @Operation(summary = "远程调用统计", description = "读取观测层 Micrometer 计时器中的真实远程调用统计。")
    public Response<List<MonitorStatisticsOverview.RemoteCallStatistics>> remoteCalls() {
        return Response.ok(monitorStatisticsService.remoteCallStatistics());
    }

    @GetMapping("/jobs")
    @EasyPermission(EasyPermissions.Schedule.JOB_LIST)
    @Operation(summary = "任务执行统计", description = "从 schedule_job 和 schedule_job_log 聚合动态任务执行状态。")
    public Response<MonitorStatisticsOverview.JobStatistics> jobStatistics(
            @RequestParam(required = false, defaultValue = "10") int limit) {
        return Response.ok(monitorStatisticsService.jobStatistics(limit));
    }
}
