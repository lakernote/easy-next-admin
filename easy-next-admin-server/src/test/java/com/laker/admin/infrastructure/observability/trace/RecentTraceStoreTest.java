package com.laker.admin.infrastructure.observability.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.laker.admin.infrastructure.security.masking.EasySensitiveDataMasker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecentTraceStoreTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T04:00:00Z"), ZONE);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 12, 0);

    @TempDir
    Path storageDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void shouldPersistSlowAndErrorAsOneCanonicalIncidentAndTrace() {
        RecentTraceStore store = store(10_000_000);
        store.restore();

        RecentTraceStore.IncidentCause cause = new RecentTraceStore.IncidentCause(
                "TRANSACTION", "DemoService#run", "write", 3_800, 1_000,
                true, false, "LONG_TRANSACTION");

        store.recordIncident("HTTP", "incident-trace", "Demo#run", "GET /demo", 500, "trace tree", 4_000,
                true, true, List.of("SLOW_REQUEST", "HTTP_ERROR"), IllegalStateException.class.getName(),
                "failed", "stack", cause, cause, null);

        assertThat(store.recentIncidents(10)).singleElement().satisfies(incident -> {
            assertThat(incident.slow()).isTrue();
            assertThat(incident.error()).isTrue();
            assertThat(incident.reasons()).containsExactly("SLOW_REQUEST", "HTTP_ERROR");
            assertThat(incident.primaryCause()).isEqualTo(cause);
            assertThat(incident.slowCause()).isEqualTo(cause);
        });
        assertThat(store.recentTraces(10)).singleElement()
                .satisfies(trace -> assertThat(trace.traceId()).isEqualTo("incident-trace"));
        assertThat(store.findCorrelation("incident-trace").incident()).isNotNull();
    }

    @Test
    void shouldReadLegacyIncidentWithoutCauseFields() throws Exception {
        String legacyIncident = """
                {"kind":"incident","data":{"type":"HTTP","traceId":"legacy-incident","name":"Demo#run",\
                "method":"GET /demo","status":200,"tree":"tree","costMs":2000,"slow":true,"error":false,\
                "reasons":["SLOW_REQUEST"],"errorType":"","errorMessage":"","stackTrace":"",\
                "finishedAt":"2026-10-14T12:00:00"}}
                """;
        Files.writeString(segment("2026-10-14"), legacyIncident + System.lineSeparator(), StandardCharsets.UTF_8);
        RecentTraceStore store = store(10_000_000);
        store.restore();

        assertThat(store.recentIncidents(10)).singleElement().satisfies(incident -> {
            assertThat(incident.traceId()).isEqualTo("legacy-incident");
            assertThat(incident.primaryCause()).isNull();
            assertThat(incident.slowCause()).isNull();
            assertThat(incident.errorCause()).isNull();
        });
    }

    @Test
    void shouldDiscardNormalSuccessfulRootInsteadOfCreatingTrace() {
        RecentTraceStore store = store(10_000_000);
        store.restore();

        store.recordIncident("HTTP", "normal-trace", "Demo#run", "GET /demo", 200, "trace tree", 10,
                false, false, List.of());

        assertThat(store.recentIncidents(10)).isEmpty();
        assertThat(store.recentTraces(10)).isEmpty();
    }

    @Test
    void shouldWriteDailySegmentAndReadNewestSegmentsBeforeLegacyFile() throws Exception {
        Path legacy = storageDirectory.resolve("runtime-diagnosis.jsonl");
        writeLegacyTraceWithoutType(legacy, "legacy-trace", "legacy", NOW.minusDays(2));
        writeRecord(segment("2026-10-13"), "trace",
                trace("HTTP", "yesterday-trace", "yesterday", NOW.minusDays(1)));
        RecentTraceStore store = store(10_000_000);
        store.restore();

        store.recordTrace("JOB", "today-trace", "today", "daily-job", 200, "tree", 12);

        // Reads wait for the bounded asynchronous writer to flush queued records.
        List<RecentTraceStore.TraceSample> traces = store.recentTraces(10);
        assertThat(storageDirectory.resolve("runtime-diagnosis-2026-10-14.jsonl")).isRegularFile();
        assertThat(traces)
                .extracting(RecentTraceStore.TraceSample::name)
                .containsExactly("today", "yesterday", "legacy");
        assertThat(store.findTrace("legacy-trace").type()).isNull();
        assertThat(store.findTrace("today-trace").type()).isEqualTo("JOB");
    }

    @Test
    void shouldSkipMalformedLinesAndKeepReadingLaterRecords() throws Exception {
        Path current = segment("2026-10-14");
        writeRecord(current, "trace", trace("HTTP", "first", "first", NOW.minusMinutes(2)));
        Files.writeString(current, "{broken-json" + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        writeRecord(current, "trace", trace("KAFKA", "second", "second", NOW.minusMinutes(1)));
        RecentTraceStore store = store(10_000_000);
        store.restore();

        assertThat(store.recentTraces(10))
                .extracting(RecentTraceStore.TraceSample::traceId)
                .containsExactly("second", "first");
        assertThat(store.findTrace("second").name()).isEqualTo("second");
    }

    @Test
    void shouldCorrelateTraceTransactionsAndOperationsAcrossCompatibleFiles() throws Exception {
        String traceId = "correlated-trace";
        writeRecord(storageDirectory.resolve("runtime-diagnosis.jsonl"), "transaction",
                new RecentTraceStore.TransactionSample(traceId, "legacy-tx", true, 10, NOW.minusNanos(8_000_000)));
        writeRecord(segment("2026-10-14"), "trace",
                trace("HTTP", traceId, "request", NOW));
        writeRecord(segment("2026-10-14"), "operation",
                new RecentTraceStore.OperationSample("SQL", traceId, "UserMapper#select", "worker", "success",
                        20, 5, 1_024, 0, -1, 0, -1, "SELECT · select * from sys_user",
                        "", "", NOW.minusNanos(5_000_000)));
        RecentTraceStore store = store(10_000_000);
        store.restore();

        RecentTraceStore.TraceCorrelation correlation = store.findCorrelation(traceId);

        assertThat(correlation.trace().name()).isEqualTo("request");
        assertThat(correlation.transactions()).extracting(RecentTraceStore.TransactionSample::name)
                .containsExactly("legacy-tx");
        assertThat(correlation.operations()).singleElement().satisfies(operation -> {
            assertThat(operation.type()).isEqualTo("SQL");
            assertThat(operation.detail()).contains("sys_user");
        });
    }

    @Test
    void shouldNotMixOperationsWhenClientReusesTraceIdAcrossRoots() throws Exception {
        String traceId = "client-reused-trace";
        LocalDateTime olderFinishedAt = NOW.minusSeconds(10);
        LocalDateTime latestFinishedAt = NOW;
        Path segment = segment("2026-10-14");
        writeRecord(segment, "operation", new RecentTraceStore.OperationSample(
                "SQL", traceId, "OldMapper#select", "worker", "failure",
                20, 5, 128, 0, 0, 0, 0, "SELECT old", "", "old stack",
                olderFinishedAt.minusNanos(10_000_000)));
        writeRecord(segment, "transaction", new RecentTraceStore.TransactionSample(
                traceId, "old-transaction", false, 20, olderFinishedAt.minusNanos(5_000_000)));
        writeRecord(segment, "incident", incident(traceId, olderFinishedAt));
        writeRecord(segment, "operation", new RecentTraceStore.OperationSample(
                "SQL", traceId, "LatestMapper#select", "worker", "failure",
                20, 5, 128, 0, 0, 0, 0, "SELECT latest", "", "latest stack",
                latestFinishedAt.minusNanos(10_000_000)));
        writeRecord(segment, "transaction", new RecentTraceStore.TransactionSample(
                traceId, "latest-transaction", false, 20, latestFinishedAt.minusNanos(5_000_000)));
        writeRecord(segment, "incident", incident(traceId, latestFinishedAt));
        RecentTraceStore store = store(10_000_000);
        store.restore();

        RecentTraceStore.TraceCorrelation correlation = store.findCorrelation(traceId);

        assertThat(correlation.trace().finishedAt()).isEqualTo(latestFinishedAt);
        assertThat(correlation.incident().finishedAt()).isEqualTo(latestFinishedAt);
        assertThat(correlation.operations()).extracting(RecentTraceStore.OperationSample::name)
                .containsExactly("LatestMapper#select");
        assertThat(correlation.transactions()).extracting(RecentTraceStore.TransactionSample::name)
                .containsExactly("latest-transaction");
        assertThat(correlation.startedAt()).isEqualTo(latestFinishedAt.minusNanos(101_000_000));
        assertThat(correlation.finishedAt()).isEqualTo(latestFinishedAt);
    }

    @Test
    void shouldNotCorrelateOrphanChildrenWithoutARootTrace() throws Exception {
        String traceId = "orphan-client-trace";
        Path segment = segment("2026-10-14");
        writeRecord(segment, "operation", new RecentTraceStore.OperationSample(
                "SQL", traceId, "OrphanMapper#select", "worker", "failure",
                20, 5, 128, 0, 0, 0, 0, "SELECT orphan", "", "orphan stack", NOW));
        writeRecord(segment, "transaction", new RecentTraceStore.TransactionSample(
                traceId, "orphan-transaction", false, 20, NOW));
        RecentTraceStore store = store(10_000_000);
        store.restore();

        RecentTraceStore.TraceCorrelation correlation = store.findCorrelation(traceId);

        assertThat(correlation.trace()).isNull();
        assertThat(correlation.incident()).isNull();
        assertThat(correlation.operations()).isEmpty();
        assertThat(correlation.transactions()).isEmpty();
    }

    @Test
    void shouldReadMetricHistoryNewestFirstAcrossSegments() throws Exception {
        Map<String, String> tags = Map.of("area", "heap");
        writeRecord(segment("2026-10-13"), "metrics", new RecentTraceStore.MetricsSnapshot(
                NOW.minusDays(1), List.of(new RecentTraceStore.MetricSample(
                "jvm.memory.used", tags, Map.of("VALUE", 1d)))));
        writeRecord(segment("2026-10-14"), "metrics", new RecentTraceStore.MetricsSnapshot(
                NOW, List.of(new RecentTraceStore.MetricSample(
                "jvm.memory.used", tags, Map.of("VALUE", 2d)))));
        RecentTraceStore store = store(10_000_000);
        store.restore();

        assertThat(store.metricHistory("jvm.memory.used", tags, 10))
                .extracting(point -> point.measurements().get("VALUE"))
                .containsExactly(2d, 1d);
    }

    @Test
    void shouldDeleteExpiredSegmentsAndExpiredLegacyFileWithoutRewritingRetainedDay() throws Exception {
        Path expired = segment("2026-09-13");
        Path retainedBoundary = segment("2026-09-14");
        Path legacy = storageDirectory.resolve("runtime-diagnosis.jsonl");
        writeRecord(expired, "trace", trace("HTTP", "expired", "expired", NOW.minusDays(31)));
        writeRecord(retainedBoundary, "trace", trace("HTTP", "boundary", "boundary", NOW.minusDays(30)));
        writeRecord(legacy, "trace", trace("HTTP", "legacy-expired", "legacy-expired", NOW.minusDays(31)));
        byte[] retainedContent = Files.readAllBytes(retainedBoundary);
        RecentTraceStore store = store(10_000_000);

        store.restore();

        assertThat(expired).doesNotExist();
        assertThat(legacy).doesNotExist();
        assertThat(retainedBoundary).hasBinaryContent(retainedContent);
    }

    @Test
    void shouldSkipScheduledPruningWhenDiagnosisStorageIsDisabled() throws Exception {
        Path expired = segment("2026-09-13");
        writeRecord(expired, "trace", trace("HTTP", "disabled", "disabled", NOW.minusDays(31)));
        byte[] original = Files.readAllBytes(expired);
        RecentTraceStore store = new RecentTraceStore(objectMapper, storageDirectory.toString(), 30,
                10_000_000, CLOCK, false);

        store.pruneExpired();

        assertThat(expired).hasBinaryContent(original);
    }

    @Test
    void shouldDeleteOldestCompleteSegmentsWhenHistoryExceedsCapacity() throws Exception {
        Path oldest = segment("2026-10-12");
        Path middle = segment("2026-10-13");
        Path newest = segment("2026-10-14");
        writeBytes(oldest, 600);
        writeBytes(middle, 600);
        writeBytes(newest, 600);
        RecentTraceStore store = store(1_300);

        store.restore();

        assertThat(oldest).doesNotExist();
        assertThat(middle).isRegularFile();
        assertThat(newest).isRegularFile();
        assertThat(store.storageBytes()).isLessThanOrEqualTo(1_300);
    }

    @Test
    void shouldSerializeConcurrentAppendsWithoutProducingUnreadableLines() throws Exception {
        RecentTraceStore store = store(10_000_000);
        store.restore();
        var executor = Executors.newFixedThreadPool(8);
        try {
            List<java.util.concurrent.Callable<Void>> tasks = IntStream.range(0, 200)
                    .mapToObj(index -> (java.util.concurrent.Callable<Void>) () -> {
                        store.recordEvent(new RecentTraceStore.EventSample("TEST", "event-" + index,
                                index, 0, "detail", NOW));
                        return null;
                    }).toList();
            for (var future : executor.invokeAll(tasks)) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(store.recentEvents(250)).hasSize(200);
    }

    @Test
    void shouldRollAndEvictClosedSegmentsWithinTheCurrentDay() throws Exception {
        RecentTraceStore store = new RecentTraceStore(objectMapper, storageDirectory.toString(), 30,
                2_000, 700, CLOCK, true);
        store.restore();

        for (int index = 0; index < 8; index++) {
            store.recordEvent(new RecentTraceStore.EventSample("TEST", "event-" + index,
                    index, 0, "x".repeat(350), NOW.plusSeconds(index)));
            store.recentEvents(1);
        }

        try (var files = Files.list(storageDirectory)) {
            List<String> names = files.map(path -> path.getFileName().toString()).sorted().toList();
            assertThat(names).anyMatch(name -> name.matches("runtime-diagnosis-2026-10-14-\\d{3}\\.jsonl"));
            assertThat(names.size()).isLessThan(8);
        }
        assertThat(store.storageBytes()).isLessThanOrEqualTo(2_000);
        assertThat(store.recentEvents(20)).isNotEmpty();
    }

    @Test
    void shouldRejectRecordLargerThanSegmentAndHistoryCaps() throws Exception {
        RecentTraceStore store = new RecentTraceStore(objectMapper, storageDirectory.toString(), 30,
                300, 300, CLOCK, true);
        store.restore();

        store.recordEvent(new RecentTraceStore.EventSample("TEST", "oversized",
                1, 0, "x".repeat(1_000), NOW));

        assertThat(store.recentEvents(10)).isEmpty();
        assertThat(store.storageBytes()).isLessThanOrEqualTo(300);
        assertThat(store.droppedWrites()).isEqualTo(1);
    }

    @Test
    void shouldTrimCrashTailBeforeAppendingNewRecord() throws Exception {
        Path current = segment("2026-10-14");
        writeRecord(current, "event", new RecentTraceStore.EventSample(
                "TEST", "complete", 1, 0, "ok", NOW.minusSeconds(1)));
        Files.writeString(current, "{\"kind\":\"event\",\"data\":", StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
        RecentTraceStore store = store(10_000_000);

        store.restore();
        store.recordEvent(new RecentTraceStore.EventSample("TEST", "after-restart", 2, 0, "ok", NOW));

        assertThat(store.recentEvents(10)).extracting(RecentTraceStore.EventSample::name)
                .containsExactly("after-restart", "complete");
    }

    @Test
    void shouldScanPastNonMatchingTailRecordsWithinServerTimeWindow() throws Exception {
        Path current = segment("2026-10-14");
        writeRecord(current, "operation", operation("failure", NOW.minusHours(2)));
        writeRecord(current, "operation", operation("failure", NOW.minusMinutes(20)));
        for (int index = 0; index < 50; index++) {
            writeRecord(current, "operation", operation("success", NOW.minusMinutes(19).plusSeconds(index)));
        }
        RecentTraceStore store = store(10_000_000);
        store.restore();

        assertThat(store.recentOperationsMatching(10, NOW.minusHours(1), null,
                operation -> !"success".equals(operation.outcome())))
                .singleElement()
                .satisfies(operation -> assertThat(operation.finishedAt()).isEqualTo(NOW.minusMinutes(20)));
    }

    @Test
    void shouldScanCompactOperationSummariesWithoutRetainingDiagnosticEvidence() throws Exception {
        Path current = segment("2026-10-14");
        writeRecord(current, "operation", new RecentTraceStore.OperationSample(
                "SQL", "trace-new", "  OrderMapper  #select ", "worker", "failure",
                200, 20, 2_000, 1, 2, 3, 4, "SELECT   *\nFROM orders",
                "lock".repeat(2_000), "stack".repeat(2_000), Map.of("HTTP",
                new RecentTraceStore.ChildOperationSummary(1, 0, 10, 10)), NOW.minusSeconds(1)));
        // Appended later but carrying an older timestamp: the compact top-K must still retain the newest sample.
        writeRecord(current, "operation", new RecentTraceStore.OperationSample(
                "HTTP", "trace-old", "old", "worker", "success",
                10, 1, 100, 0, 0, 0, 0, "ignored detail",
                "lock".repeat(2_000), "stack".repeat(2_000), NOW.minusSeconds(30)));
        RecentTraceStore store = store(10_000_000);
        store.restore();
        List<RecentTraceStore.OperationSummarySample> summaries = new ArrayList<>();

        RecentTraceStore.OperationScanResult result = store.scanRecentOperationSummaries(
                1, NOW.minusHours(1), NOW.plusNanos(1), sample -> true, summaries::add);

        assertThat(result.readSamples()).isEqualTo(1);
        assertThat(result.scanLimitReached()).isTrue();
        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.type()).isEqualTo("SQL");
            assertThat(summary.name()).isEqualTo("OrderMapper #select");
            assertThat(summary.detail()).isEqualTo("SELECT * FROM orders");
            assertThat(summary.finishedAt()).isEqualTo(NOW.minusSeconds(1));
        });
    }

    @Test
    void shouldTolerateSmallConcurrentTimestampReorderingAtWindowBoundary() throws Exception {
        Path current = segment("2026-10-14");
        writeRecord(current, "operation", operation("failure", NOW.minusMinutes(5)));
        // A producer that was scheduled later may append an older timestamp after the qualifying record.
        writeRecord(current, "operation", operation("success", NOW.minusMinutes(10).minusSeconds(30)));
        RecentTraceStore store = store(10_000_000);
        store.restore();

        assertThat(store.recentOperations(10, NOW.minusMinutes(10), null))
                .singleElement()
                .satisfies(operation -> assertThat(operation.finishedAt()).isEqualTo(NOW.minusMinutes(5)));
    }

    @Test
    void shouldSelectNewestIncidentAndTraceBeyondRequestedPhysicalLimit() throws Exception {
        Path current = segment("2026-10-14");
        LocalDateTime newestAt = NOW.minusSeconds(5);
        writeRecord(current, "incident", incident("newest", newestAt));
        for (int index = 0; index < 10; index++) {
            writeRecord(current, "incident", incident("older-" + index,
                    NOW.minusSeconds(20L + index)));
        }
        RecentTraceStore store = store(10_000_000);
        store.restore();

        assertThat(store.recentIncidents(2))
                .extracting(RecentTraceStore.IncidentSample::traceId)
                .containsExactly("newest", "older-0");
        assertThat(store.recentTraces(2))
                .extracting(RecentTraceStore.TraceSample::traceId)
                .containsExactly("newest", "older-0");
    }

    @Test
    void shouldPersistNanosecondsWithPrivateIsoMapperEvenWhenApiMapperUsesSecondPrecision() throws Exception {
        ObjectMapper apiMapper = secondPrecisionApiMapper();
        RecentTraceStore store = new RecentTraceStore(apiMapper, storageDirectory.toString(), 30,
                10_000_000, CLOCK);
        LocalDateTime preciseFinishedAt = NOW.withNano(987_654_321);
        store.restore();

        store.recordOperation(operation("success", preciseFinishedAt));

        assertThat(store.recentOperations(1)).singleElement()
                .satisfies(operation -> assertThat(operation.finishedAt()).isEqualTo(preciseFinishedAt));
        assertThat(Files.readString(segment("2026-10-14"), StandardCharsets.UTF_8))
                .contains("\"finishedAt\":\"2026-10-14T12:00:00.987654321\"");
        assertThat(apiMapper.writeValueAsString(preciseFinishedAt)).isEqualTo("\"2026-10-14 12:00:00\"");
    }

    @Test
    void shouldReadLegacySpaceSeparatedSecondPrecisionTimestamp() throws Exception {
        Path current = segment("2026-10-14");
        Files.createDirectories(current.getParent());
        Files.writeString(current, """
                {"kind":"trace","data":{"type":"HTTP","traceId":"legacy-seconds","name":"legacy","method":"GET","status":200,"tree":"{}","costMs":10,"finishedAt":"2026-10-14 12:00:00"}}
                """, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        RecentTraceStore store = new RecentTraceStore(secondPrecisionApiMapper(), storageDirectory.toString(), 30,
                10_000_000, CLOCK);
        store.restore();

        assertThat(store.findTrace("legacy-seconds").finishedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldMaskAndBoundSensitiveDiagnosticContentBeforePersistence() throws Exception {
        String traceId = "sensitive-trace";
        String operationName = "DemoController#run";
        RecentTraceStore store = new RecentTraceStore(objectMapper, new EasySensitiveDataMasker(objectMapper),
                storageDirectory.toString(), 30, 10_000_000, CLOCK);
        store.restore();
        RecentTraceStore.IncidentCause cause = new RecentTraceStore.IncidentCause(
                "REMOTE", "BillingClient#charge",
                "https://billing.test/charge?token=cause-token&password=cause-password",
                3_000, 1_000, true, true, "REMOTE_ERROR");

        store.recordIncident("HTTP", traceId, operationName,
                "GET /api/demo?password=uri-password&token=uri-token&Authorization=uri-auth&keyword=ok",
                500, "password=tree-password token=tree-token Authorization: Bearer tree-auth",
                4_000, true, true, List.of("SLOW_REQUEST", "HTTP_ERROR"),
                "java.lang.IllegalStateException",
                "{\"password\":\"message-password\",\"token\":\"message-token\","
                        + "\"Authorization\":\"Bearer message-auth\"}",
                "Authorization: Bearer stack-auth\npassword=stack-password\ntoken=stack-token",
                cause, cause, cause);
        store.recordOperation(new RecentTraceStore.OperationSample(
                "REMOTE", traceId, "BillingClient#charge", "http-worker password=thread-password", "failure",
                3_000, 100, 2_048, 1, 10, 2, 20,
                "POST /charge?token=operation-token&password=operation-password Authorization=operation-auth "
                        + "x".repeat(EasySensitiveDataMasker.DEFAULT_TEXT_LIMIT + 500),
                "lock owner token=lock-token", "Authorization: Bearer operation-stack-auth",
                Map.of(), NOW));
        store.recordTransaction(traceId, "OrderService#create", false, 3_500);
        store.recordEvent(new RecentTraceStore.EventSample("CUSTOM", "quota.warning", 2, 1,
                "{\"password\":\"event-password\",\"token\":\"event-token\","
                        + "\"Authorization\":\"event-auth\"}", NOW));
        store.recordTrace("KAFKA", "direct-trace", "OrderConsumer#consume",
                "orders?token=direct-token", 500, "password=direct-password", 900);

        RecentTraceStore.TraceCorrelation correlation = store.findCorrelation(traceId);
        RecentTraceStore.IncidentSample incident = correlation.incident();
        RecentTraceStore.OperationSample operation = correlation.operations().get(0);
        RecentTraceStore.EventSample event = store.recentEvents(1).get(0);
        RecentTraceStore.TraceSample directTrace = store.findTrace("direct-trace");
        String persisted = Files.readString(segment("2026-10-14"), StandardCharsets.UTF_8);

        assertThat(correlation.trace()).satisfies(trace -> {
            assertThat(trace.type()).isEqualTo("HTTP");
            assertThat(trace.traceId()).isEqualTo(traceId);
            assertThat(trace.name()).isEqualTo(operationName);
        });
        assertThat(incident.method())
                .isEqualTo("GET /api/demo?password=******&token=******&Authorization=******&keyword=ok");
        assertThat(incident.errorMessage()).containsOnlyOnce("\"password\":\"******\"")
                .containsOnlyOnce("\"token\":\"******\"")
                .containsOnlyOnce("\"Authorization\":\"******\"");
        assertThat(incident.stackTrace()).contains("Authorization: ******", "password=******", "token=******");
        assertThat(incident.primaryCause().name()).isEqualTo("BillingClient#charge");
        assertThat(incident.primaryCause().detail()).contains("token=******", "password=******");
        assertThat(operation.type()).isEqualTo("REMOTE");
        assertThat(operation.traceId()).isEqualTo(traceId);
        assertThat(operation.name()).isEqualTo("BillingClient#charge");
        assertThat(operation.threadName()).contains("password=******");
        assertThat(operation.detail()).hasSizeLessThanOrEqualTo(EasySensitiveDataMasker.DEFAULT_TEXT_LIMIT)
                .contains("token=******", "password=******", "Authorization=******")
                .endsWith("...(truncated)");
        assertThat(operation.lockInfo()).contains("token=******");
        assertThat(operation.stackTrace()).contains("Authorization: ******");
        assertThat(correlation.transactions()).singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.traceId()).isEqualTo(traceId);
                    assertThat(transaction.name()).isEqualTo("OrderService#create");
                });
        assertThat(event.name()).isEqualTo("quota.warning");
        assertThat(event.detail()).contains("\"password\":\"******\"", "\"token\":\"******\"",
                "\"Authorization\":\"******\"");
        assertThat(directTrace).satisfies(trace -> {
            assertThat(trace.type()).isEqualTo("KAFKA");
            assertThat(trace.traceId()).isEqualTo("direct-trace");
            assertThat(trace.name()).isEqualTo("OrderConsumer#consume");
            assertThat(trace.method()).isEqualTo("orders?token=******");
            assertThat(trace.tree()).isEqualTo("password=******");
        });
        assertThat(persisted)
                .contains("******")
                .doesNotContain("uri-password", "uri-token", "uri-auth", "tree-password", "tree-token",
                        "tree-auth", "message-password", "message-token", "message-auth", "stack-password",
                        "stack-token", "stack-auth", "cause-token", "cause-password", "operation-token",
                        "operation-password", "operation-auth", "thread-password", "lock-token",
                        "operation-stack-auth", "event-password", "event-token", "event-auth", "direct-token",
                        "direct-password");
    }

    @Test
    void shouldDropDiagnosticRecordWithoutAffectingCallerWhenMaskerFails() {
        EasySensitiveDataMasker masker = mock(EasySensitiveDataMasker.class);
        when(masker.sanitizeJsonText(anyString(), anyInt()))
                .thenThrow(new IllegalStateException("masker unavailable"));
        RecentTraceStore store = new RecentTraceStore(objectMapper, masker, storageDirectory.toString(), 30,
                10_000_000, CLOCK);
        store.restore();

        assertThatCode(() -> store.recordEvent(new RecentTraceStore.EventSample(
                "CUSTOM", "masker.failure", 1, 0, "password=must-not-leak", NOW)))
                .doesNotThrowAnyException();

        assertThat(store.recentEvents(10)).isEmpty();
        assertThat(store.droppedWrites()).isEqualTo(1);
    }

    @Test
    void shouldRejectAlreadySaturatedQueueBeforeMaskingAndSerialization() throws Exception {
        CountingSensitiveDataMasker masker = new CountingSensitiveDataMasker(objectMapper);
        BlockingRecentTraceStore store = new BlockingRecentTraceStore(objectMapper, masker, storageDirectory);
        store.restore();
        try {
            store.recordEvent(new RecentTraceStore.EventSample(
                    "TEST", "in-flight", 1, 0, "password=first-secret", NOW));
            assertThat(store.writerEntered.await(5, TimeUnit.SECONDS)).isTrue();

            for (int index = 0; index < store.queueCapacity(); index++) {
                store.recordEvent(new RecentTraceStore.EventSample(
                        "TEST", "queued-" + index, index, 0, "password=queued-secret", NOW));
            }
            assertThat(store.pendingWrites()).isEqualTo(store.queueCapacity() + 1L);

            long maskingCallsBeforeRejectedRecord = masker.calls();
            long pendingBytesBeforeRejectedRecord = store.pendingBytes();
            store.recordEvent(new RecentTraceStore.EventSample(
                    "TEST", "must-fast-reject", 1, 0, "password=must-not-be-processed", NOW));

            assertThat(masker.calls()).isEqualTo(maskingCallsBeforeRejectedRecord);
            assertThat(store.pendingWrites()).isEqualTo(store.queueCapacity() + 1L);
            assertThat(store.pendingBytes()).isEqualTo(pendingBytesBeforeRejectedRecord);
            assertThat(store.droppedWrites()).isEqualTo(1);
            assertThat(store.byteBudgetDroppedWrites()).isZero();
        } finally {
            store.close();
            store.releaseWriter.countDown();
            assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
                while (store.pendingWrites() != 0) Thread.sleep(10);
            });
        }
    }

    @Test
    void shouldDrainQueuedWritesBeforeBoundedShutdownCompletes() throws Exception {
        RecentTraceStore store = store(10_000_000);
        store.restore();
        for (int index = 0; index < 300; index++) {
            store.recordEvent(new RecentTraceStore.EventSample(
                    "TEST", "shutdown-" + index, index, 0, "detail", NOW.plusNanos(index)));
        }

        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), store::close);

        assertThat(store.pendingWrites()).isZero();
        assertThat(store.droppedWrites()).isZero();
        RecentTraceStore restarted = store(10_000_000);
        restarted.restore();
        try {
            assertThat(restarted.recentEvents(400)).hasSize(300);
        } finally {
            restarted.close();
        }
    }

    @Test
    void shouldBoundShutdownAndAccountForQueuedWritesWhenWriterCannotHandoff() throws Exception {
        BlockingRecentTraceStore store = new BlockingRecentTraceStore(objectMapper, storageDirectory);
        store.restore();
        store.recordEvent(new RecentTraceStore.EventSample("TEST", "in-flight", 1, 0, "detail", NOW));
        assertThat(store.writerEntered.await(5, TimeUnit.SECONDS)).isTrue();
        for (int index = 0; index < 10; index++) {
            store.recordEvent(new RecentTraceStore.EventSample(
                    "TEST", "queued-" + index, index, 0, "detail", NOW.plusNanos(index + 1L)));
        }

        assertTimeoutPreemptively(java.time.Duration.ofSeconds(2), store::close);

        assertThat(store.droppedWrites()).isEqualTo(10);
        assertThat(store.pendingWrites()).isEqualTo(1);
        store.releaseWriter.countDown();
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            while (store.pendingWrites() != 0) Thread.sleep(10);
        });
        assertThat(store.recentEvents(20)).singleElement()
                .extracting(RecentTraceStore.EventSample::name).isEqualTo("in-flight");
    }

    @Test
    void shouldUseConfiguredShutdownWindowForSlowInFlightBatch() throws Exception {
        SlowHandoffRecentTraceStore store = new SlowHandoffRecentTraceStore(objectMapper, storageDirectory);
        store.restore();
        store.recordEvent(new RecentTraceStore.EventSample("TEST", "in-flight", 1, 0, "detail", NOW));
        assertThat(store.writerEntered.await(5, TimeUnit.SECONDS)).isTrue();
        store.recordEvent(new RecentTraceStore.EventSample("TEST", "queued", 1, 0, "detail", NOW.plusNanos(1)));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> closing = executor.submit(store::close);
            Thread.sleep(1_200);
            assertThat(closing).matches(future -> !future.isDone(),
                    "close should still honor the configured 3 second drain window");

            store.releaseWriter.countDown();
            closing.get(2, TimeUnit.SECONDS);
        } finally {
            store.releaseWriter.countDown();
            executor.shutdownNow();
        }

        assertThat(store.pendingWrites()).isZero();
        assertThat(store.droppedWrites()).isZero();
        assertThat(store.recentEvents(10)).extracting(RecentTraceStore.EventSample::name)
                .containsExactlyInAnyOrder("in-flight", "queued");
    }

    private static final class BlockingRecentTraceStore extends RecentTraceStore {
        private final CountDownLatch writerEntered = new CountDownLatch(1);
        private final CountDownLatch releaseWriter = new CountDownLatch(1);

        private BlockingRecentTraceStore(ObjectMapper objectMapper, Path directory) {
            this(objectMapper, new EasySensitiveDataMasker(objectMapper), directory);
        }

        private BlockingRecentTraceStore(ObjectMapper objectMapper, EasySensitiveDataMasker masker, Path directory) {
            super(objectMapper, masker, directory.toString(), 30,
                    10_000_000, 8_388_608, CLOCK, true, 100);
        }

        @Override
        void beforeAsyncWriteBatch() {
            writerEntered.countDown();
            boolean interrupted = false;
            while (true) {
                try {
                    releaseWriter.await();
                    break;
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static final class CountingSensitiveDataMasker extends EasySensitiveDataMasker {
        private final AtomicLong calls = new AtomicLong();

        private CountingSensitiveDataMasker(ObjectMapper objectMapper) {
            super(objectMapper);
        }

        @Override
        public String sanitizeJsonText(String text, int maxLength) {
            calls.incrementAndGet();
            return super.sanitizeJsonText(text, maxLength);
        }

        private long calls() {
            return calls.get();
        }
    }

    private static final class SlowHandoffRecentTraceStore extends RecentTraceStore {
        private final CountDownLatch writerEntered = new CountDownLatch(1);
        private final CountDownLatch releaseWriter = new CountDownLatch(1);

        private SlowHandoffRecentTraceStore(ObjectMapper objectMapper, Path directory) {
            super(objectMapper, new EasySensitiveDataMasker(objectMapper), directory.toString(), 30,
                    10_000_000, 8_388_608, CLOCK, true, 3_000);
        }

        @Override
        void beforeAsyncWriteBatch() {
            writerEntered.countDown();
            try {
                releaseWriter.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private RecentTraceStore store(long maxHistoryBytes) {
        return new RecentTraceStore(objectMapper, storageDirectory.toString(), 30, maxHistoryBytes, CLOCK);
    }

    private Path segment(String date) {
        return storageDirectory.resolve("runtime-diagnosis-" + date + ".jsonl");
    }

    private RecentTraceStore.TraceSample trace(String type, String traceId, String name,
                                               LocalDateTime finishedAt) {
        return new RecentTraceStore.TraceSample(type, traceId, name, "GET /api/test", 200,
                "tree", 10, finishedAt);
    }

    private RecentTraceStore.OperationSample operation(String outcome, LocalDateTime finishedAt) {
        return new RecentTraceStore.OperationSample("SQL", "trace-" + finishedAt, "Mapper#query", "worker",
                outcome, 10, 1, 128, 0, 0, 0, 0, "SELECT", "", "", finishedAt);
    }

    private RecentTraceStore.IncidentSample incident(String traceId, LocalDateTime finishedAt) {
        return new RecentTraceStore.IncidentSample("HTTP", traceId, "DemoController#run", "GET /demo", 500,
                "tree", 100, true, true, List.of("SLOW_REQUEST", "HTTP_ERROR"),
                "java.lang.IllegalStateException", "failed", "stack", finishedAt);
    }

    private void writeLegacyTraceWithoutType(Path file, String traceId, String name,
                                             LocalDateTime finishedAt) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("traceId", traceId);
        data.put("name", name);
        data.put("method", "GET /legacy");
        data.put("status", 200);
        data.put("tree", "tree");
        data.put("costMs", 1);
        data.put("finishedAt", finishedAt);
        writeRecord(file, "trace", data);
    }

    private void writeRecord(Path file, String kind, Object data) throws Exception {
        Files.createDirectories(file.getParent());
        String line = objectMapper.writeValueAsString(Map.of("kind", kind, "data", data))
                + System.lineSeparator();
        Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private void writeBytes(Path file, int size) throws Exception {
        Files.createDirectories(file.getParent());
        byte[] content = new byte[size];
        content[content.length - 1] = '\n';
        Files.write(file, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private ObjectMapper secondPrecisionApiMapper() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        JavaTimeModule module = new JavaTimeModule();
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(formatter));
        return new ObjectMapper().registerModule(module)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
}
