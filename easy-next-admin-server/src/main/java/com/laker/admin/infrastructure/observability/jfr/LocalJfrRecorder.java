package com.laker.admin.infrastructure.observability.jfr;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;
import jdk.jfr.consumer.RecordingFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Rotates low-overhead local JFR recordings into independently queryable files with an explicit disk budget. */
@Component
@Slf4j
public class LocalJfrRecorder {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final Pattern FILE_NAME = Pattern.compile(
            "^runtime-([0-9]{8}-[0-9]{6})(?:-([0-9]{3}))?(?:-[0-9]+)?\\.jfr$");
    private static final long DEFAULT_MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    private static final long DEFAULT_MAX_FILE_BYTES = 16L * 1024 * 1024;
    private static final int ROTATION_ACTIVE_RECORDINGS = 2;
    private static final Duration CONTINUOUS_SAMPLE_PERIOD = Duration.ofMillis(20);
    private static final Duration CONTINUOUS_EVENT_THRESHOLD = Duration.ofMillis(20);
    private static final Duration THREAD_CPU_LOAD_PERIOD = Duration.ofSeconds(10);
    private static final String ALLOCATION_SAMPLE_THROTTLE = "150/s";

    private final Path directory;
    private final int retentionDays;
    private final long maxTotalBytes;
    private final long maxFileBytes;
    private volatile Recording recording;
    private Instant segmentStartedAt;
    private Path activePartFile;
    private Path activeFinalFile;
    private Instant sessionStartedAt;
    private Instant autoStopAt;
    private volatile boolean closingRequested;
    private boolean shutdownRequested;
    private volatile String lastError = "";
    @Value("${easy.monitor.diagnosis.enabled:true}")
    private boolean diagnosisEnabled = true;
    @Value("${easy.features.monitor:true}")
    private boolean monitorEnabled = true;
    @Value("${easy.monitor.diagnosis.jfr-enabled:false}")
    private boolean jfrEnabled = false;
    @Value("${easy.monitor.diagnosis.jfr-max-session-minutes:1440}")
    private int maxSessionMinutes = 1440;

    public LocalJfrRecorder(@Value("${easy.monitor.diagnosis.storage-dir:./data/diagnosis}") String storageDir,
                            @Value("${easy.monitor.diagnosis.jfr-retention-days:1}") int retentionDays,
                            @Value("${easy.monitor.diagnosis.jfr-max-total-bytes:67108864}") long maxTotalBytes,
                            @Value("${easy.monitor.diagnosis.jfr-max-file-bytes:16777216}") long maxFileBytes,
                            @Value("${easy.monitor.diagnosis.jfr-rotation-ms:1800000}") long rotationMs) {
        this.directory = Path.of(storageDir).resolve("jfr").toAbsolutePath().normalize();
        this.retentionDays = Math.max(1, retentionDays);
        this.maxTotalBytes = maxTotalBytes > 1 ? maxTotalBytes : DEFAULT_MAX_TOTAL_BYTES;
        long configuredFileBytes = maxFileBytes > 0 ? maxFileBytes : DEFAULT_MAX_FILE_BYTES;
        // Rotation deliberately overlaps old and new recordings. Limit each one to half the total budget so the
        // overlap itself can never make the configured hard cap impossible to satisfy.
        this.maxFileBytes = Math.min(configuredFileBytes, this.maxTotalBytes / ROTATION_ACTIVE_RECORDINGS);
        // rotationMs is consumed by the @Scheduled expression as the single rotation cadence.
        // The recording itself deliberately has no maxAge: scheduler delays must not discard the start of a segment.
    }

