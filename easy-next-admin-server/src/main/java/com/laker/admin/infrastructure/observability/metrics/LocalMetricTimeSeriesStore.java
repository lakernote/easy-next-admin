package com.laker.admin.infrastructure.observability.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.infrastructure.observability.trace.BoundedDiagnosticReadExecutor;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.rrd4j.ConsolFun;
import org.rrd4j.DsType;
import org.rrd4j.core.Archive;
import org.rrd4j.core.DsDef;
import org.rrd4j.core.FetchData;
import org.rrd4j.core.RrdDb;
import org.rrd4j.core.RrdDef;
import org.rrd4j.core.RrdToolkit;
import org.rrd4j.core.Sample;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Bounded local Micrometer history backed by RRD4J.
 *
 * <p>One fixed-size RRD file is used for each metric name/tag combination. Every file has three LAST archives:
 * one minute for 24 hours, five minutes for seven days, and thirty minutes for the configured retention period
 * (at least thirty days). LAST preserves both gauges and cumulative Micrometer counters across consolidation.</p>
 *
 * <p>The small sidecar contains only the metric identity and the mapping from Micrometer statistic names to RRD
 * datasource names. Numeric time-series samples are stored solely by RRD4J.</p>
 */
@Component
@Slf4j
public class LocalMetricTimeSeriesStore {
    private static final String SERIES_PREFIX = "series-";
    private static final String RRD_SUFFIX = ".rrd";
    private static final String METADATA_SUFFIX = ".meta.json";
    private static final String LEGACY_SUFFIX = ".mts";
    private static final int METADATA_VERSION = 1;

    private static final long STEP_SECONDS = 60;
    private static final long HEARTBEAT_SECONDS = STEP_SECONDS * 3;
    private static final int ONE_DAY_ROWS = 24 * 60;
    private static final int SEVEN_DAY_FIVE_MINUTE_ROWS = 7 * 24 * 12;
    private static final int THIRTY_MINUTE_STEPS = 30;
    private static final double X_FILES_FACTOR = 0.5;
    private static final long DAY_SECONDS = 24 * 60 * 60;

    private static final int DEFAULT_RETENTION_DAYS = 30;
    private static final long DEFAULT_MAX_STORAGE_BYTES = 64L * 1024 * 1024;
    private static final long DEFAULT_MAX_SERIES_BYTES = 8L * 1024 * 1024;
    private static final int DEFAULT_MAX_SERIES = 512;
    private static final long REJECTED_SERIES_CACHE_TTL_SECONDS = 5 * 60;
    private static final int MIN_REJECTED_SERIES_CACHE_ENTRIES = 256;
    private static final int MAX_REJECTED_SERIES_CACHE_ENTRIES = 8_192;
    private static final int MAX_NEW_SERIES_ATTEMPTS_PER_BATCH = 512;
    private static final int CORE_PRIORITY = 0;
    private static final int PLATFORM_PRIORITY = 1;
    private static final int OPTIONAL_PRIORITY = 2;

    private static final int MAX_METRIC_NAME_LENGTH = 256;
    private static final int MAX_TAGS = 16;
    private static final int MAX_TAG_KEY_LENGTH = 64;
    private static final int MAX_TAG_VALUE_LENGTH = 512;
    private static final int MAX_MEASUREMENTS = 16;
    private static final int MAX_MEASUREMENT_NAME_LENGTH = 64;

    private final ObjectMapper objectMapper;
    private final Path storageDirectory;
    private final int retentionDays;
    private final long maxStorageBytes;
    private final long maxSeriesBytes;
    private final int maxSeries;
    private final int rejectedSeriesCacheLimit;
    private final Clock clock;
    private final boolean enabled;
    private final Object fileLock = new Object();
    /** One RRD reader may wait on/write against the global file lock; excess HTTP readers fail within 100 ms. */
    private final BoundedDiagnosticReadExecutor historyReads = new BoundedDiagnosticReadExecutor(1);
    private final AtomicLong droppedWrites = new AtomicLong();
    private final AtomicLong rejectedSeries = new AtomicLong();
    private final AtomicLong evictedSeries = new AtomicLong();
    private final AtomicLong directoryScans = new AtomicLong();
    private final AtomicLong seriesCreationAttempts = new AtomicLong();
    private final Map<String, SeriesMetadata> seriesByKey = new HashMap<>();
    private final Map<String, ObservedSampleRange> observedRangesById = new HashMap<>();
    private final int[] seriesCountsByPriority = new int[OPTIONAL_PRIORITY + 1];
    private final Map<String, Long> rejectedSeriesUntilByKey = new LinkedHashMap<>(16, 0.75F, true);

    private volatile boolean initialized;
    private long trackedStorageBytes;

    @Autowired
    public LocalMetricTimeSeriesStore(
            ObjectMapper objectMapper,
            @Value("${easy.monitor.diagnosis.metrics-storage-dir:${easy.monitor.diagnosis.storage-dir:./data/diagnosis}/metrics}") String storageDir,
            @Value("${easy.monitor.diagnosis.metrics-retention-days:${easy.monitor.diagnosis.retention-days:30}}") int retentionDays,
            @Value("${easy.monitor.diagnosis.metrics-history-max-bytes:67108864}") long maxStorageBytes,
            @Value("${easy.monitor.diagnosis.metrics-segment-max-bytes:8388608}") long maxSeriesBytes,
            @Value("${easy.monitor.diagnosis.metrics-max-series:512}") int maxSeries,
            @Value("${easy.monitor.diagnosis.enabled:true}") boolean diagnosisEnabled,
            @Value("${easy.features.monitor:true}") boolean monitorEnabled) {
        this(objectMapper, storageDir, retentionDays, maxStorageBytes, maxSeriesBytes, maxSeries,
                Clock.systemDefaultZone(), diagnosisEnabled && monitorEnabled);
    }

