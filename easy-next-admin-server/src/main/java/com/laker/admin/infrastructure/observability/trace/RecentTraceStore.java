package com.laker.admin.infrastructure.observability.trace;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.laker.admin.infrastructure.security.masking.EasySensitiveDataMasker;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Durable, bounded local history for runtime diagnosis. */
@Component
@Slf4j
public class RecentTraceStore {
    private static final String LEGACY_FILE_NAME = "runtime-diagnosis.jsonl";
    private static final String SEGMENT_PREFIX = "runtime-diagnosis-";
    private static final String SEGMENT_SUFFIX = ".jsonl";
    private static final Pattern SEGMENT_PATTERN = Pattern.compile(
            "^runtime-diagnosis-(\\d{4}-\\d{2}-\\d{2})(?:-(\\d{3,}))?\\.jsonl$");
    private static final long DEFAULT_MAX_HISTORY_BYTES = 64L * 1024 * 1024;
    private static final long DEFAULT_MAX_SEGMENT_BYTES = 8L * 1024 * 1024;
    private static final long DEFAULT_PENDING_MAX_BYTES = 16L * 1024 * 1024;
    private static final int WRITE_QUEUE_CAPACITY = 16_384;
    private static final int WRITE_BATCH_SIZE = 256;
    private static final int REVERSE_READ_BUFFER_BYTES = 64 * 1024;
    private static final int MAX_JSON_LINE_BYTES = 16 * 1024 * 1024;
    private static final byte[] LINE_SEPARATOR_BYTES = System.lineSeparator().getBytes(StandardCharsets.UTF_8);
    private static final long DEFAULT_SHUTDOWN_DRAIN_TIMEOUT_MILLIS = 5_000;
    private static final long READ_DRAIN_TIMEOUT_MILLIS = 250;
    private static final long INCIDENT_SUMMARY_CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(15);
    private static final int MAX_INCIDENT_SUMMARY_SAMPLES = 100_000;
    private static final int MAX_CONCURRENT_HISTORY_SCANS = 2;
    private static final int OPERATION_SUMMARY_NAME_LIMIT = 200;
    private static final int OPERATION_SUMMARY_DETAIL_LIMIT = 500;
    private static final Pattern OPERATION_SUMMARY_WHITESPACE = Pattern.compile("\\s+");
    private static final int DIAGNOSTIC_TEXT_LIMIT = EasySensitiveDataMasker.DEFAULT_TEXT_LIMIT;
    private static final int URI_TEXT_LIMIT = 2_048;
    /**
     * Concurrent producers may enqueue timestamps slightly out of order even though the writer itself is FIFO.
     * Recent queries recover timestamp inversions within this bound while keeping memory proportional to the limit.
     */
    private static final Duration OUT_OF_ORDER_TOLERANCE = Duration.ofMinutes(1);
    /**
     * Operation durations are persisted in whole milliseconds while timestamps retain nanoseconds. Allow the
     * sub-millisecond rounding remainder when deriving a root start, without reopening the former one-minute window
     * that could merge two requests reusing the same externally supplied trace id.
     */
    private static final Duration CORRELATION_START_ROUNDING = Duration.ofMillis(1);

    private final ObjectMapper objectMapper;
    private final EasySensitiveDataMasker sensitiveDataMasker;
    private final Path storageDirectory;
    private final Path legacyStorageFile;
    private final int retentionDays;
    private final long maxHistoryBytes;
    private final long maxSegmentBytes;
    private final Clock clock;
    private final boolean enabled;
    private final long shutdownDrainTimeoutMillis;
    private final long pendingMaxBytes;
    private final AtomicLong activeTransactions = new AtomicLong();
    private final ArrayBlockingQueue<PendingEvent> writeQueue = new ArrayBlockingQueue<>(WRITE_QUEUE_CAPACITY);
    private final AtomicLong pendingWrites = new AtomicLong();
    private final AtomicLong pendingBytes = new AtomicLong();
    private final AtomicLong droppedWrites = new AtomicLong();
    private final AtomicLong byteBudgetDroppedWrites = new AtomicLong();
    private final BoundedDiagnosticReadExecutor historyReads =
            new BoundedDiagnosticReadExecutor(MAX_CONCURRENT_HISTORY_SCANS);
    private final Object fileLock = new Object();
    private final Object incidentSummaryLock = new Object();
    private volatile boolean acceptingWrites = true;
    private volatile boolean shutdownDrainRequested;
    private volatile Thread writerThread;
    private volatile long knownStorageBytes;
    private volatile Path activeSegment;
    private volatile CachedIncidentSummary cachedIncidentSummary;

    @Autowired
    public RecentTraceStore(ObjectMapper objectMapper,
                            EasySensitiveDataMasker sensitiveDataMasker,
                            @Value("${easy.monitor.diagnosis.storage-dir:./data/diagnosis}") String storageDir,
                            @Value("${easy.monitor.diagnosis.retention-days:30}") int retentionDays,
                            @Value("${easy.monitor.diagnosis.history-max-bytes:67108864}") long maxHistoryBytes,
                            @Value("${easy.monitor.diagnosis.history-segment-max-bytes:8388608}") long maxSegmentBytes,
                            @Value("${easy.monitor.diagnosis.pending-max-bytes:16777216}") long pendingMaxBytes,
                            @Value("${easy.monitor.diagnosis.enabled:true}") boolean enabled,
                            @Value("${easy.features.monitor:true}") boolean monitorEnabled) {
        this(objectMapper, sensitiveDataMasker, storageDir, retentionDays, maxHistoryBytes, maxSegmentBytes,
                Clock.systemDefaultZone(), enabled && monitorEnabled, DEFAULT_SHUTDOWN_DRAIN_TIMEOUT_MILLIS,
                pendingMaxBytes);
    }

    RecentTraceStore(ObjectMapper objectMapper, String storageDir, int retentionDays,
                     long maxHistoryBytes, Clock clock) {
        this(objectMapper, new EasySensitiveDataMasker(objectMapper), storageDir, retentionDays, maxHistoryBytes,
                DEFAULT_MAX_SEGMENT_BYTES, clock, true);
    }

    RecentTraceStore(ObjectMapper objectMapper, String storageDir, int retentionDays,
                     long maxHistoryBytes, Clock clock, boolean enabled) {
        this(objectMapper, new EasySensitiveDataMasker(objectMapper), storageDir, retentionDays, maxHistoryBytes,
                DEFAULT_MAX_SEGMENT_BYTES, clock, enabled);
    }

    RecentTraceStore(ObjectMapper objectMapper, String storageDir, int retentionDays,
                     long maxHistoryBytes, long maxSegmentBytes, Clock clock, boolean enabled) {
        this(objectMapper, new EasySensitiveDataMasker(objectMapper), storageDir, retentionDays, maxHistoryBytes,
                maxSegmentBytes, clock, enabled);
    }

    RecentTraceStore(ObjectMapper objectMapper, EasySensitiveDataMasker sensitiveDataMasker,
                     String storageDir, int retentionDays, long maxHistoryBytes, Clock clock) {
        this(objectMapper, sensitiveDataMasker, storageDir, retentionDays, maxHistoryBytes,
                DEFAULT_MAX_SEGMENT_BYTES, clock, true);
    }

    RecentTraceStore(ObjectMapper objectMapper, EasySensitiveDataMasker sensitiveDataMasker,
                     String storageDir, int retentionDays, long maxHistoryBytes, long maxSegmentBytes,
                     Clock clock, boolean enabled) {
        this(objectMapper, sensitiveDataMasker, storageDir, retentionDays, maxHistoryBytes, maxSegmentBytes,
                clock, enabled, DEFAULT_SHUTDOWN_DRAIN_TIMEOUT_MILLIS);
    }

    RecentTraceStore(ObjectMapper objectMapper, EasySensitiveDataMasker sensitiveDataMasker,
                     String storageDir, int retentionDays, long maxHistoryBytes, long maxSegmentBytes,
                     Clock clock, boolean enabled, long shutdownDrainTimeoutMillis) {
        this(objectMapper, sensitiveDataMasker, storageDir, retentionDays, maxHistoryBytes, maxSegmentBytes,
                clock, enabled, shutdownDrainTimeoutMillis, DEFAULT_PENDING_MAX_BYTES);
    }

    RecentTraceStore(ObjectMapper objectMapper, EasySensitiveDataMasker sensitiveDataMasker,
                     String storageDir, int retentionDays, long maxHistoryBytes, long maxSegmentBytes,
                     Clock clock, boolean enabled, long shutdownDrainTimeoutMillis, long pendingMaxBytes) {
        this.objectMapper = storageObjectMapper(objectMapper);
        this.sensitiveDataMasker = Objects.requireNonNull(sensitiveDataMasker, "sensitiveDataMasker");
        this.storageDirectory = Path.of(storageDir).toAbsolutePath().normalize();
        this.legacyStorageFile = storageDirectory.resolve(LEGACY_FILE_NAME);
        this.retentionDays = Math.max(30, retentionDays);
        this.maxHistoryBytes = maxHistoryBytes > 0 ? maxHistoryBytes : DEFAULT_MAX_HISTORY_BYTES;
        long configuredSegmentBytes = maxSegmentBytes > 0 ? maxSegmentBytes : DEFAULT_MAX_SEGMENT_BYTES;
        this.maxSegmentBytes = Math.max(1, Math.min(configuredSegmentBytes, this.maxHistoryBytes));
        this.clock = clock;
        this.enabled = enabled;
        this.shutdownDrainTimeoutMillis = Math.max(1, shutdownDrainTimeoutMillis);
        this.pendingMaxBytes = pendingMaxBytes > 0 ? pendingMaxBytes : DEFAULT_PENDING_MAX_BYTES;
    }