    @PostConstruct
    public synchronized void start() {
        if (!monitorEnabled || !diagnosisEnabled || closingRequested || shutdownRequested) return;
        boolean storageReady;
        try {
            Files.createDirectories(directory);
            recoverAbandonedParts();
            storageReady = cleanupExpiredAndOversized();
        } catch (Exception exception) {
            lastError = safeMessage(exception);
            log.error("准备本地 JFR 运行诊断目录失败", exception);
            return;
        }
        // Recovery and retention are storage lifecycle work and must also run while on-demand JFR is disabled.
        if (!jfrEnabled || recording != null) return;
        if (!storageReady) return;
        if (sessionStartedAt == null) {
            sessionStartedAt = Instant.now();
            autoStopAt = sessionStartedAt.plus(Duration.ofMinutes(effectiveMaxSessionMinutes()));
        }
        if (sessionExpired()) {
            finishSession();
            return;
        }
        PreparedRecording prepared = null;
        try {
            if (!FlightRecorder.isAvailable()) {
                lastError = "当前 JVM 不支持 JFR";
                log.warn("{}，运行诊断将保留 Metrics、Trace 和事务样本", lastError);
                return;
            }
            prepared = newPreparedRecording();
            recording = prepared.recording();
            segmentStartedAt = prepared.startedAt();
            activePartFile = prepared.partFile();
            activeFinalFile = prepared.finalFile();
            lastError = "";
        } catch (RecordingStartCancelledException ignored) {
            recording = null;
            segmentStartedAt = null;
            activePartFile = null;
            activeFinalFile = null;
        } catch (Exception exception) {
            lastError = safeMessage(exception);
            if (prepared != null) {
                closeQuietly(prepared.recording());
                deleteQuietly(prepared.partFile());
            }
            recording = null;
            segmentStartedAt = null;
            deleteQuietly(activePartFile);
            activePartFile = null;
            activeFinalFile = null;
            log.error("启动本地 JFR 运行诊断失败", exception);
        }
    }

    @Scheduled(fixedDelayString = "${easy.monitor.diagnosis.jfr-rotation-ms:1800000}",
            initialDelayString = "${easy.monitor.diagnosis.jfr-rotation-ms:1800000}")
    public synchronized void rotate() {
        if (!monitorEnabled || !diagnosisEnabled || closingRequested || shutdownRequested) return;
        finalizeEndedSessionIfNecessary();
        if (!jfrEnabled) {
            cleanupExpiredAndOversized();
            return;
        }
        if (sessionExpired()) {
            finishSession();
            return;
        }
        if (recording == null) {
            cleanupExpiredAndOversized();
            start();
            return;
        }
        // The outgoing and replacement destinations coexist until the old recording has been stopped and
        // published. Reserve both maximum sizes before opening the replacement.
        if (!cleanupExpiredAndOversized(ROTATION_ACTIVE_RECORDINGS)) return;
        rotateCurrentSegment();
        cleanupExpiredAndOversized();
    }

    @Scheduled(fixedDelayString = "${easy.monitor.diagnosis.jfr-session-check-ms:60000}",
            initialDelayString = "${easy.monitor.diagnosis.jfr-session-check-ms:60000}")
    public synchronized void enforceSessionLimit() {
        if (!monitorEnabled || !diagnosisEnabled || closingRequested || shutdownRequested) return;
        finalizeEndedSessionIfNecessary();
    }

    /** Enables a bounded diagnostic recording session. Runtime enablement intentionally resets on restart. */
    public synchronized RecorderStatus enable(int durationMinutes) {
        if (!monitorEnabled || !diagnosisEnabled || closingRequested || shutdownRequested) return status();
        finalizeEndedSessionIfNecessary();
        if (recording != null && recording.getState() == RecordingState.RUNNING) return status();
        int boundedMinutes = Math.max(1, Math.min(durationMinutes, effectiveMaxSessionMinutes()));
        jfrEnabled = true;
        sessionStartedAt = Instant.now();
        autoStopAt = sessionStartedAt.plus(Duration.ofMinutes(boundedMinutes));
        start();
        return status();
    }

    /** Stops and publishes the active recording while leaving the application running. */
    public synchronized RecorderStatus disable() {
        finishSession();
        return status();
    }

    /** Lightweight hot-path check used before creating the custom per-operation JFR event. */
    public boolean isRecording() {
        Recording current = recording;
        return current != null && current.getState() == RecordingState.RUNNING;
    }

