package com.laker.admin.module.monitor.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.common.model.Response;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import com.laker.admin.infrastructure.observability.jfr.LocalJfrRecorder;
import com.laker.admin.infrastructure.observability.jfr.ActiveOperationRegistry;
import com.laker.admin.infrastructure.observability.metrics.LocalMetricTimeSeriesStore;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.audit.dto.AuditErrorLogView;
import com.laker.admin.module.audit.entity.AuditErrorLog;
import com.laker.admin.module.audit.service.IAuditErrorLogService;
import com.laker.admin.module.audit.support.AuditVisibilitySupport;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.Duration;
import java.util.function.Function;

@Tag(name = "运行诊断")
@RestController
@RequestMapping("/api/monitor/diagnosis")
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
@EasyPermission(EasyPermissions.Monitor.DIAGNOSIS_VIEW)
public class RuntimeDiagnosisController {
    private static final int SUMMARY_SAMPLE_LIMIT = 50_000;
    private final MeterRegistry meterRegistry;
    private final RecentTraceStore recentTraceStore;
    private final LocalMetricTimeSeriesStore metricTimeSeriesStore;
    private final IAuditErrorLogService errorLogService;
    private final AuditVisibilitySupport auditVisibilitySupport;
    private final long slowThresholdMs;
    private final long transactionSlowThresholdMs;
    private final long scheduleSlowThresholdMs;
    private final long kafkaSlowThresholdMs;
    private final long sqlSlowThresholdMs;
    private final long remoteSlowThresholdMs;
    private final LocalJfrRecorder jfrRecorder;
    private final ActiveOperationRegistry activeOperations;
    private final ObjectMapper objectMapper;

