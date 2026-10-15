package com.laker.admin.module.monitor.controller;

import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.observability.jfr.LocalJfrRecorder;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.monitor.service.JfrAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Tag(name = "运行诊断 JFR")
@RestController
@RequestMapping("/api/monitor/diagnosis/jfr")
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
@EasyPermission(EasyPermissions.Monitor.DIAGNOSIS_ADVANCED_VIEW)
public class JfrAnalysisController {
    private final JfrAnalysisService analysisService;
    private final LocalJfrRecorder recorder;

    public JfrAnalysisController(JfrAnalysisService analysisService, LocalJfrRecorder recorder) {
        this.analysisService = analysisService;
        this.recorder = recorder;
    }

    @GetMapping("/recording/status")
    @Operation(summary = "JFR 录制状态", description = "查看高级诊断是否可用、是否正在录制及自动停止时间。")
    public Response<LocalJfrRecorder.RecorderStatus> recordingStatus() {
        return Response.ok(recorder.status());
    }

    @PostMapping("/recording/start")
    @EasyPermission(EasyPermissions.Monitor.DIAGNOSIS_JFR_CONTROL)
    @EasyAudit(module = "高级诊断", action = "开启 JFR 录制")
    @Operation(summary = "开启 JFR 录制", description = "开启一个有界录制会话，最长 24 小时；应用重启后恢复为配置的默认状态。")
    public Response<LocalJfrRecorder.RecorderStatus> startRecording(
            @RequestParam(defaultValue = "60") int durationMinutes) {
        return Response.ok(recorder.enable(durationMinutes));
    }

    @PostMapping("/recording/stop")
    @EasyPermission(EasyPermissions.Monitor.DIAGNOSIS_JFR_CONTROL)
    @EasyAudit(module = "高级诊断", action = "停止 JFR 录制")
    @Operation(summary = "停止 JFR 录制", description = "停止当前录制并发布可分析的 JFR 分段。")
    public Response<LocalJfrRecorder.RecorderStatus> stopRecording() {
        return Response.ok(recorder.disable());
    }

    @GetMapping("/analysis")
    @Operation(summary = "JFR 应用内分析",
            description = "聚合完整 JFR 分段中的执行热点、对象分配、锁等待和 GC 暂停；limit 分别限制每组返回条数。")
    public Response<JfrAnalysisService.JfrAnalysis> analyze(
            @RequestParam(required = false) String fileName,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return Response.ok(analysisService.analyze(fileName, limit, traceId, from, to));
    }

    @GetMapping("/analysis/window")
    @Operation(summary = "跨 JFR 分段联合分析",
            description = "在统一文件数、事件数和时间预算内扫描多个分段，并在聚合前去除 chunk 边界重复事件。")
    public Response<JfrAnalysisService.JfrAnalysis> analyzeWindow(
            @RequestParam String fileNames,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String traceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        List<String> requestedFiles = Arrays.stream(fileNames.split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
        return Response.ok(analysisService.analyzeFiles(requestedFiles, limit, traceId, from, to));
    }
}
