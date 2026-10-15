package com.laker.admin.module.monitor.service;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.infrastructure.observability.trace.BoundedDiagnosticReadExecutor;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Aggregates bounded, persisted operation samples for the runtime diagnosis UI. */
@Service
public class RuntimeStatisticsService {
    static final int MAX_SCAN_SAMPLES = 50_000;
    static final int MAX_RETURNED_GROUPS = 200;
    static final int MAX_ACCUMULATED_GROUPS = 5_000;
    static final long CACHE_TTL_NANOS = TimeUnit.SECONDS.toNanos(5);

    private static final Set<Integer> SUPPORTED_WINDOWS = Set.of(1, 24, 168, 720);
    private static final int MAX_CACHE_ENTRIES = 32;
    private static final String INCIDENT_COVERAGE = "SLOW_OR_ERROR_ONLY";
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_DETAIL_LENGTH = 500;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final RecentTraceStore traceStore;
    private final Clock clock;
    private final LongSupplier ticker;
    private final BoundedDiagnosticReadExecutor scanAdmission = new BoundedDiagnosticReadExecutor(1);
    private final Map<StatisticsCacheKey, CachedStatistics> cache =
            new LinkedHashMap<>(16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<StatisticsCacheKey, CachedStatistics> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            };

    @Autowired
    public RuntimeStatisticsService(RecentTraceStore traceStore) {
        this(traceStore, Clock.systemDefaultZone(), System::nanoTime);
    }

    RuntimeStatisticsService(RecentTraceStore traceStore, Clock clock) {
        this(traceStore, clock, System::nanoTime);
    }

    RuntimeStatisticsService(RecentTraceStore traceStore, Clock clock, LongSupplier ticker) {
        this.traceStore = traceStore;
        this.clock = clock;
        this.ticker = ticker;
    }

    public StatisticsResult statistics(String requestedType, int windowHours, int limit, String sort) {
        TypeSelection selection = TypeSelection.parse(requestedType);
        validateWindow(windowHours);
        SortSpec sortSpec = SortSpec.parse(sort);
        int appliedLimit = Math.max(1, Math.min(limit, MAX_RETURNED_GROUPS));
        StatisticsCacheKey cacheKey = new StatisticsCacheKey(
                selection.label(), windowHours, limit, appliedLimit, sortSpec.canonical());

        // This endpoint can scan up to 50,000 persisted operation projections. A small bounded cache acts as
        // single-flight protection for the same view. One timed admission slot serializes distinct cold scans and
        // rejects excess dashboard requests instead of leaving servlet threads queued behind local disk work.
        return scanAdmission.execute(() -> {
            long nowNanos = ticker.getAsLong();
            CachedStatistics cached = cache.get(cacheKey);
            if (cached != null && cached.isFresh(nowNanos)) return cached.result();

            StatisticsResult result = calculateStatistics(
                    selection, windowHours, limit, appliedLimit, sortSpec);
            cache.put(cacheKey, new CachedStatistics(nowNanos, result));
            return result;
        });
    }

