package com.laker.admin.module.monitor.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.jfr.ActiveOperationRegistry;
import com.laker.admin.infrastructure.observability.jfr.LocalJfrRecorder;
import com.laker.observability.metrics.history.MetricHistoryStore;
import com.laker.observability.metrics.history.MetricHistoryPoint;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.audit.entity.AuditErrorLog;
import com.laker.admin.module.audit.service.IAuditErrorLogService;
import com.laker.admin.module.audit.support.AuditVisibilitySupport;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RuntimeDiagnosisControllerTest {

    @Test
    void rawJfrDownloadShouldUseItsOwnPermission() throws Exception {
        EasyPermission permission = RuntimeDiagnosisController.class
                .getMethod("downloadJfr", String.class).getAnnotation(EasyPermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactly(EasyPermissions.Monitor.DIAGNOSIS_JFR_DOWNLOAD);
    }

    @Test
    void eventsSlowAndErrorsShouldProjectTheSameSlowErrorIncidentOnce() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        RuntimeDiagnosisController controller = controller(traceStore);
        RecentTraceStore.IncidentSample incident = new RecentTraceStore.IncidentSample(
                "HTTP", "trace-both", "Demo#run", "GET /demo", 500, "tree", 4_000,
                true, true, List.of("SLOW_REQUEST", "HTTP_ERROR"), "IllegalStateException",
                "failed", "stack", LocalDateTime.of(2026, 10, 14, 12, 0));
        when(traceStore.recentIncidents(100)).thenReturn(List.of(incident));
        when(traceStore.recentIncidentsMatching(eq(100), isNull(), isNull(), any()))
                .thenReturn(List.of(incident));

        assertThat(controller.events(100).getData()).singleElement().satisfies(event -> {
            assertThat(event.traceId()).isEqualTo("trace-both");
            assertThat(event.slow()).isTrue();
            assertThat(event.error()).isTrue();
            assertThat(event.reasons()).containsExactly("SLOW_REQUEST", "HTTP_ERROR");
        });
        assertThat(controller.slow(100).getData()).singleElement()
                .satisfies(item -> assertThat(item.traceId()).isEqualTo("trace-both"));
        assertThat(controller.errors(100).getData()).singleElement()
                .satisfies(item -> assertThat(item.traceId()).isEqualTo("trace-both"));
    }

    @Test
    void incidentViewsShouldUsePersistedChildCauseInsteadOfRootHttpFields() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        RuntimeDiagnosisController controller = controller(traceStore);
        RecentTraceStore.IncidentCause slowCause = new RecentTraceStore.IncidentCause(
                "TRANSACTION", "OrderService#create", "write", 3_800, 1_000,
                true, false, "LONG_TRANSACTION");
        RecentTraceStore.IncidentSample incident = new RecentTraceStore.IncidentSample(
                "HTTP", "trace-child-cause", "OrderController#create", "POST /orders", 200, "tree", 4_000,
                true, false, List.of("LONG_TRANSACTION"), "", "", "", slowCause, slowCause, null,
                LocalDateTime.of(2026, 10, 14, 12, 0));
        when(traceStore.recentIncidents(100)).thenReturn(List.of(incident));
        when(traceStore.recentIncidentsMatching(eq(100), isNull(), isNull(), any()))
                .thenReturn(List.of(incident));

        assertThat(controller.events(100).getData()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo("LONG_TRANSACTION");
            assertThat(event.name()).isEqualTo("OrderService#create");
            assertThat(event.method()).isEqualTo("write");
            assertThat(event.costMs()).isEqualTo(3_800);
            assertThat(event.value()).isEqualTo(3_800);
            assertThat(event.threshold()).isEqualTo(1_000);
        });
        assertThat(controller.slow(100).getData()).singleElement().satisfies(item -> {
            assertThat(item.type()).isEqualTo("TRANSACTION");
            assertThat(item.name()).isEqualTo("OrderService#create");
            assertThat(item.method()).isEqualTo("write");
            assertThat(item.costMs()).isEqualTo(3_800);
        });
    }

    @Test
    void legacyRemoteSlowIncidentShouldUseRemoteThreshold() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setHttpSlowThresholdMs(111);
        config.getTrace().setRemoteSlowThresholdMs(222);
        RuntimeDiagnosisController controller = controller(traceStore, mock(LocalJfrRecorder.class), config);
        RecentTraceStore.IncidentSample incident = new RecentTraceStore.IncidentSample(
                "REMOTE", "trace-remote", "InventoryClient#load", "outbound", 200, "tree", 333,
                true, false, List.of("SLOW_REMOTE"), "", "", "",
                LocalDateTime.of(2026, 10, 14, 12, 0));
        when(traceStore.recentIncidents(100)).thenReturn(List.of(incident));

        assertThat(controller.events(100).getData()).singleElement()
                .satisfies(event -> assertThat(event.threshold()).isEqualTo(222));
    }

    @Test
    void metricsShouldExposePersistedRrdSeriesAfterRestartBeforeMeterReregistration() {
        MetricHistoryStore metricStore = mock(MetricHistoryStore.class);
        when(metricStore.seriesCatalog()).thenReturn(List.of(
                new MetricHistoryStore.MetricSeriesDescriptor(
                        "http.server.requests", java.util.Map.of("uri", "/retired"), List.of("COUNT"))));
        RuntimeDiagnosisController controller = controller(mock(RecentTraceStore.class),
                mock(LocalJfrRecorder.class), new EasyNextAdminConfig(), new SimpleMeterRegistry(), metricStore);

        assertThat(controller.metrics(100).getData()).singleElement().satisfies(metric -> {
            assertThat(metric.name()).isEqualTo("http.server.requests");
            assertThat(metric.type()).isEqualTo("RRD4J_HISTORY");
            assertThat(metric.tags()).containsEntry("uri", "/retired");
        });
    }

    @Test
    void metricHistoryShouldNotRescanLegacyJsonlWhenRrdHasTheSeries() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        MetricHistoryStore metricStore = mock(MetricHistoryStore.class);
        MetricHistoryPoint point = new MetricHistoryPoint(
                "2026-10-15T12:00:00", Map.of("area", "heap"), Map.of("VALUE", 42D));
        when(metricStore.metricHistory("jvm.memory.used", Map.of("area", "heap"), 100))
                .thenReturn(List.of(point));
        RuntimeDiagnosisController controller = controller(traceStore, mock(LocalJfrRecorder.class),
                new EasyNextAdminConfig(), new SimpleMeterRegistry(), metricStore);

        List<MetricHistoryPoint> result = controller.metricHistory(
                "jvm.memory.used", "{\"area\":\"heap\"}", 100).getData();

        assertThat(result).containsExactly(point);
        verifyNoInteractions(traceStore);
    }

    @Test
    void errorViewShouldUsePersistedSqlCause() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        RuntimeDiagnosisController controller = controller(traceStore);
        RecentTraceStore.IncidentCause errorCause = new RecentTraceStore.IncidentCause(
                "SQL", "OrderMapper#insert", "INSERT orders", 120, 500,
                false, true, "SQL_ERROR");
        RecentTraceStore.IncidentSample incident = new RecentTraceStore.IncidentSample(
                "HTTP", "trace-sql-error", "OrderController#create", "POST /orders", 500, "tree", 150,
                false, true, List.of("SQL_ERROR"), "java.sql.SQLException", "duplicate key", "stack",
                errorCause, null, errorCause, LocalDateTime.of(2026, 10, 14, 12, 0));
        when(traceStore.recentIncidentsMatching(eq(100), isNull(), isNull(), any()))
                .thenReturn(List.of(incident));

        assertThat(controller.errors(100).getData()).singleElement().satisfies(error -> {
            assertThat(error.requestUri()).isEqualTo("INSERT orders");
            assertThat(error.requestMethod()).isEqualTo("SQL");
            assertThat(error.errorType()).isEqualTo("java.sql.SQLException");
        });
    }

    @Test
    void traceCorrelationShouldPreferCanonicalIncidentOverDuplicateRuntimeError() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        RuntimeDiagnosisController controller = controller(traceStore);
        LocalDateTime finishedAt = LocalDateTime.of(2026, 10, 14, 12, 0);
        String stackTrace = "java.lang.IllegalStateException: failed\n\tat Demo.run(Demo.java:1)";
        RecentTraceStore.IncidentCause errorCause = new RecentTraceStore.IncidentCause(
                "HTTP", "DemoController#run", "GET /demo", 100, 0,
                false, true, "ERROR");
        RecentTraceStore.IncidentSample incident = new RecentTraceStore.IncidentSample(
                "HTTP", "trace-duplicate", "DemoController#run", "GET /demo", 500, "tree", 100,
                false, true, List.of("ERROR"), "java.lang.IllegalStateException", "failed", stackTrace,
                errorCause, null, errorCause, finishedAt);
        RecentTraceStore.OperationSample operation = new RecentTraceStore.OperationSample(
                "HTTP", "trace-duplicate", "DemoController#run", "http-thread", "error",
                100, 10, 1_024, 0, 0, 0, 0, "GET /demo", "", stackTrace, finishedAt);
        when(traceStore.findCorrelation("trace-duplicate")).thenReturn(new RecentTraceStore.TraceCorrelation(
                incident.trace(), List.of(), List.of(operation), incident));

        RuntimeDiagnosisController.TraceCorrelationView result =
                controller.traceCorrelation("trace-duplicate").getData();

        assertThat(result.errors()).singleElement().satisfies(error -> {
            assertThat(error.source()).isEqualTo("INCIDENT");
            assertThat(error.errorType()).isEqualTo("java.lang.IllegalStateException");
            assertThat(error.errorMessage()).isEqualTo("failed");
        });
    }

    @Test
    void traceCorrelationShouldEnrichBlankIncidentStackAndKeepNestedFailure() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        RuntimeDiagnosisController controller = controller(traceStore);
        LocalDateTime finishedAt = LocalDateTime.of(2026, 10, 15, 9, 30, 0, 123_000_000);
        String diagnosticStack = "http-nio-8080-exec-7 state=RUNNABLE\n"
                + "\tat com.laker.admin.module.demo.DemoController.missing(DemoController.java:42)";
        RecentTraceStore.IncidentCause errorCause = new RecentTraceStore.IncidentCause(
                "HTTP", "DemoController#missing", "GET /api/missing", 32, 0,
                false, true, "HTTP_ERROR");
        RecentTraceStore.IncidentSample incident = new RecentTraceStore.IncidentSample(
                "HTTP", "trace-http-404", "DemoController#missing", "GET /api/missing", 404, "tree", 32,
                false, true, List.of("HTTP_ERROR"), "HTTP_ERROR", "HTTP 执行失败", "",
                errorCause, null, errorCause, finishedAt);
        RecentTraceStore.OperationSample nestedSql = new RecentTraceStore.OperationSample(
                "SQL", "trace-http-404", "DemoMapper#selectMissing", "http-nio-8080-exec-7", "failure",
                8, 2, 256, 0, 0, 0, 0, "SELECT missing", "",
                "java.sql.SQLException: nested lookup failed\n\tat DemoMapper.selectMissing(DemoMapper.java:11)",
                finishedAt.minusNanos(2_000_000));
        RecentTraceStore.OperationSample rootHttp = new RecentTraceStore.OperationSample(
                "HTTP", "trace-http-404", "DemoController#missing", "http-nio-8080-exec-7", "failure",
                32, 5, 1_024, 0, 0, 0, 0, "GET /api/missing", "owned=0 waiting=-",
                diagnosticStack, finishedAt);
        when(traceStore.findCorrelation("trace-http-404")).thenReturn(new RecentTraceStore.TraceCorrelation(
                incident.trace(), List.of(), List.of(nestedSql, rootHttp), incident));

        RuntimeDiagnosisController.TraceCorrelationView result =
                controller.traceCorrelation("trace-http-404").getData();

        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors()).filteredOn(error -> "INCIDENT".equals(error.source()))
                .singleElement().satisfies(error -> {
                    assertThat(error.errorType()).isEqualTo("HTTP_ERROR");
                    assertThat(error.stackTrace()).isEqualTo(diagnosticStack);
                });
        assertThat(result.errors()).filteredOn(error -> "RUNTIME".equals(error.source()))
                .singleElement().satisfies(error -> {
                    assertThat(error.requestMethod()).isEqualTo("SQL");
                    assertThat(error.requestUri()).isEqualTo("SELECT missing");
                    assertThat(error.errorType()).isEqualTo("java.sql.SQLException");
                });
    }

    @Test
    void traceCorrelationShouldBoundAuditErrorsToSelectedRootWindow() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), AuditErrorLog.class);
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        IAuditErrorLogService errorLogService = mock(IAuditErrorLogService.class);
        AuditVisibilitySupport visibilitySupport = mock(AuditVisibilitySupport.class);
        LocalDateTime finishedAt = LocalDateTime.of(2026, 10, 15, 9, 30, 10, 987_654_321);
        RecentTraceStore.TraceSample trace = new RecentTraceStore.TraceSample(
                "HTTP", "client-reused-trace", "DemoController#run", "GET /demo", 500,
                "tree", 1_500, finishedAt);
        when(traceStore.findCorrelation("client-reused-trace"))
                .thenReturn(new RecentTraceStore.TraceCorrelation(trace, List.of(), List.of(), null));
        when(errorLogService.list(org.mockito.ArgumentMatchers.<Wrapper<AuditErrorLog>>any()))
                .thenReturn(List.of());
        when(visibilitySupport.visibleUserIds()).thenReturn(Optional.empty());
        RuntimeDiagnosisController controller = new RuntimeDiagnosisController(
                mock(MeterRegistry.class), traceStore, mock(MetricHistoryStore.class), errorLogService,
                visibilitySupport, new EasyNextAdminConfig(), mock(LocalJfrRecorder.class),
                mock(ActiveOperationRegistry.class), new ObjectMapper());

        controller.traceCorrelation("client-reused-trace");

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Wrapper<AuditErrorLog>> queryCaptor = (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
        verify(errorLogService).list(queryCaptor.capture());
        @SuppressWarnings("unchecked")
        LambdaQueryWrapper<AuditErrorLog> query = (LambdaQueryWrapper<AuditErrorLog>) queryCaptor.getValue();
        // eq(traceId), ge(rootStart) and le(rootEnd) each add a condition triple.
        assertThat(query.getExpression().getNormal().size()).isGreaterThanOrEqualTo(9);
        query.getSqlSegment();
        assertThat(query.getParamNameValuePairs().values()).contains(
                LocalDateTime.of(2026, 10, 15, 9, 30, 9),
                LocalDateTime.of(2026, 10, 15, 9, 30, 11));
    }

    @Test
    void traceCorrelationShouldNotQueryAuditHistoryWithoutARootTrace() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        IAuditErrorLogService errorLogService = mock(IAuditErrorLogService.class);
        when(traceStore.findCorrelation("missing-root")).thenReturn(new RecentTraceStore.TraceCorrelation(
                null, List.of(new RecentTraceStore.TransactionSample(
                        "missing-root", "orphan", false, 1, LocalDateTime.now())), List.of(), null));
        RuntimeDiagnosisController controller = new RuntimeDiagnosisController(
                mock(MeterRegistry.class), traceStore, mock(MetricHistoryStore.class), errorLogService,
                mock(AuditVisibilitySupport.class), new EasyNextAdminConfig(), mock(LocalJfrRecorder.class),
                mock(ActiveOperationRegistry.class), new ObjectMapper());

        RuntimeDiagnosisController.TraceCorrelationView result =
                controller.traceCorrelation("missing-root").getData();

        assertThat(result.trace()).isNull();
        assertThat(result.transactions()).isEmpty();
        assertThat(result.operations()).isEmpty();
        assertThat(result.errors()).isEmpty();
        verifyNoInteractions(errorLogService);
    }

    @Test
    void eventPageShouldSortOutOfOrderIncidentsWithoutDuplicateOrLoss() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        RuntimeDiagnosisController controller = controller(traceStore);
        List<RecentTraceStore.IncidentSample> physicalOrder = outOfOrderIncidents();
        when(traceStore.retentionDays()).thenReturn(30);
        when(traceStore.recentIncidentsMatching(anyInt(), any(LocalDateTime.class),
                nullable(LocalDateTime.class), any()))
                .thenAnswer(invocation -> incidentQuery(physicalOrder, invocation.getArgument(0),
                        invocation.getArgument(1), invocation.getArgument(2)));

        RuntimeDiagnosisController.HistoryPage<RuntimeDiagnosisController.EventView> first =
                controller.eventPage(24, null, 2).getData();
        RuntimeDiagnosisController.HistoryPage<RuntimeDiagnosisController.EventView> second =
                controller.eventPage(24, first.nextBefore(), 2).getData();
        RuntimeDiagnosisController.HistoryPage<RuntimeDiagnosisController.EventView> third =
                controller.eventPage(24, second.nextBefore(), 2).getData();

        assertThat(first.items()).extracting(RuntimeDiagnosisController.EventView::traceId)
                .containsExactly("trace-newest", "trace-tie-1");
        assertThat(second.items()).extracting(RuntimeDiagnosisController.EventView::traceId)
                .containsExactly("trace-tie-2", "trace-tie-3");
        assertThat(third.items()).extracting(RuntimeDiagnosisController.EventView::traceId)
                .containsExactly("trace-oldest");
        assertThat(eventTraceIds(first, second, third))
                .containsExactly("trace-newest", "trace-tie-1", "trace-tie-2", "trace-tie-3", "trace-oldest")
                .doesNotHaveDuplicates();
    }

    @Test
    void slowPageShouldSortOutOfOrderIncidentsWithoutDuplicateOrLoss() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        RuntimeDiagnosisController controller = controller(traceStore);
        List<RecentTraceStore.IncidentSample> physicalOrder = outOfOrderIncidents();
        when(traceStore.retentionDays()).thenReturn(30);
        when(traceStore.recentIncidentsMatching(anyInt(), any(LocalDateTime.class),
                nullable(LocalDateTime.class), any())).thenAnswer(invocation -> incidentQuery(
                        physicalOrder, invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2)));

        RuntimeDiagnosisController.HistoryPage<RuntimeDiagnosisController.SlowView> first =
                controller.slowPage(24, null, 2).getData();
        RuntimeDiagnosisController.HistoryPage<RuntimeDiagnosisController.SlowView> second =
                controller.slowPage(24, first.nextBefore(), 2).getData();
        RuntimeDiagnosisController.HistoryPage<RuntimeDiagnosisController.SlowView> third =
                controller.slowPage(24, second.nextBefore(), 2).getData();

        assertThat(first.items()).extracting(RuntimeDiagnosisController.SlowView::traceId)
                .containsExactly("trace-newest", "trace-tie-1");
        assertThat(second.items()).extracting(RuntimeDiagnosisController.SlowView::traceId)
                .containsExactly("trace-tie-2", "trace-tie-3");
        assertThat(third.items()).extracting(RuntimeDiagnosisController.SlowView::traceId)
                .containsExactly("trace-oldest");
        assertThat(slowTraceIds(first, second, third))
                .containsExactly("trace-newest", "trace-tie-1", "trace-tie-2", "trace-tie-3", "trace-oldest")
                .doesNotHaveDuplicates();
    }

    @Test
    void errorPageShouldSortOutOfOrderIncidentsWithoutDuplicateOrLoss() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        RuntimeDiagnosisController controller = controller(traceStore);
        List<RecentTraceStore.IncidentSample> physicalOrder = outOfOrderIncidents();
        when(traceStore.retentionDays()).thenReturn(30);
        when(traceStore.recentIncidentsMatching(anyInt(), any(LocalDateTime.class),
                nullable(LocalDateTime.class), any())).thenAnswer(invocation -> incidentQuery(
                        physicalOrder, invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2)));

        RuntimeDiagnosisController.HistoryPage<RuntimeDiagnosisController.RuntimeErrorView> first =
                controller.errorPage(24, null, 2).getData();
        RuntimeDiagnosisController.HistoryPage<RuntimeDiagnosisController.RuntimeErrorView> second =
                controller.errorPage(24, first.nextBefore(), 2).getData();
        RuntimeDiagnosisController.HistoryPage<RuntimeDiagnosisController.RuntimeErrorView> third =
                controller.errorPage(24, second.nextBefore(), 2).getData();

        assertThat(first.items()).extracting(RuntimeDiagnosisController.RuntimeErrorView::traceId)
                .containsExactly("trace-newest", "trace-tie-1");
        assertThat(second.items()).extracting(RuntimeDiagnosisController.RuntimeErrorView::traceId)
                .containsExactly("trace-tie-2", "trace-tie-3");
        assertThat(third.items()).extracting(RuntimeDiagnosisController.RuntimeErrorView::traceId)
                .containsExactly("trace-oldest");
        assertThat(errorTraceIds(first, second, third))
                .containsExactly("trace-newest", "trace-tie-1", "trace-tie-2", "trace-tie-3", "trace-oldest")
                .doesNotHaveDuplicates();
    }

    @Test
    void tracePageShouldNotDuplicateOrLoseSamplesThatShareTheSameTimestamp() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        RuntimeDiagnosisController controller = controller(traceStore);
        LocalDateTime finishedAt = LocalDateTime.of(2026, 10, 14, 12, 34, 56, 123_000_000);
        List<RecentTraceStore.TraceSample> samples = List.of(
                trace("trace-1", finishedAt),
                trace("trace-2", finishedAt),
                trace("trace-3", finishedAt),
                trace("trace-4", finishedAt),
                trace("trace-5", finishedAt));
        when(traceStore.retentionDays()).thenReturn(30);
        when(traceStore.recentTracesMatching(anyInt(), any(LocalDateTime.class),
                nullable(LocalDateTime.class), any()))
                .thenAnswer(invocation -> {
                    int limit = invocation.getArgument(0);
                    return samples.subList(0, Math.min(limit, samples.size()));
                });

        RuntimeDiagnosisController.HistoryPage<RecentTraceStore.TraceSample> first =
                controller.tracePage(24, null, 2).getData();
        RuntimeDiagnosisController.HistoryPage<RecentTraceStore.TraceSample> second =
                controller.tracePage(24, first.nextBefore(), 2).getData();
        RuntimeDiagnosisController.HistoryPage<RecentTraceStore.TraceSample> third =
                controller.tracePage(24, second.nextBefore(), 2).getData();

        assertThat(first.items()).extracting(RecentTraceStore.TraceSample::traceId)
                .containsExactly("trace-1", "trace-2");
        assertThat(first.hasMore()).isTrue();
        assertThat(first.nextBefore()).isEqualTo(finishedAt + "|2");
        assertThat(second.items()).extracting(RecentTraceStore.TraceSample::traceId)
                .containsExactly("trace-3", "trace-4");
        assertThat(second.hasMore()).isTrue();
        assertThat(second.nextBefore()).isEqualTo(finishedAt + "|4");
        assertThat(third.items()).extracting(RecentTraceStore.TraceSample::traceId)
                .containsExactly("trace-5");
        assertThat(third.hasMore()).isFalse();
        assertThat(third.nextBefore()).isNull();

        List<String> allTraceIds = new ArrayList<>();
        first.items().forEach(item -> allTraceIds.add(item.traceId()));
        second.items().forEach(item -> allTraceIds.add(item.traceId()));
        third.items().forEach(item -> allTraceIds.add(item.traceId()));
        assertThat(allTraceIds).containsExactly("trace-1", "trace-2", "trace-3", "trace-4", "trace-5")
                .doesNotHaveDuplicates();

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<LocalDateTime> before = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(traceStore, times(3)).recentTracesMatching(limit.capture(), any(LocalDateTime.class),
                before.capture(), any());
        assertThat(limit.getAllValues()).containsExactly(3, 5, 7);
        assertThat(before.getAllValues()).containsExactly(null, finishedAt.plusNanos(1), finishedAt.plusNanos(1));
    }

    @Test
    void wholeSecondLegacyTraceShouldIncludeTheRestOfSecondInJfrWindow() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        LocalJfrRecorder jfrRecorder = mock(LocalJfrRecorder.class);
        RuntimeDiagnosisController controller = controller(traceStore, jfrRecorder);
        LocalDateTime finishedAt = LocalDateTime.of(2026, 10, 14, 12, 34, 56);
        var recordedTo = finishedAt.atZone(ZoneId.systemDefault()).toInstant();
        var sameSecondFile = new LocalJfrRecorder.JfrFile("same-second.jfr", 100,
                recordedTo.plusMillis(500).toString(), recordedTo.plusSeconds(2).toString(),
                recordedTo.plusSeconds(2).toString());
        when(traceStore.findTrace("legacy-trace")).thenReturn(trace("legacy-trace", finishedAt));
        when(jfrRecorder.files()).thenReturn(List.of(sameSecondFile));

        RuntimeDiagnosisController.TraceJfrSelection selection =
                controller.jfrFilesForTrace("legacy-trace").getData();

        assertThat(selection.to()).isEqualTo(recordedTo.plusSeconds(1).minusNanos(1).toString());
        assertThat(selection.files()).containsExactly(sameSecondFile);
    }

    @Test
    void preciseTraceShouldNotExpandJfrWindow() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        LocalJfrRecorder jfrRecorder = mock(LocalJfrRecorder.class);
        RuntimeDiagnosisController controller = controller(traceStore, jfrRecorder);
        LocalDateTime finishedAt = LocalDateTime.of(2026, 10, 14, 12, 34, 56, 123_456_789);
        var recordedTo = finishedAt.atZone(ZoneId.systemDefault()).toInstant();
        var laterFile = new LocalJfrRecorder.JfrFile("later.jfr", 100,
                recordedTo.plusNanos(1).toString(), recordedTo.plusSeconds(2).toString(),
                recordedTo.plusSeconds(2).toString());
        when(traceStore.findTrace("precise-trace")).thenReturn(trace("precise-trace", finishedAt));
        when(jfrRecorder.files()).thenReturn(List.of(laterFile));

        RuntimeDiagnosisController.TraceJfrSelection selection =
                controller.jfrFilesForTrace("precise-trace").getData();

        assertThat(selection.to()).isEqualTo(recordedTo.toString());
        assertThat(selection.files()).isEmpty();
    }

    private static RecentTraceStore.TraceSample trace(String traceId, LocalDateTime finishedAt) {
        return new RecentTraceStore.TraceSample("HTTP", traceId, "/api/test", "GET", 200,
                "{}", 10, finishedAt);
    }

    private static List<RecentTraceStore.IncidentSample> outOfOrderIncidents() {
        LocalDateTime newest = LocalDateTime.now().minusMinutes(1).withNano(123_000_000);
        LocalDateTime tied = newest.minusSeconds(1);
        LocalDateTime oldest = tied.minusSeconds(1);
        return List.of(
                incident("trace-tie-1", tied),
                incident("trace-newest", newest),
                incident("trace-tie-2", tied),
                incident("trace-oldest", oldest),
                incident("trace-tie-3", tied));
    }

    private static RecentTraceStore.IncidentSample incident(String traceId, LocalDateTime finishedAt) {
        return new RecentTraceStore.IncidentSample("HTTP", traceId, "DemoController#run", "GET /demo", 500,
                "tree", 1_000, true, true, List.of("SLOW_REQUEST", "HTTP_ERROR"),
                "java.lang.IllegalStateException", "failed", "stack", finishedAt);
    }

    private static List<RecentTraceStore.IncidentSample> incidentQuery(
            List<RecentTraceStore.IncidentSample> physicalOrder, int limit,
            LocalDateTime from, LocalDateTime before) {
        return physicalOrder.stream()
                .filter(sample -> from == null || !sample.finishedAt().isBefore(from))
                .filter(sample -> before == null || sample.finishedAt().isBefore(before))
                .limit(limit)
                .toList();
    }

    @SafeVarargs
    private static List<String> eventTraceIds(
            RuntimeDiagnosisController.HistoryPage<RuntimeDiagnosisController.EventView>... pages) {
        List<String> result = new ArrayList<>();
        for (var page : pages) page.items().forEach(item -> result.add(item.traceId()));
        return result;
    }

    @SafeVarargs
    private static List<String> slowTraceIds(
            RuntimeDiagnosisController.HistoryPage<RuntimeDiagnosisController.SlowView>... pages) {
        List<String> result = new ArrayList<>();
        for (var page : pages) page.items().forEach(item -> result.add(item.traceId()));
        return result;
    }

    @SafeVarargs
    private static List<String> errorTraceIds(
            RuntimeDiagnosisController.HistoryPage<RuntimeDiagnosisController.RuntimeErrorView>... pages) {
        List<String> result = new ArrayList<>();
        for (var page : pages) page.items().forEach(item -> result.add(item.traceId()));
        return result;
    }

    private static RuntimeDiagnosisController controller(RecentTraceStore traceStore) {
        return controller(traceStore, mock(LocalJfrRecorder.class));
    }

    private static RuntimeDiagnosisController controller(RecentTraceStore traceStore,
                                                         LocalJfrRecorder jfrRecorder) {
        return controller(traceStore, jfrRecorder, new EasyNextAdminConfig());
    }

    private static RuntimeDiagnosisController controller(RecentTraceStore traceStore,
                                                         LocalJfrRecorder jfrRecorder,
                                                         EasyNextAdminConfig config) {
        return controller(traceStore, jfrRecorder, config, mock(MeterRegistry.class),
                mock(MetricHistoryStore.class));
    }

    private static RuntimeDiagnosisController controller(RecentTraceStore traceStore,
                                                         LocalJfrRecorder jfrRecorder,
                                                         EasyNextAdminConfig config,
                                                         MeterRegistry meterRegistry,
                                                         MetricHistoryStore metricStore) {
        return new RuntimeDiagnosisController(meterRegistry, traceStore,
                metricStore,
                mock(IAuditErrorLogService.class), mock(AuditVisibilitySupport.class),
                config, jfrRecorder,
                mock(ActiveOperationRegistry.class), new ObjectMapper());
    }
}
