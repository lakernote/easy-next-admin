package com.laker.admin.module.monitor.service;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeStatisticsServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T04:00:00Z"), ZONE);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 12, 0);

    @Test
    void shouldAggregateErrorsPercentilesAndResourceUsageWithExplicitSqlSampling() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        stubScan(traceStore, NOW.minusHours(24), List.of(
                sample("HTTP", "OrderController#list", "success", 10, 2, 100, "", NOW.minusMinutes(5)),
                sample("HTTP", "OrderController#list", "failure", 20, 4, 300, "", NOW.minusMinutes(4)),
                sample("HTTP", "OrderController#list", "success", 30, -1, -1, "", NOW.minusMinutes(3)),
                sample("SQL", "OrderMapper#selectPage", "success", 200, 20, 1_000,
                        "SELECT · select * from orders where id = ?", NOW.minusMinutes(2)),
                sample("SQL", "OrderMapper#selectPage", "failure", 400, 40, 3_000,
                        "SELECT · select * from orders where id = ?", NOW.minusMinutes(1)),
                sample("HTTP", "OrderController#list", "failure", 999, 99, 9_999,
                        "", NOW.minusHours(25)),
                sample("UNKNOWN", "ignored", "failure", 999, 99, 9_999, "", NOW.minusMinutes(1))));
        RuntimeStatisticsService service = new RuntimeStatisticsService(traceStore, CLOCK);

        RuntimeStatisticsService.StatisticsResult result = service.statistics(
                "ALL", 24, 20, "totalDurationMs,desc");

        assertThat(result.sampled()).isTrue();
        assertThat(result.coverage()).isEqualTo("SLOW_OR_ERROR_ONLY");
        assertThat(result.matchedSamples()).isEqualTo(5);
        assertThat(result.totalGroups()).isEqualTo(2);
        assertThat(result.items()).extracting(RuntimeStatisticsService.StatisticRow::type)
                .containsExactly("SQL", "HTTP");

        RuntimeStatisticsService.StatisticRow http = row(result, "HTTP");
        assertThat(http.name()).isEqualTo("OrderController#list");
        assertThat(http.count()).isEqualTo(3);
        assertThat(http.errorCount()).isEqualTo(1);
        assertThat(http.errorRate()).isCloseTo(1.0 / 3.0, within(0.000_001));
        assertThat(http.avgDurationMs()).isEqualTo(20.0);
        assertThat(http.maxDurationMs()).isEqualTo(30);
        assertThat(http.p50Ms()).isEqualTo(20);
        assertThat(http.p95Ms()).isEqualTo(30);
        assertThat(http.p99Ms()).isEqualTo(30);
        assertThat(http.totalDurationMs()).isEqualTo(60);
        assertThat(http.avgCpuMs()).isEqualTo(3.0);
        assertThat(http.avgAllocatedBytes()).isEqualTo(200.0);
        assertThat(http.cpuSamples()).isEqualTo(2);
        assertThat(http.allocatedSamples()).isEqualTo(2);
        assertThat(http.sampled()).isTrue();
        assertThat(http.coverage()).isEqualTo("SLOW_OR_ERROR_ONLY");

        RuntimeStatisticsService.StatisticRow sql = row(result, "SQL");
        assertThat(sql.count()).isEqualTo(2);
        assertThat(sql.errorCount()).isEqualTo(1);
        assertThat(sql.sampled()).isTrue();
        assertThat(sql.coverage()).isEqualTo("SLOW_OR_ERROR_ONLY");
        assertThat(sql.detail()).startsWith("SELECT ·");
        RuntimeStatisticsService.TypeCoverage sqlCoverage = result.coverageByType().stream()
                .filter(item -> "SQL".equals(item.type()))
                .findFirst().orElseThrow();
        assertThat(sqlCoverage.sampled()).isTrue();
        assertThat(sqlCoverage.note()).contains("仅代表这些样本");
    }

    @Test
    void shouldFilterByTypeAndWindowThenApplySortAndOutputLimit() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        stubScan(traceStore, NOW.minusHours(1), List.of(
                sample("JOB", "nightly-report", "success", 50, 5, 100, "", NOW.minusMinutes(50)),
                sample("JOB", "nightly-report", "failure", 150, 15, 300, "", NOW.minusMinutes(40)),
                sample("JOB", "cache-clean", "success", 25, 3, 80, "", NOW.minusMinutes(30)),
                sample("JOB", "expired", "failure", 500, 50, 500, "", NOW.minusHours(2)),
                sample("KAFKA", "OrderConsumer#consume", "failure", 900, 90, 900,
                        "", NOW.minusMinutes(10))));
        RuntimeStatisticsService service = new RuntimeStatisticsService(traceStore, CLOCK);

        RuntimeStatisticsService.StatisticsResult result = service.statistics(
                "job", 1, 1, "errorRate:desc");

        assertThat(result.type()).isEqualTo("JOB");
        assertThat(result.sampled()).isTrue();
        assertThat(result.coverage()).isEqualTo("SLOW_OR_ERROR_ONLY");
        assertThat(result.appliedLimit()).isEqualTo(1);
        assertThat(result.totalGroups()).isEqualTo(2);
        assertThat(result.items()).singleElement().satisfies(row -> {
            assertThat(row.type()).isEqualTo("JOB");
            assertThat(row.name()).isEqualTo("nightly-report");
            assertThat(row.errorRate()).isEqualTo(0.5);
        });
        assertThat(result.coverageByType()).singleElement().satisfies(coverage -> {
            assertThat(coverage.readSamples()).isEqualTo(4);
            assertThat(coverage.matchedSamples()).isEqualTo(3);
        });
        verify(traceStore).scanRecentOperationSummaries(eq(RuntimeStatisticsService.MAX_SCAN_SAMPLES),
                eq(NOW.minusHours(1)), eq(NOW.plusNanos(1)), any(), any());
    }

    @Test
    void shouldPushTypeAndWindowIntoStoreBeforeApplyingScanLimit() {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        stubScan(traceStore, NOW.minusHours(24), List.of(
                sample("HTTP", "retained-http", "failure", 50, 5, 100, "", NOW.minusHours(23))));
        RuntimeStatisticsService service = new RuntimeStatisticsService(traceStore, CLOCK);

        RuntimeStatisticsService.StatisticsResult result = service.statistics(
                "HTTP", 24, 20, "count,desc");

        assertThat(result.items()).singleElement()
                .satisfies(row -> assertThat(row.name()).isEqualTo("retained-http"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Predicate<RecentTraceStore.OperationSummarySample>> predicate =
                ArgumentCaptor.forClass(Predicate.class);
        verify(traceStore).scanRecentOperationSummaries(eq(RuntimeStatisticsService.MAX_SCAN_SAMPLES),
                eq(NOW.minusHours(24)), eq(NOW.plusNanos(1)), predicate.capture(), any());
        assertThat(predicate.getValue().test(
                sample("HTTP", "accepted", "success", 1, 1, 1, "", NOW))).isTrue();
        assertThat(predicate.getValue().test(
                sample("SQL", "rejected", "success", 1, 1, 1, "", NOW))).isFalse();
    }

    @Test
    void shouldCoalesceConcurrentRefreshesAndRescanAfterShortTtl() throws Exception {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        AtomicInteger scans = new AtomicInteger();
        when(traceStore.scanRecentOperationSummaries(eq(RuntimeStatisticsService.MAX_SCAN_SAMPLES),
                eq(NOW.minusHours(24)), eq(NOW.plusNanos(1)), any(), any())).thenAnswer(invocation -> {
                    scans.incrementAndGet();
                    TimeUnit.MILLISECONDS.sleep(50);
                    @SuppressWarnings("unchecked")
                    Consumer<RecentTraceStore.OperationSummarySample> visitor = invocation.getArgument(4);
                    visitor.accept(sample("HTTP", "Demo#run", "success", 10, 1, 100, "", NOW));
                    return new RecentTraceStore.OperationScanResult(1, false);
                });
        AtomicLong ticker = new AtomicLong(1);
        RuntimeStatisticsService service = new RuntimeStatisticsService(traceStore, CLOCK, ticker::get);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<RuntimeStatisticsService.StatisticsResult>> calls = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                calls.add(executor.submit(() -> {
                    start.await(2, TimeUnit.SECONDS);
                    return service.statistics("http", 24, 20, "count,desc");
                }));
            }
            start.countDown();
            for (Future<RuntimeStatisticsService.StatisticsResult> call : calls) {
                assertThat(call.get(2, TimeUnit.SECONDS).items()).hasSize(1);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(scans).hasValue(1);
        verify(traceStore, times(1)).scanRecentOperationSummaries(
                eq(RuntimeStatisticsService.MAX_SCAN_SAMPLES), eq(NOW.minusHours(24)),
                eq(NOW.plusNanos(1)), any(), any());

        ticker.addAndGet(RuntimeStatisticsService.CACHE_TTL_NANOS);
        assertThat(service.statistics("HTTP", 24, 20, "count,desc").items()).hasSize(1);
        assertThat(scans).hasValue(2);
    }

    @Test
    void shouldRejectExcessColdScanInsteadOfBlockingARequestThreadIndefinitely() throws Exception {
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        CountDownLatch scanEntered = new CountDownLatch(1);
        CountDownLatch releaseScan = new CountDownLatch(1);
        when(traceStore.scanRecentOperationSummaries(eq(RuntimeStatisticsService.MAX_SCAN_SAMPLES),
                eq(NOW.minusHours(24)), eq(NOW.plusNanos(1)), any(), any())).thenAnswer(invocation -> {
                    scanEntered.countDown();
                    releaseScan.await(5, TimeUnit.SECONDS);
                    return new RecentTraceStore.OperationScanResult(0, false);
                });
        RuntimeStatisticsService service = new RuntimeStatisticsService(traceStore, CLOCK);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<RuntimeStatisticsService.StatisticsResult> owner = executor.submit(
                () -> service.statistics("HTTP", 24, 20, "count,desc"));
        try {
            assertThat(scanEntered.await(2, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> service.statistics("JOB", 24, 20, "count,desc"))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS));
        } finally {
            releaseScan.countDown();
            assertThat(owner.get(2, TimeUnit.SECONDS).items()).isEmpty();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectUnsupportedTypeWindowAndSort() {
        RuntimeStatisticsService service = new RuntimeStatisticsService(mock(RecentTraceStore.class), CLOCK);

        assertThatThrownBy(() -> service.statistics("CACHE", 24, 10, "count,desc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("type 仅支持");
        assertThatThrownBy(() -> service.statistics("HTTP", 2, 10, "count,desc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("windowHours");
        assertThatThrownBy(() -> service.statistics("HTTP", 24, 10, "unknown,desc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sort 字段");
        assertThatThrownBy(() -> service.statistics("HTTP", 24, 10, "count,sideways"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("sort 方向");
    }

    private static RuntimeStatisticsService.StatisticRow row(
            RuntimeStatisticsService.StatisticsResult result, String type) {
        return result.items().stream().filter(item -> type.equals(item.type())).findFirst().orElseThrow();
    }

    private static void stubScan(RecentTraceStore traceStore, LocalDateTime from,
                                 List<RecentTraceStore.OperationSummarySample> samples) {
        when(traceStore.scanRecentOperationSummaries(eq(RuntimeStatisticsService.MAX_SCAN_SAMPLES),
                eq(from), eq(NOW.plusNanos(1)), any(), any())).thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Predicate<RecentTraceStore.OperationSummarySample> predicate = invocation.getArgument(3);
                    @SuppressWarnings("unchecked")
                    Consumer<RecentTraceStore.OperationSummarySample> visitor = invocation.getArgument(4);
                    List<RecentTraceStore.OperationSummarySample> selected = samples.stream()
                            .filter(predicate).limit(RuntimeStatisticsService.MAX_SCAN_SAMPLES).toList();
                    selected.forEach(visitor);
                    return new RecentTraceStore.OperationScanResult(selected.size(),
                            selected.size() >= RuntimeStatisticsService.MAX_SCAN_SAMPLES);
                });
    }

    private static RecentTraceStore.OperationSummarySample sample(String type, String name, String outcome,
                                                                   long durationMs, long cpuTimeMs,
                                                                   long allocatedBytes, String detail,
                                                                   LocalDateTime finishedAt) {
        return new RecentTraceStore.OperationSummarySample(type, name, outcome, durationMs, cpuTimeMs,
                allocatedBytes, detail, finishedAt);
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