    private StatisticsResult calculateStatistics(TypeSelection selection, int windowHours, int requestedLimit,
                                                 int appliedLimit, SortSpec sortSpec) {
        LocalDateTime to = LocalDateTime.now(clock);
        LocalDateTime from = to.minusHours(windowHours);

        EnumMap<OperationType, CoverageCounter> coverageCounters = new EnumMap<>(OperationType.class);
        for (OperationType type : selection.types()) {
            coverageCounters.put(type, new CoverageCounter());
        }

        Map<GroupKey, StatisticsAccumulator> groups = new LinkedHashMap<>();
        EnumMap<OperationType, StatisticsAccumulator> overflowGroups = new EnumMap<>(OperationType.class);
        boolean[] groupLimitReached = {false};
        long[] matchedSamples = {0};

        // The store keeps only a compact top-K projection while scanning. Large retained stack/lock evidence and
        // child-operation maps are never materialized by this aggregate endpoint.
        RecentTraceStore.OperationScanResult scan = traceStore.scanRecentOperationSummaries(
                MAX_SCAN_SAMPLES, from, to.plusNanos(1),
                sample -> selection.includes(OperationType.parseStored(sample == null ? null : sample.type())),
                sample -> {
                    OperationType type = OperationType.parseStored(sample == null ? null : sample.type());
                    if (type == null || !selection.includes(type)) {
                        return;
                    }
                    CoverageCounter counter = coverageCounters.get(type);
                    counter.readSamples++;
                    if (!inWindow(sample.finishedAt(), from, to)) {
                        return;
                    }
                    counter.matchedSamples++;
                    matchedSamples[0]++;

                    GroupKey groupKey = groupKey(type, sample);
                    StatisticsAccumulator accumulator = groups.get(groupKey);
                    if (accumulator == null && groups.size() < MAX_ACCUMULATED_GROUPS) {
                        accumulator = new StatisticsAccumulator(
                                type, groupKey.name(), displayDetail(type, sample.detail()));
                        groups.put(groupKey, accumulator);
                    } else if (accumulator == null) {
                        groupLimitReached[0] = true;
                        accumulator = overflowGroups.computeIfAbsent(type,
                                ignored -> new StatisticsAccumulator(type, "其他操作（分组上限外）", ""));
                    }
                    accumulator.add(sample);
                });
        if (scan == null) {
            scan = new RecentTraceStore.OperationScanResult(0, false);
        }
        boolean scanLimitReached = scan.scanLimitReached();

        List<StatisticRow> rows = new ArrayList<>(groups.size() + overflowGroups.size());
        groups.values().forEach(accumulator -> rows.add(accumulator.toRow()));
        overflowGroups.values().forEach(accumulator -> rows.add(accumulator.toRow()));
        rows.sort(sortSpec.comparator());
        int totalGroups = rows.size();
        List<StatisticRow> limitedRows = List.copyOf(rows.subList(0, Math.min(appliedLimit, rows.size())));

        List<TypeCoverage> coverageByType = selection.types().stream()
                .map(type -> coverage(type, coverageCounters.get(type), scanLimitReached))
                .toList();
        boolean sampled = true;
        String coverage = INCIDENT_COVERAGE;
        return new StatisticsResult(selection.label(), windowHours, from, to, requestedLimit, appliedLimit,
                sortSpec.canonical(), MAX_SCAN_SAMPLES, scan.readSamples(), matchedSamples[0], scanLimitReached,
                groupLimitReached[0], sampled, coverage, totalGroups, coverageByType, limitedRows);
    }

    private record StatisticsCacheKey(String type, int windowHours, int requestedLimit, int appliedLimit,
                                      String sort) {
    }

    private record CachedStatistics(long createdAtNanos, StatisticsResult result) {
        private boolean isFresh(long nowNanos) {
            long age = nowNanos - createdAtNanos;
            return age >= 0 && age < CACHE_TTL_NANOS;
        }
    }

