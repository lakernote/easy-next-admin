package com.laker.admin.infrastructure.observability.jfr;

import jdk.jfr.FlightRecorder;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LocalJfrRecorderTest {

    @Test
    void shouldRejectCompletedFileSymlinks(@TempDir Path directory) throws Exception {
        Path jfrDirectory = directory.resolve("jfr");
        Files.createDirectories(jfrDirectory);
        Path outside = directory.resolve("outside.jfr");
        Files.write(outside, new byte[]{1});
        String fileName = "runtime-20261015-120000-000.jfr";
        Files.createSymbolicLink(jfrDirectory.resolve(fileName), outside);
        LocalJfrRecorder recorder = new LocalJfrRecorder(
                directory.toString(), 1, 64L * 1024 * 1024, 16L * 1024 * 1024, 60_000);

        assertThat(recorder.files()).isEmpty();
        assertThat(recorder.resolveFile(fileName)).isNull();
    }

    @Test
    void shouldHandoffRecordingAcrossRotationsAndPublishValidSegments(@TempDir Path directory) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        LocalJfrRecorder recorder = enabledRecorder(directory, 16L * 1024 * 1024);

        recorder.start();
        Recording liveRecording = activeRecording(recorder);
        assertThat(recorder.status().available()).isTrue();
        assertThat(recorder.status().running()).isTrue();
        assertThat(recorder.files()).isEmpty();
        assertThat(liveRecording.getMaxAge()).isNull();
        assertThat(liveRecording.getDestination().getFileName().toString()).endsWith(".jfr.part");
        assertThat(liveRecording.getDumpOnExit()).isTrue();
        assertThat(liveRecording.getSettings())
                .containsEntry("jdk.ExecutionSample#enabled", "true")
                .containsEntry("jdk.ExecutionSample#period", "20000000 ns")
                .containsEntry("jdk.ObjectAllocationSample#enabled", "true")
                .containsEntry("jdk.ObjectAllocationSample#throttle", "150/s")
                .containsEntry("jdk.JavaMonitorEnter#threshold", "20000000 ns")
                .containsEntry("jdk.JavaMonitorWait#threshold", "20000000 ns")
                .containsEntry("jdk.ThreadPark#threshold", "20000000 ns")
                .containsEntry("jdk.ThreadCPULoad#period", "10000000000 ns")
                .containsEntry("jdk.GarbageCollection#enabled", "true")
                .containsEntry("jdk.GCPhasePause#enabled", "true")
                .containsEntry("jdk.FileRead#enabled", "true")
                .containsEntry("jdk.FileRead#threshold", "20000000 ns")
                .containsEntry("jdk.FileWrite#enabled", "true")
                .containsEntry("jdk.SocketRead#enabled", "true")
                .containsEntry("jdk.SocketWrite#enabled", "true")
                .containsEntry("jdk.JavaErrorThrow#enabled", "true")
                .containsEntry("com.laker.admin.RuntimeOperation#enabled", "true")
                .containsEntry("com.laker.admin.RuntimeOperation#stackTrace", "false");

        Set<String> publishedFiles = new HashSet<>();
        for (int index = 0; index < 3; index++) {
            String operation = "rotation-" + index;
            commitOperationEvent(operation);
            recorder.rotate();

            Recording nextRecording = activeRecording(recorder);
            assertThat(nextRecording).isNotSameAs(liveRecording);
            assertThat(nextRecording.getState()).isEqualTo(jdk.jfr.RecordingState.RUNNING);
            assertThat(liveRecording.getState()).isEqualTo(jdk.jfr.RecordingState.CLOSED);
            assertThat(recorder.files()).hasSize(index + 1);
            LocalJfrRecorder.JfrFile segment = recorder.files().stream()
                    .filter(file -> !publishedFiles.contains(file.fileName()))
                    .findFirst()
                    .orElseThrow();
            publishedFiles.add(segment.fileName());
            assertThat(runtimeOperationTraceIds(recorder.resolveFile(segment.fileName())))
                    .contains(operation);
            liveRecording = nextRecording;
        }

        assertValidSegments(recorder, recorder.files(), 3);

        commitOperationEvent("shutdown");
        assertTimeoutPreemptively(Duration.ofSeconds(10), recorder::stop);

        assertThat(liveRecording.getState()).isEqualTo(jdk.jfr.RecordingState.CLOSED);
        assertThat(recorder.status().running()).isFalse();
        assertValidSegments(recorder, recorder.files(), 4);
        try (var paths = Files.walk(directory)) {
            assertThat(paths.filter(path -> path.getFileName().toString().endsWith(".part"))).isEmpty();
        }
        assertThat(recorder.resolveFile("../../application.yaml")).isNull();

        recorder.rotate();
        assertThat(recorder.status().running()).isFalse();
    }

    @Test
    void shouldKeepReplacementRunningAndRecoverCompletedPartWhenPublishFails(@TempDir Path directory) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        LocalJfrRecorder recorder = enabledRecorder(directory, 16L * 1024 * 1024);
        recorder.start();
        Recording liveRecording = activeRecording(recorder);
        Instant cursor = segmentStartedAt(recorder);
        commitOperationEvent("before-failure");

        Path blockedFinalFile = activeFinalFile(recorder);
        Files.createDirectory(blockedFinalFile);
        recorder.rotate();

        Recording replacement = activeRecording(recorder);
        assertThat(replacement).isNotSameAs(liveRecording);
        assertThat(replacement.getState()).isEqualTo(jdk.jfr.RecordingState.RUNNING);
        assertThat(liveRecording.getState()).isEqualTo(jdk.jfr.RecordingState.CLOSED);
        assertThat(segmentStartedAt(recorder)).isAfter(cursor);
        assertThat(recorder.status().lastError()).isNotBlank();
        assertThat(recorder.files()).isEmpty();
        assertThat(activePartFiles(directory)).hasSize(2);

        commitOperationEvent("after-failure");
        recorder.rotate();

        Recording continued = activeRecording(recorder);
        assertThat(continued).isNotSameAs(replacement);
        assertThat(continued.getState()).isEqualTo(jdk.jfr.RecordingState.RUNNING);
        assertThat(replacement.getState()).isEqualTo(jdk.jfr.RecordingState.CLOSED);
        assertThat(recorder.files()).hasSize(1).anySatisfy(file ->
                assertThat(runtimeOperationTraceIds(recorder.resolveFile(file.fileName())))
                        .contains("after-failure"));
        assertThat(activePartFiles(directory)).hasSize(2);

        Files.delete(blockedFinalFile);
        recorder.stop();
        assertThat(recorder.files()).hasSize(2);

        LocalJfrRecorder restarted = enabledRecorder(directory, 16L * 1024 * 1024);
        try {
            restarted.start();
            assertThat(restarted.files()).hasSize(3);
            assertThat(restarted.files()).anySatisfy(file ->
                    assertThat(runtimeOperationTraceIds(restarted.resolveFile(file.fileName())))
                            .contains("before-failure"));
            assertThat(activePartFiles(directory)).hasSize(1);
        } finally {
            assertTimeoutPreemptively(Duration.ofSeconds(10), restarted::stop);
        }
    }

    @Test
    void shouldCloseEveryFailedSegmentAndKeepPartsInsideTotalCapacity(@TempDir Path directory) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        long maxTotalBytes = 16L * 1024 * 1024;
        LocalJfrRecorder recorder = enabledRecorder(directory, maxTotalBytes, maxTotalBytes);
        try {
            recorder.start();
            for (int index = 0; index < 6; index++) {
                Recording failedSegment = activeRecording(recorder);
                Path blockedFinalFile = activeFinalFile(recorder);
                Files.createDirectory(blockedFinalFile);
                commitOperationEvent("continuous-publish-failure-" + index);

                recorder.rotate();

                assertThat(failedSegment.getState()).isEqualTo(jdk.jfr.RecordingState.CLOSED);
                assertThat(activeRecording(recorder).getState()).isEqualTo(jdk.jfr.RecordingState.RUNNING);
                assertThat(activePartFiles(directory)).isNotEmpty();
                assertThat(recorder.status().totalBytes()).isLessThanOrEqualTo(maxTotalBytes);
            }
        } finally {
            assertTimeoutPreemptively(Duration.ofSeconds(10), recorder::stop);
        }
    }

    @Test
    void shouldExpireNonActivePartWhileKeepingCurrentRecording(@TempDir Path directory) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        LocalJfrRecorder recorder = enabledRecorder(directory, 16L * 1024 * 1024);
        try {
            recorder.start();
            Path expiredPart = directory.resolve("jfr/runtime-20261012-120000-000.jfr.part");
            Files.write(expiredPart, new byte[]{1});
            Files.setLastModifiedTime(expiredPart, FileTime.from(Instant.now().minus(Duration.ofDays(2))));

            recorder.rotate();

            assertThat(expiredPart).doesNotExist();
            assertThat(recorder.status().running()).isTrue();
            assertThat(activePartFiles(directory)).hasSize(1);
        } finally {
            assertTimeoutPreemptively(Duration.ofSeconds(10), recorder::stop);
        }
    }

    @Test
    void shouldReportUnionOfRecordingIntervalsWithoutCountingIdleGaps(@TempDir Path directory) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        LocalJfrRecorder recorder = enabledRecorder(directory, 3, 64L * 1024 * 1024,
                16L * 1024 * 1024);
        try {
            recorder.start();
            Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
            Instant firstStart = now.minus(Duration.ofHours(30));
            writeFormalSegment(directory, firstStart, firstStart.plus(Duration.ofHours(2)));
            Instant secondStart = now.minus(Duration.ofHours(10));
            writeFormalSegment(directory, secondStart, secondStart.plus(Duration.ofHours(3)));
            setSegmentStartedAt(recorder, now.minus(Duration.ofHours(2)));

            LocalJfrRecorder.RecorderStatus status = recorder.status();

            assertThat(status.completedFiles()).isEqualTo(2);
            assertThat(status.coverageHours()).isEqualTo(7);
            assertThat(Instant.parse(status.oldestRecordingAt())).isEqualTo(firstStart);
        } finally {
            assertTimeoutPreemptively(Duration.ofSeconds(10), recorder::stop);
        }
    }

    @Test
    void shouldSerializeScheduledRotationAndShutdown(@TempDir Path directory) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        LocalJfrRecorder recorder = enabledRecorder(directory, 16L * 1024 * 1024);
        recorder.start();
        commitOperationEvent("rotation-stop-race");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> rotation = executor.submit(() -> awaitAndRun(ready, start, recorder::rotate));
            Future<?> shutdown = executor.submit(() -> awaitAndRun(ready, start, recorder::stop));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
                rotation.get();
                shutdown.get();
            });
        } finally {
            executor.shutdownNow();
        }

        assertThat(recorder.status().running()).isFalse();
        assertThat(recorder.status().lastError()).isEmpty();
        assertThat(recorder.files()).isNotEmpty().allSatisfy(file -> {
            Path resolved = recorder.resolveFile(file.fileName());
            assertThat(resolved).isRegularFile();
            try (RecordingFile recordingFile = new RecordingFile(resolved)) {
                assertThat(recordingFile.readEventTypes()).isNotEmpty();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        try (var paths = Files.walk(directory)) {
            assertThat(paths.filter(path -> path.getFileName().toString().endsWith(".part"))).isEmpty();
        }
    }

    @Test
    void shouldBlockNewRotationAfterContextCloseMarkerAndStillFinalizeActiveSegment(@TempDir Path directory)
            throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        LocalJfrRecorder recorder = enabledRecorder(directory, 16L * 1024 * 1024);
        recorder.start();
        Recording active = activeRecording(recorder);
        commitOperationEvent("context-close");

        recorder.markClosing();
        recorder.rotate();

        assertThat(activeRecording(recorder)).isSameAs(active);
        assertThat(active.getState()).isEqualTo(jdk.jfr.RecordingState.RUNNING);
        assertThat(recorder.files()).isEmpty();

        assertTimeoutPreemptively(Duration.ofSeconds(10), recorder::stop);
        assertThat(active.getState()).isEqualTo(jdk.jfr.RecordingState.CLOSED);
        assertThat(recorder.status().running()).isFalse();
        assertThat(recorder.status().lastError()).isEmpty();
        assertValidSegments(recorder, recorder.files(), 1);
    }

    @Test
    void shouldPublishStoppedRecordingUnderMaxSizePressureWithoutChunkStreaming(@TempDir Path directory) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        long configuredFileBytes = 4L * 1024 * 1024;
        LocalJfrRecorder recorder = enabledRecorder(directory, configuredFileBytes);
        try {
            recorder.start();
            String payload = "x".repeat(1024);
            assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
                for (int index = 0; index < 15_000; index++) {
                    commitOperationEvent("pressure-" + index, payload + index);
                }
                recorder.rotate();
            });

            assertThat(recorder.status().running()).isTrue();
            assertThat(activeRecording(recorder).getMaxSize()).isEqualTo(configuredFileBytes);
            assertThat(recorder.status().lastError()).isEmpty();
            assertThat(recorder.files()).hasSize(1).allSatisfy(file -> {
                assertThat(file.sizeBytes()).isPositive();
                Path resolved = recorder.resolveFile(file.fileName());
                try (RecordingFile recordingFile = new RecordingFile(resolved)) {
                    assertThat(recordingFile.readEventTypes()).isNotEmpty();
                } catch (Exception exception) {
                    throw new AssertionError(exception);
                }
                Instant nominalStart = nominalStart(file.fileName());
                // Status/file listing must remain metadata-only. The nominal filename start is conservative when
                // maxSize evicts early chunks; JFR analysis filters actual events inside the requested window.
                assertThat(Instant.parse(file.recordingStartedAt())).isEqualTo(nominalStart);
            });
        } finally {
            assertTimeoutPreemptively(Duration.ofSeconds(10), recorder::stop);
        }
        assertThat(recorder.status().lastError()).isEmpty();
    }

    @Test
    void shouldUseTheRotationPropertyForFixedDelayAndInitialDelay() throws Exception {
        Scheduled scheduled = LocalJfrRecorder.class.getDeclaredMethod("rotate").getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString()).isEqualTo("${easy.monitor.diagnosis.jfr-rotation-ms:1800000}");
        assertThat(scheduled.initialDelayString()).isEqualTo(scheduled.fixedDelayString());
        assertThat(Modifier.isSynchronized(LocalJfrRecorder.class.getDeclaredMethod("rotate").getModifiers())).isTrue();
        assertThat(Modifier.isSynchronized(LocalJfrRecorder.class.getDeclaredMethod("stop").getModifiers())).isTrue();
        assertThat(Modifier.isSynchronized(LocalJfrRecorder.class.getDeclaredMethod("markClosing").getModifiers()))
                .isFalse();
    }

    @Test
    void shouldEnableAndDisableBoundedRuntimeRecording(@TempDir Path directory) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        LocalJfrRecorder recorder = new LocalJfrRecorder(directory.toString(), 1,
                64L * 1024 * 1024, 16L * 1024 * 1024, 12_345);
        try {
            assertThat(recorder.status().enabled()).isFalse();

            LocalJfrRecorder.RecorderStatus running = recorder.enable(60);

            assertThat(running.enabled()).isTrue();
            assertThat(running.running()).isTrue();
            assertThat(running.sessionStartedAt()).isNotBlank();
            assertThat(running.autoStopAt()).isNotBlank();
            assertThat(running.maxSessionMinutes()).isEqualTo(1440);

            LocalJfrRecorder.RecorderStatus stopped = recorder.disable();

            assertThat(stopped.enabled()).isFalse();
            assertThat(stopped.running()).isFalse();
            assertThat(stopped.sessionStartedAt()).isNull();
            assertThat(stopped.autoStopAt()).isNull();
            assertThat(stopped.completedFiles()).isEqualTo(1);
        } finally {
            recorder.stop();
        }
    }

    @Test
    void shouldRecoverAndCleanJfrStorageWhileRecordingIsDisabled(@TempDir Path directory) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        Path jfrDirectory = directory.resolve("jfr");
        Files.createDirectories(jfrDirectory);
        Path abandonedPart = jfrDirectory.resolve("runtime-20261015-120000-000.jfr.part");
        try (Recording abandoned = new Recording(Configuration.getConfiguration("profile"))) {
            abandoned.setToDisk(true);
            abandoned.setDestination(abandonedPart);
            abandoned.enable("com.laker.admin.RuntimeOperation").withStackTrace();
            abandoned.start();
            commitOperationEvent("disabled-recovery");
            abandoned.stop();
        }
        Path expired = jfrDirectory.resolve("runtime-20261012-120000-000.jfr");
        Files.write(expired, new byte[]{1});
        Files.setLastModifiedTime(expired, FileTime.from(Instant.now().minus(Duration.ofDays(2))));

        LocalJfrRecorder recorder = new LocalJfrRecorder(directory.toString(), 1,
                64L * 1024 * 1024, 16L * 1024 * 1024, 12_345);
        try {
            recorder.start();

            assertThat(recorder.status().enabled()).isFalse();
            assertThat(recorder.status().running()).isFalse();
            assertThat(recorder.files()).hasSize(1);
            assertThat(activePartFiles(directory)).isEmpty();
            assertThat(expired).doesNotExist();
            assertThat(runtimeOperationTraceIds(recorder.resolveFile(recorder.files().get(0).fileName())))
                    .contains("disabled-recovery");
        } finally {
            recorder.stop();
        }
    }

    @Test
    void shouldReportFeatureSwitchesAsUnavailableWithActionableReason(@TempDir Path directory) throws Exception {
        LocalJfrRecorder recorder = new LocalJfrRecorder(directory.toString(), 1,
                64L * 1024 * 1024, 16L * 1024 * 1024, 12_345);

        setBooleanField(recorder, "monitorEnabled", false);
        assertThat(recorder.status().available()).isFalse();
        assertThat(recorder.status().lastError()).isEqualTo("监控功能已关闭");
        assertThat(recorder.enable(60).enabled()).isFalse();
        assertThat(recorder.status().sessionStartedAt()).isNull();

        setBooleanField(recorder, "monitorEnabled", true);
        setBooleanField(recorder, "diagnosisEnabled", false);
        assertThat(recorder.status().available()).isFalse();
        assertThat(recorder.status().lastError()).isEqualTo("本地诊断已关闭");
        assertThat(recorder.enable(60).enabled()).isFalse();
        assertThat(recorder.status().sessionStartedAt()).isNull();
    }

    @Test
    void shouldSkipRotationAndCleanupWhenMonitorOrDiagnosisIsDisabled(@TempDir Path directory) throws Exception {
        Path jfrDirectory = directory.resolve("jfr");
        Files.createDirectories(jfrDirectory);
        Path monitorDisabledFile = jfrDirectory.resolve("runtime-20261012-120000-000.jfr");
        Files.write(monitorDisabledFile, new byte[]{1});
        Files.setLastModifiedTime(monitorDisabledFile, FileTime.from(Instant.now().minus(Duration.ofDays(2))));
        LocalJfrRecorder recorder = new LocalJfrRecorder(directory.toString(), 1,
                64L * 1024 * 1024, 16L * 1024 * 1024, 12_345);

        setBooleanField(recorder, "monitorEnabled", false);
        recorder.rotate();
        assertThat(monitorDisabledFile).exists();

        Path diagnosisDisabledFile = jfrDirectory.resolve("runtime-20261012-130000-000.jfr");
        Files.write(diagnosisDisabledFile, new byte[]{1});
        Files.setLastModifiedTime(diagnosisDisabledFile, FileTime.from(Instant.now().minus(Duration.ofDays(2))));
        setBooleanField(recorder, "monitorEnabled", true);
        setBooleanField(recorder, "diagnosisEnabled", false);
        recorder.rotate();

        assertThat(monitorDisabledFile).exists();
        assertThat(diagnosisDisabledFile).exists();
        recorder.stop();
    }

    @Test
    void shouldBoundEveryRotatedRecordingByRemainingSessionAndPublishOnAutoStop(@TempDir Path directory)
            throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        LocalJfrRecorder recorder = new LocalJfrRecorder(directory.toString(), 1,
                64L * 1024 * 1024, 16L * 1024 * 1024, 12_345);
        try {
            LocalJfrRecorder.RecorderStatus started = recorder.enable(Integer.MAX_VALUE);
            Recording first = activeRecording(recorder);
            assertThat(started.maxSessionMinutes()).isEqualTo(1440);
            assertThat(first.getDuration()).isNotNull().isPositive().isLessThanOrEqualTo(Duration.ofHours(24));

            setAutoStopAt(recorder, Instant.now().plusSeconds(90));
            commitOperationEvent("bounded-first");
            recorder.rotate();

            Recording replacement = activeRecording(recorder);
            assertThat(replacement).isNotSameAs(first);
            assertThat(replacement.getDuration()).isNotNull().isPositive()
                    .isLessThanOrEqualTo(Duration.ofSeconds(90));

            commitOperationEvent("bounded-replacement");
            setAutoStopAt(recorder, Instant.now().minusMillis(1));
            recorder.enforceSessionLimit();

            LocalJfrRecorder.RecorderStatus stopped = recorder.status();
            assertThat(stopped.enabled()).isFalse();
            assertThat(stopped.running()).isFalse();
            assertThat(stopped.sessionStartedAt()).isNull();
            assertThat(stopped.autoStopAt()).isNull();
            assertThat(stopped.completedFiles()).isEqualTo(2);
        } finally {
            recorder.stop();
        }
    }

    @Test
    void shouldUseFullCompletedBudgetWhenStoppedAndReserveBeforeStart(@TempDir Path directory) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        long maxTotalBytes = 8L * 1024 * 1024;
        Path stoppedDirectory = directory.resolve("stopped");
        Path stoppedFile = writeSizedCompletedFile(stoppedDirectory, 5L * 1024 * 1024);
        LocalJfrRecorder stopped = new LocalJfrRecorder(stoppedDirectory.toString(), 1,
                maxTotalBytes, maxTotalBytes, 12_345);
        stopped.start();
        assertThat(stoppedFile).exists();
        assertThat(stopped.status().totalBytes()).isEqualTo(Files.size(stoppedFile));
        stopped.stop();

        Path startingDirectory = directory.resolve("starting");
        Path oldFile = writeSizedCompletedFile(startingDirectory, 5L * 1024 * 1024);
        LocalJfrRecorder starting = enabledRecorder(startingDirectory, maxTotalBytes, maxTotalBytes);
        try {
            starting.start();

            assertThat(oldFile).doesNotExist();
            assertThat(activeRecording(starting).getMaxSize()).isEqualTo(maxTotalBytes / 2);
            assertThat(starting.status().totalBytes()).isLessThanOrEqualTo(maxTotalBytes);
        } finally {
            starting.stop();
        }
    }

    @Test
    void shouldRollbackRotationWhenOutgoingStopFailsAndKeepItsPartProtected(@TempDir Path directory)
            throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        FailingStopRecorder recorder = new FailingStopRecorder(directory, 1, false);
        setBooleanField(recorder, "jfrEnabled", true);
        recorder.start();
        Recording outgoing = activeRecording(recorder);
        Path outgoingPart = outgoing.getDestination();
        commitOperationEvent("rotation-stop-failure");

        recorder.rotate();

        assertThat(activeRecording(recorder)).isSameAs(outgoing);
        assertThat(outgoing.getState()).isEqualTo(jdk.jfr.RecordingState.RUNNING);
        assertThat(outgoingPart).exists();
        assertThat(activePartFiles(directory)).containsExactly(outgoingPart);

        recorder.rotate();
        assertThat(activeRecording(recorder)).isNotSameAs(outgoing);
        assertThat(outgoing.getState()).isEqualTo(jdk.jfr.RecordingState.CLOSED);
        assertThat(recorder.files()).hasSize(1);
        recorder.stop();
    }

    @Test
    void shouldContinueSessionWhenRotationStopReportsFailureAfterStopping(@TempDir Path directory)
            throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        FailingStopRecorder recorder = new FailingStopRecorder(directory, 1, true);
        setBooleanField(recorder, "jfrEnabled", true);
        recorder.start();
        Recording outgoing = activeRecording(recorder);
        commitOperationEvent("rotation-stop-after-complete");

        recorder.rotate();

        assertThat(outgoing.getState()).isEqualTo(jdk.jfr.RecordingState.CLOSED);
        assertThat(activeRecording(recorder)).isNotSameAs(outgoing);
        assertThat(activeRecording(recorder).getState()).isEqualTo(jdk.jfr.RecordingState.RUNNING);
        assertThat(recorder.status().enabled()).isTrue();
        assertThat(recorder.files()).hasSize(1);
        recorder.stop();
    }

    @Test
    void shouldRetryRuntimeStopFailureWithoutOrphaningRunningRecording(@TempDir Path directory)
            throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        FailingStopRecorder recorder = new FailingStopRecorder(directory, 2, false);
        setBooleanField(recorder, "jfrEnabled", true);
        recorder.start();
        Recording active = activeRecording(recorder);

        LocalJfrRecorder.RecorderStatus firstAttempt = recorder.disable();

        assertThat(firstAttempt.enabled()).isFalse();
        assertThat(firstAttempt.running()).isTrue();
        assertThat(firstAttempt.sessionStartedAt()).isNotNull();
        assertThat(active.getState()).isEqualTo(jdk.jfr.RecordingState.RUNNING);

        recorder.enforceSessionLimit();
        LocalJfrRecorder.RecorderStatus recovered = recorder.status();
        assertThat(recovered.running()).isFalse();
        assertThat(recovered.sessionStartedAt()).isNull();
        assertThat(recovered.completedFiles()).isEqualTo(1);
        recorder.stop();
    }

    @Test
    void shouldRecoverStoppedShutdownPartAfterStopReportedFailure(@TempDir Path directory) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        FailingStopRecorder recorder = new FailingStopRecorder(directory, 1, true);
        setBooleanField(recorder, "jfrEnabled", true);
        recorder.start();
        Recording active = activeRecording(recorder);
        Path part = active.getDestination();
        commitOperationEvent("shutdown-stop-failure");

        recorder.stop();

        assertThat(active.getState()).isIn(jdk.jfr.RecordingState.STOPPED, jdk.jfr.RecordingState.CLOSED);
        assertThat(activeRecording(recorder)).isNull();
        LocalJfrRecorder.RecorderStatus recovered = recorder.status();
        assertThat(recovered.running()).isFalse();
        assertThat(recovered.completedFiles()).isEqualTo(1);
        assertThat(part).doesNotExist();
        assertThat(active.getState()).isEqualTo(jdk.jfr.RecordingState.CLOSED);
    }

    private static void awaitAndRun(CountDownLatch ready, CountDownLatch start, Runnable action) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("concurrent start timed out");
            action.run();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    private static Path writeSizedCompletedFile(Path directory, long size) throws Exception {
        Path jfrDirectory = directory.resolve("jfr");
        Files.createDirectories(jfrDirectory);
        Path file = jfrDirectory.resolve("runtime-20261015-120000-000.jfr");
        try (var channel = java.nio.channels.FileChannel.open(file,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(size - 1);
            channel.write(java.nio.ByteBuffer.wrap(new byte[]{1}));
        }
        return file;
    }

    private static final class FailingStopRecorder extends LocalJfrRecorder {
        private final AtomicInteger remainingFailures;
        private final boolean stopBeforeFailure;

        private FailingStopRecorder(Path directory, int failures, boolean stopBeforeFailure) {
            super(directory.toString(), 1, 64L * 1024 * 1024, 16L * 1024 * 1024, 12_345);
            this.remainingFailures = new AtomicInteger(failures);
            this.stopBeforeFailure = stopBeforeFailure;
        }

        @Override
        void stopRecording(Recording current) {
            if (remainingFailures.getAndUpdate(value -> Math.max(0, value - 1)) > 0) {
                if (stopBeforeFailure) super.stopRecording(current);
                throw new IllegalStateException("simulated stop failure");
            }
            super.stopRecording(current);
        }
    }

    private static LocalJfrRecorder enabledRecorder(Path directory, long maxFileBytes) throws Exception {
        return enabledRecorder(directory, 1, 64L * 1024 * 1024, maxFileBytes);
    }

    private static LocalJfrRecorder enabledRecorder(Path directory, long maxTotalBytes,
                                                     long maxFileBytes) throws Exception {
        return enabledRecorder(directory, 1, maxTotalBytes, maxFileBytes);
    }

    private static LocalJfrRecorder enabledRecorder(Path directory, int retentionDays, long maxTotalBytes,
                                                     long maxFileBytes) throws Exception {
        LocalJfrRecorder recorder = new LocalJfrRecorder(directory.toString(), retentionDays,
                maxTotalBytes, maxFileBytes, 12_345);
        Field enabled = LocalJfrRecorder.class.getDeclaredField("jfrEnabled");
        enabled.setAccessible(true);
        enabled.setBoolean(recorder, true);
        return recorder;
    }

    private static Recording activeRecording(LocalJfrRecorder recorder) throws Exception {
        Field field = LocalJfrRecorder.class.getDeclaredField("recording");
        field.setAccessible(true);
        return (Recording) field.get(recorder);
    }

    private static Instant segmentStartedAt(LocalJfrRecorder recorder) throws Exception {
        Field field = LocalJfrRecorder.class.getDeclaredField("segmentStartedAt");
        field.setAccessible(true);
        return (Instant) field.get(recorder);
    }

    private static void setSegmentStartedAt(LocalJfrRecorder recorder, Instant value) throws Exception {
        Field field = LocalJfrRecorder.class.getDeclaredField("segmentStartedAt");
        field.setAccessible(true);
        field.set(recorder, value);
    }

    private static Path activeFinalFile(LocalJfrRecorder recorder) throws Exception {
        Field field = LocalJfrRecorder.class.getDeclaredField("activeFinalFile");
        field.setAccessible(true);
        return (Path) field.get(recorder);
    }

    private static void setAutoStopAt(LocalJfrRecorder recorder, Instant value) throws Exception {
        Field field = LocalJfrRecorder.class.getDeclaredField("autoStopAt");
        field.setAccessible(true);
        field.set(recorder, value);
    }

    private static void setBooleanField(LocalJfrRecorder recorder, String name, boolean value) throws Exception {
        Field field = LocalJfrRecorder.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(recorder, value);
    }

    private static List<Path> activePartFiles(Path directory) throws Exception {
        try (var paths = Files.list(directory.resolve("jfr"))) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".jfr.part")).toList();
        }
    }

    private static void writeFormalSegment(Path directory, Instant startedAt, Instant endedAt) throws Exception {
        Path jfrDirectory = directory.resolve("jfr");
        Files.createDirectories(jfrDirectory);
        String fileName = "runtime-" + LocalDateTime.ofInstant(startedAt, ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".jfr";
        Path file = jfrDirectory.resolve(fileName);
        Files.write(file, new byte[]{1});
        Files.setLastModifiedTime(file, FileTime.from(endedAt));
    }

    private static Instant nominalStart(String fileName) {
        int timestampStart = "runtime-".length();
        int timestampEnd = timestampStart + "yyyyMMdd-HHmmss-SSS".length();
        LocalDateTime started = LocalDateTime.parse(fileName.substring(timestampStart, timestampEnd),
                DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        return started.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static void commitOperationEvent(String operation) {
        commitOperationEvent(operation, null);
    }

    private static void commitOperationEvent(String operation, String detail) {
        RuntimeOperationJfrEvent event = new RuntimeOperationJfrEvent();
        event.type = "TEST";
        event.traceId = operation;
        event.operation = operation;
        event.detail = detail;
        event.outcome = "SUCCESS";
        event.begin();
        event.commit();
    }

    private static void assertValidSegments(LocalJfrRecorder recorder,
                                            List<LocalJfrRecorder.JfrFile> files,
                                            int expectedCount) {
        assertThat(files).hasSize(expectedCount).allSatisfy(file -> {
            assertThat(file.sizeBytes()).isPositive();
            assertThat(file.fileName()).endsWith(".jfr");
            Path resolved = recorder.resolveFile(file.fileName());
            assertThat(resolved).isRegularFile();
            try (RecordingFile recordingFile = new RecordingFile(resolved)) {
                assertThat(recordingFile.readEventTypes()).isNotEmpty();
                List<jdk.jfr.consumer.RecordedEvent> events = RecordingFile.readAllEvents(resolved);
                assertThat(events)
                        .anySatisfy(event -> assertThat(event.getEventType().getName())
                                .isEqualTo("com.laker.admin.RuntimeOperation"));
                assertThat(events.stream()
                        .filter(event -> event.getEventType().getName()
                                .equals("com.laker.admin.RuntimeOperation")))
                        .allSatisfy(event -> assertThat(event.getStackTrace()).isNull());
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
    }

    private static List<String> runtimeOperationTraceIds(Path recording) {
        try {
            return RecordingFile.readAllEvents(recording).stream()
                    .filter(event -> event.getEventType().getName().equals("com.laker.admin.RuntimeOperation"))
                    .map(event -> event.getString("traceId"))
                    .toList();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