    LocalMetricTimeSeriesStore(ObjectMapper objectMapper, String storageDir, int retentionDays,
                               long maxStorageBytes, long maxSeriesBytes, int maxSeries,
                               Clock clock, boolean enabled) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy();
        this.storageDirectory = Path.of(storageDir).toAbsolutePath().normalize();
        this.retentionDays = Math.max(DEFAULT_RETENTION_DAYS, retentionDays);
        this.maxStorageBytes = maxStorageBytes > 0 ? maxStorageBytes : DEFAULT_MAX_STORAGE_BYTES;
        long configuredSeriesBytes = maxSeriesBytes > 0 ? maxSeriesBytes : DEFAULT_MAX_SERIES_BYTES;
        this.maxSeriesBytes = Math.min(configuredSeriesBytes, this.maxStorageBytes);
        this.maxSeries = maxSeries > 0 ? maxSeries : DEFAULT_MAX_SERIES;
        this.rejectedSeriesCacheLimit = (int) Math.max(MIN_REJECTED_SERIES_CACHE_ENTRIES,
                Math.min(MAX_REJECTED_SERIES_CACHE_ENTRIES, (long) this.maxSeries * 4));
        this.clock = Objects.requireNonNull(clock, "clock");
        this.enabled = enabled;
    }

    @PostConstruct
    public void restore() {
        synchronized (fileLock) {
            if (initialized) return;
            if (!enabled) {
                initialized = true;
                return;
            }
            try {
                Files.createDirectories(storageDirectory);
                cleanupObsoleteFilesLocked();
                restoreIndexLocked();
                refreshStorageBytesLocked();
                pruneExpiredLocked();
                enforceCapacityLocked();
                log.info("Metrics RRD4J 本地时序已恢复，目录 {}，series={}，占用={} bytes",
                        storageDirectory, seriesByKey.size(), trackedStorageBytes);
            } catch (Exception exception) {
                log.error("无法恢复 Metrics RRD4J 本地时序目录 {}", storageDirectory, exception);
            } finally {
                initialized = true;
            }
        }
    }

    /** Persists a complete low-cardinality Micrometer snapshot at the current minute boundary. */
    public void recordMetricsSnapshot(List<RecentTraceStore.MetricSample> samples) {
        if (!enabled || samples == null || samples.isEmpty()) return;
        ensureRestored();
        synchronized (fileLock) {
            long timestamp = normalizeTimestamp(clock.instant().getEpochSecond());
            boolean dropped = false;
            Exception failure = null;
            String reason = null;
            List<NormalizedSample> normalizedSamples = new ArrayList<>(samples.size());
            for (RecentTraceStore.MetricSample sample : samples) {
                NormalizedSample normalized = normalize(sample);
                if (normalized == null) {
                    dropped = true;
                    reason = "指标名称、标签或 measurement 超出低基数存储限制";
                    rejectedSeries.incrementAndGet();
                } else {
                    normalizedSamples.add(normalized);
                }
            }
            normalizedSamples.sort(Comparator.comparingInt(
                            (NormalizedSample sample) -> metricPriority(sample.name()))
                    .thenComparing(NormalizedSample::name)
                    .thenComparing(sample -> sample.tags().toString()));
            if (requiresBatchMaintenanceLocked(normalizedSamples, timestamp)) {
                prepareWriteBatchLocked(timestamp);
            }
            BatchAdmissionBudget admissionBudget = new BatchAdmissionBudget(
                    Math.min(maxSeries, MAX_NEW_SERIES_ATTEMPTS_PER_BATCH));
            for (NormalizedSample normalized : normalizedSamples) {
                try {
                    SeriesResolution resolution = resolveSeriesLocked(normalized, timestamp, admissionBudget);
                    if (resolution.metadata() == null) {
                        dropped = true;
                        reason = resolution.reason();
                        if (resolution.rejectedSeries()) rejectedSeries.incrementAndGet();
                        continue;
                    }
                    if (!resolution.complete()) {
                        dropped = true;
                        reason = resolution.reason();
                    }
                    if (!writeSampleLocked(resolution.metadata(), normalized.measurements(), timestamp)) {
                        dropped = true;
                        reason = "RRD4J series 写入失败";
                    }
                } catch (Exception exception) {
                    dropped = true;
                    failure = exception;
                    reason = "RRD4J 快照写入异常";
                }
            }
            if (dropped) markDropped(reason == null ? "RRD4J 快照未完整写入" : reason, failure);
        }
    }

    /** Returns matching points newest first, combining the three RRD resolutions without duplicate boundaries. */
    public List<RecentTraceStore.MetricHistoryPoint> metricHistory(
            String name, Map<String, String> tags, int limit) {
        if (!enabled || name == null || name.isBlank() || limit <= 0) return List.of();
        return historyReads.execute(() -> metricHistoryAdmitted(name, tags, limit));
    }

    private List<RecentTraceStore.MetricHistoryPoint> metricHistoryAdmitted(
            String name, Map<String, String> tags, int limit) {
        ensureRestored();
        synchronized (fileLock) {
            int safeLimit = Math.max(1, limit);
            Map<String, String> normalizedTags = immutableSortedMap(tags == null ? Map.of() : tags);
            List<SeriesMetadata> matching = seriesByKey.values().stream()
                    .filter(metadata -> name.equals(metadata.name()))
                    // An empty selector identifies the untagged series. Treating it as a wildcard can open and
                    // scan every high-cardinality series sharing this meter name while holding the global RRD lock.
                    .filter(metadata -> normalizedTags.equals(metadata.tags()))
                    .sorted(Comparator.comparing(SeriesMetadata::id))
                    .toList();
            if (matching.isEmpty()) return List.of();

            PriorityQueue<HistoryPoint> newest = new PriorityQueue<>(safeLimit,
                    Comparator.comparingLong(HistoryPoint::epochSecond)
                            .thenComparing(point -> point.tags().toString()));
            long now = normalizeTimestamp(clock.instant().getEpochSecond());
            long oldest = now - (long) retentionDays * DAY_SECONDS;
            long oneDayAgo = now - DAY_SECONDS;
            long sevenDaysAgo = now - 7L * DAY_SECONDS;

            collectTierLocked(matching, Math.max(oldest, oneDayAgo), now, STEP_SECONDS, safeLimit, newest);
            if (newest.size() < safeLimit && sevenDaysAgo < oneDayAgo) {
                collectTierLocked(matching, Math.max(oldest, sevenDaysAgo), oneDayAgo - STEP_SECONDS,
                        5 * STEP_SECONDS, safeLimit, newest);
            }
            if (newest.size() < safeLimit && oldest < sevenDaysAgo) {
                collectTierLocked(matching, oldest, sevenDaysAgo - STEP_SECONDS,
                        THIRTY_MINUTE_STEPS * STEP_SECONDS, safeLimit, newest);
            }

            return newest.stream()
                    .sorted(Comparator.comparingLong(HistoryPoint::epochSecond).reversed()
                            .thenComparing(point -> point.tags().toString()))
                    .map(point -> new RecentTraceStore.MetricHistoryPoint(
                            LocalDateTime.ofInstant(Instant.ofEpochSecond(point.epochSecond()), clock.getZone()).toString(),
                            point.tags(), point.measurements()))
                    .toList();
        }
    }

    /**
     * Lists the persisted RRD series independently from the current process MeterRegistry.
     * Dynamic URI, job or client tags may not be registered again immediately after a restart,
     * but their retained history must remain discoverable from the Trend selector.
     */
    public List<MetricSeriesDescriptor> seriesCatalog() {
        if (!enabled) return List.of();
        return historyReads.execute(this::seriesCatalogAdmitted);
    }

    private List<MetricSeriesDescriptor> seriesCatalogAdmitted() {
        ensureRestored();
        synchronized (fileLock) {
            return seriesByKey.values().stream()
                    .sorted(Comparator.comparing(SeriesMetadata::name)
                            .thenComparing(metadata -> metadata.tags().toString()))
                    .map(metadata -> new MetricSeriesDescriptor(metadata.name(), metadata.tags(),
                            metadata.dataSources().keySet().stream().sorted().toList()))
                    .toList();
        }
    }

    /** RRD archives overwrite old slots themselves; this removes only series that have stopped reporting. */
    @Scheduled(cron = "0 23 2 * * *")
    public void pruneExpired() {
        if (!enabled) return;
        ensureRestored();
        synchronized (fileLock) {
            try {
                refreshStorageBytesLocked();
                if (pruneExpiredLocked()) rejectedSeriesUntilByKey.clear();
                enforceCapacityLocked();
            } catch (Exception exception) {
                log.warn("清理 Metrics RRD4J series 失败，目录 {}", storageDirectory, exception);
            }
        }
    }

    public StorageStatus status() {
        if (!enabled) return disabledStatus();
        return historyReads.execute(this::statusAdmitted);
    }

    private StorageStatus statusAdmitted() {
        if (enabled) ensureRestored();
        synchronized (fileLock) {
            ObservedSampleRange observed = observedRangesById.values().stream()
                    .reduce(ObservedSampleRange::span)
                    .orElse(null);
            LocalDate oldest = observed == null ? null : sampleDate(observed.oldestEpochSecond());
            LocalDate newest = observed == null ? null : sampleDate(observed.newestEpochSecond());
            long coverageDays = oldest == null || newest == null ? 0
                    : Math.min(retentionDays, Math.max(1, ChronoUnit.DAYS.between(oldest, newest) + 1));
            return new StorageStatus(enabled, storageDirectory.toString(), retentionDays, maxStorageBytes,
                    maxSeriesBytes, trackedStorageBytes, oldest == null ? "" : oldest.toString(), coverageDays,
                    seriesByKey.size(), droppedWrites.get(), rejectedSeries.get(), evictedSeries.get());
        }
    }

    private StorageStatus disabledStatus() {
        return new StorageStatus(false, storageDirectory.toString(), retentionDays, maxStorageBytes,
                maxSeriesBytes, 0, "", 0, 0, droppedWrites.get(), rejectedSeries.get(), evictedSeries.get());
    }

    public long storageBytes() {
        return status().storageBytes();
    }

    public String oldestSegmentDate() {
        return status().oldestSegmentDate();
    }

    public long segmentCoverageDays() {
        return status().coverageDays();
    }

    public long droppedWrites() {
        return droppedWrites.get();
    }

    public long rejectedSeries() {
        return rejectedSeries.get();
    }

    public long evictedSeries() {
        return evictedSeries.get();
    }

    public int retentionDays() {
        return retentionDays;
    }

    public long maxStorageBytes() {
        return maxStorageBytes;
    }

    /** Kept for the existing diagnosis API; this is now the upper bound for one RRD series file. */
    public long maxSegmentBytes() {
        return maxSeriesBytes;
    }

    public boolean enabled() {
        return enabled;
    }

    long directoryScanCount() {
        return directoryScans.get();
    }

    long seriesCreationAttemptCount() {
        return seriesCreationAttempts.get();
    }

    int rejectedSeriesCacheSize() {
        synchronized (fileLock) {
            return rejectedSeriesUntilByKey.size();
        }
    }

    private void ensureRestored() {
        if (!initialized) restore();
    }

    private NormalizedSample normalize(RecentTraceStore.MetricSample sample) {
        if (sample == null || sample.name() == null || sample.name().isBlank()
                || sample.name().length() > MAX_METRIC_NAME_LENGTH) return null;
        Map<String, String> tags = sample.tags() == null ? Map.of() : sample.tags();
        if (tags.size() > MAX_TAGS || tags.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getValue() == null || entry.getKey().length() > MAX_TAG_KEY_LENGTH
                || entry.getValue().length() > MAX_TAG_VALUE_LENGTH)) return null;
        Map<String, Double> measurements = sample.measurements() == null ? Map.of() : sample.measurements();
        if (measurements.isEmpty() || measurements.size() > MAX_MEASUREMENTS
                || measurements.entrySet().stream().anyMatch(entry -> entry.getKey() == null
                || entry.getKey().isBlank() || entry.getKey().length() > MAX_MEASUREMENT_NAME_LENGTH)) return null;
        Map<String, Double> finite = new TreeMap<>();
        measurements.forEach((key, value) -> {
            if (value != null && Double.isFinite(value)) finite.put(key, value);
        });
        if (finite.isEmpty()) return null;
        return new NormalizedSample(sample.name(), immutableSortedMap(tags), Map.copyOf(finite));
    }

    private SeriesResolution resolveSeriesLocked(NormalizedSample sample, long timestamp,
                                                 BatchAdmissionBudget admissionBudget) throws IOException {
        String key = seriesKey(sample.name(), sample.tags());
        SeriesMetadata metadata = seriesByKey.get(key);
        if (metadata == null) {
            String keyFingerprint = sha256(key);
            Long rejectedUntil = rejectedSeriesUntilByKey.get(keyFingerprint);
            if (rejectedUntil != null) {
                if (rejectedUntil > timestamp) {
                    return new SeriesResolution(null, false, true,
                            "series 数量或 Metrics 本地容量仍在短时拒绝窗口内");
                }
                rejectedSeriesUntilByKey.remove(keyFingerprint);
            }
            if (!admissionBudget.tryAcquire(keyFingerprint)) {
                return new SeriesResolution(null, false, true,
                        "单批新 series 候选数已达 Metrics 本地上限");
            }
            seriesCreationAttempts.incrementAndGet();
            metadata = createSeriesLocked(key, keyFingerprint, sample, timestamp);
            if (metadata == null) {
                rememberRejectedSeriesLocked(keyFingerprint, timestamp);
                return new SeriesResolution(null, false, true, "series 数量或 Metrics 本地容量已达上限");
            }
            rejectedSeriesUntilByKey.remove(keyFingerprint);
            return new SeriesResolution(metadata, true, false, "");
        }
        SeriesMetadata currentMetadata = metadata;
        Set<String> missing = sample.measurements().keySet().stream()
                .filter(measurement -> !currentMetadata.dataSources().containsKey(measurement))
                .collect(Collectors.toSet());
        if (missing.isEmpty()) return new SeriesResolution(metadata, true, false, "");
        SeriesMetadata expanded = expandSeriesLocked(metadata, missing);
        return expanded == null
                ? new SeriesResolution(metadata, false, false, "RRD4J series schema 或本地容量已达上限")
                : new SeriesResolution(expanded, true, false, "");
    }

    private SeriesMetadata createSeriesLocked(String key, String keyFingerprint,
                                              NormalizedSample sample, long timestamp) throws IOException {
        String id = SERIES_PREFIX + keyFingerprint;
        Map<String, String> dataSources = dataSourceMapping(sample.measurements().keySet(), Map.of());
        SeriesMetadata metadata = new SeriesMetadata(METADATA_VERSION, id, sample.name(), sample.tags(),
                dataSources, timestamp);
        RrdDef definition = definition(rrdPath(id), timestamp, dataSources.values());
        byte[] metadataBytes = objectMapper.writeValueAsBytes(metadata);
        long required = definition.getEstimatedSize() + metadataBytes.length;
        if (definition.getEstimatedSize() > maxSeriesBytes
                || !ensureAdmissionCapacityLocked(sample.name(), required, true, null)) return null;

        Path rrdTarget = rrdPath(id);
        Path metadataTarget = metadataPath(id);
        Path rrdTemp = storageDirectory.resolve(id + ".rrd.tmp");
        Path metadataTemp = storageDirectory.resolve(id + ".meta.json.tmp");
        Files.deleteIfExists(rrdTemp);
        Files.deleteIfExists(metadataTemp);
        try {
            RrdDef tempDefinition = definition(rrdTemp, timestamp, dataSources.values());
            try (RrdDb ignored = new RrdDb(tempDefinition)) {
                // Creation allocates every archive slot, making the file size stable for its lifetime.
            }
            Files.write(metadataTemp, metadataBytes);
            long previousRrdBytes = sizeQuietly(rrdTarget);
            long previousMetadataBytes = sizeQuietly(metadataTarget);
            moveAtomically(rrdTemp, rrdTarget);
            adjustTrackedStorageBytes(sizeQuietly(rrdTarget) - previousRrdBytes);
            moveAtomically(metadataTemp, metadataTarget);
            adjustTrackedStorageBytes(sizeQuietly(metadataTarget) - previousMetadataBytes);
            if (trackedStorageBytes > maxStorageBytes) {
                deleteFiles(id);
                return null;
            }
            addSeriesLocked(key, metadata);
            return metadata;
        } catch (Exception exception) {
            Files.deleteIfExists(rrdTemp);
            Files.deleteIfExists(metadataTemp);
            deleteFiles(id);
            if (exception instanceof IOException ioException) throw ioException;
            throw new IOException("Failed to create RRD4J series " + id, exception);
        }
    }

    private SeriesMetadata expandSeriesLocked(SeriesMetadata metadata, Set<String> missing) throws IOException {
        Map<String, String> expandedMapping = dataSourceMapping(missing, metadata.dataSources());
        if (expandedMapping.size() > MAX_MEASUREMENTS) return null;
        Path rrd = rrdPath(metadata.id());
        List<DsDef> additions = new ArrayList<>();
        RrdDef expandedDefinition;
        Set<String> existingNames;
        try (RrdDb database = new RrdDb(rrd.toString())) {
            existingNames = Set.copyOf(Arrays.asList(database.getDsNames()));
            expandedDefinition = database.getRrdDef();
        }
        for (String measurement : missing.stream().sorted().toList()) {
            String dataSource = expandedMapping.get(measurement);
            if (existingNames.contains(dataSource)) continue;
            DsDef definition = new DsDef(dataSource, DsType.GAUGE, HEARTBEAT_SECONDS,
                    Double.NaN, Double.NaN);
            additions.add(definition);
            expandedDefinition.addDatasource(definition);
        }

        SeriesMetadata expanded = new SeriesMetadata(metadata.version(), metadata.id(), metadata.name(),
                metadata.tags(), expandedMapping, metadata.createdAtEpochSecond());
        byte[] metadataBytes = objectMapper.writeValueAsBytes(expanded);
        long currentRrdBytes = sizeQuietly(rrd);
        long currentMetadataBytes = sizeQuietly(metadataPath(metadata.id()));
        long growth = Math.max(0, expandedDefinition.getEstimatedSize() - currentRrdBytes)
                + Math.max(0, metadataBytes.length - currentMetadataBytes);
        if (expandedDefinition.getEstimatedSize() > maxSeriesBytes
                || !ensureAdmissionCapacityLocked(metadata.name(), growth, false, metadata.id())) return null;

        if (!additions.isEmpty()) {
            RrdToolkit.addDatasources(rrd.toString(), additions, false);
            adjustTrackedStorageBytes(sizeQuietly(rrd) - currentRrdBytes);
        }
        writeMetadataAtomically(expanded, metadataBytes);
        adjustTrackedStorageBytes(sizeQuietly(metadataPath(metadata.id())) - currentMetadataBytes);
        seriesByKey.put(seriesKey(expanded.name(), expanded.tags()), expanded);
        return expanded;
    }

    private boolean writeSampleLocked(SeriesMetadata metadata, Map<String, Double> measurements, long timestamp) {
        Path rrd = rrdPath(metadata.id());
        try (RrdDb database = new RrdDb(rrd.toString())) {
            if (timestamp <= database.getLastUpdateTime()) return true;
            Sample sample = database.createSample(timestamp);
            int written = 0;
            for (Map.Entry<String, Double> measurement : measurements.entrySet()) {
                String dataSource = metadata.dataSources().get(measurement.getKey());
                if (dataSource != null) {
                    sample.setValue(dataSource, measurement.getValue());
                    written++;
                }
            }
            if (written == 0) return false;
            sample.update();
            try {
                updateObservedRangeAfterWrite(metadata.id(), timestamp, database);
            } catch (IOException exception) {
                observedRangesById.remove(metadata.id());
                log.warn("Metrics RRD4J series {} 已写入，但无法更新实际覆盖范围，本次状态将保守显示为无覆盖",
                        metadata.id(), exception);
            }
            return true;
        } catch (Exception exception) {
            log.warn("写入 Metrics RRD4J series {} 失败", metadata.id(), exception);
            return false;
        }
    }

    private void collectTierLocked(List<SeriesMetadata> matching, long start, long end, long resolution,
                                   int limit, PriorityQueue<HistoryPoint> newest) {
        if (start > end) return;
        for (SeriesMetadata metadata : matching) {
            try (RrdDb database = new RrdDb(rrdPath(metadata.id()).toString())) {
                var request = database.createFetchRequest(ConsolFun.LAST, start, end, resolution);
                request.setFilter(Set.copyOf(metadata.dataSources().values()));
                FetchData data = request.fetchData();
                long[] timestamps = data.getTimestamps();
                NavigableMap<String, double[]> valuesByMeasurement = new TreeMap<>();
                metadata.dataSources().forEach((measurement, dataSource) ->
                        valuesByMeasurement.put(measurement, data.getValues(dataSource)));
                for (int row = 0; row < timestamps.length; row++) {
                    long timestamp = timestamps[row];
                    if (timestamp < start || timestamp > end) continue;
                    Map<String, Double> measurements = new LinkedHashMap<>();
                    for (Map.Entry<String, double[]> values : valuesByMeasurement.entrySet()) {
                        double value = values.getValue()[row];
                        if (Double.isFinite(value)) measurements.put(values.getKey(), value);
                    }
                    if (!measurements.isEmpty()) addNewest(newest, limit,
                            new HistoryPoint(timestamp, metadata.tags(), Map.copyOf(measurements)));
                }
            } catch (Exception exception) {
                log.warn("读取 Metrics RRD4J series {} 失败", metadata.id(), exception);
            }
        }
    }

    private static void addNewest(PriorityQueue<HistoryPoint> newest, int limit, HistoryPoint candidate) {
        if (newest.size() < limit) {
            newest.offer(candidate);
            return;
        }
        HistoryPoint oldest = newest.peek();
        if (oldest != null && candidate.epochSecond() > oldest.epochSecond()) {
            newest.poll();
            newest.offer(candidate);
        }
    }

    private void restoreIndexLocked() {
        seriesByKey.clear();
        observedRangesById.clear();
        Arrays.fill(seriesCountsByPriority, 0);
        if (!Files.isDirectory(storageDirectory)) return;
        Set<String> restoredIds = new java.util.HashSet<>();
        try (var paths = Files.list(storageDirectory)) {
            for (Path metadataFile : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(METADATA_SUFFIX)).toList()) {
                String filename = metadataFile.getFileName().toString();
                String id = filename.substring(0, filename.length() - METADATA_SUFFIX.length());
                try {
                    SeriesMetadata metadata = objectMapper.readValue(metadataFile.toFile(), SeriesMetadata.class);
                    if (!validMetadata(id, metadata)) throw new IOException("invalid series metadata");
                    if (removeOversizedRrdLocked(id)) continue;
                    ObservedSampleRange observed = validateOrMigrateAndReadRrd(metadata);
                    String key = seriesKey(metadata.name(), metadata.tags());
                    SeriesMetadata previous = seriesByKey.putIfAbsent(key, normalizedMetadata(metadata));
                    if (previous != null) throw new IOException("duplicate metric series");
                    if (observed != null) observedRangesById.put(id, observed);
                    restoredIds.add(id);
                } catch (IncompatibleRrdLayoutException exception) {
                    deleteFiles(id);
                    long evicted = evictedSeries.incrementAndGet();
                    log.warn("忽略并删除与当前归档配置不兼容的 Metrics RRD4J series {}，{}；下一次采样将按当前配置重建，累计淘汰 {} 个 series",
                            id, exception.getMessage(), evicted);
                } catch (Exception exception) {
                    log.warn("忽略并删除损坏的 Metrics RRD4J series 元数据 {}", metadataFile, exception);
                    deleteFiles(id);
                }
            }
        } catch (Exception exception) {
            log.warn("扫描 Metrics RRD4J 元数据失败，目录 {}", storageDirectory, exception);
        }
        removeOrphanRrdFilesLocked(restoredIds);
        seriesByKey.values().forEach(metadata -> seriesCountsByPriority[metricPriority(metadata.name())]++);
    }

    private boolean removeOversizedRrdLocked(String id) throws IOException {
        Path rrd = rrdPath(id);
        if (!Files.isRegularFile(rrd)) return false;
        long bytes = Files.size(rrd);
        if (bytes <= maxSeriesBytes) return false;

        deleteFiles(id);
        long evicted = evictedSeries.incrementAndGet();
        log.warn("忽略并删除超过单 series 上限的 Metrics RRD4J 文件 {}，size={} bytes，limit={} bytes，累计淘汰 {} 个 series",
                rrd.getFileName(), bytes, maxSeriesBytes, evicted);
        return true;
    }

    private boolean validMetadata(String id, SeriesMetadata metadata) {
        if (metadata == null || metadata.version() != METADATA_VERSION || !id.equals(metadata.id())
                || metadata.name() == null || metadata.name().isBlank() || metadata.tags() == null
                || metadata.dataSources() == null || metadata.dataSources().isEmpty()
                || metadata.dataSources().size() > MAX_MEASUREMENTS || metadata.createdAtEpochSecond() < 0) return false;
        if (!id.equals(SERIES_PREFIX + sha256(seriesKey(metadata.name(), metadata.tags())))) return false;
        return metadata.dataSources().values().stream().allMatch(value -> value != null && !value.isBlank());
    }

    private ObservedSampleRange validateAndReadRrd(SeriesMetadata metadata) throws IOException {
        return validateAndReadRrd(metadata, rrdPath(metadata.id()));
    }

    private ObservedSampleRange validateAndReadRrd(SeriesMetadata metadata, Path rrd) throws IOException {
        if (!Files.isRegularFile(rrd)) throw new IOException("RRD file is missing");
        try (RrdDb database = new RrdDb(rrd.toString())) {
            RrdDef definition = database.getRrdDef();
            validateArchiveLayout(definition);
            validateDatasourceLayout(definition);
            Set<String> dataSources = Set.copyOf(Arrays.asList(database.getDsNames()));
            if (!dataSources.containsAll(metadata.dataSources().values())) {
                throw new IOException("RRD datasource mapping is incomplete");
            }
            return readObservedRange(database, metadata.dataSources().values());
        }
    }

    /**
     * RRD archives have a fixed row count. Changing only the configured retention therefore needs an explicit
     * resize instead of treating the existing series as corrupt. RRD4J copies the newest rows when shrinking and
     * pads the older side with unknown values when growing, so every sample that fits in the new window survives.
     *
     * <p>The resized database is fully validated before a same-directory atomic replacement. If migration cannot
     * be completed safely, the original file remains active and readable with its previous retention window; a
     * later restart can retry the migration.</p>
     */
    private ObservedSampleRange validateOrMigrateAndReadRrd(SeriesMetadata metadata) throws IOException {
        try {
            return validateAndReadRrd(metadata);
        } catch (IncompatibleRrdLayoutException incompatible) {
            Path source = rrdPath(metadata.id());
            ObservedSampleRange originalRange;
            int existingRetentionRows;
            try (RrdDb database = new RrdDb(source.toString())) {
                RrdDef definition = database.getRrdDef();
                validateDatasourceLayout(definition);
                existingRetentionRows = retentionRowsWhenOnlyRetentionDiffers(definition);
                if (existingRetentionRows < 0) throw incompatible;
                Set<String> dataSources = Set.copyOf(Arrays.asList(database.getDsNames()));
                if (!dataSources.containsAll(metadata.dataSources().values())) {
                    throw new IOException("RRD datasource mapping is incomplete");
                }
                originalRange = readObservedRange(database, metadata.dataSources().values());
            }

            int expectedRows = retentionThirtyMinuteRows();
            Path temporary = storageDirectory.resolve(metadata.id() + ".rrd.resize.tmp");
            try {
                Files.deleteIfExists(temporary);
                RrdToolkit.resizeArchive(source.toString(), temporary.toString(), ConsolFun.LAST,
                        THIRTY_MINUTE_STEPS, expectedRows);
                long migratedBytes = Files.size(temporary);
                if (migratedBytes > maxSeriesBytes) {
                    throw new IOException("resized RRD exceeds per-series limit: " + migratedBytes
                            + " > " + maxSeriesBytes);
                }
                ObservedSampleRange migratedRange = validateAndReadRrd(metadata, temporary);
                replaceAtomically(temporary, source);
                log.info("已迁移 Metrics RRD4J series {} 的 30 分钟归档，rows {} -> {}，历史样本已保留",
                        metadata.id(), existingRetentionRows, expectedRows);
                return migratedRange;
            } catch (Exception migrationFailure) {
                deleteQuietly(temporary);
                log.warn("Metrics RRD4J series {} 保留期归档迁移失败，继续使用原 {} rows 文件且不删除历史；后续重启将重试",
                        metadata.id(), existingRetentionRows, migrationFailure);
                return originalRange;
            }
        }
    }

    /** Returns the current long-retention row count only when that is the sole schema difference. */
    private int retentionRowsWhenOnlyRetentionDiffers(RrdDef definition) {
        if (definition.getStep() != STEP_SECONDS) return -1;
        List<ArchiveLayout> actual = Arrays.stream(definition.getArcDefs())
                .map(archive -> new ArchiveLayout(archive.getConsolFun(), archive.getXff(),
                        archive.getSteps(), archive.getRows()))
                .sorted(ArchiveLayout.ORDER)
                .toList();
        List<ArchiveLayout> expected = expectedArchiveLayouts();
        if (actual.size() != expected.size()) return -1;
        int retentionRows = -1;
        for (int index = 0; index < actual.size(); index++) {
            ArchiveLayout current = actual.get(index);
            ArchiveLayout target = expected.get(index);
            if (current.consolidationFunction() != target.consolidationFunction()
                    || Double.compare(current.xff(), target.xff()) != 0
                    || current.steps() != target.steps()) return -1;
            if (current.steps() == THIRTY_MINUTE_STEPS) {
                retentionRows = current.rows();
            } else if (current.rows() != target.rows()) {
                return -1;
            }
        }
        return retentionRows >= 2 && retentionRows != retentionThirtyMinuteRows() ? retentionRows : -1;
    }

    private void validateArchiveLayout(RrdDef definition) throws IncompatibleRrdLayoutException {
        if (definition.getStep() != STEP_SECONDS) {
            throw new IncompatibleRrdLayoutException(
                    "step=" + definition.getStep() + "，expected=" + STEP_SECONDS);
        }
        List<ArchiveLayout> actual = Arrays.stream(definition.getArcDefs())
                .map(archive -> new ArchiveLayout(archive.getConsolFun(), archive.getXff(),
                        archive.getSteps(), archive.getRows()))
                .sorted(ArchiveLayout.ORDER)
                .toList();
        List<ArchiveLayout> expected = expectedArchiveLayouts();
        if (!actual.equals(expected)) {
            throw new IncompatibleRrdLayoutException("archives=" + actual + "，expected=" + expected);
        }
    }

    private List<ArchiveLayout> expectedArchiveLayouts() {
        return List.of(
                new ArchiveLayout(ConsolFun.LAST, X_FILES_FACTOR, 1, ONE_DAY_ROWS),
                new ArchiveLayout(ConsolFun.LAST, X_FILES_FACTOR, 5, SEVEN_DAY_FIVE_MINUTE_ROWS),
                new ArchiveLayout(ConsolFun.LAST, X_FILES_FACTOR,
                        THIRTY_MINUTE_STEPS, retentionThirtyMinuteRows())
        );
    }

    private void validateDatasourceLayout(RrdDef definition) throws IncompatibleRrdLayoutException {
        for (DsDef datasource : definition.getDsDefs()) {
            if (datasource.getDsType() != DsType.GAUGE
                    || datasource.getHeartbeat() != HEARTBEAT_SECONDS
                    || !Double.isNaN(datasource.getMinValue())
                    || !Double.isNaN(datasource.getMaxValue())) {
                throw new IncompatibleRrdLayoutException("datasource=" + datasource.getDsName()
                        + " type=" + datasource.getDsType()
                        + " heartbeat=" + datasource.getHeartbeat()
                        + " min=" + datasource.getMinValue()
                        + " max=" + datasource.getMaxValue()
                        + "，expected=GAUGE/" + HEARTBEAT_SECONDS + "/NaN/NaN");
            }
        }
    }

    private ObservedSampleRange readObservedRange(RrdDb database, Iterable<String> dataSources) throws IOException {
        long oldest = Long.MAX_VALUE;
        long newest = Long.MIN_VALUE;
        for (int archiveIndex = 0; archiveIndex < database.getArcCount(); archiveIndex++) {
            Archive archive = database.getArchive(archiveIndex);
            long start = archive.getStartTime();
            long step = archive.getArcStep();
            for (String dataSource : dataSources) {
                double[] values = archive.getRobin(database.getDsIndex(dataSource)).getValues();
                for (int row = 0; row < values.length; row++) {
                    if (!Double.isFinite(values[row])) continue;
                    long timestamp = start + row * step;
                    oldest = Math.min(oldest, timestamp);
                    newest = Math.max(newest, timestamp);
                }
            }
        }
        return oldest == Long.MAX_VALUE ? null : new ObservedSampleRange(oldest, newest);
    }

    private void updateObservedRangeAfterWrite(String id, long timestamp, RrdDb database) throws IOException {
        ObservedSampleRange previous = observedRangesById.get(id);
        ObservedSampleRange updated = previous == null
                ? new ObservedSampleRange(timestamp, timestamp)
                : previous.include(timestamp);
        long oldestArchiveStart = Long.MAX_VALUE;
        for (int archiveIndex = 0; archiveIndex < database.getArcCount(); archiveIndex++) {
            oldestArchiveStart = Math.min(oldestArchiveStart, database.getArchive(archiveIndex).getStartTime());
        }
        if (updated.oldestEpochSecond() < oldestArchiveStart) {
            SeriesMetadata metadata = seriesByKey.values().stream()
                    .filter(series -> series.id().equals(id))
                    .findFirst()
                    .orElse(null);
            ObservedSampleRange rescanned = metadata == null
                    ? null
                    : readObservedRange(database, metadata.dataSources().values());
            if (rescanned == null) observedRangesById.remove(id);
            else observedRangesById.put(id, rescanned);
            return;
        }
        observedRangesById.put(id, updated);
    }

    private LocalDate sampleDate(long epochSecond) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), clock.getZone()).toLocalDate();
    }

    private SeriesMetadata normalizedMetadata(SeriesMetadata metadata) {
        return new SeriesMetadata(metadata.version(), metadata.id(), metadata.name(),
                immutableSortedMap(metadata.tags()), immutableSortedMap(metadata.dataSources()),
                metadata.createdAtEpochSecond());
    }

    /** Performs the two O(number-of-files/series) maintenance passes once for the whole minute snapshot. */
    private void prepareWriteBatchLocked(long timestamp) {
        pruneRejectedSeriesCacheLocked(timestamp);
        refreshStorageBytesLocked();
        if (pruneExpiredLocked()) rejectedSeriesUntilByKey.clear();
        enforceCapacityLocked();
    }

    private boolean requiresBatchMaintenanceLocked(List<NormalizedSample> samples, long timestamp) {
        for (NormalizedSample sample : samples) {
            String key = seriesKey(sample.name(), sample.tags());
            SeriesMetadata metadata = seriesByKey.get(key);
            if (metadata == null) {
                Long rejectedUntil = rejectedSeriesUntilByKey.get(sha256(key));
                if (rejectedUntil == null || rejectedUntil <= timestamp) return true;
                continue;
            }
            if (sample.measurements().keySet().stream()
                    .anyMatch(measurement -> !metadata.dataSources().containsKey(measurement))) return true;
        }
        return false;
    }

    private void pruneRejectedSeriesCacheLocked(long timestamp) {
        rejectedSeriesUntilByKey.entrySet().removeIf(entry -> entry.getValue() <= timestamp);
    }

    private void rememberRejectedSeriesLocked(String key, long timestamp) {
        rejectedSeriesUntilByKey.put(key, timestamp + REJECTED_SERIES_CACHE_TTL_SECONDS);
        while (rejectedSeriesUntilByKey.size() > rejectedSeriesCacheLimit) {
            var iterator = rejectedSeriesUntilByKey.entrySet().iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
    }

    private boolean pruneExpiredLocked() {
        long cutoff = clock.instant().getEpochSecond() - (long) retentionDays * DAY_SECONDS;
        List<SeriesMetadata> expired = seriesByKey.values().stream()
                .filter(metadata -> lastUpdateTime(metadata) < cutoff)
                .toList();
        expired.forEach(metadata -> removeSeriesLocked(metadata, "过期"));
        return !expired.isEmpty();
    }

    private void enforceCapacityLocked() {
        while ((seriesByKey.size() > maxSeries || trackedStorageBytes > maxStorageBytes)
                && !seriesByKey.isEmpty()) {
            SeriesMetadata candidate = lowestPriorityLruCandidate(null, Integer.MIN_VALUE, true);
            if (candidate == null) break;
            evictSeriesLocked(candidate, "容量");
        }
    }

    /**
     * Makes room without allowing optional, high-cardinality meters to displace JVM/process/system and
     * easy.operations series. A quarter of the configured series slots remains available for core meters;
     * higher-priority arrivals may evict lower-priority least-recently-written series.
     */
    private boolean ensureAdmissionCapacityLocked(String incomingName, long requiredBytes, boolean needsSlot,
                                                   String excludedId) {
        if (requiredBytes < 0 || requiredBytes > maxStorageBytes) return false;
        int incomingPriority = metricPriority(incomingName);
        if (needsSlot && incomingPriority > CORE_PRIORITY) {
            int nonCoreLimit = maxNonCoreSeries();
            while (nonCoreSeriesCount() >= nonCoreLimit) {
                if (!hasLowerPrioritySeriesLocked(incomingPriority)) return false;
                SeriesMetadata candidate = lowestPriorityLruCandidate(excludedId, incomingPriority, false);
                if (candidate == null) return false;
                evictSeriesLocked(candidate, "优先级保留");
            }
        }
        while ((needsSlot && seriesByKey.size() >= maxSeries) || !hasCapacityLocked(requiredBytes)) {
            if (!hasLowerPrioritySeriesLocked(incomingPriority)) return false;
            SeriesMetadata candidate = lowestPriorityLruCandidate(excludedId, incomingPriority, false);
            if (candidate == null) return false;
            evictSeriesLocked(candidate, "优先级容量");
        }
        return true;
    }

    private int maxNonCoreSeries() {
        if (maxSeries <= 1) return maxSeries;
        int reservedCoreSlots = Math.max(1, maxSeries / 4);
        return Math.max(1, maxSeries - reservedCoreSlots);
    }

    private long nonCoreSeriesCount() {
        return (long) seriesCountsByPriority[PLATFORM_PRIORITY] + seriesCountsByPriority[OPTIONAL_PRIORITY];
    }

    private boolean hasLowerPrioritySeriesLocked(int incomingPriority) {
        for (int priority = incomingPriority + 1; priority <= OPTIONAL_PRIORITY; priority++) {
            if (seriesCountsByPriority[priority] > 0) return true;
        }
        return false;
    }

    /** Selects the least valuable candidate first, then the least recently written within that priority. */
    private SeriesMetadata lowestPriorityLruCandidate(String excludedId, int incomingPriority,
                                                       boolean allowAnyPriority) {
        return seriesByKey.values().stream()
                .filter(metadata -> !metadata.id().equals(excludedId))
                .filter(metadata -> allowAnyPriority || metricPriority(metadata.name()) > incomingPriority)
                .min(Comparator.comparingInt((SeriesMetadata metadata) -> metricPriority(metadata.name()))
                        .reversed()
                        .thenComparingLong(this::lastUpdateTime)
                        .thenComparing(SeriesMetadata::id))
                .orElse(null);
    }

    private void evictSeriesLocked(SeriesMetadata metadata, String reason) {
        removeSeriesLocked(metadata, reason);
        evictedSeries.incrementAndGet();
    }

    private void addSeriesLocked(String key, SeriesMetadata metadata) {
        SeriesMetadata previous = seriesByKey.put(key, metadata);
        if (previous == null) {
            seriesCountsByPriority[metricPriority(metadata.name())]++;
        }
    }

    private void removeSeriesLocked(SeriesMetadata metadata, String reason) {
        SeriesMetadata removed = seriesByKey.remove(seriesKey(metadata.name(), metadata.tags()));
        if (removed != null) {
            int priority = metricPriority(removed.name());
            seriesCountsByPriority[priority] = Math.max(0, seriesCountsByPriority[priority] - 1);
        }
        deleteFiles(metadata.id());
        log.info("已按{}策略删除 Metrics RRD4J series {} ({})", reason, metadata.id(), metadata.name());
    }

    private void cleanupObsoleteFilesLocked() {
        if (!Files.isDirectory(storageDirectory)) return;
        try (var paths = Files.list(storageDirectory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String filename = path.getFileName().toString();
                if (filename.endsWith(LEGACY_SUFFIX) || filename.endsWith(".tmp") || filename.endsWith(".bak")) {
                    Files.deleteIfExists(path);
                    if (filename.endsWith(LEGACY_SUFFIX)) {
                        log.info("已删除不再使用的 Metrics 自研时序分段 {}", filename);
                    }
                }
            }
        } catch (Exception exception) {
            log.warn("清理 Metrics 旧格式或临时文件失败，目录 {}", storageDirectory, exception);
        }
    }

    private void removeOrphanRrdFilesLocked(Set<String> restoredIds) {
        if (!Files.isDirectory(storageDirectory)) return;
        try (var paths = Files.list(storageDirectory)) {
            for (Path rrd : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(RRD_SUFFIX)).toList()) {
                String filename = rrd.getFileName().toString();
                String id = filename.substring(0, filename.length() - RRD_SUFFIX.length());
                if (!restoredIds.contains(id)) {
                    Files.deleteIfExists(rrd);
                    log.warn("已删除缺少有效元数据的 Metrics RRD4J 文件 {}", filename);
                }
            }
        } catch (Exception exception) {
            log.warn("清理孤立 Metrics RRD4J 文件失败，目录 {}", storageDirectory, exception);
        }
    }

    private RrdDef definition(Path path, long timestamp, Iterable<String> dataSources) {
        RrdDef definition = new RrdDef(path.toString(), timestamp - STEP_SECONDS, STEP_SECONDS);
        dataSources.forEach(dataSource -> definition.addDatasource(
                dataSource, DsType.GAUGE, HEARTBEAT_SECONDS, Double.NaN, Double.NaN));
        definition.addArchive(ConsolFun.LAST, X_FILES_FACTOR, 1, ONE_DAY_ROWS);
        definition.addArchive(ConsolFun.LAST, X_FILES_FACTOR, 5, SEVEN_DAY_FIVE_MINUTE_ROWS);
        definition.addArchive(ConsolFun.LAST, X_FILES_FACTOR, THIRTY_MINUTE_STEPS,
                retentionThirtyMinuteRows());
        return definition;
    }

    private int retentionThirtyMinuteRows() {
        return Math.toIntExact(Math.min(Integer.MAX_VALUE,
                Math.max(1_440L, (long) retentionDays * 24 * 2)));
    }

    private Map<String, String> dataSourceMapping(Iterable<String> measurements,
                                                   Map<String, String> existing) {
        Map<String, String> mapping = new TreeMap<>(existing);
        Set<String> used = new java.util.HashSet<>(existing.values());
        for (String measurement : measurements) {
            if (mapping.containsKey(measurement)) continue;
            String digest = sha256(measurement);
            String candidate = "m" + digest.substring(0, 19);
            int offset = 0;
            while (used.contains(candidate)) {
                offset++;
                candidate = "m" + digest.substring(offset, offset + 19);
            }
            mapping.put(measurement, candidate);
            used.add(candidate);
        }
        return Map.copyOf(mapping);
    }

    private void writeMetadataAtomically(SeriesMetadata metadata, byte[] bytes) throws IOException {
        Path target = metadataPath(metadata.id());
        Path temp = storageDirectory.resolve(metadata.id() + ".meta.json.tmp");
        Files.write(temp, bytes);
        moveAtomically(temp, target);
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Migration must never expose a half-replaced database or remove the original on an unsupported filesystem. */
    private static void replaceAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic RRD replacement is not supported; original file was preserved", exception);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // A failed migration must never turn temporary-file cleanup into deletion of the original series.
        }
    }

    private boolean hasCapacityLocked(long requiredBytes) {
        if (requiredBytes < 0 || requiredBytes > maxStorageBytes) return false;
        return trackedStorageBytes <= maxStorageBytes - requiredBytes;
    }

    private void refreshStorageBytesLocked() {
        trackedStorageBytes = directoryBytesLocked();
    }

    private void adjustTrackedStorageBytes(long delta) {
        trackedStorageBytes = Math.max(0, trackedStorageBytes + delta);
    }

    private long directoryBytesLocked() {
        directoryScans.incrementAndGet();
        if (!Files.isDirectory(storageDirectory)) return 0;
        try (var paths = Files.list(storageDirectory)) {
            return paths.filter(Files::isRegularFile).mapToLong(this::sizeQuietly).sum();
        } catch (Exception exception) {
            log.warn("统计 Metrics RRD4J 目录大小失败: {}", storageDirectory, exception);
            return 0;
        }
    }

    private long lastUpdateTime(SeriesMetadata metadata) {
        ObservedSampleRange observed = observedRangesById.get(metadata.id());
        return observed == null ? metadata.createdAtEpochSecond() : observed.newestEpochSecond();
    }

    private void deleteFiles(String id) {
        observedRangesById.remove(id);
        try {
            Path rrd = rrdPath(id);
            long bytes = sizeQuietly(rrd);
            if (Files.deleteIfExists(rrd)) adjustTrackedStorageBytes(-bytes);
        } catch (Exception exception) {
            log.warn("删除 Metrics RRD4J 文件失败: {}", rrdPath(id), exception);
        }
        try {
            Path metadata = metadataPath(id);
            long bytes = sizeQuietly(metadata);
            if (Files.deleteIfExists(metadata)) adjustTrackedStorageBytes(-bytes);
        } catch (Exception exception) {
            log.warn("删除 Metrics RRD4J 元数据失败: {}", metadataPath(id), exception);
        }
    }

    private Path rrdPath(String id) {
        return storageDirectory.resolve(id + RRD_SUFFIX);
    }

    private Path metadataPath(String id) {
        return storageDirectory.resolve(id + METADATA_SUFFIX);
    }

    private long sizeQuietly(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void markDropped(String reason, Exception exception) {
        long dropped = droppedWrites.incrementAndGet();
        if (dropped == 1 || dropped % 100 == 0) {
            if (exception == null) {
                log.warn("{}，累计丢弃 {} 个 Metrics 快照", reason, dropped);
            } else {
                log.warn("{}，累计丢弃 {} 个 Metrics 快照", reason, dropped, exception);
            }
        }
    }

    private static long normalizeTimestamp(long epochSecond) {
        return Math.floorDiv(epochSecond, STEP_SECONDS) * STEP_SECONDS;
    }

    static int metricPriority(String name) {
        if (name == null) return OPTIONAL_PRIORITY;
        if (name.startsWith("jvm.") || name.startsWith("process.") || name.startsWith("system.")
                || name.equals("easy.operations") || name.startsWith("easy.operations.")) {
            return CORE_PRIORITY;
        }
        if (name.startsWith("hikaricp.") || name.startsWith("jdbc.connections.")
                || name.startsWith("tomcat.threads.") || name.startsWith("executor.")) {
            return PLATFORM_PRIORITY;
        }
        return OPTIONAL_PRIORITY;
    }

    private static String seriesKey(String name, Map<String, String> tags) {
        StringBuilder key = new StringBuilder(name.length() + tags.size() * 32 + 16);
        appendPart(key, name);
        new TreeMap<>(tags).forEach((tag, value) -> {
            appendPart(key, tag);
            appendPart(key, value);
        });
        return key.toString();
    }

    private static void appendPart(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value).append(';');
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private static <V> Map<String, V> immutableSortedMap(Map<String, V> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return java.util.Collections.unmodifiableMap(new TreeMap<>(source));
    }

    private record NormalizedSample(String name, Map<String, String> tags, Map<String, Double> measurements) {
    }

    private record SeriesResolution(SeriesMetadata metadata, boolean complete, boolean rejectedSeries,
                                    String reason) {
    }

    private static final class BatchAdmissionBudget {
        private final int limit;
        private final Set<String> attemptedKeys = new java.util.HashSet<>();

        private BatchAdmissionBudget(int limit) {
            this.limit = Math.max(1, limit);
        }

        private boolean tryAcquire(String key) {
            if (attemptedKeys.contains(key) || attemptedKeys.size() >= limit) return false;
            attemptedKeys.add(key);
            return true;
        }
    }

    private record SeriesMetadata(int version, String id, String name, Map<String, String> tags,
                                  Map<String, String> dataSources, long createdAtEpochSecond) {
    }

    private record HistoryPoint(long epochSecond, Map<String, String> tags, Map<String, Double> measurements) {
    }

    private record ObservedSampleRange(long oldestEpochSecond, long newestEpochSecond) {
        private ObservedSampleRange include(long epochSecond) {
            return new ObservedSampleRange(Math.min(oldestEpochSecond, epochSecond),
                    Math.max(newestEpochSecond, epochSecond));
        }

        private ObservedSampleRange span(ObservedSampleRange other) {
            return new ObservedSampleRange(Math.min(oldestEpochSecond, other.oldestEpochSecond),
                    Math.max(newestEpochSecond, other.newestEpochSecond));
        }
    }

    private record ArchiveLayout(ConsolFun consolidationFunction, double xff, int steps, int rows) {
        private static final Comparator<ArchiveLayout> ORDER = Comparator
                .comparingInt(ArchiveLayout::steps)
                .thenComparing(layout -> layout.consolidationFunction().name())
                .thenComparingDouble(ArchiveLayout::xff)
                .thenComparingInt(ArchiveLayout::rows);
    }

    private static final class IncompatibleRrdLayoutException extends IOException {
        private IncompatibleRrdLayoutException(String message) {
            super(message);
        }
    }

    public record StorageStatus(boolean enabled, String storageDirectory, int retentionDays,
                                long maxStorageBytes, long maxSegmentBytes, long storageBytes,
                                String oldestSegmentDate, long coverageDays, int segmentCount,
                                long droppedWrites, long rejectedSeries, long evictedSeries) {
    }

    public record MetricSeriesDescriptor(String name, Map<String, String> tags, List<String> measurements) {
        public MetricSeriesDescriptor {
            tags = tags == null ? Map.of() : Map.copyOf(tags);
            measurements = measurements == null ? List.of() : List.copyOf(measurements);
        }
    }
}