    /**
     * Runtime history is an internal storage contract and must not inherit the API's second-precision
     * {@code yyyy-MM-dd HH:mm:ss} formatter. Keep a private mapper so JSONL retains the event's exact
     * timestamp while the public API format remains unchanged. The reader accepts the previous space-separated
     * representation so existing local history survives an upgrade.
     */
    private static ObjectMapper storageObjectMapper(ObjectMapper apiObjectMapper) {
        ObjectMapper storageMapper = apiObjectMapper.copy();
        SimpleModule preciseTimeModule = new SimpleModule("runtime-diagnosis-storage-time");
        preciseTimeModule.addSerializer(LocalDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator generator,
                                  SerializerProvider serializers) throws IOException {
                generator.writeString(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value));
            }
        });
        preciseTimeModule.addDeserializer(LocalDateTime.class, new JsonDeserializer<>() {
            @Override
            public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                String value = parser.getValueAsString();
                if (value == null) {
                    return (LocalDateTime) context.handleUnexpectedToken(LocalDateTime.class, parser);
                }
                try {
                    return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                } catch (DateTimeParseException isoFailure) {
                    try {
                        return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    } catch (DateTimeParseException legacyFailure) {
                        return (LocalDateTime) context.handleWeirdStringValue(LocalDateTime.class, value,
                                "expected ISO_LOCAL_DATE_TIME or legacy yyyy-MM-dd HH:mm:ss");
                    }
                }
            }
        });
        storageMapper.registerModule(preciseTimeModule);
        return storageMapper;
    }

    @PostConstruct
    public void restore() {
        if (!enabled) return;
        try {
            Files.createDirectories(storageDirectory);
            activeSegment = resolveWritableSegment(0);
            repairTrailingPartialLine(activeSegment);
            pruneExpired();
        } catch (Exception exception) {
            knownStorageBytes = calculateStorageBytes();
            log.error("无法恢复运行诊断本地文件目录 {}，本次运行可能无法保留完整诊断记录",
                    storageDirectory, exception);
        } finally {
            startWriterIfNeeded();
        }
    }

    @PreDestroy
    public synchronized void close() {
        if (shutdownDrainRequested) return;
        acceptingWrites = false;
        shutdownDrainRequested = true;
        Thread thread = writerThread;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(shutdownDrainTimeoutMillis);
        if (thread != null) {
            // Do not interrupt: a FileChannel operation is interruptible and would turn an otherwise writable
            // in-flight record into ClosedByInterruptException. The queue poll is already bounded to 250 ms.
            // Give the current batch the complete configured shutdown window. Once it completes, the shutdown flag
            // makes the writer exit and any remaining queue entries are drained synchronously with time left.
            joinUntil(thread, deadline);
        }

        if (thread != null && thread.isAlive()) {
            long discarded = discardQueuedWrites();
            log.error("运行诊断写入线程未在关闭时限内交接，已准确丢弃队列中 {} 条记录；仍有 {} 条正在写入",
                    discarded, pendingWrites.get());
            return;
        }
        drainQueuedWritesSynchronously(deadline);
    }

    public void recordTrace(String traceId, String name, String method, int status, String tree, long costMs) {
        recordTrace("HTTP", traceId, name, method, status, tree, costMs);
    }

    public void recordTrace(String type, String traceId, String name, String method, int status, String tree,
                            long costMs) {
        append("trace", new TraceSample(type, traceId, name, method, status, tree, costMs, now()));
    }

    /**
     * Persists one canonical incident for a root operation. A root that is both slow and failed is deliberately
     * represented by this single record; Events, Slow, Errors and Trace are projections of the same data.
     */
    public void recordIncident(String type, String traceId, String name, String method, int status, String tree,
                               long costMs, boolean slow, boolean error, List<String> reasons,
                               String errorType, String errorMessage, String stackTrace) {
        recordIncident(type, traceId, name, method, status, tree, costMs, slow, error, reasons,
                errorType, errorMessage, stackTrace, null, null, null);
    }

    public void recordIncident(String type, String traceId, String name, String method, int status, String tree,
                               long costMs, boolean slow, boolean error, List<String> reasons,
                               String errorType, String errorMessage, String stackTrace,
                               IncidentCause primaryCause, IncidentCause slowCause, IncidentCause errorCause) {
        recordIncident(type, traceId, name, method, status, tree, costMs, slow, error, reasons,
                errorType, errorMessage, stackTrace, primaryCause, slowCause, errorCause, now());
    }

    public void recordIncident(String type, String traceId, String name, String method, int status, String tree,
                               long costMs, boolean slow, boolean error, List<String> reasons,
                               String errorType, String errorMessage, String stackTrace,
                               IncidentCause primaryCause, IncidentCause slowCause, IncidentCause errorCause,
                               LocalDateTime finishedAt) {
        if (!slow && !error) return;
        append("incident", new IncidentSample(type, traceId, name, method, status, tree, costMs, slow, error,
                reasons, errorType, errorMessage, stackTrace, primaryCause, slowCause, errorCause,
                finishedAt == null ? now() : finishedAt));
    }

    public void recordIncident(String type, String traceId, String name, String method, int status, String tree,
                               long costMs, boolean slow, boolean error, List<String> reasons) {
        recordIncident(type, traceId, name, method, status, tree, costMs, slow, error, reasons, "", "", "");
    }

    public void recordTransaction(String traceId, String name, boolean success, long costMs) {
        recordTransaction(traceId, name, success, costMs, now());
    }

    public void recordTransaction(String traceId, String name, boolean success, long costMs,
                                  LocalDateTime finishedAt) {
        append("transaction", new TransactionSample(traceId, name, success, costMs,
                finishedAt == null ? now() : finishedAt));
    }

    public void recordMetricsSnapshot(List<MetricSample> samples) {
        append("metrics", new MetricsSnapshot(now(), samples));
    }

    public void recordOperation(OperationSample sample) {
        append("operation", sample);
    }

    public void recordEvent(EventSample sample) {
        append("event", sample);
    }

    public List<TraceSample> traces() {
        return recentTraces(200);
    }

    public List<TransactionSample> transactions() {
        return recentTransactions(200);
    }

    public List<OperationSample> operations() {
        return recentOperations(200);
    }

    public List<TraceSample> recentTraces(int limit) {
        return recentTraces(limit, null, null);
    }

    public List<TraceSample> recentTraces(int limit, LocalDateTime from, LocalDateTime before) {
        return readRecentTraces(limit, from, before, trace -> true);
    }

    public List<TraceSample> recentTracesMatching(int limit, LocalDateTime from, LocalDateTime before,
                                                  Predicate<TraceSample> predicate) {
        return readRecentTraces(limit, from, before, predicate);
    }

    public List<IncidentSample> recentIncidents(int limit) {
        return recentIncidents(limit, null, null);
    }

    public List<IncidentSample> recentIncidents(int limit, LocalDateTime from, LocalDateTime before) {
        return readRecent("incident", IncidentSample.class, limit, IncidentSample::finishedAt, from, before);
    }

    public List<IncidentSample> recentIncidentsMatching(int limit, LocalDateTime from, LocalDateTime before,
                                                        Predicate<IncidentSample> predicate) {
        return readRecent("incident", IncidentSample.class, limit, IncidentSample::finishedAt,
                from, before, predicate);
    }

    /**
     * Returns the bounded incident counts used by the live diagnosis header. The page polls this value every five
     * seconds, while rebuilding it can deserialize tens of thousands of JSONL rows. A short monotonic TTL and one
     * rebuild lock keep that polling path bounded; incident list/detail endpoints continue to read current data.
     */
    public IncidentSummary recentIncidentSummary(Duration window, int sampleLimit) {
        Duration safeWindow = window == null || window.isZero() || window.isNegative()
                ? Duration.ofHours(1) : window;
        int safeLimit = Math.max(1, Math.min(sampleLimit, MAX_INCIDENT_SUMMARY_SAMPLES));
        long nowNanos = System.nanoTime();
        CachedIncidentSummary cached = cachedIncidentSummary;
        if (cached != null && cached.matches(safeWindow, safeLimit, nowNanos)) return cached.summary();

        // Enter the shared read budget before waiting for the rebuild lock. At most two servlet threads can then be
        // involved in a cold summary rebuild; further dashboard polls receive a bounded 429 instead of piling up.
        return historyReads.execute(() -> {
            synchronized (incidentSummaryLock) {
                long admittedNowNanos = System.nanoTime();
                CachedIncidentSummary admittedCached = cachedIncidentSummary;
                if (admittedCached != null
                        && admittedCached.matches(safeWindow, safeLimit, admittedNowNanos)) {
                    return admittedCached.summary();
                }

                LocalDateTime sampledAt = now();
                LocalDateTime from;
                try {
                    from = sampledAt.minus(safeWindow);
                } catch (RuntimeException ignored) {
                    from = LocalDateTime.MIN;
                }
                List<IncidentSample> samples = recentIncidents(safeLimit + 1, from, null);
                boolean truncated = samples.size() > safeLimit;
                List<IncidentSample> counted = truncated ? samples.subList(0, safeLimit) : samples;
                IncidentSummary summary = new IncidentSummary(sampledAt, safeWindow,
                        counted.size(), counted.stream().filter(IncidentSample::slow).count(),
                        counted.stream().filter(IncidentSample::error).count(), truncated, safeLimit);
                cachedIncidentSummary = new CachedIncidentSummary(
                        safeWindow, safeLimit, admittedNowNanos, summary);
                return summary;
            }
        });
    }

    public List<TransactionSample> recentTransactions(int limit) {
        return recentTransactions(limit, null, null);
    }

    public List<TransactionSample> recentTransactions(int limit, LocalDateTime from, LocalDateTime before) {
        return readRecent("transaction", TransactionSample.class, limit, TransactionSample::finishedAt, from, before);
    }

    public List<TransactionSample> recentTransactionsMatching(int limit, LocalDateTime from, LocalDateTime before,
                                                               Predicate<TransactionSample> predicate) {
        return readRecent("transaction", TransactionSample.class, limit, TransactionSample::finishedAt,
                from, before, predicate);
    }

    public List<OperationSample> recentOperations(int limit) {
        return recentOperations(limit, null, null);
    }

    public List<OperationSample> recentOperations(int limit, LocalDateTime from, LocalDateTime before) {
        return readRecent("operation", OperationSample.class, limit, OperationSample::finishedAt, from, before);
    }

    public List<OperationSample> recentOperationsMatching(int limit, LocalDateTime from, LocalDateTime before,
                                                          Predicate<OperationSample> predicate) {
        return readRecent("operation", OperationSample.class, limit, OperationSample::finishedAt,
                from, before, predicate);
    }

    /**
     * Scans recent operation records through a compact projection intended for aggregate statistics. The bounded
     * top-K buffer never retains stack traces, lock text, thread names, trace ids or child-operation maps, so a
     * 30-day statistics query cannot materialize tens of thousands of full incident DTOs at once.
     */
    public OperationScanResult scanRecentOperationSummaries(int limit, LocalDateTime from, LocalDateTime before,
                                                            Predicate<OperationSummarySample> predicate,
                                                            Consumer<OperationSummarySample> visitor) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(visitor, "visitor");
        if (!enabled || limit <= 0 || from != null && before != null && !from.isBefore(before)) {
            return new OperationScanResult(0, false);
        }
        return historyReads.execute(() -> scanRecentOperationSummariesAdmitted(
                limit, from, before, predicate, visitor));
    }

    private OperationScanResult scanRecentOperationSummariesAdmitted(
            int limit, LocalDateTime from, LocalDateTime before,
            Predicate<OperationSummarySample> predicate, Consumer<OperationSummarySample> visitor) {
        awaitPendingWrites();
        int safeLimit = Math.max(1, limit);
        RecentTopK<OperationSummarySample> result = new RecentTopK<>(safeLimit);
        boolean[] datedScanComplete = {false};
        for (Path file : historyFilesNewestFirst()) {
            boolean legacy = file.equals(legacyStorageFile);
            if (datedScanComplete[0] && !legacy) continue;
            LocalDate fileDate = segmentDate(file);
            if (datedFileOutsideWindow(fileDate, from, before)) continue;
            readJsonLinesReverse(file, root -> {
                if (!"operation".equals(root.path("kind").asText())) return true;
                JsonNode data = root.path("data");
                LocalDateTime occurredAt = operationFinishedAt(data);
                if (occurredAt == null) return true;
                if (before != null && !occurredAt.isBefore(before)) return true;
                if (!legacy && result.isBeyondReorderWindow(occurredAt, from)) {
                    datedScanComplete[0] = true;
                    return false;
                }
                if (from != null && occurredAt.isBefore(from)) return true;
                OperationSummarySample sample = operationSummary(data, occurredAt);
                if (!predicate.test(sample)) return true;
                result.offer(sample, occurredAt);
                return true;
            });
        }
        int readSamples = result.size();
        result.forEachNewest(visitor);
        return new OperationScanResult(readSamples, readSamples >= safeLimit);
    }

    private LocalDateTime operationFinishedAt(JsonNode data) {
        try {
            JsonNode value = data.get("finishedAt");
            return value == null || value.isNull() ? null : objectMapper.treeToValue(value, LocalDateTime.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static OperationSummarySample operationSummary(JsonNode data, LocalDateTime finishedAt) {
        String type = data.path("type").asText("");
        String detail = "SQL".equalsIgnoreCase(type)
                ? boundedOperationSummaryText(data.path("detail").asText(""), OPERATION_SUMMARY_DETAIL_LIMIT)
                : "";
        return new OperationSummarySample(type,
                boundedOperationSummaryText(data.path("name").asText(""), OPERATION_SUMMARY_NAME_LIMIT),
                data.path("outcome").asText(""), data.path("durationMs").asLong(),
                data.path("cpuTimeMs").asLong(), data.path("allocatedBytes").asLong(), detail, finishedAt);
    }

    private static String boundedOperationSummaryText(String value, int maxLength) {
        if (value == null || value.isBlank()) return "";
        String normalized = OPERATION_SUMMARY_WHITESPACE.matcher(value).replaceAll(" ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "…";
    }

    public List<EventSample> recentEvents(int limit) {
        return recentEvents(limit, null, null);
    }

    public List<EventSample> recentEvents(int limit, LocalDateTime from, LocalDateTime before) {
        return readRecent("event", EventSample.class, limit, EventSample::occurredAt, from, before);
    }

    public TraceSample findTrace(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }
        return historyReads.execute(() -> findTraceAdmitted(traceId));
    }

    private TraceSample findTraceAdmitted(String traceId) {
        awaitPendingWrites();
        for (Path file : historyFilesNewestFirst()) {
            TraceSample match = findLatestTraceInFile(file, traceId);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    public IncidentSample findIncident(String traceId) {
        if (traceId == null || traceId.isBlank()) return null;
        return historyReads.execute(() -> findIncidentAdmitted(traceId));
    }

    private IncidentSample findIncidentAdmitted(String traceId) {
        awaitPendingWrites();
        for (Path file : historyFilesNewestFirst()) {
            IncidentSample match = findLatestIncidentInFile(file, traceId);
            if (match != null) return match;
        }
        return null;
    }

    public TraceCorrelation findCorrelation(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return new TraceCorrelation(null, List.of(), List.of());
        }
        return historyReads.execute(() -> findCorrelationAdmitted(traceId));
    }

    private TraceCorrelation findCorrelationAdmitted(String traceId) {
        awaitPendingWrites();
        TraceSample[] latestTrace = new TraceSample[1];
        IncidentSample[] latestIncident = new IncidentSample[1];
        LocalDateTime[] traceStartedAt = new LocalDateTime[1];
        List<TransactionSample> transactions = new ArrayList<>();
        List<OperationSample> operations = new ArrayList<>();
        for (Path file : historyFilesNewestFirst()) {
            if (file.equals(legacyStorageFile)) continue;
            LocalDate fileDate = segmentDate(file);
            if (traceStartedAt[0] != null && fileDate != null
                    && fileDate.isBefore(traceStartedAt[0].toLocalDate())) break;
            scanCorrelationFile(file, traceId, latestTrace, latestIncident, traceStartedAt, transactions, operations);
        }
        // The pre-segmentation file may contain records from any date, so inspect it separately during migration.
        if (Files.isRegularFile(legacyStorageFile)) {
            scanCorrelationFile(legacyStorageFile, traceId, latestTrace, latestIncident,
                    traceStartedAt, transactions, operations);
        }
        TraceSample root = latestTrace[0];
        if (root == null) {
            // A trace id alone is not an execution boundary. Orphan child records can remain after a partial
            // legacy migration or damaged tail, and combining all of them would merge unrelated client reuse.
            return new TraceCorrelation(null, List.of(), List.of(), null);
        }
        transactions.sort(Comparator.comparing(TransactionSample::finishedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        operations.sort(Comparator.comparing(OperationSample::finishedAt,
                Comparator.nullsLast(Comparator.naturalOrder())));
        IncidentSample rootIncident = sameRoot(root, latestIncident[0]) ? latestIncident[0] : null;
        if (root != null && root.finishedAt() != null) {
            transactions.removeIf(sample -> !insideRootWindow(root, sample.finishedAt()));
            operations.removeIf(sample -> !insideRootWindow(root, sample.finishedAt()));
        }
        return new TraceCorrelation(root, List.copyOf(transactions), List.copyOf(operations), rootIncident);
    }

    private static boolean sameRoot(TraceSample root, IncidentSample incident) {
        if (root == null || incident == null) return false;
        return root.costMs() == incident.costMs()
                && Objects.equals(root.type(), incident.type())
                && Objects.equals(root.name(), incident.name())
                && Objects.equals(root.method(), incident.method())
                && Objects.equals(root.finishedAt(), incident.finishedAt());
    }

    private static boolean insideRootWindow(TraceSample root, LocalDateTime timestamp) {
        if (root == null || root.finishedAt() == null || timestamp == null) return false;
        LocalDateTime startedAt = correlationStartedAt(root);
        return (startedAt == null || !timestamp.isBefore(startedAt)) && !timestamp.isAfter(root.finishedAt());
    }

    private static LocalDateTime correlationStartedAt(TraceSample root) {
        if (root == null || root.finishedAt() == null) return null;
        try {
            return root.finishedAt().minus(Duration.ofMillis(Math.max(0, root.costMs())))
                    .minus(CORRELATION_START_ROUNDING);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean scanCorrelationFile(Path file, String traceId, TraceSample[] latestTrace,
                                        IncidentSample[] latestIncident,
                                        LocalDateTime[] traceStartedAt, List<TransactionSample> transactions,
                                        List<OperationSample> operations) {
        boolean[] matched = {false};
        readJsonLinesReverse(file, root -> {
            JsonNode data = root.path("data");
            LocalDateTime timestamp = eventTime(root);
            if (traceStartedAt[0] != null && timestamp != null
                    && timestamp.isBefore(traceStartedAt[0].minus(OUT_OF_ORDER_TOLERANCE))) {
                return false;
            }
            if (!traceId.equals(data.path("traceId").asText())) return true;
            matched[0] = true;
            switch (root.path("kind").asText()) {
                case "incident" -> {
                    IncidentSample sample = objectMapper.treeToValue(data, IncidentSample.class);
                    if (latestIncident[0] == null) latestIncident[0] = sample;
                    if (latestTrace[0] == null) {
                        latestTrace[0] = sample.trace();
                        traceStartedAt[0] = correlationStartedAt(sample.trace());
                    }
                }
                case "trace" -> {
                    TraceSample sample = objectMapper.treeToValue(data, TraceSample.class);
                    if (latestTrace[0] == null) {
                        latestTrace[0] = sample;
                        traceStartedAt[0] = correlationStartedAt(sample);
                    }
                }
                case "transaction" -> transactions.add(objectMapper.treeToValue(data, TransactionSample.class));
                case "operation" -> operations.add(objectMapper.treeToValue(data, OperationSample.class));
                default -> {
                    // Other event kinds do not carry per-trace correlation data.
                }
            }
            return true;
        });
        return matched[0];
    }

    public int retentionDays() {
        return retentionDays;
    }

    public long maxHistoryBytes() {
        return maxHistoryBytes;
    }

    public long storageBytes() {
        awaitPendingWrites();
        return calculateStorageBytes();
    }

    public long pendingWrites() {
        return pendingWrites.get();
    }

    public long pendingBytes() {
        return pendingBytes.get();
    }

    public long pendingMaxBytes() {
        return pendingMaxBytes;
    }

    public long droppedWrites() {
        return droppedWrites.get();
    }

    public long byteBudgetDroppedWrites() {
        return byteBudgetDroppedWrites.get();
    }

    public int queueCapacity() {
        return WRITE_QUEUE_CAPACITY;
    }

    public String oldestSegmentDate() {
        return segmentFiles().stream().map(this::segmentDate).filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder()).map(LocalDate::toString).orElse("");
    }

    public long segmentCoverageDays() {
        String oldest = oldestSegmentDate();
        if (oldest.isBlank()) return 0;
        try {
            return Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.parse(oldest), LocalDate.now(clock)) + 1);
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public List<MetricHistoryPoint> metricHistory(String name, Map<String, String> tags, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return historyReads.execute(() -> metricHistoryAdmitted(name, tags, limit));
    }

    private List<MetricHistoryPoint> metricHistoryAdmitted(String name, Map<String, String> tags, int limit) {
        awaitPendingWrites();
        int safeLimit = Math.max(1, limit);
        Map<String, String> requestedTags = tags == null ? Map.of() : tags;
        List<MetricHistoryPoint> result = new ArrayList<>(Math.min(safeLimit, 1_024));
        for (Path file : historyFilesNewestFirst()) {
            readJsonLinesReverse(file, root -> {
                if (!"metrics".equals(root.path("kind").asText())) {
                    return true;
                }
                MetricsSnapshot snapshot = objectMapper.treeToValue(root.path("data"), MetricsSnapshot.class);
                if (snapshot.samples() == null) {
                    return true;
                }
                for (MetricSample metric : snapshot.samples()) {
                    Map<String, String> metricTags = metric.tags() == null ? Map.of() : metric.tags();
                    if (!name.equals(metric.name()) || !requestedTags.equals(metricTags)) {
                        continue;
                    }
                    result.add(new MetricHistoryPoint(
                            snapshot.capturedAt() == null ? "" : snapshot.capturedAt().toString(),
                            metric.tags(), metric.measurements()));
                    if (result.size() >= safeLimit) return false;
                }
                return result.size() < safeLimit;
            });
            if (result.size() >= safeLimit) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private <T> List<T> readRecent(String kind, Class<T> type, int limit,
                                   Function<T, LocalDateTime> timestamp, LocalDateTime from,
                                   LocalDateTime before) {
        return readRecent(kind, type, limit, timestamp, from, before, item -> true);
    }

    private <T> List<T> readRecent(String kind, Class<T> type, int limit,
                                   Function<T, LocalDateTime> timestamp, LocalDateTime from,
                                   LocalDateTime before, Predicate<T> predicate) {
        if (!enabled) return List.of();
        if (limit <= 0) {
            return List.of();
        }
        if (from != null && before != null && !from.isBefore(before)) return List.of();
        return historyReads.execute(() -> readRecentAdmitted(
                kind, type, limit, timestamp, from, before, predicate));
    }

    private <T> List<T> readRecentAdmitted(String kind, Class<T> type, int limit,
                                           Function<T, LocalDateTime> timestamp, LocalDateTime from,
                                           LocalDateTime before, Predicate<T> predicate) {
        awaitPendingWrites();
        int safeLimit = Math.max(1, limit);
        RecentTopK<T> result = new RecentTopK<>(safeLimit);
        boolean[] datedScanComplete = {false};
        for (Path file : historyFilesNewestFirst()) {
            boolean legacy = file.equals(legacyStorageFile);
            if (datedScanComplete[0] && !legacy) continue;
            LocalDate fileDate = segmentDate(file);
            if (datedFileOutsideWindow(fileDate, from, before)) continue;
            readJsonLinesReverse(file, root -> {
                if (!kind.equals(root.path("kind").asText())) {
                    return true;
                }
                T item = objectMapper.treeToValue(root.path("data"), type);
                LocalDateTime occurredAt = timestamp.apply(item);
                if (occurredAt == null) return true;
                if (before != null && !occurredAt.isBefore(before)) return true;
                if (!legacy && result.isBeyondReorderWindow(occurredAt, from)) {
                    datedScanComplete[0] = true;
                    return false;
                }
                if (from != null && occurredAt.isBefore(from)) return true;
                if (!predicate.test(item)) return true;
                result.offer(item, occurredAt);
                return true;
            });
        }
        return result.newestFirst();
    }

    /** Reads both canonical incidents and the pre-incident trace format during the local-history transition. */
    private List<TraceSample> readRecentTraces(int limit, LocalDateTime from, LocalDateTime before,
                                               Predicate<TraceSample> predicate) {
        if (!enabled) return List.of();
        if (limit <= 0) return List.of();
        if (from != null && before != null && !from.isBefore(before)) return List.of();
        return historyReads.execute(() -> readRecentTracesAdmitted(limit, from, before, predicate));
    }

    private List<TraceSample> readRecentTracesAdmitted(int limit, LocalDateTime from, LocalDateTime before,
                                                       Predicate<TraceSample> predicate) {
        awaitPendingWrites();
        int safeLimit = Math.max(1, limit);
        RecentTopK<TraceSample> result = new RecentTopK<>(safeLimit);
        boolean[] datedScanComplete = {false};
        for (Path file : historyFilesNewestFirst()) {
            boolean legacy = file.equals(legacyStorageFile);
            if (datedScanComplete[0] && !legacy) continue;
            LocalDate fileDate = segmentDate(file);
            if (datedFileOutsideWindow(fileDate, from, before)) continue;
            readJsonLinesReverse(file, root -> {
                String kind = root.path("kind").asText();
                TraceSample item;
                if ("incident".equals(kind)) {
                    item = objectMapper.treeToValue(root.path("data"), IncidentSample.class).trace();
                } else if ("trace".equals(kind)) {
                    item = objectMapper.treeToValue(root.path("data"), TraceSample.class);
                } else {
                    return true;
                }
                LocalDateTime occurredAt = item.finishedAt();
                if (occurredAt == null) return true;
                if (before != null && !occurredAt.isBefore(before)) return true;
                if (!legacy && result.isBeyondReorderWindow(occurredAt, from)) {
                    datedScanComplete[0] = true;
                    return false;
                }
                if (from != null && occurredAt.isBefore(from)) return true;
                if (!predicate.test(item)) return true;
                result.offer(item, occurredAt);
                return true;
            });
        }
        return result.newestFirst();
    }

    private static boolean datedFileOutsideWindow(LocalDate fileDate, LocalDateTime from, LocalDateTime before) {
        if (fileDate == null) return false;
        LocalDate earliestDate = from == null ? null : from.minus(OUT_OF_ORDER_TOLERANCE).toLocalDate();
        LocalDate latestDate = before == null ? null : before.plus(OUT_OF_ORDER_TOLERANCE).toLocalDate();
        return earliestDate != null && fileDate.isBefore(earliestDate)
                || latestDate != null && fileDate.isAfter(latestDate);
    }

    /**
     * Keeps memory proportional to the requested page while scanning far enough past the current Kth timestamp
     * to absorb records appended out of timestamp order by concurrent producers.
     */
    private static final class RecentTopK<T> {
        private final int limit;
        private final PriorityQueue<TimestampedValue<T>> values;
        private long sequence;

        private RecentTopK(int limit) {
            this.limit = limit;
            this.values = new PriorityQueue<>(RecentTopK::compareWorstFirst);
        }

        private void offer(T value, LocalDateTime timestamp) {
            TimestampedValue<T> candidate = new TimestampedValue<>(value, timestamp, sequence++);
            if (values.size() < limit) {
                values.offer(candidate);
                return;
            }
            if (compareWorstFirst(candidate, values.peek()) > 0) {
                values.poll();
                values.offer(candidate);
            }
        }

        private boolean isBeyondReorderWindow(LocalDateTime timestamp, LocalDateTime from) {
            LocalDateTime boundary = values.size() >= limit ? values.peek().timestamp() : from;
            return boundary != null && timestamp.isBefore(boundary.minus(OUT_OF_ORDER_TOLERANCE));
        }

        private List<T> newestFirst() {
            return values.stream().sorted(RecentTopK::compareNewestFirst)
                    .map(TimestampedValue::value).toList();
        }

        private int size() {
            return values.size();
        }

        private void forEachNewest(Consumer<T> visitor) {
            List<TimestampedValue<T>> oldestFirst = new ArrayList<>(values.size());
            while (!values.isEmpty()) oldestFirst.add(values.poll());
            for (int index = oldestFirst.size() - 1; index >= 0; index--) {
                TimestampedValue<T> value = oldestFirst.set(index, null);
                visitor.accept(value.value());
            }
        }

        private static int compareWorstFirst(TimestampedValue<?> left, TimestampedValue<?> right) {
            int timestampOrder = left.timestamp().compareTo(right.timestamp());
            return timestampOrder != 0 ? timestampOrder : Long.compare(right.sequence(), left.sequence());
        }

        private static int compareNewestFirst(TimestampedValue<?> left, TimestampedValue<?> right) {
            int timestampOrder = right.timestamp().compareTo(left.timestamp());
            return timestampOrder != 0 ? timestampOrder : Long.compare(left.sequence(), right.sequence());
        }
    }

    private record TimestampedValue<T>(T value, LocalDateTime timestamp, long sequence) { }

    private record CachedIncidentSummary(Duration window, int sampleLimit, long createdAtNanos,
                                         IncidentSummary summary) {
        private boolean matches(Duration requestedWindow, int requestedLimit, long nowNanos) {
            long age = nowNanos - createdAtNanos;
            return sampleLimit == requestedLimit && window.equals(requestedWindow)
                    && age >= 0 && age < INCIDENT_SUMMARY_CACHE_TTL_NANOS;
        }
    }

    private TraceSample findLatestTraceInFile(Path file, String traceId) {
        TraceSample[] match = new TraceSample[1];
        readJsonLinesReverse(file, root -> {
            String kind = root.path("kind").asText();
            TraceSample sample;
            if ("incident".equals(kind)) {
                sample = objectMapper.treeToValue(root.path("data"), IncidentSample.class).trace();
            } else if ("trace".equals(kind)) {
                sample = objectMapper.treeToValue(root.path("data"), TraceSample.class);
            } else return true;
            if (traceId.equals(sample.traceId())) {
                match[0] = sample;
                return false;
            }
            return true;
        });
        return match[0];
    }

    private IncidentSample findLatestIncidentInFile(Path file, String traceId) {
        IncidentSample[] match = new IncidentSample[1];
        readJsonLinesReverse(file, root -> {
            if (!"incident".equals(root.path("kind").asText())) return true;
            IncidentSample sample = objectMapper.treeToValue(root.path("data"), IncidentSample.class);
            if (traceId.equals(sample.traceId())) {
                match[0] = sample;
                return false;
            }
            return true;
        });
        return match[0];
    }

    public long activeTransactions() {
        return activeTransactions.get();
    }

    public void transactionStarted() {
        activeTransactions.incrementAndGet();
    }

    public void transactionFinished() {
        activeTransactions.updateAndGet(value -> Math.max(0, value - 1));
    }

    /** Removes complete expired day segments; the legacy file is retained until all parseable records expire. */
    @Scheduled(cron = "0 17 2 * * *")
    public void pruneExpired() {
        if (!enabled) return;
        awaitPendingWrites();
        synchronized (fileLock) {
        try {
            Files.createDirectories(storageDirectory);
            LocalDate cutoffDate = LocalDate.now(clock).minusDays(retentionDays);
            for (Path segment : segmentFiles()) {
                LocalDate segmentDate = segmentDate(segment);
                if (segmentDate != null && segmentDate.isBefore(cutoffDate)) {
                    deleteHistoryFile(segment, "过期");
                }
            }
            LocalDateTime cutoffTime = now().minusDays(retentionDays);
            if (Files.isRegularFile(legacyStorageFile) && legacyFileExpired(cutoffTime)) {
                deleteHistoryFile(legacyStorageFile, "过期旧格式");
            }
            knownStorageBytes = enforceCapacity();
        } catch (Exception exception) {
            knownStorageBytes = calculateStorageBytes();
            log.error("清理运行诊断分段失败，目录 {}", storageDirectory, exception);
        }
        }
    }

    private void append(String kind, Object data) {
        if (!enabled || !acceptingWrites) return;
        // Shed overload before masking and JSON serialization. The atomic counters are only a cheap prefilter;
        // the queue/budget checks below remain the authoritative admission checks. A full queue or completely
        // consumed byte budget cannot accept even the smallest JSONL record at this instant, so dropping here is
        // safe and keeps an incident storm from spending business-thread CPU on a record that cannot be retained.
        if (pendingWrites.get() >= WRITE_QUEUE_CAPACITY && writeQueue.remainingCapacity() == 0) {
            recordQueueSaturationDrop();
            return;
        }
        if (pendingBytes.get() >= pendingMaxBytes) {
            recordByteBudgetSaturationDrop();
            return;
        }
        byte[] line;
        try {
            Object sanitizedData = sanitizeForStorage(data);
            line = serializePendingLine(kind, sanitizedData);
        } catch (RecordTooLargeException exception) {
            long dropped = droppedWrites.incrementAndGet();
            log.error("运行诊断记录超过单行或分段容量上限，kind={}，limit={}，累计丢弃 {} 条",
                    kind, exception.limitBytes(), dropped);
            return;
        } catch (Exception exception) {
            long dropped = droppedWrites.incrementAndGet();
            log.error("运行诊断记录脱敏或序列化失败，已安全丢弃，kind={}，exceptionType={}，累计丢弃 {} 条",
                    kind, exception.getClass().getSimpleName(), dropped);
            return;
        }
        long pendingLineBytes = line.length;
        boolean byteBudgetRejected = false;
        synchronized (this) {
            if (!acceptingWrites) return;
            if (!reservePendingBytes(pendingLineBytes)) {
                byteBudgetRejected = true;
            } else {
                startWriterIfNeeded();
                pendingWrites.incrementAndGet();
                if (writeQueue.offer(new PendingEvent(kind, line, null))) return;
                pendingWrites.decrementAndGet();
                releasePendingBytes(pendingLineBytes);
            }
        }
        if (byteBudgetRejected) {
            recordByteBudgetSaturationDrop();
            return;
        }
        recordQueueSaturationDrop();
    }

    private void recordByteBudgetSaturationDrop() {
        droppedWrites.incrementAndGet();
        long byteBudgetDropped = byteBudgetDroppedWrites.incrementAndGet();
        if (byteBudgetDropped == 1 || byteBudgetDropped % 1_000 == 0) {
            log.error("运行诊断待写内存预算已满，pendingBytes={}，limit={}，累计因内存预算丢弃 {} 条记录",
                    pendingBytes.get(), pendingMaxBytes, byteBudgetDropped);
        }
    }

    private void recordQueueSaturationDrop() {
        long dropped = droppedWrites.incrementAndGet();
        if (dropped == 1 || dropped % 1_000 == 0) {
            log.error("运行诊断写入队列已满，累计丢弃 {} 条记录；请检查磁盘吞吐或提高采样阈值", dropped);
        }
    }

    /** Serializes one complete JSONL record once, with a hard bound before it can enter the writer queue. */
    private byte[] serializePendingLine(String kind, Object data) throws IOException {
        int hardLineLimit = (int) Math.min(MAX_JSON_LINE_BYTES, Math.min(maxSegmentBytes, maxHistoryBytes));
        BoundedByteArrayOutputStream output = new BoundedByteArrayOutputStream(hardLineLimit);
        objectMapper.writeValue(output, new StoredEvent(kind, data));
        output.write(LINE_SEPARATOR_BYTES);
        return output.toByteArray();
    }

    private boolean reservePendingBytes(long bytes) {
        while (true) {
            long current = pendingBytes.get();
            if (bytes > pendingMaxBytes - current) return false;
            if (pendingBytes.compareAndSet(current, current + bytes)) return true;
        }
    }

    private void releasePendingBytes(long bytes) {
        if (bytes > 0) pendingBytes.addAndGet(-bytes);
    }

    /**
     * Sanitizes diagnostic content before it can enter the asynchronous writer queue. Correlation keys remain
     * unchanged so type/name/traceId joins keep working, while user-controlled descriptions are masked and bounded.
     */
    private Object sanitizeForStorage(Object data) {
        if (data instanceof TraceSample sample) {
            return new TraceSample(sample.type(), sample.traceId(), sample.name(), sanitizeUri(sample.method()),
                    sample.status(), sanitizeText(sample.tree()), sample.costMs(), sample.finishedAt());
        }
        if (data instanceof IncidentSample sample) {
            return new IncidentSample(sample.type(), sample.traceId(), sample.name(), sanitizeUri(sample.method()),
                    sample.status(), sanitizeText(sample.tree()), sample.costMs(), sample.slow(), sample.error(),
                    sample.reasons(), sanitizeText(sample.errorType()), sanitizeText(sample.errorMessage()),
                    sanitizeText(sample.stackTrace()), sanitizeCause(sample.primaryCause()),
                    sanitizeCause(sample.slowCause()), sanitizeCause(sample.errorCause()), sample.finishedAt());
        }
        if (data instanceof OperationSample sample) {
            return new OperationSample(sample.type(), sample.traceId(), sample.name(),
                    sanitizeText(sample.threadName()), sample.outcome(), sample.durationMs(), sample.cpuTimeMs(),
                    sample.allocatedBytes(), sample.blockedCount(), sample.blockedTimeMs(), sample.waitedCount(),
                    sample.waitedTimeMs(), sanitizeText(sample.detail()), sanitizeText(sample.lockInfo()),
                    sanitizeText(sample.stackTrace()), sample.childSummaries(), sample.finishedAt());
        }
        if (data instanceof EventSample sample) {
            return new EventSample(sample.type(), sample.name(), sample.value(), sample.threshold(),
                    sanitizeText(sample.detail()), sample.occurredAt());
        }
        // Transactions contain correlation fields and numeric outcome data only. Metric names/tags are a separate,
        // low-cardinality time-series contract; changing either here would break joins and series identity.
        return data;
    }

    private IncidentCause sanitizeCause(IncidentCause cause) {
        if (cause == null) return null;
        return new IncidentCause(cause.type(), cause.name(), sanitizeText(cause.detail()), cause.durationMs(),
                cause.thresholdMs(), cause.slow(), cause.error(), cause.reason());
    }

    private String sanitizeUri(String value) {
        if (value == null || value.isBlank()) return value;
        return sanitizeText(sensitiveDataMasker.maskUri(value), URI_TEXT_LIMIT);
    }

    private String sanitizeText(String value) {
        return sanitizeText(value, DIAGNOSTIC_TEXT_LIMIT);
    }

    private String sanitizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) return value;
        String sanitized = sensitiveDataMasker.sanitizeJsonText(value, maxLength);
        return sensitiveDataMasker.truncate(sensitiveDataMasker.maskText(sanitized), maxLength);
    }

    private Path resolveWritableSegment(long incomingBytes) {
        LocalDate today = LocalDate.now(clock);
        Path current = activeSegment;
        if (current != null && today.equals(segmentDate(current))
                && sizeQuietly(current) + Math.max(0, incomingBytes) <= maxSegmentBytes) {
            return current;
        }
        List<Path> todaySegments = segmentFiles().stream()
                .filter(path -> today.equals(segmentDate(path)))
                .sorted(Comparator.comparingInt(this::segmentIndex))
                .toList();
        Path latest = todaySegments.isEmpty()
                ? storageDirectory.resolve(SEGMENT_PREFIX + today + SEGMENT_SUFFIX)
                : todaySegments.get(todaySegments.size() - 1);
        if (!Files.exists(latest) || sizeQuietly(latest) + Math.max(0, incomingBytes) <= maxSegmentBytes) {
            activeSegment = latest;
            return latest;
        }
        int nextIndex = Math.max(1, segmentIndex(latest) + 1);
        Path next = storageDirectory.resolve(SEGMENT_PREFIX + today + "-%03d".formatted(nextIndex) + SEGMENT_SUFFIX);
        activeSegment = next;
        return next;
    }

    private void startWriterIfNeeded() {
        if (writerThread != null || !acceptingWrites) return;
        synchronized (this) {
            if (writerThread != null || !acceptingWrites) return;
            Thread thread = new Thread(this::runWriter, "easy-diagnosis-writer");
            thread.setDaemon(true);
            writerThread = thread;
            thread.start();
        }
    }

    private void runWriter() {
        List<PendingEvent> batch = new ArrayList<>(WRITE_BATCH_SIZE);
        while (!shutdownDrainRequested && (acceptingWrites || !writeQueue.isEmpty())) {
            try {
                PendingEvent first = writeQueue.poll(250, TimeUnit.MILLISECONDS);
                if (first == null) continue;
                batch.add(first);
                writeQueue.drainTo(batch, WRITE_BATCH_SIZE - 1);
                beforeAsyncWriteBatch();
                writeBatch(batch);
            } catch (InterruptedException exception) {
                if (!shutdownDrainRequested && acceptingWrites) continue;
            } finally {
                if (!batch.isEmpty()) {
                    completeBatch(batch);
                    batch.clear();
                }
            }
        }
    }

    /** Test seam executed only by the asynchronous writer, never by the synchronous shutdown drain. */
    void beforeAsyncWriteBatch() {
        // no-op
    }

    private void drainQueuedWritesSynchronously(long deadline) {
        List<PendingEvent> batch = new ArrayList<>(WRITE_BATCH_SIZE);
        while (System.nanoTime() < deadline) {
            PendingEvent first = writeQueue.poll();
            if (first == null) break;
            batch.add(first);
            writeQueue.drainTo(batch, WRITE_BATCH_SIZE - 1);
            try {
                writeBatch(batch);
            } finally {
                completeBatch(batch);
                batch.clear();
            }
        }
        long discarded = discardQueuedWrites();
        if (discarded > 0) {
            log.error("运行诊断关闭同步落盘达到 {} ms 上限，已准确丢弃剩余 {} 条记录",
                    shutdownDrainTimeoutMillis, discarded);
        }
    }

    private long discardQueuedWrites() {
        List<PendingEvent> discarded = new ArrayList<>(writeQueue.size());
        writeQueue.drainTo(discarded);
        long dataEvents = discarded.stream().filter(event -> event.barrier() == null).count();
        long dataBytes = discarded.stream().mapToLong(PendingEvent::sizeBytes).sum();
        if (dataEvents > 0) {
            pendingWrites.addAndGet(-dataEvents);
            releasePendingBytes(dataBytes);
            droppedWrites.addAndGet(dataEvents);
        }
        discarded.stream().map(PendingEvent::barrier).filter(java.util.Objects::nonNull)
                .forEach(CountDownLatch::countDown);
        return dataEvents;
    }

    private void completeBatch(List<PendingEvent> batch) {
        long dataEvents = batch.stream().filter(event -> event.barrier() == null).count();
        if (dataEvents > 0) {
            pendingWrites.addAndGet(-dataEvents);
            releasePendingBytes(batch.stream().mapToLong(PendingEvent::sizeBytes).sum());
        }
        batch.stream().map(PendingEvent::barrier).filter(java.util.Objects::nonNull)
                .forEach(CountDownLatch::countDown);
    }

    private static void joinUntil(Thread thread, long deadline) {
        while (thread.isAlive()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return;
            try {
                TimeUnit.NANOSECONDS.timedJoin(thread, remaining);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void writeBatch(List<PendingEvent> batch) {
        synchronized (fileLock) {
            try {
                Files.createDirectories(storageDirectory);
            } catch (Exception exception) {
                long dataEvents = batch.stream().filter(event -> event.barrier() == null).count();
                droppedWrites.addAndGet(dataEvents);
                knownStorageBytes = calculateStorageBytes();
                log.error("创建运行诊断目录失败，目录 {}", storageDirectory, exception);
                return;
            }
            FileChannel channel = null;
            Path openTarget = null;
            try {
                for (PendingEvent event : batch) {
                    if (event.barrier() != null) continue;
                    try {
                        byte[] line = event.line();
                        Path target = resolveWritableSegment(line.length);
                        if (knownStorageBytes + line.length > maxHistoryBytes) {
                            knownStorageBytes = enforceCapacity(line.length);
                        }
                        if (knownStorageBytes + line.length > maxHistoryBytes) {
                            long dropped = droppedWrites.incrementAndGet();
                            log.error("运行诊断目录无法在硬上限 {} bytes 内为新记录预留 {} bytes，kind={}，累计丢弃 {} 条",
                                    maxHistoryBytes, line.length, event.kind(), dropped);
                            continue;
                        }
                        if (channel == null || !target.equals(openTarget)) {
                            closeChannelQuietly(channel);
                            channel = FileChannel.open(target, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                            openTarget = target;
                        }
                        appendCompleteLine(channel, line);
                        knownStorageBytes += line.length;
                        if (knownStorageBytes > maxHistoryBytes) knownStorageBytes = enforceCapacity();
                    } catch (Exception exception) {
                        long dropped = droppedWrites.incrementAndGet();
                        knownStorageBytes = calculateStorageBytes();
                        log.error("写入运行诊断记录失败，kind={}，目录 {}，累计丢弃 {} 条",
                                event.kind(), storageDirectory, dropped, exception);
                    }
                }
            } finally {
                closeChannelQuietly(channel);
            }
        }
    }

    /**
     * Appends one complete JSONL record and restores the previous file length when an in-process write fails.
     * A hard process crash is handled by {@link #repairTrailingPartialLine(Path)} during the next startup.
     */
    private void appendCompleteLine(FileChannel channel, byte[] line) throws Exception {
        long originalSize = channel.size();
        channel.position(originalSize);
        try {
            ByteBuffer buffer = ByteBuffer.wrap(line);
            while (buffer.hasRemaining()) channel.write(buffer);
        } catch (Exception exception) {
            try {
                channel.truncate(originalSize);
            } catch (Exception truncateFailure) {
                exception.addSuppressed(truncateFailure);
            }
            throw exception;
        }
    }

    private void closeChannelQuietly(FileChannel channel) {
        if (channel == null) return;
        try {
            channel.close();
        } catch (Exception exception) {
            log.warn("关闭运行诊断分段写入通道失败", exception);
        }
    }

    private void awaitPendingWrites() {
        if (pendingWrites.get() <= 0 || shutdownDrainRequested) return;
        startWriterIfNeeded();
        // Diagnosis reads prefer a slightly stale bounded result to occupying a Tomcat thread for seconds while the
        // disk writer is overloaded. Detail/list polling will pick up the record on the next request.
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(READ_DRAIN_TIMEOUT_MILLIS);
        CountDownLatch barrier = new CountDownLatch(1);
        try {
            while (!writeQueue.offer(new PendingEvent("", null, barrier), 10, TimeUnit.MILLISECONDS)) {
                if (System.nanoTime() >= deadline) return;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining > 0) barrier.await(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private List<Path> historyFilesNewestFirst() {
        List<Path> files = new ArrayList<>(segmentFiles());
        Comparator<Path> segmentOrder = Comparator
                .comparing(this::segmentDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparingInt(this::segmentIndex);
        files.sort(segmentOrder.reversed());
        if (Files.isRegularFile(legacyStorageFile)) {
            files.add(legacyStorageFile);
        }
        return files;
    }

    private List<Path> segmentFiles() {
        if (!Files.isDirectory(storageDirectory)) {
            return List.of();
        }
        try (var paths = Files.list(storageDirectory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> SEGMENT_PATTERN.matcher(path.getFileName().toString()).matches())
                    .toList();
        } catch (Exception exception) {
            log.warn("列出运行诊断分段失败，目录 {}", storageDirectory, exception);
            return List.of();
        }
    }

    private LocalDate segmentDate(Path path) {
        if (path == null) {
            return null;
        }
        Matcher matcher = SEGMENT_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return LocalDate.parse(matcher.group(1));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private int segmentIndex(Path path) {
        if (path == null) return -1;
        Matcher matcher = SEGMENT_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.matches() || matcher.group(2) == null) return 0;
        try { return Integer.parseInt(matcher.group(2)); }
        catch (RuntimeException ignored) { return 0; }
    }

    private long sizeQuietly(Path path) {
        try { return Files.isRegularFile(path) ? Files.size(path) : 0; }
        catch (Exception ignored) { return 0; }
    }

    /** Removes only the unterminated tail left by a process crash before this segment is reused for appends. */
    private void repairTrailingPartialLine(Path path) {
        if (path == null || !Files.isRegularFile(path)) return;
        try (SeekableByteChannel channel = Files.newByteChannel(path,
                StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            long size = channel.size();
            if (size == 0) return;
            ByteBuffer single = ByteBuffer.allocate(1);
            channel.position(size - 1);
            channel.read(single);
            if (single.array()[0] == '\n') return;

            ByteBuffer buffer = ByteBuffer.allocate(REVERSE_READ_BUFFER_BYTES);
            long position = size;
            while (position > 0) {
                int requested = (int) Math.min(REVERSE_READ_BUFFER_BYTES, position);
                position -= requested;
                channel.position(position);
                buffer.clear();
                buffer.limit(requested);
                while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                    // Fill the requested window.
                }
                for (int index = buffer.position() - 1; index >= 0; index--) {
                    if (buffer.get(index) == '\n') {
                        long retained = position + index + 1;
                        channel.truncate(retained);
                        log.warn("运行诊断分段 {} 存在未完成尾行，已从 {} bytes 截断到 {} bytes",
                                path.getFileName(), size, retained);
                        return;
                    }
                }
            }
            channel.truncate(0);
            log.warn("运行诊断分段 {} 仅包含未完成尾行，已清空 {} bytes", path.getFileName(), size);
        } catch (Exception exception) {
            log.warn("修复运行诊断分段未完成尾行失败: {}", path, exception);
        }
    }

    private long enforceCapacity() {
        return enforceCapacity(0);
    }

    /** Frees closed segments until the directory can accommodate the requested bytes within the hard cap. */
    private long enforceCapacity(long reservedBytes) {
        List<Path> files = new ArrayList<>();
        if (Files.isRegularFile(legacyStorageFile)) {
            files.add(legacyStorageFile);
        }
        List<Path> segments = new ArrayList<>(segmentFiles());
        segments.sort(Comparator.comparing(this::segmentDate,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparingInt(this::segmentIndex));
        files.addAll(segments);

        Map<Path, Long> sizes = new LinkedHashMap<>();
        long total = 0;
        for (Path file : files) {
            try {
                long size = Files.size(file);
                sizes.put(file, size);
                total += size;
            } catch (Exception exception) {
                log.warn("读取运行诊断分段大小失败: {}", file, exception);
            }
        }
        for (Map.Entry<Path, Long> entry : sizes.entrySet()) {
            if (total + Math.max(0, reservedBytes) <= maxHistoryBytes) {
                break;
            }
            if (entry.getKey().equals(activeSegment)) {
                continue;
            }
            try {
                if (Files.deleteIfExists(entry.getKey())) {
                    total -= entry.getValue();
                    log.warn("运行诊断历史超过容量上限 {} bytes，已删除最旧分段 {}",
                            maxHistoryBytes, entry.getKey().getFileName());
                }
            } catch (Exception exception) {
                log.warn("删除超容量运行诊断分段失败: {}", entry.getKey(), exception);
            }
        }
        return Math.max(0, total);
    }

    private long calculateStorageBytes() {
        long total = 0;
        for (Path file : allHistoryFiles()) {
            try {
                total += Files.size(file);
            } catch (Exception ignored) {
                // A concurrent cleanup may remove a segment between listing and reading its size.
            }
        }
        return total;
    }

    private List<Path> allHistoryFiles() {
        List<Path> files = new ArrayList<>(segmentFiles());
        if (Files.isRegularFile(legacyStorageFile)) {
            files.add(legacyStorageFile);
        }
        return files;
    }

    private boolean legacyFileExpired(LocalDateTime cutoff) {
        boolean[] sawTimestamp = {false};
        boolean[] retained = {false};
        readJsonLinesReverse(legacyStorageFile, root -> {
            LocalDateTime timestamp = eventTime(root);
            if (timestamp != null) {
                sawTimestamp[0] = true;
                if (!timestamp.isBefore(cutoff)) {
                    retained[0] = true;
                    return false;
                }
            }
            return true;
        });
        if (retained[0]) {
            return false;
        }
        if (sawTimestamp[0]) {
            return true;
        }
        try {
            return Files.size(legacyStorageFile) == 0
                    || Files.getLastModifiedTime(legacyStorageFile).toInstant()
                    .isBefore(cutoff.atZone(clock.getZone()).toInstant());
        } catch (Exception ignored) {
            return false;
        }
    }

    private void deleteHistoryFile(Path path, String reason) {
        try {
            if (Files.deleteIfExists(path)) {
                log.info("已删除{}运行诊断分段 {}", reason, path.getFileName());
            }
        } catch (Exception exception) {
            log.warn("删除{}运行诊断分段失败: {}", reason, path, exception);
        }
    }

    /** Reads newline-delimited JSON from the tail so recent-list endpoints do not rescan a large day segment. */
    private void readJsonLinesReverse(Path file, JsonLineVisitor visitor) {
        historyReads.execute(() -> {
            readJsonLinesReverseBounded(file, visitor);
            return null;
        });
    }

    /** Actual file scan; callers enter through the shared two-reader limiter above. */
    private void readJsonLinesReverseBounded(Path file, JsonLineVisitor visitor) {
        int[] malformedLines = {0};
        try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
            long position = channel.size();
            ByteBuffer buffer = ByteBuffer.allocate(REVERSE_READ_BUFFER_BYTES);
            ByteArrayOutputStream reversedLine = new ByteArrayOutputStream(1_024);
            boolean oversized = false;
            boolean keepReading = true;
            while (position > 0 && keepReading) {
                int requested = (int) Math.min(REVERSE_READ_BUFFER_BYTES, position);
                position -= requested;
                channel.position(position);
                buffer.clear();
                buffer.limit(requested);
                while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
                    // Local files normally fill the buffer in one read; keep reading for correctness.
                }
                int read = buffer.position();
                for (int index = read - 1; index >= 0; index--) {
                    byte value = buffer.get(index);
                    if (value == '\n') {
                        if (oversized) {
                            malformedLines[0]++;
                        } else if (reversedLine.size() > 0) {
                            keepReading = visitReversedLine(reversedLine, visitor, malformedLines);
                        }
                        reversedLine.reset();
                        oversized = false;
                        if (!keepReading) break;
                    } else if (!oversized) {
                        if (reversedLine.size() >= MAX_JSON_LINE_BYTES) {
                            reversedLine.reset();
                            oversized = true;
                        } else {
                            reversedLine.write(value);
                        }
                    }
                }
            }
            if (keepReading) {
                if (oversized) malformedLines[0]++;
                else if (reversedLine.size() > 0) visitReversedLine(reversedLine, visitor, malformedLines);
            }
        } catch (java.nio.file.NoSuchFileException ignored) {
            return;
        } catch (Exception exception) {
            log.warn("倒序读取运行诊断分段失败: {}", file, exception);
        }
        if (malformedLines[0] > 0) {
            log.warn("运行诊断分段 {} 倒序读取时跳过 {} 行损坏、过大或不兼容记录",
                    file.getFileName(), malformedLines[0]);
        }
    }

    private boolean visitReversedLine(ByteArrayOutputStream reversedLine, JsonLineVisitor visitor,
                                      int[] malformedLines) throws Exception {
        byte[] reversed = reversedLine.toByteArray();
        int length = reversed.length;
        while (length > 0 && reversed[length - 1] == '\r') length--;
        byte[] line = new byte[length];
        for (int index = 0; index < length; index++) line[index] = reversed[length - index - 1];
        try {
            JsonNode root = objectMapper.readTree(line);
            return root == null || visitor.visit(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            malformedLines[0]++;
            return true;
        }
    }

    private LocalDateTime eventTime(JsonNode root) {
        String kind = root.path("kind").asText();
        JsonNode data = root.path("data");
        String field = switch (kind) {
            case "trace", "incident", "transaction", "operation" -> "finishedAt";
            case "event" -> "occurredAt";
            case "metrics" -> "capturedAt";
            default -> "";
        };
        try {
            return field.isEmpty() ? null : objectMapper.treeToValue(data.get(field), LocalDateTime.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    @FunctionalInterface
    private interface JsonLineVisitor {
        boolean visit(JsonNode root) throws Exception;
    }

    private record PendingEvent(String kind, byte[] line, CountDownLatch barrier) {
        private long sizeBytes() {
            return line == null ? 0 : line.length;
        }
    }

    /** ByteArrayOutputStream variant that fails before a diagnostic record can exceed its configured line bound. */
    private static final class BoundedByteArrayOutputStream extends ByteArrayOutputStream {
        private final int maxBytes;

        private BoundedByteArrayOutputStream(int maxBytes) {
            super(Math.min(Math.max(1, maxBytes), 1_024));
            this.maxBytes = Math.max(0, maxBytes);
        }

        @Override
        public synchronized void write(int value) {
            ensureWritable(1);
            super.write(value);
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            ensureWritable(length);
            super.write(bytes, offset, length);
        }

        private void ensureWritable(int length) {
            if (length > maxBytes - count) {
                throw new RecordTooLargeException(maxBytes);
            }
        }
    }

    private static final class RecordTooLargeException extends RuntimeException {
        private final int limitBytes;

        private RecordTooLargeException(int limitBytes) {
            super("runtime diagnosis record exceeds " + limitBytes + " bytes");
            this.limitBytes = limitBytes;
        }

        private int limitBytes() {
            return limitBytes;
        }
    }


    public record TraceSample(String type, String traceId, String name, String method, int status, String tree,
                              long costMs, LocalDateTime finishedAt) {
    }

    public record IncidentSample(String type, String traceId, String name, String method, int status, String tree,
                                 long costMs, boolean slow, boolean error, List<String> reasons,
                                 String errorType, String errorMessage, String stackTrace,
                                 IncidentCause primaryCause, IncidentCause slowCause, IncidentCause errorCause,
                                 LocalDateTime finishedAt) {
        public IncidentSample {
            type = type == null || type.isBlank() ? "HTTP" : type;
            traceId = traceId == null ? "" : traceId;
            name = name == null ? "" : name;
            method = method == null ? "" : method;
            tree = tree == null ? "" : tree;
            reasons = reasons == null ? List.of() : reasons.stream()
                    .filter(Objects::nonNull).filter(reason -> !reason.isBlank()).distinct().toList();
            errorType = errorType == null ? "" : errorType;
            errorMessage = errorMessage == null ? "" : errorMessage;
            stackTrace = stackTrace == null ? "" : stackTrace;
        }

        /** Source-compatible constructor for callers and JSON fixtures written before cause fields existed. */
        public IncidentSample(String type, String traceId, String name, String method, int status, String tree,
                              long costMs, boolean slow, boolean error, List<String> reasons,
                              String errorType, String errorMessage, String stackTrace,
                              LocalDateTime finishedAt) {
            this(type, traceId, name, method, status, tree, costMs, slow, error, reasons,
                    errorType, errorMessage, stackTrace, null, null, null, finishedAt);
        }

        public TraceSample trace() {
            return new TraceSample(type, traceId, name, method, status, tree, costMs, finishedAt);
        }
    }

    /**
     * Operation that best explains an incident. The overall cause prefers an error; slow/error projections retain
     * their own cause so one failed child cannot hide a different slow child of the same root operation.
     */
    public record IncidentCause(String type, String name, String detail, long durationMs, long thresholdMs,
                                boolean slow, boolean error, String reason) {
        public IncidentCause {
            type = type == null || type.isBlank() ? "OTHER" : type;
            name = name == null ? "" : name;
            detail = detail == null ? "" : detail;
            durationMs = Math.max(0, durationMs);
            thresholdMs = Math.max(0, thresholdMs);
            reason = reason == null ? "" : reason;
        }
    }

    public record TransactionSample(String traceId, String name, boolean success, long costMs,
                                    LocalDateTime finishedAt) {
    }

    public record MetricSample(String name, Map<String, String> tags, Map<String, Double> measurements) {
    }

    public record MetricsSnapshot(LocalDateTime capturedAt, List<MetricSample> samples) {
    }

    public record MetricHistoryPoint(String capturedAt, Map<String, String> tags,
                                     Map<String, Double> measurements) {
    }

    public record IncidentSummary(LocalDateTime sampledAt, Duration window, long incidents, long slow, long errors,
                                  boolean truncated, int sampleLimit) { }

    public record OperationSample(String type, String traceId, String name, String threadName, String outcome,
                                  long durationMs, long cpuTimeMs, long allocatedBytes, long blockedCount,
                                  long blockedTimeMs, long waitedCount, long waitedTimeMs, String detail,
                                  String lockInfo, String stackTrace,
                                  Map<String, ChildOperationSummary> childSummaries,
                                  LocalDateTime finishedAt) {
        public OperationSample {
            childSummaries = childSummaries == null ? Map.of() : Map.copyOf(childSummaries);
        }

        public OperationSample(String type, String traceId, String name, String threadName, String outcome,
                               long durationMs, long cpuTimeMs, long allocatedBytes, long blockedCount,
                               long blockedTimeMs, long waitedCount, long waitedTimeMs, String detail,
                               String lockInfo, String stackTrace, LocalDateTime finishedAt) {
            this(type, traceId, name, threadName, outcome, durationMs, cpuTimeMs, allocatedBytes,
                    blockedCount, blockedTimeMs, waitedCount, waitedTimeMs, detail, lockInfo, stackTrace,
                    Map.of(), finishedAt);
        }
    }

    /** Compact projection for bounded statistics scans; intentionally excludes retained diagnostic evidence. */
    public record OperationSummarySample(String type, String name, String outcome, long durationMs,
                                         long cpuTimeMs, long allocatedBytes, String detail,
                                         LocalDateTime finishedAt) { }

    public record OperationScanResult(int readSamples, boolean scanLimitReached) { }

    public record ChildOperationSummary(long count, long errorCount, long totalDurationMs, long maxDurationMs) { }

    public record TraceCorrelation(TraceSample trace, List<TransactionSample> transactions,
                                   List<OperationSample> operations, IncidentSample incident) {
        public TraceCorrelation(TraceSample trace, List<TransactionSample> transactions,
                                List<OperationSample> operations) {
            this(trace, transactions, operations, null);
        }

        /** Bounds auxiliary stores to the selected root when a client reuses the same trace id. */
        public LocalDateTime startedAt() {
            return correlationStartedAt(trace);
        }

        public LocalDateTime finishedAt() {
            return trace == null ? null : trace.finishedAt();
        }
    }

    public record EventSample(String type, String name, double value, double threshold, String detail,
                              LocalDateTime occurredAt) {
    }

    private record StoredEvent(String kind, Object data) {
    }
}
