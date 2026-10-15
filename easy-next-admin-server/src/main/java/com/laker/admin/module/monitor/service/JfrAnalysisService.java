package com.laker.admin.module.monitor.service;

import com.laker.admin.infrastructure.observability.jfr.LocalJfrRecorder;
import jdk.jfr.consumer.RecordedClass;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordingFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads a bounded subset of a completed local JFR recording and turns it into
 * small aggregates suitable for the runtime diagnosis page.
 */
@Service
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
public class JfrAnalysisService {
    static final int MAX_RESULT_LIMIT = 100;
    static final int MAX_SCANNED_EVENTS = 250_000;
    static final int MAX_MULTI_FILES = 32;
    private static final int MAX_DEFAULT_FILE_CANDIDATES = 32;
    private static final int MAX_AGGREGATE_KEYS = 5_000;
    private static final int MAX_STACK_FINGERPRINT_FRAMES = 64;
    private static final int MAX_CONCURRENT_ANALYSES = 2;
    private static final long MAX_SCAN_NANOS = Duration.ofSeconds(5).toNanos();

    private static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
    private static final String ALLOCATION_SAMPLE = "jdk.ObjectAllocationSample";
    private static final String MONITOR_ENTER = "jdk.JavaMonitorEnter";
    private static final String MONITOR_WAIT = "jdk.JavaMonitorWait";
    private static final String THREAD_PARK = "jdk.ThreadPark";
    private static final String GARBAGE_COLLECTION = "jdk.GarbageCollection";
    private static final String GC_PHASE_PAUSE = "jdk.GCPhasePause";
    private static final String FILE_READ = "jdk.FileRead";
    private static final String FILE_WRITE = "jdk.FileWrite";
    private static final String SOCKET_READ = "jdk.SocketRead";
    private static final String SOCKET_WRITE = "jdk.SocketWrite";
    private static final String JAVA_ERROR_THROW = "jdk.JavaErrorThrow";
    private static final String RUNTIME_OPERATION = "com.laker.admin.RuntimeOperation";

    private final LocalJfrRecorder recorder;
    private final int maxWindowEvents;
    private final long maxScanNanos;
    /**
     * Single-file and multi-file reads share one small budget. Both paths perform the same bounded but CPU and
     * disk intensive event scan, so separate limits would still allow them to saturate the instance together.
     */
    private final Semaphore analysisSlots = new Semaphore(MAX_CONCURRENT_ANALYSES);

    @Autowired
    public JfrAnalysisService(LocalJfrRecorder recorder) {
        this(recorder, MAX_SCANNED_EVENTS, MAX_SCAN_NANOS);
    }

    JfrAnalysisService(LocalJfrRecorder recorder, int maxWindowEvents, long maxScanNanos) {
        this.recorder = recorder;
        this.maxWindowEvents = Math.max(1, maxWindowEvents);
        this.maxScanNanos = Math.max(1, maxScanNanos);
    }

    public JfrAnalysis analyze(String fileName, int requestedLimit) {
        return analyze(fileName, requestedLimit, null, null, null);
    }

    public JfrAnalysis analyze(String fileName, int requestedLimit, String traceId, Instant from, Instant to) {
        if (!analysisSlots.tryAcquire()) {
            return empty("BUSY", "当前 JFR 分析任务较多，请稍后重试", normalize(fileName));
        }
        try {
            return analyzeWithinBudget(fileName, requestedLimit, traceId, from, to);
        } finally {
            analysisSlots.release();
        }
    }

