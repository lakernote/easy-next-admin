package com.laker.admin.module.monitor.service;

import com.laker.admin.infrastructure.observability.jfr.LocalJfrRecorder;
import com.laker.admin.infrastructure.observability.jfr.RuntimeOperationJfrEvent;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JfrAnalysisServiceTest {

    @Test
    void shouldUseTheProductionConstructorWhenCreatedBySpring() {
        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);

        new ApplicationContextRunner()
                .withBean(LocalJfrRecorder.class, () -> recorder)
                .withUserConfiguration(JfrAnalysisService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JfrAnalysisService.class);
                });
    }

    @Test
    void shouldAnalyzeACompletedRecordingWithBoundedAggregates(@TempDir Path tempDir) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        String fileName = "runtime-20261014-120000.jfr";
        Path recordingFile = tempDir.resolve(fileName);
        createRecording(recordingFile);

        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);
        when(recorder.resolveFile(fileName)).thenReturn(recordingFile);

        JfrAnalysisService.JfrAnalysis result = new JfrAnalysisService(recorder).analyze(fileName, 1_000);

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.fileName()).isEqualTo(fileName);
        assertThat(result.fileSizeBytes()).isPositive();
        assertThat(result.scannedEvents()).isPositive();
        assertThat(result.relevantEvents()).isPositive();
        assertThat(result.locks()).isNotEmpty().hasSizeLessThanOrEqualTo(JfrAnalysisService.MAX_RESULT_LIMIT);
        assertThat(result.locks().get(0).count()).isPositive();
        assertThat(result.locks().get(0).totalDurationNanos()).isPositive();
        assertThat(result.ioActivities()).isNotEmpty()
                .anySatisfy(io -> assertThat(io.key()).startsWith("FILE_"));
        assertThat(result.ioActivities())
                .anySatisfy(io -> assertThat(io.key()).startsWith("SOCKET_").doesNotContain("unknown"));
        assertThat(result.errorThrows())
                .anySatisfy(error -> assertThat(error.key()).isEqualTo(AssertionError.class.getName()));
        assertThat(result.operations()).singleElement().satisfies(operation -> {
            assertThat(operation.traceId()).isEqualTo("trace-jfr-test");
            assertThat(operation.type()).isEqualTo("HTTP");
            assertThat(operation.errorType()).isEqualTo(OutOfMemoryError.class.getName());
            assertThat(operation.durationNanos()).isPositive();
        });
    }

    @Test
    void shouldFilterRuntimeOperationsByTraceId(@TempDir Path tempDir) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        String fileName = "runtime-20261014-120000.jfr";
        Path recordingFile = tempDir.resolve(fileName);
        createRecording(recordingFile);
        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);
        when(recorder.resolveFile(fileName)).thenReturn(recordingFile);

        JfrAnalysisService service = new JfrAnalysisService(recorder);

        assertThat(service.analyze(fileName, 20, "trace-jfr-test", null, null).operations())
                .singleElement().extracting(JfrAnalysisService.RuntimeOperation::operation)
                .isEqualTo("TestController#test");
        assertThat(service.analyze(fileName, 20, "other-trace", null, null).operations()).isEmpty();
    }

    @Test
    void shouldDeduplicateChunkEventsBeforeAggregatingMultipleFiles(@TempDir Path tempDir) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        String firstName = "runtime-20261014-120000.jfr";
        String secondName = "runtime-20261014-120500.jfr";
        Path first = tempDir.resolve(firstName);
        Path second = tempDir.resolve(secondName);
        createRecording(first);
        Files.copy(first, second);

        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);
        when(recorder.resolveFile(firstName)).thenReturn(first);
        when(recorder.resolveFile(secondName)).thenReturn(second);
        JfrAnalysisService service = new JfrAnalysisService(recorder);
        JfrAnalysisService.JfrAnalysis single = service.analyze(firstName, 20,
                "trace-jfr-test", Instant.EPOCH, Instant.now().plusSeconds(60));

        JfrAnalysisService.JfrAnalysis result = service.analyzeFiles(List.of(firstName, secondName), 20,
                "trace-jfr-test", Instant.EPOCH, Instant.now().plusSeconds(60));

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.scannedEvents()).isEqualTo(single.scannedEvents() * 2);
        assertThat(result.relevantEvents()).isEqualTo(single.relevantEvents());
        assertThat(result.deduplicatedEvents()).isEqualTo(single.relevantEvents()).isPositive();
        assertThat(result.operations()).hasSize(1);
        assertThat(result.locks()).usingRecursiveComparison().isEqualTo(single.locks());
        assertThat(result.message()).contains("去重 " + result.deduplicatedEvents());
    }

    @Test
    void shouldAnalyzeRealAdjacentHandoffSegmentsWithoutDuplicateOperations(@TempDir Path tempDir) {
        assumeTrue(FlightRecorder.isAvailable());
        LocalJfrRecorder recorder = new LocalJfrRecorder(tempDir.toString(), 30,
                64L * 1024 * 1024, 16L * 1024 * 1024, 60_000);
        recorder.enable(60);
        try {
            commitOperation("trace-boundary-a", "operation-a");
            recorder.rotate();
            commitOperation("trace-boundary-b", "operation-b");
            recorder.rotate();
            List<String> files = recorder.files().stream().map(LocalJfrRecorder.JfrFile::fileName).toList();

            JfrAnalysisService.JfrAnalysis result = new JfrAnalysisService(recorder).analyzeFiles(files, 20,
                    null, Instant.EPOCH, Instant.now().plusSeconds(60));

            assertThat(files).hasSize(2);
            assertThat(result.status()).isEqualTo("READY");
            assertThat(result.operations()).extracting(JfrAnalysisService.RuntimeOperation::traceId)
                    .contains("trace-boundary-a", "trace-boundary-b")
                    .doesNotHaveDuplicates();
        } finally {
            recorder.stop();
        }
    }

    @Test
    void shouldReturnPartialResultsWhenOneWindowFileIsUnreadable(@TempDir Path tempDir) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        String validName = "runtime-20261014-120000.jfr";
        String brokenName = "runtime-20261014-120500.jfr";
        Path valid = tempDir.resolve(validName);
        Path broken = Files.writeString(tempDir.resolve(brokenName), "not-a-jfr");
        createRecording(valid);
        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);
        when(recorder.resolveFile(validName)).thenReturn(valid);
        when(recorder.resolveFile(brokenName)).thenReturn(broken);

        JfrAnalysisService.JfrAnalysis result = new JfrAnalysisService(recorder).analyzeFiles(
                List.of(validName, brokenName), 20, "trace-jfr-test",
                Instant.EPOCH, Instant.now().plusSeconds(60));

        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.truncated()).isTrue();
        assertThat(result.operations()).hasSize(1);
        assertThat(result.message()).contains("1 个分段无法读取");
    }

    @Test
    void shouldApplyOneFileLimitToTheWholeWindowAnalysis(@TempDir Path tempDir) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        Path valid = tempDir.resolve("source.jfr");
        createRecording(valid);
        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);
        List<String> names = new ArrayList<>();
        for (int index = 0; index <= JfrAnalysisService.MAX_MULTI_FILES; index++) {
            String name = "runtime-20261014-" + String.format("%06d", index) + ".jfr";
            names.add(name);
            when(recorder.resolveFile(name)).thenReturn(valid);
        }

        JfrAnalysisService.JfrAnalysis result = new JfrAnalysisService(recorder).analyzeFiles(names, 20,
                "trace-jfr-test", Instant.EPOCH, Instant.now().plusSeconds(60));

        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.truncated()).isTrue();
        assertThat(result.message()).contains("文件数超过上限");
        verify(recorder, never()).resolveFile(names.get(JfrAnalysisService.MAX_MULTI_FILES));
    }

    @Test
    void shouldRejectMissingOrReversedWindowBeforeOpeningFiles() {
        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);
        JfrAnalysisService service = new JfrAnalysisService(recorder);

        assertThat(service.analyzeFiles(List.of("runtime.jfr"), 20, null, null, Instant.now()).status())
                .isEqualTo("INVALID_RANGE");
        assertThat(service.analyzeFiles(List.of("runtime.jfr"), 20, null,
                Instant.now(), Instant.EPOCH).status()).isEqualTo("INVALID_RANGE");
        verify(recorder, never()).resolveFile("runtime.jfr");
    }

    @Test
    void shouldRejectSingleAndWindowAnalysisWhenSharedConcurrencySlotsAreBusy() throws Exception {
        JfrAnalysisService service = new JfrAnalysisService(mock(LocalJfrRecorder.class));
        Field field = JfrAnalysisService.class.getDeclaredField("analysisSlots");
        field.setAccessible(true);
        Semaphore slots = (Semaphore) field.get(service);
        slots.acquire(2);
        try {
            assertThat(service.analyze("runtime.jfr", 20).status()).isEqualTo("BUSY");
            assertThat(service.analyzeFiles(List.of("runtime.jfr"), 20, null,
                    Instant.EPOCH, Instant.now()).status()).isEqualTo("BUSY");
        } finally {
            slots.release(2);
        }
    }

    @Test
    void shouldSkipBrokenLatestRecordingWhenChoosingDefault(@TempDir Path tempDir) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        String brokenName = "runtime-20261014-120100.jfr";
        String validName = "runtime-20261014-120000.jfr";
        Path broken = Files.writeString(tempDir.resolve(brokenName), "not-a-jfr");
        Path valid = tempDir.resolve(validName);
        createRecording(valid);

        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);
        when(recorder.files()).thenReturn(List.of(
                new LocalJfrRecorder.JfrFile(brokenName, Files.size(broken), "2026-10-14T12:01:00Z"),
                new LocalJfrRecorder.JfrFile(validName, Files.size(valid), "2026-10-14T12:00:00Z")));
        when(recorder.resolveFile(brokenName)).thenReturn(broken);
        when(recorder.resolveFile(validName)).thenReturn(valid);

        JfrAnalysisService.JfrAnalysis result = new JfrAnalysisService(recorder).analyze(null, 10);

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.fileName()).isEqualTo(validName);
    }

    @Test
    void shouldBoundDefaultRecordingValidationFromFirstOpenedCandidate(@TempDir Path tempDir) throws Exception {
        String brokenName = "runtime-20261014-120100.jfr";
        String nextName = "runtime-20261014-120000.jfr";
        Path broken = Files.writeString(tempDir.resolve(brokenName), "not-a-jfr");
        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);
        when(recorder.files()).thenReturn(List.of(
                new LocalJfrRecorder.JfrFile(brokenName, Files.size(broken), "2026-10-14T12:01:00Z"),
                new LocalJfrRecorder.JfrFile(nextName, 1, "2026-10-14T12:00:00Z")));
        when(recorder.resolveFile(brokenName)).thenReturn(broken);

        JfrAnalysisService.JfrAnalysis result = new JfrAnalysisService(recorder, 100, 1)
                .analyze(null, 10);

        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.truncated()).isTrue();
        assertThat(result.message()).contains("文件校验达到安全时间上限");
        verify(recorder, never()).resolveFile(nextName);
    }

    @Test
    void shouldRejectAnyNameNotResolvedByLocalRecorder() {
        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);
        String requested = "../../application.yaml";
        when(recorder.resolveFile(requested)).thenReturn(null);

        JfrAnalysisService.JfrAnalysis result = new JfrAnalysisService(recorder).analyze(requested, 20);

        assertThat(result.status()).isEqualTo("NOT_FOUND");
        assertThat(result.executionHotspots()).isEmpty();
        verify(recorder).resolveFile(requested);
    }

    @Test
    void shouldReturnFriendlyStatusForUnreadableRecording(@TempDir Path tempDir) throws Exception {
        String fileName = "runtime-20261014-120000.jfr";
        Path broken = Files.writeString(tempDir.resolve(fileName), "not-a-jfr");
        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);
        when(recorder.resolveFile(fileName)).thenReturn(broken);

        JfrAnalysisService.JfrAnalysis result = new JfrAnalysisService(recorder).analyze(fileName, 20);

        assertThat(result.status()).isEqualTo("INVALID");
        assertThat(result.message()).contains("无法读取");
        assertThat(result.scannedEvents()).isZero();
    }

    @Test
    void shouldPreserveRequestedWindowAndEventLimitSemantics(@TempDir Path tempDir) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        String fileName = "runtime-20261014-120000.jfr";
        Path recordingFile = tempDir.resolve(fileName);
        RecordedEvent target = createWindowRecording(recordingFile, 1_100, 0);
        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);
        when(recorder.resolveFile(fileName)).thenReturn(recordingFile);
        JfrAnalysisService service = new JfrAnalysisService(recorder, 1, Duration.ofSeconds(5).toNanos());

        JfrAnalysisService.JfrAnalysis single = service.analyze(fileName, 20, "target-trace",
                target.getStartTime(), target.getEndTime());
        JfrAnalysisService.JfrAnalysis multi = service.analyzeFiles(List.of(fileName), 20, "target-trace",
                target.getStartTime(), target.getEndTime());

        assertThat(single.status()).isEqualTo("READY");
        assertThat(single.truncated()).isFalse();
        assertThat(single.operations()).singleElement()
                .extracting(JfrAnalysisService.RuntimeOperation::traceId).isEqualTo("target-trace");
        assertThat(multi.status()).isEqualTo("READY");
        assertThat(multi.truncated()).isFalse();
        assertThat(multi.operations()).singleElement()
                .extracting(JfrAnalysisService.RuntimeOperation::traceId).isEqualTo("target-trace");
    }

    @Test
    void shouldApplyDeadlineBeforeReachingRequestedWindow(@TempDir Path tempDir) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        String fileName = "runtime-20261014-120000.jfr";
        Path recordingFile = tempDir.resolve(fileName);
        RecordedEvent target = createWindowRecording(recordingFile, 1_100, 0);
        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);
        when(recorder.resolveFile(fileName)).thenReturn(recordingFile);

        JfrAnalysisService.JfrAnalysis result = new JfrAnalysisService(recorder, 10_000, 1)
                .analyze(fileName, 20, "target-trace", target.getStartTime(), target.getEndTime());

        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.truncated()).isTrue();
        assertThat(result.operations()).isEmpty();
        assertThat(result.message()).contains("安全上限");
    }

    @Test
    void shouldApplySharedDeadlineWhileScanningEventsAfterWindow(@TempDir Path tempDir) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        String fileName = "runtime-20261014-120000.jfr";
        Path recordingFile = tempDir.resolve(fileName);
        RecordedEvent target = createWindowRecording(recordingFile, 0, 1_100);
        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);
        when(recorder.resolveFile(fileName)).thenReturn(recordingFile);

        JfrAnalysisService.JfrAnalysis result = new JfrAnalysisService(recorder, 10_000, 1)
                .analyze(fileName, 20, "target-trace", target.getStartTime(), target.getEndTime());

        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.truncated()).isTrue();
        assertThat(result.operations()).isEmpty();
        assertThat(result.scannedEvents()).isLessThan(1_100);
    }

    @Test
    void shouldStopMultiFileScanWhenEmptyWindowConsumesTotalDeadline(@TempDir Path tempDir) throws Exception {
        assumeTrue(FlightRecorder.isAvailable());
        String firstName = "runtime-20261014-120000.jfr";
        String secondName = "runtime-20261014-120500.jfr";
        Path first = tempDir.resolve(firstName);
        Path second = tempDir.resolve(secondName);
        createWindowRecording(first, 1_100, 0);
        Files.copy(first, second);
        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);
        when(recorder.resolveFile(firstName)).thenReturn(first);
        when(recorder.resolveFile(secondName)).thenReturn(second);

        Instant future = Instant.now().plus(Duration.ofDays(1));
        JfrAnalysisService.JfrAnalysis result = new JfrAnalysisService(recorder, 10_000, 1)
                .analyzeFiles(List.of(firstName, secondName), 20, null, future, future.plusSeconds(1));

        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.truncated()).isTrue();
        assertThat(result.fileName()).isEqualTo(firstName);
        assertThat(result.message()).contains("联合分析 1 个", "分析时间达到安全上限");
        assertThat(result.scannedEvents()).isZero();
    }

    private static void createRecording(Path destination) throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable("jdk.ThreadPark").withThreshold(Duration.ZERO).withStackTrace();
            recording.enable("jdk.GarbageCollection").withThreshold(Duration.ZERO).withStackTrace();
            recording.enable("jdk.FileRead").withThreshold(Duration.ZERO).withStackTrace();
            recording.enable("jdk.FileWrite").withThreshold(Duration.ZERO).withStackTrace();
            recording.enable("jdk.SocketRead").withThreshold(Duration.ZERO).withStackTrace();
            recording.enable("jdk.SocketWrite").withThreshold(Duration.ZERO).withStackTrace();
            recording.enable("jdk.JavaErrorThrow").withStackTrace();
            recording.enable(RuntimeOperationJfrEvent.class).withStackTrace();
            recording.start();
            Path ioEvidence = destination.resolveSibling("jfr-io-evidence.tmp");
            Files.writeString(ioEvidence, "runtime diagnosis io evidence");
            Files.readString(ioEvidence);
            try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                 Socket client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
                 Socket accepted = server.accept()) {
                client.getOutputStream().write(7);
                client.getOutputStream().flush();
                accepted.getInputStream().read();
                accepted.getOutputStream().write(9);
                accepted.getOutputStream().flush();
                client.getInputStream().read();
            }
            try {
                throw new AssertionError("runtime diagnosis error evidence");
            } catch (AssertionError ignored) {
                // The JFR event, rather than the exception path, is the subject of this test.
            }
            RuntimeOperationJfrEvent operation = new RuntimeOperationJfrEvent();
            operation.type = "HTTP";
            operation.traceId = "trace-jfr-test";
            operation.operation = "TestController#test";
            operation.detail = "GET /api/test";
            operation.outcome = "failure";
            operation.errorType = OutOfMemoryError.class.getName();
            operation.begin();
            LockSupport.parkNanos(JfrAnalysisServiceTest.class, Duration.ofMillis(3).toNanos());
            operation.cpuTimeMs = 1;
            operation.allocatedBytes = 128;
            operation.blockedCount = 0;
            operation.waitedCount = 1;
            operation.end();
            operation.commit();
            System.gc();
            recording.stop();
            recording.dump(destination);
            Files.deleteIfExists(ioEvidence);
        }
    }

    private static RecordedEvent createWindowRecording(Path destination, int beforeCount, int afterCount)
            throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable(RuntimeOperationJfrEvent.class);
            recording.start();
            for (int index = 0; index < beforeCount; index++) {
                commitOperation("before-" + index, "before");
            }
            LockSupport.parkNanos(Duration.ofMillis(2).toNanos());
            commitOperation("target-trace", "target");
            LockSupport.parkNanos(Duration.ofMillis(2).toNanos());
            for (int index = 0; index < afterCount; index++) {
                commitOperation("after-" + index, "after");
            }
            recording.stop();
            recording.dump(destination);
        }
        return RecordingFile.readAllEvents(destination).stream()
                .filter(event -> event.getEventType().getName().equals("com.laker.admin.RuntimeOperation"))
                .filter(event -> "target-trace".equals(event.getString("traceId")))
                .findFirst()
                .orElseThrow();
    }

    private static void commitOperation(String traceId, String operationName) {
        RuntimeOperationJfrEvent operation = new RuntimeOperationJfrEvent();
        operation.type = "HTTP";
        operation.traceId = traceId;
        operation.operation = operationName;
        operation.outcome = "success";
        operation.begin();
        operation.commit();
    }
}
