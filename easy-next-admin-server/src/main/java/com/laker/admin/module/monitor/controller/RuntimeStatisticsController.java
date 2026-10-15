package com.laker.admin.module.monitor.controller;

import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.monitor.service.RuntimeStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "运行诊断统计")
@RestController
@RequestMapping("/api/monitor/diagnosis/statistics")
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
@EasyPermission(EasyPermissions.Monitor.DIAGNOSIS_VIEW)
public class RuntimeStatisticsController {
    private final RuntimeStatisticsService statisticsService;

    public RuntimeStatisticsController(RuntimeStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping
    @Operation(summary = "运行操作统计",
            description = "按本地持久化的 incident 操作样本聚合 HTTP、Job、Kafka、事务、SQL 和远程调用。"
                    + "所有类型均只覆盖慢调用、错误或包含慢错子调用的样本，不能代表全量调用；"
                    + "全量调用量与 CPU、线程分配内存等资源趋势请查看 easy.operations.* Metrics。"
                    + "响应中的 sampled 和 coverage 会明确标识覆盖范围。")
    public Response<RuntimeStatisticsService.StatisticsResult> statistics(
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(defaultValue = "24") int windowHours,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "totalDurationMs,desc") String sort) {
        return Response.ok(statisticsService.statistics(type, windowHours, limit, sort));
    }
}