    /** Marks shutdown before lifecycle destruction without waiting for a rotation that may already own this monitor. */
    @EventListener(ContextClosedEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void markClosing() {
        closingRequested = true;
    }

    @PreDestroy
    public synchronized void stop() {
        if (shutdownRequested) return;
        shutdownRequested = true;
        jfrEnabled = false;
        stopActiveRecording(true);
        if (recording == null) {
            sessionStartedAt = null;
            autoStopAt = null;
        }
    }

    private void stopActiveRecording(boolean preserveIncompletePart) {
        Recording current = recording;
        Path currentPartFile = activePartFile;
        Path currentFinalFile = activeFinalFile;
        Finalization finalization = Finalization.empty();
        try {
            finalization = stopAndPublish(current, currentPartFile, currentFinalFile, preserveIncompletePart);
            if (finalization.failure() != null) {
                lastError = safeMessage(finalization.failure());
                log.warn("停止本地 JFR 录制失败", finalization.failure());
            }
        } finally {
            // JVM shutdown hooks run concurrently. If JFR is already destroying its repository, stop() may fail
            // while its own dump-on-exit path is still writing the destination. Closing here can truncate that
            // file to zero bytes. Keep the recording and its active .part identity in that uncertain state so
            // retention cannot reclassify and delete a destination that the JFR shutdown hook still owns.
            if (finalization.safeToClose()) {
                closeQuietly(current);
                recording = null;
                segmentStartedAt = null;
                activePartFile = null;
                activeFinalFile = null;
                cleanupExpiredAndOversized();
            }
        }
    }

    public synchronized List<JfrFile> files() {
        finalizeEndedSessionIfNecessary();
        return completedFiles();
    }

    private List<JfrFile> completedFiles() {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var paths = Files.list(directory)) {
            List<Path> completedPaths = paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> FILE_NAME.matcher(path.getFileName().toString()).matches())
                    .toList();
            return completedPaths.stream()
                    .map(this::toJfrFile)
                    .filter(Objects::nonNull)
                    .filter(file -> file.sizeBytes() > 0)
                    .sorted(Comparator.comparing(JfrFile::modifiedAt).reversed())
                    .toList();
        } catch (Exception exception) {
            lastError = safeMessage(exception);
            log.warn("列出 JFR 录制文件失败: {}", directory, exception);
            return List.of();
        }
    }

    public Path resolveFile(String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) return null;
        Path file = directory.resolve(fileName).normalize();
        try {
            if (!file.getParent().equals(directory)) return null;
            Path realDirectory = directory.toRealPath();
            Path realFile = file.toRealPath();
            return realFile.getParent().equals(realDirectory)
                    && Files.isRegularFile(realFile, LinkOption.NOFOLLOW_LINKS)
                    && Files.size(realFile) > 0 ? realFile : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public synchronized RecorderStatus status() {
        finalizeEndedSessionIfNecessary();
        List<JfrFile> completed = completedFiles();
        long completedBytes = completed.stream().mapToLong(JfrFile::sizeBytes).sum();
        long activeBytes = activeRecordingSizeQuietly();
        long recoverableBytes = nonActivePartFilesOldestFirst().stream().mapToLong(this::sizeQuietly).sum();
        long totalBytes = completedBytes + activeBytes + recoverableBytes;
        boolean running = recording != null && recording.getState() == RecordingState.RUNNING;
        CoverageSummary coverage = coverageSummary(completed, running);
        boolean flightRecorderAvailable = FlightRecorder.isAvailable();
        boolean available = monitorEnabled && diagnosisEnabled && flightRecorderAvailable;
        String statusError = !monitorEnabled ? "监控功能已关闭"
                : !diagnosisEnabled ? "本地诊断已关闭"
                : !flightRecorderAvailable ? "当前 JVM 不支持 JFR"
                : lastError;
        return new RecorderStatus(monitorEnabled && diagnosisEnabled && jfrEnabled, available, running, statusError, retentionDays, maxTotalBytes,
                completed.size(), completedBytes, activeBytes, totalBytes, coverage.oldestRecordingAt(), coverage.coverageHours(),
                totalBytes >= Math.round(maxTotalBytes * 0.9), instantText(sessionStartedAt), instantText(autoStopAt),
                effectiveMaxSessionMinutes());
    }

    private boolean sessionExpired() {
        Instant deadline = autoStopAt;
        return jfrEnabled && deadline != null && !Instant.now().isBefore(deadline);
    }

    /** Publishes a recording stopped by its JFR duration before exposing recorder state or starting a replacement. */
    private void finalizeEndedSessionIfNecessary() {
        Recording current = recording;
        boolean recordingEnded = current != null
                && (current.getState() == RecordingState.STOPPED || current.getState() == RecordingState.CLOSED);
        // A runtime disable failure must not leave a RUNNING recording orphaned merely because jfrEnabled is now
        // false. Retry once per scheduled/status lifecycle pass. During JVM shutdown a RUNNING destination may be
        // owned by JFR's shutdown hook, so only retry it after that recording reports STOPPED/CLOSED.
        boolean runtimeStopRetry = current != null && !jfrEnabled && !shutdownRequested;
        if (sessionExpired() || recordingEnded || runtimeStopRetry) finishSession();
    }

    private void finishSession() {
        jfrEnabled = false;
        stopActiveRecording(false);
        if (recording == null) {
            sessionStartedAt = null;
            autoStopAt = null;
        }
    }

    private int effectiveMaxSessionMinutes() {
        return Math.max(1, Math.min(maxSessionMinutes, 24 * 60));
    }

    private static String instantText(Instant value) {
        return value == null ? null : value.toString();
    }

    private CoverageSummary coverageSummary(List<JfrFile> completed, boolean running) {
        Instant now = Instant.now();
        List<RecordingInterval> intervals = new ArrayList<>();
        for (JfrFile file : completed) {
            addInterval(intervals, file.recordingStartedAt(), file.recordingEndedAt(), now);
        }
        if (running && segmentStartedAt != null) {
            addInterval(intervals, segmentStartedAt, now, now);
        }
        if (intervals.isEmpty()) return new CoverageSummary(null, 0);

        intervals.sort(Comparator.comparing(RecordingInterval::startedAt));
        Instant oldest = intervals.get(0).startedAt();
        Instant mergedStart = oldest;
        Instant mergedEnd = intervals.get(0).endedAt();
        Duration covered = Duration.ZERO;
        for (int index = 1; index < intervals.size(); index++) {
            RecordingInterval interval = intervals.get(index);
            if (!interval.startedAt().isAfter(mergedEnd)) {
                if (interval.endedAt().isAfter(mergedEnd)) mergedEnd = interval.endedAt();
                continue;
            }
            covered = covered.plus(Duration.between(mergedStart, mergedEnd));
            mergedStart = interval.startedAt();
            mergedEnd = interval.endedAt();
        }
        covered = covered.plus(Duration.between(mergedStart, mergedEnd));
        return new CoverageSummary(oldest.toString(), Math.max(0, covered.toHours()));
    }

    private void addInterval(List<RecordingInterval> intervals, String startedAt, String endedAt, Instant now) {
        if (startedAt == null || endedAt == null) return;
        try {
            addInterval(intervals, Instant.parse(startedAt), Instant.parse(endedAt), now);
        } catch (RuntimeException ignored) {
            // A malformed legacy filename should not make the whole recorder status unavailable.
        }
    }

    private void addInterval(List<RecordingInterval> intervals, Instant startedAt, Instant endedAt, Instant now) {
        if (startedAt == null || endedAt == null || startedAt.isAfter(now)) return;
        Instant boundedEnd = endedAt.isAfter(now) ? now : endedAt;
        if (boundedEnd.isBefore(startedAt)) return;
        intervals.add(new RecordingInterval(startedAt, boundedEnd));
    }

    /**
     * Starts the next recording before stopping the current one, so rotation has no collection gap. The brief
     * overlap is intentional and is removed by the cross-file event fingerprinting in {@code JfrAnalysisService}.
     *
     * <p>Each recording owns a preconfigured {@code .jfr.part} destination. {@link Recording#stop()} asks JFR to
     * finish that destination before the file is validated and atomically published. Do not replace this with a
     * stopped copy plus a chunk stream: the copy still shares disk chunks with the live source, which may evict
     * them under {@code maxSize} pressure while they are read.</p>
     */
    private void rotateCurrentSegment() {
        Recording current = recording;
        Instant currentStartedAt = segmentStartedAt;
        Path currentPartFile = activePartFile;
        Path currentFinalFile = activeFinalFile;
        PreparedRecording next = null;
        boolean nextIsActive = false;
        Finalization finalization = Finalization.empty();
        try {
            next = newPreparedRecording();
            recording = next.recording();
            segmentStartedAt = next.startedAt();
            activePartFile = next.partFile();
            activeFinalFile = next.finalFile();
            nextIsActive = true;

            finalization = stopAndPublish(current, currentPartFile, currentFinalFile, false);
            if (finalization.failure() != null) {
                if (!finalization.safeToClose()) {
                    // The outgoing recording may still own its destination. Roll the handoff back so its .part
                    // remains the protected active path and can be retried on the next rotation/lifecycle pass.
                    recording = current;
                    segmentStartedAt = currentStartedAt;
                    activePartFile = currentPartFile;
                    activeFinalFile = currentFinalFile;
                    nextIsActive = false;
                }
                throw finalization.failure();
            }
        } catch (RecordingStartCancelledException ignored) {
            // ContextClosedEvent won the race before the replacement reached Recording.start().
        } catch (Exception exception) {
            // If creating the replacement fails, the current RUNNING recording stays active. Once the handoff
            // succeeds, any export failure only loses that closed segment; the replacement continues collecting.
            lastError = safeMessage(exception);
            log.warn("轮转本地 JFR 录制失败", exception);
        } finally {
            if (current != recording && finalization.safeToClose()) closeQuietly(current);
            if (next != null && !nextIsActive) {
                closeQuietly(next.recording());
                deleteQuietly(next.partFile());
            }
        }
    }

    private PreparedRecording newPreparedRecording() throws Exception {
        ensureNotClosing();
        // A session may run for 24 hours. JDK 17 recommends the default template for continuous recordings;
        // profile.jfc is intended for shorter profiling sessions and enables higher-frequency data collection.
        Recording newRecording = new Recording(Configuration.getConfiguration("default"));
        boolean started = false;
        Path finalFile = null;
        Path partFile = null;
        try {
            newRecording.setName("EasyNextAdmin Runtime Diagnostics");
            newRecording.setToDisk(true);
            newRecording.setMaxSize(maxFileBytes);
            // Keep the analysis contract explicit while retaining the continuous template's conservative rates.
            newRecording.enable("jdk.ExecutionSample").withPeriod(CONTINUOUS_SAMPLE_PERIOD);
            newRecording.enable("jdk.ObjectAllocationSample")
                    .with("throttle", ALLOCATION_SAMPLE_THROTTLE).withStackTrace();
            newRecording.enable("jdk.JavaMonitorEnter")
                    .withThreshold(CONTINUOUS_EVENT_THRESHOLD).withStackTrace();
            newRecording.enable("jdk.JavaMonitorWait")
                    .withThreshold(CONTINUOUS_EVENT_THRESHOLD).withStackTrace();
            newRecording.enable("jdk.ThreadPark")
                    .withThreshold(CONTINUOUS_EVENT_THRESHOLD).withStackTrace();
            newRecording.enable("jdk.ThreadCPULoad").withPeriod(THREAD_CPU_LOAD_PERIOD);
            newRecording.enable("jdk.GarbageCollection").withThreshold(Duration.ZERO);
            newRecording.enable("jdk.GCPhasePause").withThreshold(Duration.ZERO);
            newRecording.enable("jdk.FileRead").withThreshold(CONTINUOUS_EVENT_THRESHOLD).withStackTrace();
            newRecording.enable("jdk.FileWrite").withThreshold(CONTINUOUS_EVENT_THRESHOLD).withStackTrace();
            newRecording.enable("jdk.SocketRead").withThreshold(CONTINUOUS_EVENT_THRESHOLD).withStackTrace();
            newRecording.enable("jdk.SocketWrite").withThreshold(CONTINUOUS_EVENT_THRESHOLD).withStackTrace();
            // JavaErrorThrow explicitly excludes OutOfMemoryError. Capture other Error stacks here; OOM is bound
            // to a trace through RuntimeOperation.errorType and supported by allocation/GC evidence in the window.
            newRecording.enable("jdk.JavaErrorThrow").withStackTrace();
            // RuntimeOperation is emitted for every observed operation so it can provide the traceId/time/thread
            // correlation key. Capturing a synchronous stack for every fast SQL/transaction would turn this key
            // into a hot-path profiler. Slow/error stacks are already captured by the incident path, while JFR's
            // periodic and thresholded standard events retain profiler stacks.
            newRecording.enable("com.laker.admin.RuntimeOperation").withoutStackTrace();

            Instant requestedStart = Instant.now();
            String baseName = "runtime-" + LocalDateTime.ofInstant(requestedStart, ZoneId.systemDefault())
                    .format(FILE_TIME);
            finalFile = uniqueFinalPath(baseName);
            partFile = finalFile.resolveSibling(finalFile.getFileName() + ".part");
            newRecording.setDestination(partFile);
            newRecording.setDumpOnExit(true);
            ensureNotClosing();
            Duration remaining = remainingSessionDuration();
            newRecording.setDuration(remaining);
            newRecording.start();
            started = true;
            Instant actualStart = Objects.requireNonNullElse(newRecording.getStartTime(), requestedStart);
            return new PreparedRecording(newRecording, actualStart, partFile, finalFile);
        } finally {
            if (!started) {
                closeQuietly(newRecording);
                deleteQuietly(partFile);
            }
        }
    }

    private Duration remainingSessionDuration() {
        Instant deadline = autoStopAt;
        if (!jfrEnabled || deadline == null) throw new RecordingStartCancelledException();
        Duration remaining = Duration.between(Instant.now(), deadline);
        if (remaining.isZero() || remaining.isNegative()) throw new RecordingStartCancelledException();
        return remaining;
    }

    private void ensureNotClosing() {
        if (closingRequested || shutdownRequested) throw new RecordingStartCancelledException();
    }

    private Finalization stopAndPublish(Recording current, Path partFile, Path finalFile,
                                        boolean preserveIncompletePart) {
        if (current == null) return new Finalization(true, true, null);
        Exception stopFailure = null;
        boolean stopCompleted = current.getState() != RecordingState.RUNNING;
        try {
            if (!stopCompleted) stopRecording(current);
            stopCompleted = true;
        } catch (Exception exception) {
            stopFailure = exception;
            stopCompleted = current.getState() != RecordingState.RUNNING;
        }

        if (stopFailure != null && !stopCompleted) {
            // PlatformRecorder.destroy() may already be exporting this destination from a concurrent JVM hook.
            // Do not validate, move, close or delete anything until that owner has finished.
            return new Finalization(false, false, stopFailure);
        }

        try {
            publishCompletedPart(partFile, finalFile, preserveIncompletePart);
            lastError = "";
            return new Finalization(true, true, null);
        } catch (Exception publishFailure) {
            if (stopFailure != null) publishFailure.addSuppressed(stopFailure);
            // stop() already completed, so JFR no longer owns a live segment. Keep the finished .part for recovery,
            // but always close the Recording object; otherwise every failed publish leaks one stopped recording.
            return new Finalization(false, true, publishFailure);
        }
    }

    /** Package-visible seam for exercising platform stop failures without replacing the final JDK Recording type. */
    void stopRecording(Recording current) {
        current.stop();
    }

    private void publishCompletedPart(Path partFile, Path finalFile,
                                      boolean preserveIncompletePart) throws IOException {
        if (partFile == null || finalFile == null || !Files.isRegularFile(partFile)) {
            throw new IOException("JFR 未生成完整临时分段");
        }
        long size = sizeQuietly(partFile);
        if (size <= 0 || !isValidRecording(partFile)) {
            if (!preserveIncompletePart) deleteQuietly(partFile);
            throw new IOException("JFR 分段无效");
        }
        moveAtomically(partFile, finalFile);
    }

    private boolean isValidRecording(Path path) {
        if (path == null) return false;
        try {
            if (!Files.isRegularFile(path) || Files.size(path) <= 0) return false;
            try (RecordingFile recordingFile = new RecordingFile(path)) {
                recordingFile.readEventTypes();
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean cleanupExpiredAndOversized() {
        if (!monitorEnabled || !diagnosisEnabled) return true;
        boolean activeDestination = recording != null
                && recording.getState() != RecordingState.CLOSED
                && activePartFile != null;
        return cleanupExpiredAndOversized(jfrEnabled || activeDestination ? 1 : 0);
    }

    private boolean cleanupExpiredAndOversized(int reservedActiveRecordings) {
        if (!monitorEnabled || !diagnosisEnabled) return true;
        if (!Files.isDirectory(directory)) return true;
        Instant cutoff = LocalDateTime.now().minusDays(retentionDays)
                .atZone(ZoneId.systemDefault()).toInstant();
        List<Path> stored = storedFilesOldestFirst();
        for (Path path : stored) {
            try {
                if (Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) Files.deleteIfExists(path);
            } catch (Exception exception) {
                lastError = safeMessage(exception);
                log.warn("删除过期 JFR 文件失败: {}", path, exception);
            }
        }
        stored = storedFilesOldestFirst();
        long totalBytes = stored.stream().mapToLong(this::sizeQuietly).sum();
        // Reserve before start as well as while a destination exists. Rotation asks for two reservations because
        // the new recording starts before the old one is stopped, preserving continuous collection.
        long reservedBytes = Math.min(maxTotalBytes,
                maxFileBytes * (long) Math.max(0, reservedActiveRecordings));
        long storedBudget = maxTotalBytes - reservedBytes;
        for (Path path : stored) {
            if (totalBytes <= storedBudget) break;
            long size = sizeQuietly(path);
            try {
                if (Files.deleteIfExists(path)) totalBytes = Math.max(0, totalBytes - size);
            } catch (Exception exception) {
                lastError = safeMessage(exception);
                log.warn("JFR 总容量超过 {} bytes，删除最旧分段失败: {}", maxTotalBytes, path, exception);
            }
        }
        boolean withinBudget = totalBytes <= storedBudget;
        if (!withinBudget) {
            lastError = "JFR 存储无法在总容量上限内为新录制预留空间";
            log.error("{}: storedBytes={}, storedBudget={}, directory={}",
                    lastError, totalBytes, storedBudget, directory);
        }
        return withinBudget;
    }

    /** Completed recordings and recoverable non-active parts share the same retention and capacity budget. */
    private List<Path> storedFilesOldestFirst() {
        if (!Files.isDirectory(directory)) return List.of();
        try (var paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> isCompletedFile(path) || isNonActivePartFile(path))
                    .sorted(Comparator.comparing(this::modifiedAtQuietly))
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<Path> nonActivePartFilesOldestFirst() {
        if (!Files.isDirectory(directory)) return List.of();
        try (var paths = Files.list(directory)) {
            return paths.filter(Files::isRegularFile)
                    .filter(this::isNonActivePartFile)
                    .sorted(Comparator.comparing(this::modifiedAtQuietly))
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private boolean isCompletedFile(Path path) {
        return FILE_NAME.matcher(path.getFileName().toString()).matches();
    }

    private boolean isNonActivePartFile(Path path) {
        String fileName = path.getFileName().toString();
        if (!fileName.endsWith(".part")) return false;
        String finalName = fileName.substring(0, fileName.length() - ".part".length());
        return FILE_NAME.matcher(finalName).matches() && !isActivePart(path);
    }

    private boolean isActivePart(Path path) {
        Path active = activePartFile;
        return active != null && active.equals(path.toAbsolutePath().normalize());
    }

    /** Recovers a complete destination left by JVM shutdown; only a fresh process deletes an invalid tail. */
    private void recoverAbandonedParts() {
        if (!Files.isDirectory(directory)) return;
        try (var paths = Files.list(directory)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isNonActivePartFile)
                    .forEach(this::recoverAbandonedPart);
        } catch (Exception exception) {
            log.warn("恢复上次退出遗留的 JFR 临时文件失败: {}", directory, exception);
        }
    }

    private void recoverAbandonedPart(Path partFile) {
        String partName = partFile.getFileName().toString();
        String finalName = partName.substring(0, partName.length() - ".part".length());
        if (!FILE_NAME.matcher(finalName).matches() || !isValidRecording(partFile)) {
            deleteQuietly(partFile);
            return;
        }
        Path finalFile = directory.resolve(finalName);
        if (Files.exists(finalFile)) {
            finalFile = uniqueFinalPath(finalName.substring(0, finalName.length() - ".jfr".length()));
        }
        try {
            moveAtomically(partFile, finalFile);
            log.info("已恢复上次退出时完成的 JFR 分段: {}", finalFile.getFileName());
        } catch (Exception exception) {
            lastError = safeMessage(exception);
            log.warn("发布上次退出遗留的 JFR 分段失败: {}", partFile, exception);
        }
    }

    private Path uniqueFinalPath(String baseName) {
        Path candidate = directory.resolve(baseName + ".jfr");
        int suffix = 1;
        while (Files.exists(candidate) || Files.exists(candidate.resolveSibling(candidate.getFileName() + ".part"))) {
            candidate = directory.resolve(baseName + "-" + suffix++ + ".jfr");
        }
        return candidate;
    }

    private void moveAtomically(Path source, Path target) throws java.io.IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source, target);
        }
    }

    /**
     * Builds request-facing metadata without opening the recording. The filename start is deliberately conservative:
     * max-size eviction can make the first retained event later, but selecting one extra segment is cheaper and safer
     * than scanning every JFR file on each status poll.
     */
    private JfrFile toJfrFile(Path path) {
        try {
            FileTime modified = Files.getLastModifiedTime(path);
            long size = Files.size(path);
            String fileName = path.getFileName().toString();
            String nominalStart = recordingStartedAt(fileName);
            return new JfrFile(fileName, size, nominalStart,
                    modified.toInstant().toString(), modified.toInstant().toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String recordingStartedAt(String fileName) {
        var matcher = FILE_NAME.matcher(fileName);
        if (!matcher.matches()) return null;
        try {
            LocalDateTime started = LocalDateTime.parse(matcher.group(1),
                    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            if (matcher.group(2) != null) started = started.withNano(Integer.parseInt(matcher.group(2)) * 1_000_000);
            return started.atZone(ZoneId.systemDefault()).toInstant().toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private long sizeQuietly(Path path) {
        try { return Files.size(path); }
        catch (Exception ignored) { return 0; }
    }

    private Instant modifiedAtQuietly(Path path) {
        try { return Files.getLastModifiedTime(path).toInstant(); }
        catch (Exception ignored) { return Instant.EPOCH; }
    }

    private long activeRecordingSizeQuietly() {
        Recording current = recording;
        if (current == null || current.getState() == RecordingState.CLOSED) return 0;
        try { return Math.max(sizeQuietly(activePartFile), Math.max(0, current.getSize())); }
        catch (RuntimeException ignored) { return 0; }
    }

    private void closeQuietly(Recording target) {
        if (target == null) return;
        try { target.close(); }
        catch (RuntimeException ignored) { }
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); }
        catch (Exception ignored) { }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record PreparedRecording(Recording recording, Instant startedAt, Path partFile, Path finalFile) { }

    private record RecordingInterval(Instant startedAt, Instant endedAt) { }

    private record CoverageSummary(String oldestRecordingAt, long coverageHours) { }

    private static final class RecordingStartCancelledException extends RuntimeException { }

    private record Finalization(boolean published, boolean safeToClose, Exception failure) {
        private static Finalization empty() {
            return new Finalization(false, false, null);
        }
    }

    public record JfrFile(String fileName, long sizeBytes, String recordingStartedAt,
                          String recordingEndedAt, String modifiedAt) {
        public JfrFile(String fileName, long sizeBytes, String modifiedAt) {
            this(fileName, sizeBytes, null, modifiedAt, modifiedAt);
        }
    }

    public record RecorderStatus(boolean enabled, boolean available, boolean running, String lastError, int retentionDays,
                                 long maxTotalBytes, int completedFiles, long completedBytes,
                                 long activeBytes, long totalBytes, String oldestRecordingAt,
                                 long coverageHours, boolean capacityPressure, String sessionStartedAt,
                                 String autoStopAt, int maxSessionMinutes) { }
}