    private static void validateWindow(int windowHours) {
        if (!SUPPORTED_WINDOWS.contains(windowHours)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "windowHours 仅支持 1、24、168、720");
        }
    }

    private static boolean inWindow(LocalDateTime finishedAt, LocalDateTime from, LocalDateTime to) {
        return finishedAt != null && !finishedAt.isBefore(from) && !finishedAt.isAfter(to);
    }

    private static GroupKey groupKey(OperationType type, RecentTraceStore.OperationSummarySample sample) {
        String name = boundedText(sample.name(), "未命名操作", MAX_NAME_LENGTH);
        String discriminator = type == OperationType.SQL
                ? boundedText(sample.detail(), "", MAX_DETAIL_LENGTH) : "";
        return new GroupKey(type, name, discriminator);
    }

    private static String displayDetail(OperationType type, String detail) {
        if (type != OperationType.SQL) {
            return "";
        }
        return boundedText(detail, "", MAX_DETAIL_LENGTH);
    }

    private static String boundedText(String value, String fallback, int maxLength) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = WHITESPACE.matcher(value).replaceAll(" ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "…";
    }

    private static TypeCoverage coverage(OperationType type, CoverageCounter counter,
                                         boolean scanLimitReached) {
        String note = "本地详情只保留慢调用、错误或包含慢错子调用的 incident 样本；count、errorRate 和分位数仅代表这些样本。全量次数与资源趋势请查看 easy.operations.* 及对应业务 Metrics。";
        return new TypeCoverage(type.name(), "operation", true, INCIDENT_COVERAGE,
                counter.readSamples, counter.matchedSamples, scanLimitReached, note);
    }

    private enum OperationType {
        HTTP,
        JOB,
        KAFKA,
        TRANSACTION,
        REMOTE,
        SQL;

        private static OperationType parseStored(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
    }

    private record TypeSelection(String label, List<OperationType> types) {
        private static TypeSelection parse(String requestedType) {
            String normalized = requestedType == null || requestedType.isBlank()
                    ? "ALL" : requestedType.trim().toUpperCase(Locale.ROOT);
            if ("ALL".equals(normalized)) {
                return new TypeSelection("ALL", List.of(OperationType.values()));
            }
            OperationType type = OperationType.parseStored(normalized);
            if (type == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "type 仅支持 ALL、HTTP、JOB、KAFKA、TRANSACTION、REMOTE、SQL");
            }
            return new TypeSelection(type.name(), List.of(type));
        }

        private boolean includes(OperationType type) {
            return type != null && types.contains(type);
        }

        private boolean isAll() {
            return "ALL".equals(label);
        }
    }

    private record GroupKey(OperationType type, String name, String discriminator) {
    }

    private static final class CoverageCounter {
        private int readSamples;
        private int matchedSamples;
    }

    private static final class StatisticsAccumulator {
        private final OperationType type;
        private final String name;
        private final String detail;
        private final List<Long> durations = new ArrayList<>();
        private long count;
        private long errorCount;
        private long totalDurationMs;
        private long maxDurationMs;
        private double cpuTotalMs;
        private long cpuSamples;
        private double allocatedTotalBytes;
        private long allocatedSamples;

        private StatisticsAccumulator(OperationType type, String name, String detail) {
            this.type = type;
            this.name = name;
            this.detail = detail;
        }

        private void add(RecentTraceStore.OperationSummarySample sample) {
            long duration = Math.max(sample.durationMs(), 0);
            count++;
            if (!"success".equalsIgnoreCase(sample.outcome())) {
                errorCount++;
            }
            totalDurationMs = saturatedAdd(totalDurationMs, duration);
            maxDurationMs = Math.max(maxDurationMs, duration);
            durations.add(duration);
            if (sample.cpuTimeMs() >= 0) {
                cpuTotalMs += sample.cpuTimeMs();
                cpuSamples++;
            }
            if (sample.allocatedBytes() >= 0) {
                allocatedTotalBytes += sample.allocatedBytes();
                allocatedSamples++;
            }
        }

        private StatisticRow toRow() {
            durations.sort(Long::compareTo);
            return new StatisticRow(type.name(), name, detail, isSampled(type),
                    INCIDENT_COVERAGE,
                    count, errorCount, count == 0 ? 0 : errorCount / (double) count,
                    count == 0 ? 0 : totalDurationMs / (double) count,
                    maxDurationMs, percentile(durations, 0.50), percentile(durations, 0.95),
                    percentile(durations, 0.99), totalDurationMs,
                    cpuSamples == 0 ? null : cpuTotalMs / cpuSamples,
                    allocatedSamples == 0 ? null : allocatedTotalBytes / allocatedSamples,
                    cpuSamples, allocatedSamples);
        }
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static boolean isSampled(OperationType type) {
        return true;
    }

    private static long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0;
        }
        int index = Math.max(0, (int) Math.ceil(percentile * sortedValues.size()) - 1);
        return sortedValues.get(Math.min(index, sortedValues.size() - 1));
    }

    private enum SortField {
        TYPE("type"),
        NAME("name"),
        COUNT("count"),
        ERROR_COUNT("errorCount"),
        ERROR_RATE("errorRate"),
        AVG_DURATION("avgDurationMs"),
        MAX_DURATION("maxDurationMs"),
        P50("p50Ms"),
        P95("p95Ms"),
        P99("p99Ms"),
        TOTAL_DURATION("totalDurationMs"),
        AVG_CPU("avgCpuMs"),
        AVG_ALLOCATED("avgAllocatedBytes");

        private final String fieldName;

        SortField(String fieldName) {
            this.fieldName = fieldName;
        }

        private static SortField parse(String raw) {
            String normalized = raw.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "type" -> TYPE;
                case "name" -> NAME;
                case "count" -> COUNT;
                case "errorcount" -> ERROR_COUNT;
                case "errorrate" -> ERROR_RATE;
                case "avg", "avgduration", "avgdurationms" -> AVG_DURATION;
                case "max", "maxduration", "maxdurationms" -> MAX_DURATION;
                case "p50", "p50ms" -> P50;
                case "p95", "p95ms" -> P95;
                case "p99", "p99ms" -> P99;
                case "totalduration", "totaldurationms" -> TOTAL_DURATION;
                case "avgcpu", "avgcpums" -> AVG_CPU;
                case "avgallocated", "avgallocatedbytes" -> AVG_ALLOCATED;
                default -> throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "不支持的 sort 字段: " + raw);
            };
        }
    }

    private record SortSpec(SortField field, boolean ascending) {
        private static SortSpec parse(String raw) {
            String value = raw == null || raw.isBlank() ? "totalDurationMs,desc" : raw.trim();
            String[] parts = value.split("[:,]", -1);
            if (parts.length > 2 || parts[0].isBlank()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "sort 格式应为 field,asc 或 field,desc");
            }
            SortField field = SortField.parse(parts[0].trim());
            String direction = parts.length == 1 || parts[1].isBlank()
                    ? (field == SortField.NAME || field == SortField.TYPE ? "asc" : "desc")
                    : parts[1].trim().toLowerCase(Locale.ROOT);
            if (!"asc".equals(direction) && !"desc".equals(direction)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "sort 方向仅支持 asc 或 desc");
            }
            return new SortSpec(field, "asc".equals(direction));
        }

        private String canonical() {
            return field.fieldName + "," + (ascending ? "asc" : "desc");
        }

        private Comparator<StatisticRow> comparator() {
            return (left, right) -> {
                int compared = switch (field) {
                    case TYPE -> left.type().compareTo(right.type());
                    case NAME -> left.name().compareTo(right.name());
                    case COUNT -> Long.compare(left.count(), right.count());
                    case ERROR_COUNT -> Long.compare(left.errorCount(), right.errorCount());
                    case ERROR_RATE -> Double.compare(left.errorRate(), right.errorRate());
                    case AVG_DURATION -> Double.compare(left.avgDurationMs(), right.avgDurationMs());
                    case MAX_DURATION -> Long.compare(left.maxDurationMs(), right.maxDurationMs());
                    case P50 -> Long.compare(left.p50Ms(), right.p50Ms());
                    case P95 -> Long.compare(left.p95Ms(), right.p95Ms());
                    case P99 -> Long.compare(left.p99Ms(), right.p99Ms());
                    case TOTAL_DURATION -> Long.compare(left.totalDurationMs(), right.totalDurationMs());
                    case AVG_CPU -> compareNullable(left.avgCpuMs(), right.avgCpuMs(), ascending);
                    case AVG_ALLOCATED -> compareNullable(
                            left.avgAllocatedBytes(), right.avgAllocatedBytes(), ascending);
                };
                if (field != SortField.AVG_CPU && field != SortField.AVG_ALLOCATED && !ascending) {
                    compared = -compared;
                }
                if (compared != 0) {
                    return compared;
                }
                int typeCompared = left.type().compareTo(right.type());
                return typeCompared != 0 ? typeCompared : left.name().compareTo(right.name());
            };
        }

        private static int compareNullable(Double left, Double right, boolean ascending) {
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return 1;
            }
            if (right == null) {
                return -1;
            }
            int compared = Double.compare(left, right);
            return ascending ? compared : -compared;
        }
    }

    public record StatisticsResult(String type, int windowHours, LocalDateTime from, LocalDateTime to,
                                   int requestedLimit, int appliedLimit, String sort, int scanLimit,
                                   int readSamples, long matchedSamples, boolean scanLimitReached,
                                   boolean groupLimitReached, boolean sampled, String coverage,
                                   int totalGroups, List<TypeCoverage> coverageByType,
                                   List<StatisticRow> items) {
    }

    public record TypeCoverage(String type, String source, boolean sampled, String coverage,
                               int readSamples, int matchedSamples, boolean scanLimitReached, String note) {
    }

    public record StatisticRow(String type, String name, String detail, boolean sampled, String coverage,
                               long count, long errorCount, double errorRate, double avgDurationMs,
                               long maxDurationMs, long p50Ms, long p95Ms, long p99Ms,
                               long totalDurationMs, Double avgCpuMs, Double avgAllocatedBytes,
                               long cpuSamples, long allocatedSamples) {
    }
}
