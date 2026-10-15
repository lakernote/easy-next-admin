package com.laker.admin.infrastructure.observability.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RecentTraceStoreSummaryTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T04:00:00Z"), ZoneOffset.ofHours(8));
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 12, 0);

    @TempDir
    Path storageDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void frequentConcurrentSummaryCallsShareOneJsonlScan() throws Exception {
        RecentTraceStore.IncidentSample incident = new RecentTraceStore.IncidentSample(
                "HTTP", "trace-summary", "Demo#run", "GET /demo", 500, "tree", 3_000,
                true, true, List.of("SLOW_REQUEST", "HTTP_ERROR"), "IllegalStateException",
                "failed", "stack", NOW);
        CountingSummaryStore store = new CountingSummaryStore(objectMapper, storageDirectory, incident);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<RecentTraceStore.IncidentSummary>> calls = IntStream.range(0, 24)
                    .mapToObj(ignored -> executor.submit(() -> {
                        start.await(2, TimeUnit.SECONDS);
                        return store.recentIncidentSummary(Duration.ofHours(1), 50_000);
                    }))
                    .toList();
            start.countDown();

            for (Future<RecentTraceStore.IncidentSummary> call : calls) {
                assertThat(call.get(2, TimeUnit.SECONDS)).satisfies(summary -> {
                    assertThat(summary.incidents()).isEqualTo(1);
                    assertThat(summary.slow()).isEqualTo(1);
                    assertThat(summary.errors()).isEqualTo(1);
                    assertThat(summary.truncated()).isFalse();
                });
            }
            assertThat(store.summaryScans()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void legacyMetricHistoryTreatsEmptyTagsAsExactUntaggedSelector() {
        RecentTraceStore store = new RecentTraceStore(objectMapper, storageDirectory.toString(), 30,
                10_000_000, CLOCK);
        store.restore();
        try {
            store.recordMetricsSnapshot(List.of(
                    new RecentTraceStore.MetricSample("same.metric", Map.of(), Map.of("VALUE", 1D)),
                    new RecentTraceStore.MetricSample("same.metric", Map.of("node", "local"),
                            Map.of("VALUE", 2D))));

            assertThat(store.metricHistory("same.metric", Map.of(), 10)).singleElement().satisfies(point -> {
                assertThat(point.tags()).isEmpty();
                assertThat(point.measurements()).containsEntry("VALUE", 1D);
            });
        } finally {
            store.close();
        }
    }

    private static final class CountingSummaryStore extends RecentTraceStore {
        private final AtomicInteger summaryScans = new AtomicInteger();
        private final IncidentSample incident;

        private CountingSummaryStore(ObjectMapper objectMapper, Path storageDirectory, IncidentSample incident) {
            super(objectMapper, storageDirectory.toString(), 30, 10_000_000, CLOCK);
            this.incident = incident;
        }

        @Override
        public List<IncidentSample> recentIncidents(int limit, LocalDateTime from, LocalDateTime before) {
            summaryScans.incrementAndGet();
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return List.of(incident);
        }

        private int summaryScans() {
            return summaryScans.get();
        }
    }
}