    public RuntimeDiagnosisController(MeterRegistry meterRegistry, RecentTraceStore recentTraceStore,
                                      LocalMetricTimeSeriesStore metricTimeSeriesStore,
                                      IAuditErrorLogService errorLogService,
                                      AuditVisibilitySupport auditVisibilitySupport,
                                      EasyNextAdminConfig config, LocalJfrRecorder jfrRecorder,
                                      ActiveOperationRegistry activeOperations, ObjectMapper objectMapper) {
        this.meterRegistry = meterRegistry;
        this.recentTraceStore = recentTraceStore;
        this.metricTimeSeriesStore = metricTimeSeriesStore;
        this.errorLogService = errorLogService;
        this.auditVisibilitySupport = auditVisibilitySupport;
        this.slowThresholdMs = config.getTrace().getHttpSlowThresholdMs();
        this.transactionSlowThresholdMs = config.getTrace().getTransactionSlowThresholdMs();
        this.scheduleSlowThresholdMs = config.getTrace().getScheduleSlowThresholdMs();
        this.kafkaSlowThresholdMs = config.getTrace().getKafkaConsumerSlowThresholdMs();
        this.sqlSlowThresholdMs = config.getTrace().getSqlSlowThresholdMs();
        this.remoteSlowThresholdMs = config.getTrace().getRemoteSlowThresholdMs();
        this.jfrRecorder = jfrRecorder;
        this.activeOperations = activeOperations;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/summary")
    @Operation(summary = "运行诊断摘要", description = "汇总最近一小时慢调用、错误、当前执行操作和本地诊断存储状态。")
    public Response<DiagnosisSummary> summary() {
        RecentTraceStore.IncidentSummary incidentSummary = recentTraceStore.recentIncidentSummary(
                Duration.ofHours(1), SUMMARY_SAMPLE_LIMIT);
        long errors = incidentSummary.errors();
        long slow = incidentSummary.slow();
        Map<String, Long> active = activeOperations.countsByType();
        long activeTotal = active.values().stream().mapToLong(Long::longValue).sum();
        String status = errors > 0 ? "ERROR" : slow > 0 ? "WARNING" : "HEALTHY";
        LocalJfrRecorder.RecorderStatus jfrStatus = jfrRecorder.status();
        long historyBytes = recentTraceStore.storageBytes();
        StorageStatus storageStatus = new StorageStatus(recentTraceStore.oldestSegmentDate(),
                recentTraceStore.segmentCoverageDays(), recentTraceStore.retentionDays(), historyBytes,
                recentTraceStore.maxHistoryBytes(), recentTraceStore.pendingWrites(),
                recentTraceStore.queueCapacity(), recentTraceStore.pendingBytes(), recentTraceStore.pendingMaxBytes(),
                recentTraceStore.droppedWrites(), recentTraceStore.byteBudgetDroppedWrites(),
                historyBytes >= Math.round(recentTraceStore.maxHistoryBytes() * 0.9));
        LocalMetricTimeSeriesStore.StorageStatus metricsStorageStatus = metricTimeSeriesStore.status();
        return Response.ok(new DiagnosisSummary(status, incidentSummary.sampledAt().toString(), 60, activeTotal, active,
                incidentSummary.incidents(), slow, errors, recentTraceStore.retentionDays(), historyBytes,
                jfrStatus.completedFiles(), jfrStatus.totalBytes(), Map.of(
                "HTTP", slowThresholdMs,
                "SQL", sqlSlowThresholdMs,
                "TRANSACTION", transactionSlowThresholdMs,
                "REMOTE", remoteSlowThresholdMs,
                "JOB", scheduleSlowThresholdMs,
                "KAFKA", kafkaSlowThresholdMs), jfrStatus, storageStatus, metricsStorageStatus,
                incidentSummary.truncated(), incidentSummary.sampleLimit()));
    }

    @GetMapping("/metrics")
    @Operation(summary = "Metrics 指标", description = "读取 Micrometer 当前值，并合并重启后仍保留在 RRD4J 中的历史系列目录。")
    public Response<List<MetricView>> metrics(@RequestParam(defaultValue = "100") int limit) {
        Map<String, MetricView> result = new LinkedHashMap<>();
        meterRegistry.getMeters().stream()
                .filter(meter -> isUsefulMetric(meter.getId().getName()))
                .map(RuntimeDiagnosisController::metricView)
                .sorted(Comparator.comparingInt((MetricView metric) -> metricPriority(metric.name()))
                        .thenComparing(MetricView::name)
                        .thenComparing(metric -> metric.tags().toString()))
                .forEach(metric -> result.put(metricSeriesKey(metric.name(), metric.tags()), metric));
        metricTimeSeriesStore.seriesCatalog().stream()
                .filter(series -> isUsefulMetric(series.name()))
                .map(RuntimeDiagnosisController::historicalMetricView)
                .forEach(metric -> result.putIfAbsent(metricSeriesKey(metric.name(), metric.tags()), metric));
        List<MetricView> limited = result.values().stream()
                .sorted(Comparator.comparingInt((MetricView metric) -> metricPriority(metric.name()))
                        .thenComparing(MetricView::name)
                        .thenComparing(metric -> metric.tags().toString()))
                .limit(clamp(limit, 5000))
                .toList();
        return Response.ok(limited);
    }

    @GetMapping("/metrics/history")
    @Operation(summary = "Metrics 历史", description = "按本地落盘的 Micrometer 快照读取 30 天内历史样本。")
    public Response<List<RecentTraceStore.MetricHistoryPoint>> metricHistory(@RequestParam String name,
                                                                             @RequestParam(required = false) String tags,
                                                                             @RequestParam(defaultValue = "50000") int limit) {
        Map<String, String> tagFilter = Map.of();
        try { if (tags != null && !tags.isBlank()) tagFilter = objectMapper.readValue(tags, new TypeReference<>() { }); }
        catch (Exception exception) { return Response.ok(List.of()); }
        int safeLimit = clamp(limit, 50000);
        List<RecentTraceStore.MetricHistoryPoint> current = metricTimeSeriesStore.metricHistory(
                name, tagFilter, safeLimit);
        // RRD4J is the canonical store. Avoid scanning the bounded incident JSONL whenever the requested series
        // already exists there; the fallback is only for data written by the pre-RRD local-history prototype.
        if (!current.isEmpty()) return Response.ok(current);
        List<RecentTraceStore.MetricHistoryPoint> legacy = recentTraceStore.metricHistory(
                name, tagFilter, safeLimit);
        return Response.ok(legacy);
    }

    @GetMapping("/operations")
    @Operation(summary = "诊断操作样本", description = "查看慢或错误 incident 的 CPU、线程分配内存、锁与堆栈采样。")
    public Response<List<RecentTraceStore.OperationSample>> operations(@RequestParam(defaultValue = "100") int limit) {
        return Response.ok(recentTraceStore.recentOperations(clamp(limit, 200)));
    }

    @GetMapping("/operations/page")
    @Operation(summary = "分页浏览诊断操作样本", description = "按服务端时间窗口和游标浏览最长 30 天的慢错操作样本。")
    public Response<HistoryPage<RecentTraceStore.OperationSample>> operationPage(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String keyword) {
        int pageSize = clamp(size, 500);
        HistoryCursor cursor = historyCursor(before);
        String normalizedKeyword = normalizeKeyword(keyword);
        List<RecentTraceStore.OperationSample> items = new ArrayList<>(recentTraceStore.recentOperationsMatching(
                pageFetchLimit(pageSize, cursor), historyFrom(hours), inclusiveBefore(localBefore(cursor)),
                item -> operationMatches(item, normalizedKeyword)));
        items.sort(Comparator.comparing(RecentTraceStore.OperationSample::finishedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return Response.ok(page(items, pageSize, cursor,
                item -> item.finishedAt() == null ? null : item.finishedAt().toString()));
    }

    @GetMapping("/active")
    @Operation(summary = "当前执行操作", description = "查看正在执行的 HTTP、SQL、事务、Job 和 Kafka 操作；列表批量读取轻量线程状态、当前等待锁和有限堆栈，完整持有锁只在慢/错深采样预算允许时抓取。")
    public Response<List<ActiveOperationRegistry.ActiveOperation>> active(
            @RequestParam(defaultValue = "100") int limit) {
        return Response.ok(activeOperations.snapshot(clamp(limit, 200)));
    }

    @GetMapping("/jfr/files")
    @EasyPermission(EasyPermissions.Monitor.DIAGNOSIS_ADVANCED_VIEW)
    @Operation(summary = "JFR 分段文件", description = "列出本地近期保存的 JFR 录制分段；默认保留 1 天并受容量上限约束。")
    public Response<List<LocalJfrRecorder.JfrFile>> jfrFiles(@RequestParam(defaultValue = "100") int limit) {
        return Response.ok(jfrRecorder.files().stream().limit(clamp(limit, 500)).toList());
    }

    @GetMapping("/jfr/files/page")
    @EasyPermission(EasyPermissions.Monitor.DIAGNOSIS_ADVANCED_VIEW)
    @Operation(summary = "分页浏览 JFR 分段", description = "按录制结束时间浏览本地保留的 JFR 文件。")
    public Response<HistoryPage<LocalJfrRecorder.JfrFile>> jfrFilePage(
            @RequestParam(defaultValue = "720") int hours,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "100") int size) {
        int pageSize = clamp(size, 500);
        HistoryCursor cursor = historyCursor(before);
        Instant beforeInstant = instantBefore(cursor);
        Instant from = Instant.now().minusSeconds((long) historyHours(hours) * 3600);
        List<LocalJfrRecorder.JfrFile> candidates = jfrRecorder.files().stream()
                .filter(item -> instant(item.recordingEndedAt()) != null)
                .filter(item -> !instant(item.recordingEndedAt()).isBefore(from))
                .filter(item -> beforeInstant == null || !instant(item.recordingEndedAt()).isAfter(beforeInstant))
                .limit(pageFetchLimit(pageSize, cursor)).toList();
        return Response.ok(page(candidates, pageSize, cursor, LocalJfrRecorder.JfrFile::recordingEndedAt));
    }

    @GetMapping("/jfr/files/for-trace/{traceId}")
    @EasyPermission(EasyPermissions.Monitor.DIAGNOSIS_ADVANCED_VIEW)
    @Operation(summary = "定位 Trace 对应 JFR 分段",
            description = "由服务端时区把 Trace 时间转换为 Instant，直接返回与执行窗口重叠的本地 JFR 分段。")
    public Response<TraceJfrSelection> jfrFilesForTrace(@PathVariable String traceId) {
        RecentTraceStore.TraceSample trace = recentTraceStore.findTrace(traceId);
        if (trace == null || trace.finishedAt() == null) {
            return Response.ok(new TraceJfrSelection(traceId, null, null, List.of()));
        }
        Instant recordedTo = trace.finishedAt().atZone(ZoneId.systemDefault()).toInstant();
        Instant from = recordedTo.minusMillis(Math.max(0, trace.costMs()));
        // Historical JSONL used the API's second-precision formatter. For those records, the real completion
        // may be anywhere in the persisted second, so include the remainder of that second in the JFR window.
        Instant to = trace.finishedAt().getNano() == 0
                ? recordedTo.plusSeconds(1).minusNanos(1)
                : recordedTo;
        List<LocalJfrRecorder.JfrFile> files = jfrRecorder.files().stream()
                .filter(file -> instant(file.recordingStartedAt()) != null && instant(file.recordingEndedAt()) != null)
                .filter(file -> !instant(file.recordingStartedAt()).isAfter(to)
                        && !instant(file.recordingEndedAt()).isBefore(from))
                .toList();
        return Response.ok(new TraceJfrSelection(traceId, from.toString(), to.toString(), files));
    }

    @GetMapping("/jfr/files/{fileName}")
    @EasyPermission(EasyPermissions.Monitor.DIAGNOSIS_JFR_DOWNLOAD)
    @Operation(summary = "下载 JFR 分段", description = "下载本地 JFR 文件并使用 JDK Mission Control 等工具分析。")
    public ResponseEntity<FileSystemResource> downloadJfr(@PathVariable String fileName) {
        var file = jfrRecorder.resolveFile(fileName);
        if (file == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(new FileSystemResource(file));
    }

    @GetMapping("/events")
    @Operation(summary = "运行 Events", description = "显示最近错误、慢请求和事务失败事件，并保留关联 traceId。")
    public Response<List<EventView>> events(@RequestParam(defaultValue = "100") int limit) {
        return Response.ok(recentTraceStore.recentIncidents(clamp(limit, 200)).stream()
                .map(this::incidentEvent).toList());
    }

    @GetMapping("/events/page")
    @Operation(summary = "分页浏览运行 Events", description = "按服务端时间窗口和游标读取事件，避免只在前端已加载数据中过滤。")
    public Response<HistoryPage<EventView>> eventPage(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean traceOnly) {
        int pageSize = clamp(size, 500);
        HistoryCursor cursor = historyCursor(before);
        LocalDateTime from = historyFrom(hours);
        List<EventView> result = new ArrayList<>(eventsInWindow(from, inclusiveBefore(localBefore(cursor)),
                pageFetchLimit(pageSize, cursor), normalizeKeyword(keyword), traceOnly));
        result.sort(Comparator.comparing(EventView::occurredAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return Response.ok(page(result, pageSize, cursor, EventView::occurredAt));
    }

    @GetMapping("/slow")
    @Operation(summary = "慢调用", description = "查看超过慢阈值的 HTTP Trace 和事务样本。")
    public Response<List<SlowView>> slow(@RequestParam(defaultValue = "100") int limit) {
        return Response.ok(recentTraceStore.recentIncidentsMatching(clamp(limit, 200), null, null,
                RecentTraceStore.IncidentSample::slow).stream().map(this::incidentSlow).toList());
    }

    @GetMapping("/slow/page")
    @Operation(summary = "分页浏览慢调用", description = "按服务端时间窗口和游标读取慢请求、长事务和慢 SQL。")
    public Response<HistoryPage<SlowView>> slowPage(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean traceOnly) {
        int pageSize = clamp(size, 500);
        HistoryCursor cursor = historyCursor(before);
        List<SlowView> result = new ArrayList<>(slowInWindow(historyFrom(hours),
                inclusiveBefore(localBefore(cursor)), pageFetchLimit(pageSize, cursor),
                normalizeKeyword(keyword), traceOnly));
        result.sort(Comparator.comparing(SlowView::occurredAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return Response.ok(page(result, pageSize, cursor, SlowView::occurredAt));
    }

    @GetMapping("/errors")
    @Operation(summary = "错误记录", description = "从 canonical incident 统一读取 HTTP、SQL、事务、Remote、Job、Kafka 运行错误。")
    public Response<List<RuntimeErrorView>> errors(@RequestParam(defaultValue = "100") int limit) {
        int safeLimit = clamp(limit, 200);
        return Response.ok(recentTraceStore.recentIncidentsMatching(safeLimit, null, null,
                RecentTraceStore.IncidentSample::error).stream().map(this::incidentError).toList());
    }

    @GetMapping("/errors/page")
    @Operation(summary = "分页浏览运行错误", description = "按服务端时间窗口和游标读取 canonical incident 中的运行错误。")
    public Response<HistoryPage<RuntimeErrorView>> errorPage(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean traceOnly) {
        int pageSize = clamp(size, 500);
        HistoryCursor cursor = historyCursor(before);
        int fetchLimit = pageFetchLimit(pageSize, cursor);
        LocalDateTime boundary = localBefore(cursor);
        LocalDateTime from = historyFrom(hours);
        String normalizedKeyword = normalizeKeyword(keyword);
        List<RuntimeErrorView> result = new ArrayList<>(recentTraceStore.recentIncidentsMatching(fetchLimit, from,
                        inclusiveBefore(boundary), incident -> incident.error()
                                && incidentMatches(incident, normalizedKeyword, traceOnly))
                .stream().map(this::incidentError).toList());
        result.sort(Comparator.comparing(RuntimeErrorView::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return Response.ok(page(result, pageSize, cursor,
                item -> item.createdAt() == null ? null : item.createdAt().toString()));
    }

    @GetMapping("/traces")
    @Operation(summary = "Trace 列表", description = "列出当前实例本地持久化的近期请求 Trace。默认目标保留 30 天，实际覆盖受容量上限约束。")
    public Response<List<RecentTraceStore.TraceSample>> traces(@RequestParam(defaultValue = "100") int limit) {
        return Response.ok(recentTraceStore.recentTraces(clamp(limit, 200)));
    }

    @GetMapping("/traces/page")
    @Operation(summary = "分页浏览 Trace", description = "按服务端时间窗口和游标浏览最长 30 天的 Trace。")
    public Response<HistoryPage<RecentTraceStore.TraceSample>> tracePage(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String keyword) {
        int pageSize = clamp(size, 500);
        HistoryCursor cursor = historyCursor(before);
        String normalizedKeyword = normalizeKeyword(keyword);
        List<RecentTraceStore.TraceSample> result = new ArrayList<>(recentTraceStore.recentTracesMatching(
                pageFetchLimit(pageSize, cursor), historyFrom(hours), inclusiveBefore(localBefore(cursor)),
                trace -> traceMatches(trace, normalizedKeyword)));
        result.sort(Comparator.comparing(RecentTraceStore.TraceSample::finishedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return Response.ok(page(result, pageSize, cursor,
                item -> item.finishedAt() == null ? null : item.finishedAt().toString()));
    }

    @GetMapping("/traces/{traceId}")
    @Operation(summary = "Trace 详情", description = "按 traceId 查看最近请求的调用树。")
    public Response<RecentTraceStore.TraceSample> trace(@PathVariable String traceId) {
        return Response.ok(recentTraceStore.findTrace(traceId));
    }

    @GetMapping("/traces/{traceId}/correlation")
    @Operation(summary = "Trace 关联诊断", description = "一次读取同一 traceId 下的调用树、事务、SQL/操作样本和异常记录。")
    public Response<TraceCorrelationView> traceCorrelation(@PathVariable String traceId) {
        RecentTraceStore.TraceCorrelation correlation = recentTraceStore.findCorrelation(traceId);
        if (correlation.trace() == null) {
            return Response.ok(new TraceCorrelationView(null, List.of(), List.of(), List.of(), List.of(), List.of()));
        }
        List<RecentTraceStore.OperationSample> operations = correlation.operations() == null
                ? List.of() : correlation.operations();
        var query = Wrappers.<AuditErrorLog>lambdaQuery().eq(AuditErrorLog::getTraceId, traceId)
                .orderByAsc(AuditErrorLog::getCreatedAt);
        if (correlation.startedAt() != null) {
            // audit_error_log.created_at is MySQL DATETIME(0). Round the diagnostic window to the same
            // precision, otherwise a row written during the first fractional second is stored just before
            // the precise in-memory start and is incorrectly excluded from the correlation result.
            query.ge(AuditErrorLog::getCreatedAt, correlation.startedAt().withNano(0));
        }
        if (correlation.finishedAt() != null) {
            LocalDateTime finishedAt = correlation.finishedAt();
            LocalDateTime databaseUpperBound = finishedAt.withNano(0);
            if (finishedAt.getNano() != 0) {
                // MySQL rounds fractional seconds when persisting into DATETIME(0), so the inclusive upper
                // bound must also include the next whole second for a fractional in-memory finish time.
                databaseUpperBound = databaseUpperBound.plusSeconds(1);
            }
            query.le(AuditErrorLog::getCreatedAt, databaseUpperBound);
        }
        applyAuditVisibility(query);
        List<RuntimeErrorView> candidates = new ArrayList<>();
        int canonicalOperationIndex = -1;
        if (correlation.incident() != null && correlation.incident().error()) {
            canonicalOperationIndex = matchingCanonicalOperationIndex(correlation.incident(), operations);
            String diagnosticStack = canonicalOperationIndex < 0 ? ""
                    : operations.get(canonicalOperationIndex).stackTrace();
            candidates.add(incidentError(correlation.incident(), diagnosticStack));
        }
        candidates.addAll(errorLogService.list(query).stream()
                .map(AuditErrorLogView::from).map(RuntimeDiagnosisController::auditErrorView).toList());
        // Canonical incident and audit evidence can describe the same exception. Runtime operation samples are
        // different execution nodes, so only suppress the one root sample represented by the incident itself.
        Map<String, RuntimeErrorView> uniqueErrors = new LinkedHashMap<>();
        candidates.forEach(error -> uniqueErrors.putIfAbsent(runtimeErrorKey(error), error));
        List<RuntimeErrorView> errors = new ArrayList<>(uniqueErrors.values());
        errors.addAll(runtimeErrors(operations, canonicalOperationIndex));
        errors.sort(Comparator.comparing(RuntimeErrorView::createdAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return Response.ok(new TraceCorrelationView(correlation.trace(), correlation.transactions(),
                correlation.operations(), errors, correlatedEvents(correlation), correlatedSlow(correlation)));
    }

    private List<EventView> correlatedEvents(RecentTraceStore.TraceCorrelation correlation) {
        if (correlation.incident() != null) {
            return List.of(incidentEvent(correlation.incident()));
        }
        List<EventView> result = new ArrayList<>();
        RecentTraceStore.TraceSample trace = correlation.trace();
        if (trace != null) {
            String type = traceType(trace.type());
            long threshold = traceThreshold(type);
            if (trace.status() >= 400) {
                result.add(new EventView(errorEventType(type), trace.traceId(), trace.name(), trace.method(),
                        trace.status(), trace.costMs(), trace.finishedAt().toString(), type + " 执行失败", 0, threshold,
                        false, true, List.of(errorEventType(type))));
            } else if (trace.costMs() >= threshold) {
                result.add(new EventView(slowEventType(type), trace.traceId(), trace.name(), trace.method(),
                        trace.status(), trace.costMs(), trace.finishedAt().toString(), type + " 超过慢阈值",
                        trace.costMs(), threshold, true, false, List.of(slowEventType(type))));
            }
        }
        correlation.transactions().stream()
                .filter(tx -> !tx.success() || tx.costMs() >= enabledThreshold(transactionSlowThresholdMs))
                .forEach(tx -> result.add(new EventView(tx.success() ? "SLOW_TRANSACTION" : "TRANSACTION_ERROR",
                        tx.traceId(), tx.name(), "", 0, tx.costMs(), tx.finishedAt().toString(),
                        tx.success() ? "事务超过慢阈值" : "事务执行失败", tx.costMs(),
                        enabledThreshold(transactionSlowThresholdMs), tx.success(), !tx.success(),
                        List.of(tx.success() ? "SLOW_TRANSACTION" : "TRANSACTION_ERROR"))));
        correlation.operations().stream().map(this::operationEvent)
                .filter(java.util.Objects::nonNull).forEach(result::add);
        result.sort(Comparator.comparing(EventView::occurredAt));
        return List.copyOf(result);
    }

    private List<SlowView> correlatedSlow(RecentTraceStore.TraceCorrelation correlation) {
        if (correlation.incident() != null) {
            return correlation.incident().slow() ? List.of(incidentSlow(correlation.incident())) : List.of();
        }
        List<SlowView> result = new ArrayList<>();
        RecentTraceStore.TraceSample trace = correlation.trace();
        if (trace != null && trace.costMs() >= traceThreshold(trace.type())) {
            result.add(new SlowView(traceType(trace.type()), trace.traceId(), trace.name(), trace.method(),
                    trace.costMs(), trace.finishedAt().toString(), List.of(slowEventType(traceType(trace.type())))));
        }
        correlation.transactions().stream()
                .filter(tx -> tx.costMs() >= enabledThreshold(transactionSlowThresholdMs))
                .forEach(tx -> result.add(new SlowView("TRANSACTION", tx.traceId(), tx.name(), "", tx.costMs(),
                        tx.finishedAt().toString(), List.of("SLOW_TRANSACTION"))));
        correlation.operations().stream().map(this::operationSlow)
                .filter(java.util.Objects::nonNull).forEach(result::add);
        result.sort(Comparator.comparing(SlowView::occurredAt));
        return List.copyOf(result);
    }

    private List<EventView> eventsInWindow(LocalDateTime from, LocalDateTime before, int limit,
                                           String keyword, boolean traceOnly) {
        return recentTraceStore.recentIncidentsMatching(limit, from, before,
                incident -> incidentMatches(incident, keyword, traceOnly)).stream()
                .map(this::incidentEvent).toList();
    }

    private List<SlowView> slowInWindow(LocalDateTime from, LocalDateTime before, int limit,
                                        String keyword, boolean traceOnly) {
        return recentTraceStore.recentIncidentsMatching(limit, from, before,
                incident -> incident.slow() && incidentMatches(incident, keyword, traceOnly)).stream()
                .map(this::incidentSlow).toList();
    }

    private static boolean incidentMatches(RecentTraceStore.IncidentSample incident,
                                           String keyword, boolean traceOnly) {
        if (incident == null || traceOnly && !hasTraceId(incident.traceId())) return false;
        return containsKeyword(keyword, incident.type(), incident.traceId(), incident.name(), incident.method(),
                incident.status(), incident.reasons(), incident.errorType(), incident.errorMessage(),
                incident.tree(), incident.primaryCause(), incident.slowCause(), incident.errorCause());
    }

    private static boolean traceMatches(RecentTraceStore.TraceSample trace, String keyword) {
        return trace != null && containsKeyword(keyword, trace.type(), trace.traceId(), trace.name(),
                trace.method(), trace.status(), trace.tree());
    }

    private static boolean operationMatches(RecentTraceStore.OperationSample operation, String keyword) {
        return operation != null && containsKeyword(keyword, operation.type(), operation.traceId(),
                operation.name(), operation.threadName(), operation.outcome(), operation.detail(),
                operation.lockInfo(), operation.stackTrace(), operation.childSummaries());
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return "";
        String normalized = keyword.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.length() <= 200 ? normalized : normalized.substring(0, 200);
    }

    private static boolean containsKeyword(String keyword, Object... values) {
        if (keyword == null || keyword.isBlank()) return true;
        for (Object value : values) {
            if (value != null && String.valueOf(value).toLowerCase(java.util.Locale.ROOT).contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTraceId(String traceId) {
        return traceId != null && !traceId.isBlank();
    }

    Response<HistoryPage<RecentTraceStore.OperationSample>> operationPage(int hours, String before, int size) {
        return operationPage(hours, before, size, null);
    }

    Response<HistoryPage<EventView>> eventPage(int hours, String before, int size) {
        return eventPage(hours, before, size, null, false);
    }

    Response<HistoryPage<SlowView>> slowPage(int hours, String before, int size) {
        return slowPage(hours, before, size, null, false);
    }

    Response<HistoryPage<RuntimeErrorView>> errorPage(int hours, String before, int size) {
        return errorPage(hours, before, size, null, false);
    }

    Response<HistoryPage<RecentTraceStore.TraceSample>> tracePage(int hours, String before, int size) {
        return tracePage(hours, before, size, null);
    }

    private LocalDateTime historyFrom(int hours) {
        return LocalDateTime.now().minusHours(historyHours(hours));
    }

    private int historyHours(int hours) {
        return Math.max(1, Math.min(hours, recentTraceStore.retentionDays() * 24));
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); }
        catch (RuntimeException ignored) { return null; }
    }

    private static <T> HistoryPage<T> page(List<T> candidates, int pageSize, HistoryCursor cursor,
                                           Function<T, String> timestamp) {
        List<T> remaining = new ArrayList<>(candidates.size());
        int skipAtBoundary = cursor == null ? 0 : cursor.offset();
        for (T candidate : candidates) {
            String occurredAt = timestamp.apply(candidate);
            if (cursor != null && cursor.timestamp().equals(occurredAt) && skipAtBoundary > 0) {
                skipAtBoundary--;
                continue;
            }
            remaining.add(candidate);
        }
        boolean hasMore = remaining.size() > pageSize;
        List<T> items = remaining.stream().limit(pageSize).toList();
        if (!hasMore || items.isEmpty()) return new HistoryPage<>(items, false, null);
        String lastTimestamp = timestamp.apply(items.get(items.size() - 1));
        int priorAtTimestamp = cursor != null && cursor.timestamp().equals(lastTimestamp) ? cursor.offset() : 0;
        int returnedAtTimestamp = (int) items.stream()
                .filter(item -> java.util.Objects.equals(lastTimestamp, timestamp.apply(item))).count();
        String next = lastTimestamp == null ? null : lastTimestamp + '|' + (priorAtTimestamp + returnedAtTimestamp);
        return new HistoryPage<>(items, next != null, next);
    }

    private static HistoryCursor historyCursor(String value) {
        if (value == null || value.isBlank()) return null;
        int separator = value.lastIndexOf('|');
        if (separator <= 0 || separator == value.length() - 1) return new HistoryCursor(value, 0);
        try {
            int offset = Math.max(0, Math.min(100_000, Integer.parseInt(value.substring(separator + 1))));
            return new HistoryCursor(value.substring(0, separator), offset);
        } catch (NumberFormatException ignored) {
            return new HistoryCursor(value, 0);
        }
    }

    private static int pageFetchLimit(int pageSize, HistoryCursor cursor) {
        return Math.min(100_001, pageSize + (cursor == null ? 0 : cursor.offset()) + 1);
    }

    private static LocalDateTime localBefore(HistoryCursor cursor) {
        if (cursor == null) return null;
        try { return LocalDateTime.parse(cursor.timestamp()); }
        catch (RuntimeException ignored) { return null; }
    }

    private static LocalDateTime inclusiveBefore(LocalDateTime value) {
        if (value == null) return null;
        try { return value.plusNanos(1); }
        catch (RuntimeException ignored) { return value; }
    }

    private static Instant instantBefore(HistoryCursor cursor) {
        return cursor == null ? null : instant(cursor.timestamp());
    }

    private EventView operationEvent(RecentTraceStore.OperationSample operation) {
        if (!isOperationOnlyType(operation.type())) return null;
        long threshold = operationThreshold(operation.type());
        boolean failed = !"success".equals(operation.outcome());
        if (!failed && threshold == Long.MAX_VALUE) return null;
        if (!failed && operation.durationMs() < threshold) return null;
        String type = traceType(operation.type());
        return new EventView(failed ? type + "_ERROR" : "SLOW_" + type,
                operation.traceId(), operation.name(), operation.detail(), failed ? 500 : 200,
                operation.durationMs(), operation.finishedAt().toString(),
                failed ? type + " 执行失败" : type + " 超过慢阈值",
                failed ? 0 : operation.durationMs(), failed ? 0 : threshold,
                !failed, failed, List.of(failed ? type + "_ERROR" : "SLOW_" + type));
    }

    private SlowView operationSlow(RecentTraceStore.OperationSample operation) {
        long threshold = operationThreshold(operation.type());
        if (threshold == Long.MAX_VALUE || !"success".equals(operation.outcome())
                || operation.durationMs() < threshold) return null;
        return new SlowView(traceType(operation.type()), operation.traceId(), operation.name(), operation.detail(),
                operation.durationMs(), operation.finishedAt().toString(),
                List.of("SLOW_" + traceType(operation.type())));
    }

    private EventView incidentEvent(RecentTraceStore.IncidentSample incident) {
        RecentTraceStore.IncidentCause cause = incidentPrimaryCause(incident);
        String type = cause.reason().isBlank()
                ? cause.error() ? errorEventType(traceType(cause.type())) : slowEventType(traceType(cause.type()))
                : cause.reason();
        String detail = incident.reasons().isEmpty()
                ? traceType(incident.type()) + (incident.error() ? " 执行失败" : " 超过慢阈值")
                : String.join("、", incident.reasons());
        return new EventView(type, incident.traceId(), cause.name(), cause.detail(), incident.status(),
                cause.durationMs(), timestamp(incident.finishedAt()), detail,
                cause.slow() ? cause.durationMs() : 0, cause.slow() ? cause.thresholdMs() : 0,
                incident.slow(), incident.error(), incident.reasons());
    }

    private SlowView incidentSlow(RecentTraceStore.IncidentSample incident) {
        RecentTraceStore.IncidentCause cause = incidentSlowCause(incident);
        return new SlowView(traceType(cause.type()), incident.traceId(), cause.name(), cause.detail(),
                cause.durationMs(), timestamp(incident.finishedAt()), incident.reasons());
    }

    private RuntimeErrorView incidentError(RecentTraceStore.IncidentSample incident) {
        return incidentError(incident, "");
    }

    private RuntimeErrorView incidentError(RecentTraceStore.IncidentSample incident, String diagnosticStack) {
        RecentTraceStore.IncidentCause cause = incidentErrorCause(incident);
        String identity = incident.traceId() + '|' + cause.type() + '|' + cause.name() + '|'
                + incident.finishedAt();
        String errorType = incident.errorType().isBlank() ? errorEventType(traceType(cause.type()))
                : incident.errorType();
        String message = incident.errorMessage().isBlank()
                ? traceType(cause.type()) + " 执行失败" : incident.errorMessage();
        String requestUri = cause.detail().isBlank() ? incident.method() : cause.detail();
        String stackTrace = incident.stackTrace().isBlank()
                ? diagnosticStack == null ? "" : diagnosticStack
                : incident.stackTrace();
        return new RuntimeErrorView("INCIDENT-" + Integer.toUnsignedString(identity.hashCode()),
                "INCIDENT", incident.traceId(), requestUri, traceType(cause.type()), errorType,
                message, stackTrace, incident.finishedAt());
    }

    private RecentTraceStore.IncidentCause incidentPrimaryCause(RecentTraceStore.IncidentSample incident) {
        if (incident.primaryCause() != null) return incident.primaryCause();
        if (incident.error()) return incidentErrorCause(incident);
        return incidentSlowCause(incident);
    }

    private RecentTraceStore.IncidentCause incidentSlowCause(RecentTraceStore.IncidentSample incident) {
        if (incident.slowCause() != null) return incident.slowCause();
        String reason = incident.reasons().stream().filter(item -> item.startsWith("SLOW_")
                        || "LONG_TRANSACTION".equals(item))
                .findFirst().orElse(slowEventType(traceType(incident.type())));
        String type = causeType(reason, incident.type());
        return new RecentTraceStore.IncidentCause(type, incident.name(), incident.method(), incident.costMs(),
                legacyIncidentThreshold(incident), true, incident.error(), reason);
    }

    private RecentTraceStore.IncidentCause incidentErrorCause(RecentTraceStore.IncidentSample incident) {
        if (incident.errorCause() != null) return incident.errorCause();
        String reason = incident.reasons().stream()
                .filter(item -> item.endsWith("_ERROR") || "ERROR".equals(item))
                .findFirst().orElse(errorEventType(traceType(incident.type())));
        return new RecentTraceStore.IncidentCause(causeType(reason, incident.type()), incident.name(),
                incident.method(), incident.costMs(), 0, incident.slow(), true, reason);
    }

    private long legacyIncidentThreshold(RecentTraceStore.IncidentSample incident) {
        if (incident.reasons().contains("LONG_TRANSACTION")) return enabledThreshold(transactionSlowThresholdMs);
        if (incident.reasons().contains("SLOW_SQL")) return enabledThreshold(sqlSlowThresholdMs);
        if (incident.reasons().contains("SLOW_REMOTE")) return enabledThreshold(remoteSlowThresholdMs);
        return traceThreshold(incident.type());
    }

    private static String causeType(String reason, String fallbackType) {
        if ("LONG_TRANSACTION".equals(reason)) return "TRANSACTION";
        if ("SLOW_REQUEST".equals(reason) || "ERROR".equals(reason) || "HTTP_ERROR".equals(reason)) return "HTTP";
        if (reason != null && reason.startsWith("SLOW_") && reason.length() > "SLOW_".length()) {
            return reason.substring("SLOW_".length());
        }
        if (reason != null && reason.endsWith("_ERROR") && reason.length() > "_ERROR".length()) {
            return reason.substring(0, reason.length() - "_ERROR".length());
        }
        return traceType(fallbackType);
    }

    private static String timestamp(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }

    private long operationThreshold(String type) {
        return switch (traceType(type)) {
            case "SQL" -> enabledThreshold(sqlSlowThresholdMs);
            case "REMOTE" -> enabledThreshold(remoteSlowThresholdMs);
            default -> Long.MAX_VALUE;
        };
    }

    private static boolean isOperationOnlyType(String type) {
        String normalized = traceType(type);
        return "SQL".equals(normalized) || "REMOTE".equals(normalized);
    }

    private int matchingCanonicalOperationIndex(RecentTraceStore.IncidentSample incident,
                                                List<RecentTraceStore.OperationSample> operations) {
        // Prefer the persisted root identity. This removes the HTTP/Job/Kafka envelope represented by the incident
        // while retaining a distinct failed SQL, transaction or remote child for drill-down.
        int root = matchingOperationIndex(operations, incident.type(), incident.name(),
                incident.costMs(), incident.finishedAt());
        if (root >= 0) return root;
        RecentTraceStore.IncidentCause cause = incidentErrorCause(incident);
        return matchingOperationIndex(operations, cause.type(), cause.name(),
                cause.durationMs(), incident.finishedAt());
    }

    private static int matchingOperationIndex(List<RecentTraceStore.OperationSample> operations,
                                              String type, String name, long durationMs,
                                              LocalDateTime finishedAt) {
        if (name == null || name.isBlank()) return -1;
        int bestIndex = -1;
        long bestDurationDistance = Long.MAX_VALUE;
        long bestTimeDistance = Long.MAX_VALUE;
        for (int index = 0; index < operations.size(); index++) {
            RecentTraceStore.OperationSample operation = operations.get(index);
            if ("success".equals(operation.outcome())
                    || !traceType(operation.type()).equalsIgnoreCase(traceType(type))
                    || !name.equals(operation.name())) continue;
            long durationDistance = Math.abs(operation.durationMs() - Math.max(0, durationMs));
            long timeDistance = timeDistanceMillis(operation.finishedAt(), finishedAt);
            if (durationDistance < bestDurationDistance
                    || (durationDistance == bestDurationDistance && timeDistance < bestTimeDistance)) {
                bestIndex = index;
                bestDurationDistance = durationDistance;
                bestTimeDistance = timeDistance;
            }
        }
        return bestIndex;
    }

    private static long timeDistanceMillis(LocalDateTime left, LocalDateTime right) {
        if (left == null || right == null) return Long.MAX_VALUE;
        try {
            return Math.abs(Duration.between(left, right).toMillis());
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static List<RuntimeErrorView> runtimeErrors(List<RecentTraceStore.OperationSample> operations,
                                                        int suppressedIndex) {
        List<RuntimeErrorView> errors = new ArrayList<>();
        for (int index = 0; index < operations.size(); index++) {
            RecentTraceStore.OperationSample operation = operations.get(index);
            if (index == suppressedIndex || "success".equals(operation.outcome())) continue;
            String headline = firstLine(operation.stackTrace());
            int separator = headline.indexOf(':');
            String errorType = separator > 0 ? headline.substring(0, separator).trim()
                    : operation.type() + "_ERROR";
            String errorMessage = separator > 0 ? headline.substring(separator + 1).trim()
                    : operation.detail() == null || operation.detail().isBlank()
                    ? operation.name() + " 执行失败" : operation.detail();
            String requestUri = operation.detail() == null || operation.detail().isBlank()
                    ? operation.name() : operation.detail();
            String identity = operation.type() + '|' + operation.traceId() + '|' + operation.name()
                    + '|' + operation.finishedAt();
            errors.add(new RuntimeErrorView("RUNTIME-" + Integer.toUnsignedString(identity.hashCode()),
                    "RUNTIME", operation.traceId(), requestUri, operation.type(), errorType, errorMessage,
                    operation.stackTrace(), operation.finishedAt()));
        }
        return List.copyOf(errors);
    }

    private static RuntimeErrorView auditErrorView(AuditErrorLogView error) {
        return new RuntimeErrorView("AUDIT-" + error.getId(), "AUDIT", error.getTraceId(), error.getRequestUri(),
                error.getRequestMethod(), error.getErrorType(), error.getErrorMessage(), error.getStackTrace(),
                error.getCreatedAt());
    }

    private static String runtimeErrorKey(RuntimeErrorView error) {
        String stackHeadline = firstLine(error.stackTrace());
        String signature = stackHeadline.isBlank()
                ? String.valueOf(error.errorType()).trim() + '|' + String.valueOf(error.errorMessage()).trim()
                : stackHeadline;
        return String.valueOf(error.traceId()).trim() + '|' + signature;
    }

    private static String firstLine(String value) {
        if (value == null || value.isBlank()) return "";
        int newline = value.indexOf('\n');
        return (newline < 0 ? value : value.substring(0, newline)).trim();
    }

    private long traceThreshold(String type) {
        return enabledThreshold(switch (traceType(type)) {
            case "JOB" -> scheduleSlowThresholdMs;
            case "KAFKA" -> kafkaSlowThresholdMs;
            default -> slowThresholdMs;
        });
    }

    private static long enabledThreshold(long threshold) { return threshold <= 0 ? Long.MAX_VALUE : threshold; }
    private static String traceType(String type) { return type == null || type.isBlank() ? "HTTP" : type; }
    private static String errorEventType(String type) { return "HTTP".equals(type) ? "ERROR" : type + "_ERROR"; }
    private static String slowEventType(String type) { return "HTTP".equals(type) ? "SLOW_REQUEST" : "SLOW_" + type; }

    private void applyAuditVisibility(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuditErrorLog> query) {
        auditVisibilitySupport.visibleUserIds().ifPresent(ids -> {
            if (ids.isEmpty()) query.apply("1 = 0");
            else query.in(AuditErrorLog::getOperatorId, ids);
        });
    }

    private static boolean isUsefulMetric(String name) {
        return name.startsWith("easy.") || name.startsWith("jvm.") || name.startsWith("process.")
                || name.startsWith("system.") || name.startsWith("http.server.requests")
                || name.startsWith("hikaricp.") || name.startsWith("jdbc.connections.")
                || name.startsWith("tomcat.threads.") || name.startsWith("executor.");
    }

    private static int metricPriority(String name) {
        if (name.startsWith("jvm.") || name.startsWith("process.") || name.startsWith("system.")) return 0;
        if (name.startsWith("hikaricp.") || name.startsWith("jdbc.connections.")
                || name.startsWith("tomcat.") || name.startsWith("executor.")) return 1;
        if (name.startsWith("easy.")) return 2;
        return 3;
    }

    private static MetricView metricView(Meter meter) {
        Map<String, String> tags = new LinkedHashMap<>();
        meter.getId().getTags().forEach(tag -> tags.put(tag.getKey(), tag.getValue()));
        Map<String, Double> measurements = new LinkedHashMap<>();
        meter.measure().forEach(measurement -> measurements.put(measurement.getStatistic().name(), measurement.getValue()));
        return new MetricView(meter.getId().getName(), meter.getId().getType().name(),
                meter.getId().getBaseUnit(), meter.getId().getDescription(), tags, measurements);
    }

    private static MetricView historicalMetricView(LocalMetricTimeSeriesStore.MetricSeriesDescriptor series) {
        return new MetricView(series.name(), "RRD4J_HISTORY", null,
                "本地 RRD4J 历史系列；当前进程尚未产生新样本", series.tags(), Map.of());
    }

    private static String metricSeriesKey(String name, Map<String, String> tags) {
        return name + "|" + new java.util.TreeMap<>(tags == null ? Map.of() : tags);
    }

    private static int clamp(int value, int max) { return Math.max(1, Math.min(value, max)); }

    public record MetricView(String name, String type, String baseUnit, String description,
                             Map<String, String> tags, Map<String, Double> measurements) { }
    public record EventView(String type, String traceId, String name, String method, int status,
                           long costMs, String occurredAt, String detail, double value, double threshold,
                           boolean slow, boolean error, List<String> reasons) { }
    public record SlowView(String type, String traceId, String name, String method, long costMs, String occurredAt,
                           List<String> reasons) { }
    public record DiagnosisSummary(String status, String sampledAt, int windowMinutes, long activeTotal,
                                   Map<String, Long> activeByType, long traces, long slow, long errors,
                                   int retentionDays, long historyBytes, int jfrFiles, long jfrBytes,
                                   Map<String, Long> thresholdsMs, LocalJfrRecorder.RecorderStatus jfr,
                                   StorageStatus storage,
                                   LocalMetricTimeSeriesStore.StorageStatus metricsStorage,
                                   boolean summaryTruncated, int summarySampleLimit) { }
    public record StorageStatus(String oldestSegmentDate, long coverageDays, int retentionDays,
                                long historyBytes, long maxHistoryBytes, long pendingWrites, int queueCapacity,
                                long pendingBytes, long pendingMaxBytes, long droppedWrites,
                                long byteBudgetDroppedWrites, boolean capacityPressure) { }
    public record RuntimeErrorView(String id, String source, String traceId, String requestUri,
                                   String requestMethod, String errorType, String errorMessage, String stackTrace,
                                   LocalDateTime createdAt) { }
    public record HistoryPage<T>(List<T> items, boolean hasMore, String nextBefore) { }
    private record HistoryCursor(String timestamp, int offset) { }
    public record TraceCorrelationView(RecentTraceStore.TraceSample trace,
                                       List<RecentTraceStore.TransactionSample> transactions,
                                       List<RecentTraceStore.OperationSample> operations,
                                       List<RuntimeErrorView> errors, List<EventView> events,
                                       List<SlowView> slow) { }
    public record TraceJfrSelection(String traceId, String from, String to,
                                    List<LocalJfrRecorder.JfrFile> files) { }
}