    private JfrAnalysis analyzeWithinBudget(String fileName, int requestedLimit,
                                            String traceId, Instant from, Instant to) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_RESULT_LIMIT));
        ScanBudget budget = new ScanBudget(maxScanNanos);
        Selection selection = select(fileName, budget);
        if (selection.path() == null) {
            return empty(selection.status(), selection.message(), selection.fileName());
        }

        Path file = selection.path();
        AnalysisAccumulator accumulator = new AnalysisAccumulator(limit, normalize(traceId), from, to);
        try {
            scanFile(file, accumulator, null, 0, budget);
            return accumulator.result(budget.reached ? "PARTIAL" : "READY",
                    budget.reached ? "JFR 文件校验或事件扫描达到安全上限，已返回部分聚合结果" : "分析完成",
                    file);
        } catch (IOException | RuntimeException exception) {
            if (accumulator.scannedEvents > 0) {
                return accumulator.result("PARTIAL", "JFR 文件读取中断，已返回可用的部分聚合结果", file);
            }
            return empty("INVALID", "JFR 文件不完整或无法读取", file.getFileName().toString());
        }
    }

    /**
     * Jointly analyzes several adjacent JFR files. Rotation briefly overlaps the outgoing and incoming recordings,
     * so the same event can occur in both files at a boundary. Aggregating after a per-file analysis would count it twice;
     * this method scans all requested files under one budget and removes cross-file duplicates before aggregation.
     */
    public JfrAnalysis analyzeFiles(List<String> requestedFileNames, int requestedLimit,
                                    String traceId, Instant from, Instant to) {
        if (!analysisSlots.tryAcquire()) {
            return empty("BUSY", "当前 JFR 联合分析任务较多，请稍后重试", null);
        }
        try {
            return analyzeFilesWithinBudget(requestedFileNames, requestedLimit, traceId, from, to);
        } finally {
            analysisSlots.release();
        }
    }

    private JfrAnalysis analyzeFilesWithinBudget(List<String> requestedFileNames, int requestedLimit,
                                                  String traceId, Instant from, Instant to) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_RESULT_LIMIT));
        if (from == null || to == null || from.isAfter(to)) {
            return empty("INVALID_RANGE", "JFR 联合分析需要合法的开始和结束时间", null);
        }

        LinkedHashSet<String> normalizedNames = new LinkedHashSet<>();
        if (requestedFileNames != null) {
            requestedFileNames.stream().map(JfrAnalysisService::normalize)
                    .filter(java.util.Objects::nonNull).forEach(normalizedNames::add);
        }
        if (normalizedNames.isEmpty()) {
            return empty("NO_RECORDING", "未指定需要联合分析的 JFR 分段", null);
        }

        boolean fileLimitReached = normalizedNames.size() > MAX_MULTI_FILES;
        List<String> selectedNames = normalizedNames.stream().limit(MAX_MULTI_FILES).toList();
        List<Path> paths = new ArrayList<>(selectedNames.size());
        int unresolvedFiles = 0;
        for (String name : selectedNames) {
            Path resolved = recorder.resolveFile(name);
            if (resolved == null) unresolvedFiles++;
            else paths.add(resolved);
        }
        if (paths.isEmpty()) {
            return empty("NOT_FOUND", "未找到指定的完整 JFR 文件", String.join(", ", selectedNames));
        }

        AnalysisAccumulator accumulator = new AnalysisAccumulator(limit, normalize(traceId), from, to);
        EventDeduplicator deduplicator = new EventDeduplicator();
        ScanBudget budget = new ScanBudget(maxScanNanos);
        List<Path> attemptedPaths = new ArrayList<>(paths.size());
        int unreadableFiles = 0;
        for (int fileIndex = 0; fileIndex < paths.size(); fileIndex++) {
            if (budget.stopIfExpired()) break;
            Path path = paths.get(fileIndex);
            attemptedPaths.add(path);
            try {
                scanFile(path, accumulator, deduplicator, fileIndex, budget);
            } catch (IOException | RuntimeException exception) {
                unreadableFiles++;
            }
            if (budget.reached) break;
        }

        if (!attemptedPaths.isEmpty() && unreadableFiles == attemptedPaths.size()
                && accumulator.scannedEvents == 0) {
            return empty("INVALID", "指定的 JFR 分段均不完整或无法读取",
                    attemptedPaths.stream().map(path -> path.getFileName().toString())
                            .reduce((left, right) -> left + ", " + right).orElse(null));
        }

        boolean partial = fileLimitReached || budget.reached || unresolvedFiles > 0 || unreadableFiles > 0;
        String message = multiAnalysisMessage(attemptedPaths.size() - unreadableFiles, accumulator.duplicateEvents,
                fileLimitReached, budget.reached, unresolvedFiles + unreadableFiles);
        return accumulator.result(partial ? "PARTIAL" : "READY", message, attemptedPaths);
    }

    private void scanFile(Path path, AnalysisAccumulator accumulator, EventDeduplicator deduplicator,
                          int sourceIndex, ScanBudget budget) throws IOException {
        if (budget.stopIfExpired()) return;
        boolean completeRecording = isCompleteRecording(path);
        if (budget.stopIfExpired()) return;
        if (!completeRecording) throw new IOException("invalid JFR recording");
        AtomicReference<Throwable> streamFailure = new AtomicReference<>();
        CountDownLatch streamClosed = new CountDownLatch(1);
        try (EventStream stream = EventStream.openFile(path)) {
            // JFR applies startTime to event end-times. This skips old chunks/events while retaining a long event
            // that began before the window and still overlaps it. Do not set endTime: that would discard events
            // which start inside the window but finish after it.
            if (accumulator.from != null) stream.setStartTime(accumulator.from);
            stream.onError(failure -> streamFailure.compareAndSet(null, failure));
            stream.onClose(streamClosed::countDown);
            stream.onEvent(event -> {
                // EventStream may still deliver events already buffered in its current chunk after close(). Keep
                // those callbacks constant-time and outside all counters once the shared budget has been reached.
                if (budget.stopIfExpired()) {
                    stream.close();
                    return;
                }
                WindowPosition position = accumulator.observe(event);
                if (position != WindowPosition.INSIDE) return;
                if (accumulator.windowEvents >= maxWindowEvents) {
                    budget.markReached();
                    stream.close();
                    return;
                }
                accumulator.acceptObserved(event, deduplicator, sourceIndex);
                if (budget.stopIfExpired()) stream.close();
            });
            if (budget.stopIfExpired()) return;
            stream.startAsync();
            boolean completed;
            try {
                completed = streamClosed.await(budget.remainingNanos(), TimeUnit.NANOSECONDS);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                throw new IOException("JFR event stream interrupted", interruptedException);
            }
            if (!completed) {
                budget.markReached();
                stream.close();
            }
        }
        Throwable failure = streamFailure.get();
        if (failure instanceof IOException ioException) throw ioException;
        if (failure != null) throw new IOException("JFR event stream failed", failure);
    }

    private static final class ScanBudget {
        private final long maxNanos;
        private volatile boolean reached;
        private boolean started;
        private long startedAt;

        private ScanBudget(long maxNanos) {
            this.maxNanos = Math.max(1, maxNanos);
        }

        /** Starts when the first recording is about to be opened, not when the first in-window event arrives. */
        private boolean stopIfExpired() {
            if (reached) return true;
            if (!started) {
                started = true;
                startedAt = System.nanoTime();
                return false;
            }
            if (System.nanoTime() - startedAt < maxNanos) return false;
            reached = true;
            return true;
        }

        private long remainingNanos() {
            if (stopIfExpired()) return 0;
            return Math.max(0, maxNanos - (System.nanoTime() - startedAt));
        }

        private void markReached() {
            reached = true;
        }
    }

    private Selection select(String requestedFileName, ScanBudget budget) {
        if (requestedFileName != null && !requestedFileName.isBlank()) {
            String normalized = requestedFileName.trim();
            Path resolved = recorder.resolveFile(normalized);
            return resolved == null
                    ? new Selection("NOT_FOUND", "未找到指定的完整 JFR 文件", normalized, null)
                    : new Selection("READY", "", normalized, resolved);
        }

        boolean hadCandidate = false;
        int inspectedCandidates = 0;
        for (LocalJfrRecorder.JfrFile candidate : recorder.files()) {
            if (inspectedCandidates++ >= MAX_DEFAULT_FILE_CANDIDATES) break;
            Path resolved = recorder.resolveFile(candidate.fileName());
            if (resolved == null) continue;
            hadCandidate = true;
            if (budget.stopIfExpired()) {
                return new Selection("PARTIAL", "JFR 文件校验达到安全时间上限", candidate.fileName(), null);
            }
            boolean completeRecording = isCompleteRecording(resolved);
            if (budget.stopIfExpired()) {
                return new Selection("PARTIAL", "JFR 文件校验达到安全时间上限", candidate.fileName(), null);
            }
            if (completeRecording) {
                return new Selection("READY", "", candidate.fileName(), resolved);
            }
        }
        return hadCandidate
                ? new Selection("INVALID", "当前没有可读取的完整 JFR 文件", null, null)
                : new Selection("NO_RECORDING", "尚未生成完整的 JFR 分段", null, null);
    }

    private static boolean isCompleteRecording(Path path) {
        try {
            if (!Files.isRegularFile(path) || Files.size(path) <= 0) return false;
            try (RecordingFile recording = new RecordingFile(path)) {
                recording.readEventTypes();
                return true;
            }
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static JfrAnalysis empty(String status, String message, String fileName) {
        return new JfrAnalysis(status, message, fileName, 0, null, null, null,
                0, 0, 0, "PARTIAL".equals(status),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static String multiAnalysisMessage(int analyzedFiles, long duplicateEvents,
                                               boolean fileLimitReached, boolean scanLimitReached,
                                               int failedFiles) {
        StringBuilder message = new StringBuilder("已联合分析 ").append(analyzedFiles)
                .append(" 个 JFR 分段，去重 ").append(duplicateEvents).append(" 个边界重复事件");
        if (fileLimitReached) message.append("；文件数超过上限，仅分析前 ").append(MAX_MULTI_FILES).append(" 个");
        if (scanLimitReached) message.append("；事件数或分析时间达到安全上限");
        if (failedFiles > 0) message.append("；").append(failedFiles).append(" 个分段无法读取");
        return message.toString();
    }

    private record Selection(String status, String message, String fileName, Path path) { }

    public record JfrAnalysis(
            String status,
            String message,
            String fileName,
            long fileSizeBytes,
            String modifiedAt,
            String recordingStartedAt,
            String recordingEndedAt,
            long scannedEvents,
            long relevantEvents,
            long deduplicatedEvents,
            boolean truncated,
            List<JfrAggregate> executionHotspots,
            List<JfrAggregate> allocations,
            List<JfrAggregate> locks,
            List<JfrAggregate> gcPauses,
            List<JfrAggregate> ioActivities,
            List<JfrAggregate> errorThrows,
            List<RuntimeOperation> operations
    ) { }

    public record RuntimeOperation(String type, String traceId, String operation, String detail, String outcome,
                                   String errorType,
                                   long durationNanos, long cpuTimeMs, long allocatedBytes,
                                   long blockedCount, long waitedCount, String startedAt, String endedAt) { }

    /**
     * Generic JFR aggregate. Durations remain in nanoseconds so short lock and
     * safepoint events are not rounded down before the browser formats them.
     */
    public record JfrAggregate(
            String key,
            String detail,
            long count,
            long estimatedBytes,
            long totalDurationNanos,
            long maxDurationNanos
    ) { }

    private static final class AnalysisAccumulator {
        private final int limit;
        private final Map<String, MutableAggregate> execution = new LinkedHashMap<>();
        private final Map<String, MutableAggregate> allocations = new LinkedHashMap<>();
        private final Map<String, MutableAggregate> locks = new LinkedHashMap<>();
        private final Map<String, MutableAggregate> gcPauses = new LinkedHashMap<>();
        private final Map<String, MutableAggregate> ioActivities = new LinkedHashMap<>();
        private final Map<String, MutableAggregate> errorThrows = new LinkedHashMap<>();
        private final List<RuntimeOperation> operations = new java.util.ArrayList<>();
        private final String traceId;
        private final Instant from;
        private final Instant to;
        private long scannedEvents;
        private long windowEvents;
        private long relevantEvents;
        private long duplicateEvents;
        private Instant startedAt;
        private Instant endedAt;

        private AnalysisAccumulator(int limit, String traceId, Instant from, Instant to) {
            this.limit = limit;
            this.traceId = traceId;
            this.from = from;
            this.to = to;
        }

        private WindowPosition observe(RecordedEvent event) {
            scannedEvents++;
            Instant eventStart = event.getStartTime();
            Instant eventEnd = event.getEndTime();
            if (startedAt == null || eventStart.isBefore(startedAt)) startedAt = eventStart;
            if (endedAt == null || eventEnd.isAfter(endedAt)) endedAt = eventEnd;
            if (from != null && eventEnd.isBefore(from)) return WindowPosition.BEFORE;
            if (to != null && eventStart.isAfter(to)) return WindowPosition.AFTER;
            return WindowPosition.INSIDE;
        }

        private void acceptObserved(RecordedEvent event, EventDeduplicator deduplicator, int sourceIndex) {
            windowEvents++;
            String eventName = event.getEventType().getName();
            if (!isRelevantEvent(eventName)) return;
            if (deduplicator != null && deduplicator.isCrossFileDuplicate(event, eventName, sourceIndex)) {
                duplicateEvents++;
                return;
            }
            switch (eventName) {
                case EXECUTION_SAMPLE -> addExecution(event);
                case ALLOCATION_SAMPLE -> addAllocation(event);
                case MONITOR_ENTER, MONITOR_WAIT, THREAD_PARK -> addLock(event, eventName);
                case GARBAGE_COLLECTION, GC_PHASE_PAUSE -> addGcPause(event, eventName);
                case FILE_READ, FILE_WRITE, SOCKET_READ, SOCKET_WRITE -> addIo(event, eventName);
                case JAVA_ERROR_THROW -> addErrorThrow(event);
                case RUNTIME_OPERATION -> addRuntimeOperation(event);
                default -> { }
            }
        }

        private void addRuntimeOperation(RecordedEvent event) {
            String eventTraceId = normalize(stringValue(event, "traceId"));
            if (traceId != null && !traceId.equals(eventTraceId)) return;
            relevantEvents++;
            if (operations.size() >= MAX_AGGREGATE_KEYS) return;
            operations.add(new RuntimeOperation(stringValue(event, "type"), eventTraceId,
                    stringValue(event, "operation"), stringValue(event, "detail"),
                    stringValue(event, "outcome"), stringValue(event, "errorType"),
                    durationNanos(event.getDuration()),
                    longValue(event, "cpuTimeMs"), longValue(event, "allocatedBytes"),
                    longValue(event, "blockedCount"), longValue(event, "waitedCount"),
                    instantString(event.getStartTime()), instantString(event.getEndTime())));
        }

        private void addExecution(RecordedEvent event) {
            relevantEvents++;
            String frame = topFrame(event);
            String owner = frameOwner(frame);
            aggregate(execution, frame, owner).add(0, 0);
        }

        private void addAllocation(RecordedEvent event) {
            relevantEvents++;
            String className = className(event, "objectClass");
            long bytes = positiveLong(event, "weight");
            aggregate(allocations, className, topFrame(event)).add(bytes, 0);
        }

        private void addLock(RecordedEvent event, String eventName) {
            relevantEvents++;
            String field = THREAD_PARK.equals(eventName) ? "parkedClass" : "monitorClass";
            String className = className(event, field);
            String kind = switch (eventName) {
                case MONITOR_ENTER -> "MONITOR_ENTER";
                case MONITOR_WAIT -> "MONITOR_WAIT";
                default -> "THREAD_PARK";
            };
            String key = kind + " · " + className;
            aggregate(locks, key, topFrame(event)).add(0, durationNanos(event.getDuration()));
        }

        private void addGcPause(RecordedEvent event, String eventName) {
            relevantEvents++;
            String name = stringValue(event, "name");
            String cause = stringValue(event, "cause");
            String phase = GC_PHASE_PAUSE.equals(eventName) ? "GC_PHASE" : "GC";
            String key = phase + " · " + firstKnown(name, "unknown");
            String detail = firstKnown(cause, topFrame(event));
            long duration = event.hasField("sumOfPauses")
                    ? durationFieldNanos(event, "sumOfPauses")
                    : durationNanos(event.getDuration());
            aggregate(gcPauses, key, detail).add(0, duration);
        }

        private void addIo(RecordedEvent event, String eventName) {
            relevantEvents++;
            boolean file = FILE_READ.equals(eventName) || FILE_WRITE.equals(eventName);
            boolean read = FILE_READ.equals(eventName) || SOCKET_READ.equals(eventName);
            String kind = file ? (read ? "FILE_READ" : "FILE_WRITE") : (read ? "SOCKET_READ" : "SOCKET_WRITE");
            String target;
            if (file) {
                target = firstKnown(stringValue(event, "path"), "unknown");
            } else {
                String host = firstKnown(stringValue(event, "host"), stringValue(event, "address"));
                long port = longValue(event, "port");
                target = host + (port >= 0 ? ":" + port : "");
            }
            long bytes = positiveLong(event, read ? "bytesRead" : "bytesWritten");
            aggregate(ioActivities, kind + " · " + target, topFrame(event))
                    .add(bytes, durationNanos(event.getDuration()));
        }

        private void addErrorThrow(RecordedEvent event) {
            relevantEvents++;
            String errorClass = className(event, "thrownClass");
            aggregate(errorThrows, errorClass, topFrame(event)).add(0, 0);
        }

        private MutableAggregate aggregate(Map<String, MutableAggregate> target, String key, String detail) {
            MutableAggregate aggregate = target.get(key);
            if (aggregate != null) return aggregate;
            if (target.size() < MAX_AGGREGATE_KEYS) {
                aggregate = new MutableAggregate(key, detail);
                target.put(key, aggregate);
                return aggregate;
            }
            return target.computeIfAbsent("other", ignored ->
                    new MutableAggregate("other", "超过聚合维度安全上限的其余事件"));
        }

        private JfrAnalysis result(String status, String message, Path file) {
            return result(status, message, List.of(file));
        }

        private JfrAnalysis result(String status, String message, List<Path> files) {
            long totalFileSize = files.stream().mapToLong(JfrAnalysisService::fileSize)
                    .reduce(0, JfrAnalysisService::saturatedAdd);
            String latestModifiedAt = files.stream().map(JfrAnalysisService::modifiedAt)
                    .filter(java.util.Objects::nonNull).max(String::compareTo).orElse(null);
            String fileNames = files.stream().map(path -> path.getFileName().toString())
                    .reduce((left, right) -> left + ", " + right).orElse(null);
            return new JfrAnalysis(status, message, fileNames, totalFileSize, latestModifiedAt,
                    instantString(startedAt), instantString(endedAt), scannedEvents, relevantEvents,
                    duplicateEvents, "PARTIAL".equals(status),
                    sorted(execution, Comparator.comparingLong(JfrAggregate::count).reversed()
                            .thenComparing(JfrAggregate::key)),
                    sorted(allocations, Comparator.comparingLong(JfrAggregate::estimatedBytes).reversed()
                            .thenComparing(Comparator.comparingLong(JfrAggregate::count).reversed())
                            .thenComparing(JfrAggregate::key)),
                    sorted(locks, Comparator.comparingLong(JfrAggregate::totalDurationNanos).reversed()
                            .thenComparing(Comparator.comparingLong(JfrAggregate::count).reversed())
                            .thenComparing(JfrAggregate::key)),
                    sorted(gcPauses, Comparator.comparingLong(JfrAggregate::totalDurationNanos).reversed()
                            .thenComparing(Comparator.comparingLong(JfrAggregate::count).reversed())
                            .thenComparing(JfrAggregate::key)),
                    sorted(ioActivities, Comparator.comparingLong(JfrAggregate::totalDurationNanos).reversed()
                            .thenComparing(Comparator.comparingLong(JfrAggregate::estimatedBytes).reversed())
                            .thenComparing(JfrAggregate::key)),
                    sorted(errorThrows, Comparator.comparingLong(JfrAggregate::count).reversed()
                            .thenComparing(JfrAggregate::key)),
                    operations.stream().sorted(Comparator.comparingLong(RuntimeOperation::durationNanos).reversed())
                            .limit(limit).toList());
        }

        private List<JfrAggregate> sorted(Map<String, MutableAggregate> source,
                                          Comparator<JfrAggregate> comparator) {
            return source.values().stream().map(MutableAggregate::snapshot)
                    .sorted(comparator).limit(limit).toList();
        }
    }

    private enum WindowPosition { BEFORE, INSIDE, AFTER }

    private static boolean isRelevantEvent(String eventName) {
        return switch (eventName) {
            case EXECUTION_SAMPLE, ALLOCATION_SAMPLE, MONITOR_ENTER, MONITOR_WAIT, THREAD_PARK,
                    GARBAGE_COLLECTION, GC_PHASE_PAUSE, FILE_READ, FILE_WRITE, SOCKET_READ, SOCKET_WRITE,
                    JAVA_ERROR_THROW, RUNTIME_OPERATION -> true;
            default -> false;
        };
    }

    /** Fixed-size fingerprints bound deduplication memory while avoiding retention of JFR event graphs. */
    private static final class EventDeduplicator {
        private final Map<EventFingerprint, Integer> firstSourceByFingerprint = new HashMap<>();

        private boolean isCrossFileDuplicate(RecordedEvent event, String eventName, int sourceIndex) {
            EventFingerprint fingerprint = fingerprint(event, eventName);
            Integer firstSource = firstSourceByFingerprint.putIfAbsent(fingerprint, sourceIndex);
            return firstSource != null && firstSource != sourceIndex;
        }
    }

    private record EventFingerprint(long high, long low) { }

    private static EventFingerprint fingerprint(RecordedEvent event, String eventName) {
        FingerprintHash hash = new FingerprintHash();
        hash.add(1); // fingerprint schema version
        hash.add(eventName);
        hash.add(event.getStartTime());
        hash.add(event.getEndTime());
        hash.add(durationNanos(event.getDuration()));
        addThreadFingerprint(hash, recordedThread(event, eventName));
        addStackFingerprint(hash, event.getStackTrace());

        switch (eventName) {
            case EXECUTION_SAMPLE -> hash.add(fieldString(event, "state"));
            case ALLOCATION_SAMPLE -> {
                hash.add(className(event, "objectClass"));
                hash.add(longValue(event, "weight"));
            }
            case MONITOR_ENTER, MONITOR_WAIT -> {
                hash.add(className(event, "monitorClass"));
                hash.add(longValue(event, "address"));
                addNamedThreadFingerprint(hash, event, "previousOwner");
                addNamedThreadFingerprint(hash, event, "notifier");
                hash.add(fieldString(event, "timedOut"));
                hash.add(durationFieldNanos(event, "timeout"));
            }
            case THREAD_PARK -> {
                hash.add(className(event, "parkedClass"));
                hash.add(longValue(event, "address"));
                hash.add(fieldString(event, "timedOut"));
                hash.add(durationFieldNanos(event, "timeout"));
            }
            case GARBAGE_COLLECTION, GC_PHASE_PAUSE -> {
                hash.add(stringValue(event, "name"));
                hash.add(stringValue(event, "cause"));
                hash.add(longValue(event, "gcId"));
                hash.add(durationFieldNanos(event, "sumOfPauses"));
                hash.add(durationFieldNanos(event, "longestPause"));
            }
            case FILE_READ, FILE_WRITE -> {
                hash.add(stringValue(event, "path"));
                hash.add(longValue(event, "bytesRead"));
                hash.add(longValue(event, "bytesWritten"));
            }
            case SOCKET_READ, SOCKET_WRITE -> {
                hash.add(stringValue(event, "host"));
                hash.add(stringValue(event, "address"));
                hash.add(longValue(event, "port"));
                hash.add(longValue(event, "bytesRead"));
                hash.add(longValue(event, "bytesWritten"));
            }
            case JAVA_ERROR_THROW -> {
                hash.add(className(event, "thrownClass"));
                hash.add(stringValue(event, "message"));
            }
            case RUNTIME_OPERATION -> {
                hash.add(stringValue(event, "type"));
                hash.add(stringValue(event, "traceId"));
                hash.add(stringValue(event, "operation"));
                hash.add(stringValue(event, "detail"));
                hash.add(stringValue(event, "outcome"));
                hash.add(stringValue(event, "errorType"));
                hash.add(longValue(event, "cpuTimeMs"));
                hash.add(longValue(event, "allocatedBytes"));
                hash.add(longValue(event, "blockedCount"));
                hash.add(longValue(event, "waitedCount"));
            }
            default -> { }
        }
        return hash.finish();
    }

    private static RecordedThread recordedThread(RecordedEvent event, String eventName) {
        try {
            if (EXECUTION_SAMPLE.equals(eventName) && event.hasField("sampledThread")) {
                return event.getThread("sampledThread");
            }
            return event.getThread();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void addThreadFingerprint(FingerprintHash hash, RecordedThread thread) {
        if (thread == null) {
            hash.add(-1);
            return;
        }
        hash.add(thread.getJavaThreadId());
        hash.add(thread.getOSThreadId());
        hash.add(thread.getJavaName());
    }

    private static void addNamedThreadFingerprint(FingerprintHash hash, RecordedEvent event, String field) {
        try {
            addThreadFingerprint(hash, event.hasField(field) ? event.getThread(field) : null);
        } catch (RuntimeException ignored) {
            addThreadFingerprint(hash, null);
        }
    }

    private static void addStackFingerprint(FingerprintHash hash, RecordedStackTrace stackTrace) {
        if (stackTrace == null || stackTrace.getFrames() == null) {
            hash.add(-1);
            return;
        }
        List<RecordedFrame> frames = stackTrace.getFrames();
        int count = Math.min(frames.size(), MAX_STACK_FINGERPRINT_FRAMES);
        hash.add(count);
        for (int index = 0; index < count; index++) {
            RecordedFrame frame = frames.get(index);
            if (frame == null || frame.getMethod() == null || frame.getMethod().getType() == null) {
                hash.add((String) null);
                continue;
            }
            hash.add(frame.getMethod().getType().getName());
            hash.add(frame.getMethod().getName());
            hash.add(frame.getMethod().getDescriptor());
            hash.add(frame.getLineNumber());
            hash.add(frame.getBytecodeIndex());
            hash.add(frame.getType());
        }
    }

    private static String fieldString(RecordedEvent event, String field) {
        if (!event.hasField(field)) return null;
        try {
            Object value = event.getValue(field);
            return value == null ? null : value.toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static final class FingerprintHash {
        private long high = 0xcbf29ce484222325L;
        private long low = 0x9e3779b97f4a7c15L;

        private void add(Instant value) {
            if (value == null) {
                add(-1);
                return;
            }
            add(value.getEpochSecond());
            add(value.getNano());
        }

        private void add(String value) {
            if (value == null) {
                add(-1);
                return;
            }
            add(value.length());
            for (int index = 0; index < value.length(); index++) add(value.charAt(index));
        }

        private void add(long value) {
            for (int shift = 0; shift < Long.SIZE; shift += Byte.SIZE) mix((int) (value >>> shift) & 0xff);
        }

        private void mix(int value) {
            high = (high ^ value) * 0x100000001b3L;
            low ^= value + 0x9e3779b9L + (low << 6) + (low >>> 2);
            low = Long.rotateLeft(low, 17) * 0x94d049bb133111ebL;
        }

        private EventFingerprint finish() {
            return new EventFingerprint(high, low);
        }
    }

    private static final class MutableAggregate {
        private final String key;
        private final String detail;
        private long count;
        private long estimatedBytes;
        private long totalDurationNanos;
        private long maxDurationNanos;

        private MutableAggregate(String key, String detail) {
            this.key = key;
            this.detail = detail;
        }

        private void add(long bytes, long durationNanos) {
            count = saturatedAdd(count, 1);
            estimatedBytes = saturatedAdd(estimatedBytes, bytes);
            totalDurationNanos = saturatedAdd(totalDurationNanos, durationNanos);
            maxDurationNanos = Math.max(maxDurationNanos, durationNanos);
        }

        private JfrAggregate snapshot() {
            return new JfrAggregate(key, detail, count, estimatedBytes,
                    totalDurationNanos, maxDurationNanos);
        }
    }

    private static String topFrame(RecordedEvent event) {
        RecordedStackTrace stackTrace = event.getStackTrace();
        if (stackTrace == null || stackTrace.getFrames().isEmpty()) return "unknown";
        for (RecordedFrame frame : stackTrace.getFrames()) {
            if (frame == null || frame.getMethod() == null || frame.getMethod().getType() == null) continue;
            String owner = frame.getMethod().getType().getName();
            String method = frame.getMethod().getName();
            int line = frame.getLineNumber();
            return owner + "." + method + (line > 0 ? ":" + line : "");
        }
        return "unknown";
    }

    private static String frameOwner(String frame) {
        if (frame == null || "unknown".equals(frame)) return "unknown";
        int methodSeparator = frame.lastIndexOf('.', frame.indexOf(':') >= 0 ? frame.indexOf(':') : frame.length());
        return methodSeparator > 0 ? frame.substring(0, methodSeparator) : frame;
    }

    private static String className(RecordedEvent event, String field) {
        if (!event.hasField(field)) return "unknown";
        try {
            RecordedClass recordedClass = event.getClass(field);
            return recordedClass == null || recordedClass.getName() == null
                    ? "unknown" : recordedClass.getName();
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    private static String stringValue(RecordedEvent event, String field) {
        if (!event.hasField(field)) return null;
        try {
            String value = event.getString(field);
            return value == null || value.isBlank() ? null : value;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static long positiveLong(RecordedEvent event, String field) {
        if (!event.hasField(field)) return 0;
        try {
            return Math.max(0, event.getLong(field));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static long longValue(RecordedEvent event, String field) {
        if (!event.hasField(field)) return -1;
        try {
            return event.getLong(field);
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static long durationFieldNanos(RecordedEvent event, String field) {
        try {
            return durationNanos(event.getDuration(field));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static long durationNanos(Duration duration) {
        if (duration == null || duration.isNegative()) return 0;
        try {
            return duration.toNanos();
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static String firstKnown(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() || "unknown".equals(preferred)
                ? (fallback == null || fallback.isBlank() ? "unknown" : fallback)
                : preferred;
    }

    private static long saturatedAdd(long left, long right) {
        if (right <= 0) return left;
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private static long fileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException ignored) {
            return 0;
        }
    }

    private static String modifiedAt(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant().toString();
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String instantString(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
