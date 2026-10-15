package com.laker.admin.module.monitor.controller;

import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.monitor.service.JvmDiagnosticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "运行诊断")
@RestController
@RequestMapping("/api/monitor/diagnosis")
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
@EasyPermission(EasyPermissions.Monitor.DIAGNOSIS_ADVANCED_VIEW)
public class JvmDiagnosticsController {
    private final JvmDiagnosticsService diagnosticsService;

    public JvmDiagnosticsController(JvmDiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @GetMapping("/threads")
    @Operation(summary = "JVM 线程现场", description = "采集当前线程状态、CPU、分配内存、锁和有限深度的堆栈。")
    public Response<JvmDiagnosticsService.ThreadDump> threads(
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(defaultValue = "64") int stackDepth) {
        return Response.ok(diagnosticsService.threads(limit, stackDepth));
    }

    @GetMapping("/deadlocks")
    @Operation(summary = "JVM 死锁诊断", description = "检测当前 JVM 的监视器和 Ownable Synchronizer 死锁并返回关联线程现场。")
    public Response<JvmDiagnosticsService.DeadlockReport> deadlocks(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "64") int stackDepth) {
        return Response.ok(diagnosticsService.deadlocks(limit, stackDepth));
    }

    @GetMapping("/pools")
    @Operation(summary = "运行池诊断", description = "聚合 Micrometer 中的 Hikari、JDBC、Tomcat 和线程执行器运行指标。")
    public Response<JvmDiagnosticsService.PoolDiagnostics> pools() {
        return Response.ok(diagnosticsService.pools());
    }

    @GetMapping("/heap-histogram/capability")
    @Operation(summary = "JVM 类直方图能力", description = "只探测当前 JVM 是否支持 DiagnosticCommand 类直方图，不执行堆扫描。")
    public Response<JvmDiagnosticsService.HeapHistogramCapability> heapHistogramCapability() {
        return Response.ok(diagnosticsService.heapHistogramCapability());
    }

    @PostMapping("/heap-histogram")
    @EasyPermission(EasyPermissions.Monitor.DIAGNOSIS_CAPTURE)
    @EasyAudit(module = "运行监控", action = "抓取 JVM 类直方图")
    @Operation(summary = "按需抓取 JVM 类直方图",
            description = "管理员手动触发 DiagnosticCommand 堆对象统计。该操作开销较高，服务端限制返回条数并拒绝并发抓取。")
    public Response<JvmDiagnosticsService.HeapHistogram> heapHistogram(
            @RequestParam(defaultValue = "100") int limit) {
        return Response.ok(diagnosticsService.heapHistogram(limit));
    }
}
