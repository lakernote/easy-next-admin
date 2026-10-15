package com.laker.admin.infrastructure.observability.jfr;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.management.ThreadInfo;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActiveOperationRegistryTest {

    @Test
    void shouldEnforceTotalAndSqlAdmissionCapsAndReleaseSlots() {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getMonitor().getDiagnosis().setActiveMaxEntries(3);
        config.getMonitor().getDiagnosis().setActiveMaxSqlEntries(1);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(meters, config);

        ActiveOperationRegistry.Scope firstSql = registry.start("SQL", "sql-1", "Mapper#one", "SELECT");
        ActiveOperationRegistry.Scope droppedSql = registry.start("SQL", "sql-2", "Mapper#two", "SELECT");
        ActiveOperationRegistry.Scope http = registry.start("HTTP", "http-1", "Demo#one", "GET /one");
        ActiveOperationRegistry.Scope job = registry.start("JOB", "job-1", "job-one", "job");
        ActiveOperationRegistry.Scope droppedKafka = registry.start("KAFKA", "kafka-1", "Listener#one", "topic");

        assertThat(registry.countsByType()).containsEntry("SQL", 1L)
                .containsEntry("HTTP", 1L)
                .containsEntry("JOB", 1L)
                .containsEntry("KAFKA", 0L);
        assertThat(meters.get("easy.diagnosis.active.admission.dropped").tag("type", "SQL")
                .counter().count()).isEqualTo(1d);
        assertThat(meters.get("easy.diagnosis.active.admission.dropped").tag("type", "KAFKA")
                .counter().count()).isEqualTo(1d);

        droppedSql.close();
        droppedKafka.close();
        firstSql.close();
        ActiveOperationRegistry.Scope replacementSql = registry.start(
                "SQL", "sql-3", "Mapper#three", "SELECT");
        assertThat(registry.countsByType()).containsEntry("SQL", 1L);

        replacementSql.close();
        http.close();
        job.close();
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void shouldNeverExceedActiveCapUnderConcurrentAdmissions() throws Exception {
        int limit = 8;
        int attempts = 64;
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getMonitor().getDiagnosis().setActiveMaxEntries(limit);
        config.getMonitor().getDiagnosis().setActiveMaxSqlEntries(limit);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(meters, config);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch begin = new CountDownLatch(1);
        CountDownLatch admittedOrDropped = new CountDownLatch(attempts);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<?>> futures = new java.util.ArrayList<>();
        try {
            for (int index = 0; index < attempts; index++) {
                int operation = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(begin.await(5, TimeUnit.SECONDS)).isTrue();
                    ActiveOperationRegistry.Scope scope = registry.start(
                            "HTTP", "concurrent-" + operation, "Demo#run", "GET /concurrent");
                    admittedOrDropped.countDown();
                    try {
                        assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    } finally {
                        scope.close();
                    }
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            begin.countDown();
            assertThat(admittedOrDropped.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(registry.countsByType()).containsEntry("HTTP", (long) limit);
            assertThat(meters.get("easy.diagnosis.active.admission.dropped").tag("type", "HTTP")
                    .counter().count()).isEqualTo((double) (attempts - limit));
        } finally {
            begin.countDown();
            release.countDown();
            for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void shouldLimitDeepSamplingToConfiguredDistinctThreadsPerCycle() throws Exception {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setHttpSlowThresholdMs(1);
        config.getMonitor().getDiagnosis().setActiveMaxSamplesPerCycle(2);
        config.getMonitor().getDiagnosis().setActiveSampleIntervalMs(20);
        com.sun.management.ThreadMXBean threadBean = mock(com.sun.management.ThreadMXBean.class);
        when(threadBean.getThreadInfo(any(long[].class), eq(true), eq(true), eq(48)))
                .thenAnswer(invocation -> {
                    long[] ids = invocation.getArgument(0);
                    ThreadInfo[] result = new ThreadInfo[ids.length];
                    for (int index = 0; index < ids.length; index++) {
                        result[index] = diagnosticInfo("SampledThread" + ids[index]);
                    }
                    return result;
                });
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(meters, config, threadBean);
        ConcurrentLinkedQueue<ActiveOperationRegistry.Scope> scopes = new ConcurrentLinkedQueue<>();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch started = new CountDownLatch(3);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<?>> futures = new java.util.ArrayList<>();
        try {
            for (int index = 0; index < 3; index++) {
                int operation = index;
                futures.add(executor.submit(() -> {
                    ActiveOperationRegistry.Scope scope = registry.start(
                            "HTTP", "sample-" + operation, "Demo#run", "GET /sample");
                    scopes.add(scope);
                    started.countDown();
                    try {
                        assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                    } finally {
                        scope.close();
                    }
                    return null;
                }));
            }
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(5);

            registry.captureSlowOperations();

            ArgumentCaptor<long[]> firstBatch = ArgumentCaptor.forClass(long[].class);
            verify(threadBean, times(1)).getThreadInfo(firstBatch.capture(), eq(true), eq(true), eq(48));
            assertThat(firstBatch.getValue()).hasSize(2);
            assertThat(scopes.stream().filter(scope -> !scope.capturedDiagnostic().stackTrace().isBlank()).count())
                    .isEqualTo(2);
            assertThat(meters.get("easy.diagnosis.active.deep.sample.deferred").counter().count())
                    .isEqualTo(1d);

            Thread.sleep(25);
            registry.captureSlowOperations();
            assertThat(scopes.stream().filter(scope -> !scope.capturedDiagnostic().stackTrace().isBlank()).count())
                    .isEqualTo(3);
            verify(threadBean, times(2)).getThreadInfo(any(long[].class), eq(true), eq(true), eq(48));
        } finally {
            release.countDown();
            for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    @Test
    void shouldLimitSynchronousFinishingDiagnosticsDuringErrorStorm() {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getMonitor().getDiagnosis().setActiveMaxSamplesPerCycle(2);
        config.getMonitor().getDiagnosis().setActiveSampleIntervalMs(60_000);
        com.sun.management.ThreadMXBean threadBean = mock(com.sun.management.ThreadMXBean.class);
        ThreadInfo diagnosticInfo = diagnosticInfo("FinishingThread");
        when(threadBean.getThreadInfo(any(long[].class), eq(true), eq(true), eq(48)))
                .thenReturn(new ThreadInfo[]{diagnosticInfo});
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(meters, config, threadBean);

        registry.start("HTTP", "one", "Demo#one", "GET /one").finishAndClose(true);
        registry.start("HTTP", "two", "Demo#two", "GET /two").finishAndClose(true);
        registry.start("HTTP", "three", "Demo#three", "GET /three").finishAndClose(true);

        verify(threadBean, times(2)).getThreadInfo(any(long[].class), eq(true), eq(true), eq(48));
        assertThat(meters.get("easy.diagnosis.active.deep.sample.deferred").counter().count())
                .isEqualTo(1d);
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void shouldShareDeepSamplingBudgetBetweenScheduledAndFinishingPaths() throws Exception {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setHttpSlowThresholdMs(1);
        config.getMonitor().getDiagnosis().setActiveMaxSamplesPerCycle(1);
        config.getMonitor().getDiagnosis().setActiveSampleIntervalMs(60_000);
        com.sun.management.ThreadMXBean threadBean = mock(com.sun.management.ThreadMXBean.class);
        ThreadInfo scheduledDiagnostic = diagnosticInfo("ScheduledThread");
        when(threadBean.getThreadInfo(any(long[].class), eq(true), eq(true), eq(48)))
                .thenReturn(new ThreadInfo[]{scheduledDiagnostic});
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(meters, config, threadBean);

        ActiveOperationRegistry.Scope scheduled = registry.start(
                "HTTP", "scheduled", "Demo#scheduled", "GET /scheduled");
        Thread.sleep(2);
        registry.captureSlowOperations();
        scheduled.close();
        registry.start("HTTP", "finishing", "Demo#finishing", "GET /finishing")
                .finishAndClose(true);

        verify(threadBean, times(1)).getThreadInfo(any(long[].class), eq(true), eq(true), eq(48));
        assertThat(meters.get("easy.diagnosis.active.deep.sample.deferred").counter().count())
                .isEqualTo(1d);
    }

    @Test
    void shouldExposeLiveResourceAndAsyncWaitingState() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setHttpSlowThresholdMs(1);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(meters, config);

        ActiveOperationRegistry.Scope scope = registry.start("HTTP", "trace-1", "Demo#run", "GET /demo");
        burnCpu();
        java.util.concurrent.locks.LockSupport.parkNanos(java.time.Duration.ofMillis(2).toNanos());
        registry.captureSlowOperations();

        assertThat(registry.snapshot()).singleElement().satisfies(operation -> {
            assertThat(operation.type()).isEqualTo("HTTP");
            assertThat(operation.traceId()).isEqualTo("trace-1");
            assertThat(operation.threadId()).isPositive();
            assertThat(operation.durationMs()).isNotNegative();
            assertThat(operation.cpuTimeMs()).isGreaterThanOrEqualTo(-1);
            assertThat(operation.allocatedBytes()).isGreaterThanOrEqualTo(-1);
            assertThat(operation.stackTrace()).isNotBlank();
            assertThat(operation.sampledAt()).isNotNull();
        });
        assertThat(meters.get("easy.operations.active").tag("type", "HTTP").gauge().value()).isEqualTo(1d);

        scope.markAsyncWaiting();
        assertThat(registry.snapshot()).singleElement().satisfies(operation -> {
            assertThat(operation.threadId()).isEqualTo(-1);
            assertThat(operation.threadState()).isEqualTo("ASYNC_WAITING");
        });

        scope.close();
        assertThat(registry.snapshot()).isEmpty();
        assertThat(meters.get("easy.operations.active").tag("type", "HTTP").gauge().value()).isZero();
    }

    @Test
    void shouldAccumulateResourcesAcrossAsyncThreadSegments() throws Exception {
        ActiveOperationRegistry registry = new ActiveOperationRegistry(
                new SimpleMeterRegistry(), new EasyNextAdminConfig());
        ActiveOperationRegistry.Scope scope = registry.start("HTTP", "trace-async", "Demo#async", "GET /async");

        burnCpu();
        scope.markAsyncWaiting();

        Thread worker = new Thread(() -> {
            scope.rebindCurrentThread();
            burnCpu();
            scope.markAsyncWaiting();
        }, "async-observation-test");
        worker.start();
        worker.join();

        ActiveOperationRegistry.ResourceUsage resources = scope.finishAndClose();
        assertThat(resources.cpuTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(resources.allocatedBytes()).isGreaterThanOrEqualTo(0);
        assertThat(resources.blockedCount()).isEqualTo(-1);
        assertThat(resources.waitedCount()).isEqualTo(-1);
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void shouldNotEnableOrSampleJvmResourcesWhenAnyDiagnosisSwitchIsOff() {
        assertResourceSamplingDisabled(config -> config.getFeatures().setMonitor(false));
        assertResourceSamplingDisabled(config -> config.getMonitor().getDiagnosis().setEnabled(false));
        assertResourceSamplingDisabled(config -> config.getMonitor().getDiagnosis().setDetailSamplingEnabled(false));
    }

    @Test
    void shouldCaptureHeldMonitorsAndSynchronizers() {
        ActiveOperationRegistry registry = new ActiveOperationRegistry(
                new SimpleMeterRegistry(), new EasyNextAdminConfig());
        ActiveOperationRegistry.Scope scope = registry.start("HTTP", "locks", "Demo#locks", "GET /locks");
        Object monitor = new Object();
        ReentrantLock synchronizer = new ReentrantLock();
        ActiveOperationRegistry.ThreadDiagnostic diagnostic;

        synchronized (monitor) {
            synchronizer.lock();
            try {
                diagnostic = scope.captureDiagnosticIfAbsent();
            } finally {
                synchronizer.unlock();
            }
        }

        assertThat(diagnostic.lockInfo())
                .contains("heldMonitors=[", "java.lang.Object@", "heldSynchronizers=[", "ReentrantLock")
                .doesNotContain("ActiveOperationRegistry$Entry");
        assertThat(diagnostic.stackTrace()).isNotBlank();
        scope.close();
    }

    @Test
    void shouldReadCountersWithoutStackWalkingAndExcludeCounterSnapshotCost() {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getMonitor().getDiagnosis().setContentionTimeEnabled(true);
        com.sun.management.ThreadMXBean threadBean = mock(com.sun.management.ThreadMXBean.class);
        long threadId = Thread.currentThread().getId();
        when(threadBean.isThreadCpuTimeSupported()).thenReturn(true);
        when(threadBean.isThreadCpuTimeEnabled()).thenReturn(true);
        when(threadBean.getThreadCpuTime(threadId)).thenReturn(10L, 20L);
        when(threadBean.isThreadAllocatedMemorySupported()).thenReturn(true);
        when(threadBean.isThreadAllocatedMemoryEnabled()).thenReturn(true);
        when(threadBean.getThreadAllocatedBytes(threadId)).thenReturn(100L, 200L);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(
                new SimpleMeterRegistry(), config, threadBean);

        ActiveOperationRegistry.Scope scope = registry.start("HTTP", "counter-cost", "Demo#run", "GET /demo");
        clearInvocations(threadBean);
        ActiveOperationRegistry.ResourceUsage resources = scope.finishAndClose();

        org.mockito.InOrder readOrder = inOrder(threadBean);
        readOrder.verify(threadBean).getThreadCpuTime(threadId);
        readOrder.verify(threadBean).getThreadAllocatedBytes(threadId);
        readOrder.verify(threadBean).getThreadInfo(threadId, 0);
        verify(threadBean, never()).getThreadInfo(threadId, 48);
        assertThat(resources.allocatedBytes()).isEqualTo(100L);
    }

    @Test
    void shouldRebaseCountersAtTheBusinessBoundary() {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        com.sun.management.ThreadMXBean threadBean = mock(com.sun.management.ThreadMXBean.class);
        long threadId = Thread.currentThread().getId();
        when(threadBean.isThreadCpuTimeSupported()).thenReturn(true);
        when(threadBean.isThreadCpuTimeEnabled()).thenReturn(true);
        when(threadBean.getThreadCpuTime(threadId)).thenReturn(10L, 100L, 30_000_100L);
        when(threadBean.isThreadAllocatedMemorySupported()).thenReturn(true);
        when(threadBean.isThreadAllocatedMemoryEnabled()).thenReturn(true);
        when(threadBean.getThreadAllocatedBytes(threadId)).thenReturn(100L, 1_000L, 1_600L);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(
                new SimpleMeterRegistry(), config, threadBean);

        ActiveOperationRegistry.Scope scope = registry.start(
                "HTTP", "business-boundary", "Demo#run", "GET /demo");
        scope.rebaseCurrentThread();
        ActiveOperationRegistry.ResourceUsage resources = scope.finishAndClose();

        assertThat(resources.cpuTimeMs()).isEqualTo(30L);
        assertThat(resources.allocatedBytes()).isEqualTo(600L);
    }

    @Test
    void shouldSkipSqlResourceCountersByDefaultButKeepSlowAndFinishingDiagnostics() throws Exception {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setSqlSlowThresholdMs(1);
        com.sun.management.ThreadMXBean threadBean = mock(com.sun.management.ThreadMXBean.class);
        ThreadInfo diagnosticInfo = mock(ThreadInfo.class);
        when(diagnosticInfo.getThreadState()).thenReturn(Thread.State.RUNNABLE);
        when(diagnosticInfo.getStackTrace()).thenReturn(new StackTraceElement[]{
                new StackTraceElement("DemoMapper", "select", "DemoMapper.java", 42)
        });
        when(diagnosticInfo.getLockedMonitors()).thenReturn(new java.lang.management.MonitorInfo[0]);
        when(diagnosticInfo.getLockedSynchronizers()).thenReturn(new java.lang.management.LockInfo[0]);
        when(threadBean.getThreadInfo(any(long[].class), eq(true), eq(true), eq(48)))
                .thenReturn(new ThreadInfo[]{diagnosticInfo});
        ActiveOperationRegistry registry = new ActiveOperationRegistry(
                new SimpleMeterRegistry(), config, threadBean);
        long threadId = Thread.currentThread().getId();

        ActiveOperationRegistry.Scope periodicallySampled = registry.start(
                "SQL", "slow-sql", "DemoMapper.select", "SELECT");
        Thread.sleep(5);
        registry.captureSlowOperations();
        assertThat(periodicallySampled.capturedDiagnostic().stackTrace())
                .contains("DemoMapper.select(DemoMapper.java:42)");
        assertThat(periodicallySampled.finishAndClose())
                .isEqualTo(new ActiveOperationRegistry.ResourceUsage(-1, -1, -1, -1, -1, -1));

        ActiveOperationRegistry.Scope finishingSampled = registry.start(
                "SQL", "error-sql", "DemoMapper.select", "SELECT");
        ActiveOperationRegistry.FinishedOperation finished = finishingSampled.finishAndClose(true);
        assertThat(finished.resources())
                .isEqualTo(new ActiveOperationRegistry.ResourceUsage(-1, -1, -1, -1, -1, -1));
        assertThat(finished.diagnostic().stackTrace()).contains("DemoMapper.select(DemoMapper.java:42)");

        verify(threadBean, never()).getThreadCpuTime(anyLong());
        verify(threadBean, never()).getThreadAllocatedBytes(anyLong());
        verify(threadBean, never()).getThreadInfo(threadId, 0);
        verify(threadBean, times(2)).getThreadInfo(any(long[].class), eq(true), eq(true), eq(48));
    }

    @Test
    void shouldReuseOneDeepDiagnosticForSlowNestedOperationsOnTheSameThread() throws Exception {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setHttpSlowThresholdMs(1);
        config.getTrace().setTransactionSlowThresholdMs(1);
        com.sun.management.ThreadMXBean threadBean = mock(com.sun.management.ThreadMXBean.class);
        ThreadInfo diagnosticInfo = mock(ThreadInfo.class);
        when(diagnosticInfo.getThreadState()).thenReturn(Thread.State.RUNNABLE);
        when(diagnosticInfo.getStackTrace()).thenReturn(new StackTraceElement[]{
                new StackTraceElement("DemoService", "save", "DemoService.java", 42)
        });
        when(diagnosticInfo.getLockedMonitors()).thenReturn(new java.lang.management.MonitorInfo[0]);
        when(diagnosticInfo.getLockedSynchronizers()).thenReturn(new java.lang.management.LockInfo[0]);
        when(threadBean.getThreadInfo(any(long[].class), eq(true), eq(true), eq(48)))
                .thenReturn(new ThreadInfo[]{diagnosticInfo});
        ActiveOperationRegistry registry = new ActiveOperationRegistry(
                new SimpleMeterRegistry(), config, threadBean);

        ActiveOperationRegistry.Scope http = registry.start("HTTP", "nested", "Demo#save", "POST /demo");
        ActiveOperationRegistry.Scope transaction = registry.start(
                "TRANSACTION", "nested", "DemoService#save", "transaction");
        Thread.sleep(5);
        registry.captureSlowOperations();

        assertThat(http.capturedDiagnostic().stackTrace()).contains("DemoService.save(DemoService.java:42)");
        assertThat(transaction.capturedDiagnostic().stackTrace()).contains("DemoService.save(DemoService.java:42)");
        verify(threadBean, times(1)).getThreadInfo(any(long[].class), eq(true), eq(true), eq(48));
        transaction.close();
        http.close();
    }

    @Test
    void shouldBatchDeepDiagnosticsForDistinctSlowThreads() throws Exception {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setHttpSlowThresholdMs(1);
        config.getTrace().setScheduleSlowThresholdMs(1);
        com.sun.management.ThreadMXBean threadBean = mock(com.sun.management.ThreadMXBean.class);
        long callerThreadId = Thread.currentThread().getId();
        AtomicLong workerThreadId = new AtomicLong();
        when(threadBean.getThreadInfo(any(long[].class), eq(true), eq(true), eq(48)))
                .thenAnswer(invocation -> {
                    long[] ids = invocation.getArgument(0);
                    ThreadInfo[] result = new ThreadInfo[ids.length];
                    for (int index = 0; index < ids.length; index++) {
                        String className = ids[index] == callerThreadId ? "CallerOperation" : "WorkerOperation";
                        result[index] = diagnosticInfo(className);
                    }
                    return result;
                });
        ActiveOperationRegistry registry = new ActiveOperationRegistry(
                new SimpleMeterRegistry(), config, threadBean);
        ActiveOperationRegistry.Scope caller = registry.start(
                "HTTP", "batch-caller", "Caller#run", "GET /batch");
        AtomicReference<ActiveOperationRegistry.Scope> workerScope = new AtomicReference<>();
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> worker = executor.submit(() -> {
            workerThreadId.set(Thread.currentThread().getId());
            ActiveOperationRegistry.Scope scope = registry.start(
                    "JOB", "batch-worker", "Worker#run", "batch-job");
            workerScope.set(scope);
            workerStarted.countDown();
            try {
                assertThat(releaseWorker.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                scope.close();
            }
        });
        try {
            assertThat(workerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(5);

            registry.captureSlowOperations();

            assertThat(caller.capturedDiagnostic().stackTrace()).contains("CallerOperation.run");
            assertThat(workerScope.get().capturedDiagnostic().stackTrace()).contains("WorkerOperation.run");
            ArgumentCaptor<long[]> ids = ArgumentCaptor.forClass(long[].class);
            verify(threadBean, times(1)).getThreadInfo(ids.capture(), eq(true), eq(true), eq(48));
            assertThat(ids.getValue()).containsExactlyInAnyOrder(callerThreadId, workerThreadId.get());
        } finally {
            caller.close();
            releaseWorker.countDown();
            worker.get(5, TimeUnit.SECONDS);
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRetryBatchDiagnosticAfterMxBeanFailure() throws Exception {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setHttpSlowThresholdMs(1);
        com.sun.management.ThreadMXBean threadBean = mock(com.sun.management.ThreadMXBean.class);
        when(threadBean.getThreadInfo(any(long[].class), eq(true), eq(true), eq(48)))
                .thenThrow(new IllegalStateException("temporarily unavailable"))
                .thenReturn(new ThreadInfo[]{diagnosticInfo("RecoveredOperation")});
        ActiveOperationRegistry registry = new ActiveOperationRegistry(
                new SimpleMeterRegistry(), config, threadBean);
        ActiveOperationRegistry.Scope scope = registry.start(
                "HTTP", "batch-retry", "Recovered#run", "GET /retry");
        Thread.sleep(5);

        registry.captureSlowOperations();
        assertThat(scope.capturedDiagnostic().stackTrace()).isBlank();

        registry.captureSlowOperations();
        assertThat(scope.capturedDiagnostic().stackTrace()).contains("RecoveredOperation.run");
        verify(threadBean, times(2)).getThreadInfo(any(long[].class), eq(true), eq(true), eq(48));
        scope.close();
    }

    @Test
    void shouldMeasureSqlResourcesWhenExplicitlyEnabled() {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getMonitor().getDiagnosis().setSqlResourceSamplingEnabled(true);
        config.getMonitor().getDiagnosis().setContentionTimeEnabled(true);
        com.sun.management.ThreadMXBean threadBean = mock(com.sun.management.ThreadMXBean.class);
        long threadId = Thread.currentThread().getId();
        when(threadBean.isThreadCpuTimeSupported()).thenReturn(true);
        when(threadBean.isThreadCpuTimeEnabled()).thenReturn(true);
        when(threadBean.getThreadCpuTime(threadId)).thenReturn(10L, 20L);
        when(threadBean.isThreadAllocatedMemorySupported()).thenReturn(true);
        when(threadBean.isThreadAllocatedMemoryEnabled()).thenReturn(true);
        when(threadBean.getThreadAllocatedBytes(threadId)).thenReturn(100L, 200L);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(
                new SimpleMeterRegistry(), config, threadBean);

        ActiveOperationRegistry.Scope scope = registry.start(
                "SQL", "sampled-sql", "DemoMapper.select", "SELECT");
        ActiveOperationRegistry.ResourceUsage resources = scope.finishAndClose();

        assertThat(resources.cpuTimeMs()).isZero();
        assertThat(resources.allocatedBytes()).isEqualTo(100L);
        verify(threadBean, times(2)).getThreadCpuTime(threadId);
        verify(threadBean, times(2)).getThreadAllocatedBytes(threadId);
        verify(threadBean, times(2)).getThreadInfo(threadId, 0);
    }

    @Test
    void shouldFreezeResourceCountersBeforeCapturingFinishingDiagnostic() {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getMonitor().getDiagnosis().setContentionTimeEnabled(true);
        com.sun.management.ThreadMXBean threadBean = mock(com.sun.management.ThreadMXBean.class);
        long threadId = Thread.currentThread().getId();
        when(threadBean.isThreadCpuTimeSupported()).thenReturn(true);
        when(threadBean.isThreadCpuTimeEnabled()).thenReturn(true);
        when(threadBean.getThreadCpuTime(threadId)).thenReturn(10L, 20L);
        when(threadBean.isThreadAllocatedMemorySupported()).thenReturn(true);
        when(threadBean.isThreadAllocatedMemoryEnabled()).thenReturn(true);
        when(threadBean.getThreadAllocatedBytes(threadId)).thenReturn(100L, 200L);
        ThreadInfo diagnosticInfo = mock(ThreadInfo.class);
        when(diagnosticInfo.getThreadState()).thenReturn(Thread.State.RUNNABLE);
        when(diagnosticInfo.getStackTrace()).thenReturn(new StackTraceElement[]{
                new StackTraceElement("Demo", "run", "Demo.java", 42)
        });
        when(diagnosticInfo.getLockedMonitors()).thenReturn(new java.lang.management.MonitorInfo[0]);
        when(diagnosticInfo.getLockedSynchronizers()).thenReturn(new java.lang.management.LockInfo[0]);
        when(threadBean.getThreadInfo(any(long[].class), eq(true), eq(true), eq(48)))
                .thenReturn(new ThreadInfo[]{diagnosticInfo});
        ActiveOperationRegistry registry = new ActiveOperationRegistry(
                new SimpleMeterRegistry(), config, threadBean);

        ActiveOperationRegistry.Scope scope = registry.start(
                "HTTP", "finish-order", "Demo#run", "GET /demo");
        clearInvocations(threadBean);
        ActiveOperationRegistry.FinishedOperation finished = scope.finishAndClose(true);

        org.mockito.InOrder readOrder = inOrder(threadBean);
        readOrder.verify(threadBean).getThreadCpuTime(threadId);
        readOrder.verify(threadBean).getThreadAllocatedBytes(threadId);
        readOrder.verify(threadBean).getThreadInfo(threadId, 0);
        readOrder.verify(threadBean).getThreadInfo(any(long[].class), eq(true), eq(true), eq(48));
        assertThat(finished.resources().cpuTimeMs()).isZero();
        assertThat(finished.resources().allocatedBytes()).isEqualTo(100L);
        assertThat(finished.diagnostic().stackTrace()).contains("Demo.run(Demo.java:42)");
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void shouldLimitEntriesBeforeCapturingLiveStacks() throws Exception {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        com.sun.management.ThreadMXBean threadBean = mock(com.sun.management.ThreadMXBean.class);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(
                new SimpleMeterRegistry(), config, threadBean);
        ActiveOperationRegistry.Scope first = registry.start("HTTP", "first", "First#run", "GET /first");
        Thread.sleep(1);
        ActiveOperationRegistry.Scope second = registry.start("HTTP", "second", "Second#run", "GET /second");
        Thread.sleep(1);
        ActiveOperationRegistry.Scope third = registry.start("HTTP", "third", "Third#run", "GET /third");
        clearInvocations(threadBean);

        List<ActiveOperationRegistry.ActiveOperation> result = registry.snapshot(1);

        assertThat(result).singleElement().satisfies(operation ->
                assertThat(operation.traceId()).isEqualTo("first"));
        ArgumentCaptor<long[]> ids = ArgumentCaptor.forClass(long[].class);
        verify(threadBean, times(1)).getThreadInfo(ids.capture(), eq(false), eq(false), eq(48));
        assertThat(ids.getValue()).containsExactly(Thread.currentThread().getId());
        verify(threadBean, never()).getThreadInfo(anyLong(), eq(48));
        verify(threadBean, never()).getThreadInfo(anyLong(), eq(0));
        first.close();
        second.close();
        third.close();
    }

    @Test
    void shouldReadOneLiveSnapshotForNestedOperationsOnTheSameThread() {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        com.sun.management.ThreadMXBean threadBean = mock(com.sun.management.ThreadMXBean.class);
        long threadId = Thread.currentThread().getId();
        when(threadBean.isThreadCpuTimeSupported()).thenReturn(true);
        when(threadBean.isThreadCpuTimeEnabled()).thenReturn(true);
        when(threadBean.isThreadAllocatedMemorySupported()).thenReturn(true);
        when(threadBean.isThreadAllocatedMemoryEnabled()).thenReturn(true);
        when(threadBean.getThreadCpuTime(threadId)).thenReturn(10L, 20L, 30L);
        when(threadBean.getThreadAllocatedBytes(threadId)).thenReturn(100L, 200L, 300L);
        ThreadInfo nestedDiagnostic = diagnosticInfo("NestedOperation");
        when(threadBean.getThreadInfo(any(long[].class), eq(false), eq(false), eq(48)))
                .thenReturn(new ThreadInfo[]{nestedDiagnostic});
        ActiveOperationRegistry registry = new ActiveOperationRegistry(
                new SimpleMeterRegistry(), config, threadBean);
        ActiveOperationRegistry.Scope http = registry.start(
                "HTTP", "nested-live", "Demo#run", "GET /demo");
        ActiveOperationRegistry.Scope transaction = registry.start(
                "TRANSACTION", "nested-live", "Demo#run", "transaction");
        clearInvocations(threadBean);

        List<ActiveOperationRegistry.ActiveOperation> operations = registry.snapshot(10);

        assertThat(operations).hasSize(2)
                .allSatisfy(operation -> assertThat(operation.stackTrace()).contains("NestedOperation.run"));
        verify(threadBean, times(1)).getThreadCpuTime(threadId);
        verify(threadBean, times(1)).getThreadAllocatedBytes(threadId);
        verify(threadBean, times(1)).getThreadInfo(any(long[].class), eq(false), eq(false), eq(48));
        verify(threadBean, never()).getThreadInfo(threadId, 0);
        transaction.close();
        http.close();
    }

    @Test
    void shouldDiscardSlowSampleWhenThreadBindingChangesAndRetryOnNewBinding() throws Exception {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setHttpSlowThresholdMs(1);
        com.sun.management.ThreadMXBean threadBean = mock(com.sun.management.ThreadMXBean.class);
        ThreadInfo sampledInfo = mock(ThreadInfo.class);
        when(sampledInfo.getThreadState()).thenReturn(Thread.State.RUNNABLE);
        when(sampledInfo.getStackTrace()).thenReturn(new StackTraceElement[]{
                new StackTraceElement("Demo", "run", "Demo.java", 42)
        });
        when(sampledInfo.getLockedMonitors()).thenReturn(new java.lang.management.MonitorInfo[0]);
        when(sampledInfo.getLockedSynchronizers()).thenReturn(new java.lang.management.LockInfo[0]);
        CountDownLatch inspectionStarted = new CountDownLatch(1);
        CountDownLatch allowInspection = new CountDownLatch(1);
        AtomicInteger inspections = new AtomicInteger();
        when(threadBean.getThreadInfo(any(long[].class), eq(true), eq(true), eq(48)))
                .thenAnswer(invocation -> {
                    if (inspections.getAndIncrement() == 0) {
                        inspectionStarted.countDown();
                        assertThat(allowInspection.await(5, TimeUnit.SECONDS)).isTrue();
                    }
                    return new ThreadInfo[]{sampledInfo};
                });
        ActiveOperationRegistry registry = new ActiveOperationRegistry(
                new SimpleMeterRegistry(), config, threadBean);
        ActiveOperationRegistry.Scope scope = registry.start("HTTP", "binding", "Demo#run", "GET /demo");
        Thread.sleep(2);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> firstSample = executor.submit(registry::captureSlowOperations);
            assertThat(inspectionStarted.await(5, TimeUnit.SECONDS)).isTrue();
            scope.markAsyncWaiting();
            allowInspection.countDown();
            firstSample.get(5, TimeUnit.SECONDS);
            assertThat(scope.capturedDiagnostic().stackTrace()).isBlank();

            scope.rebindCurrentThread();
            registry.captureSlowOperations();
            assertThat(scope.capturedDiagnostic().stackTrace()).contains("Demo.run(Demo.java:42)");
            verify(threadBean, atLeast(2)).getThreadInfo(any(long[].class), eq(true), eq(true), eq(48));
            verify(threadBean, never()).getThreadInfo(any(long[].class), eq(true), eq(true));
        } finally {
            allowInspection.countDown();
            scope.close();
            executor.shutdownNow();
        }
    }

    @Test
    void shouldKeepJvmWideContentionTimingOptIn() {
        EasyNextAdminConfig defaultConfig = new EasyNextAdminConfig();
        com.sun.management.ThreadMXBean defaultBean = mock(com.sun.management.ThreadMXBean.class);
        when(defaultBean.isThreadContentionMonitoringSupported()).thenReturn(true);
        new ActiveOperationRegistry(new SimpleMeterRegistry(), defaultConfig, defaultBean);
        verify(defaultBean, never()).setThreadContentionMonitoringEnabled(true);

        EasyNextAdminConfig optInConfig = new EasyNextAdminConfig();
        optInConfig.getMonitor().getDiagnosis().setContentionTimeEnabled(true);
        com.sun.management.ThreadMXBean optInBean = mock(com.sun.management.ThreadMXBean.class);
        when(optInBean.isThreadContentionMonitoringSupported()).thenReturn(true);
        when(optInBean.isThreadContentionMonitoringEnabled()).thenReturn(false);
        new ActiveOperationRegistry(new SimpleMeterRegistry(), optInConfig, optInBean);
        verify(optInBean).setThreadContentionMonitoringEnabled(true);
    }

    private static void assertResourceSamplingDisabled(java.util.function.Consumer<EasyNextAdminConfig> disable) {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        disable.accept(config);
        com.sun.management.ThreadMXBean threadBean = mock(com.sun.management.ThreadMXBean.class);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config, threadBean);

        ActiveOperationRegistry.Scope scope = registry.start("HTTP", "disabled", "Demo#run", "GET /demo");
        registry.captureSlowOperations();

        assertThat(registry.snapshot()).isEmpty();
        assertThat(scope.finishAndClose()).isEqualTo(new ActiveOperationRegistry.ResourceUsage(-1, -1, -1, -1, -1, -1));
        verify(threadBean, never()).setThreadContentionMonitoringEnabled(true);
        verify(threadBean, never()).setThreadCpuTimeEnabled(true);
        verify(threadBean, never()).setThreadAllocatedMemoryEnabled(true);
    }

    private static void burnCpu() {
        long value = 0;
        for (int index = 0; index < 200_000; index++) value += index;
        if (value == Long.MIN_VALUE) throw new AssertionError();
    }

    private static ThreadInfo diagnosticInfo(String className) {
        ThreadInfo info = mock(ThreadInfo.class);
        when(info.getThreadState()).thenReturn(Thread.State.RUNNABLE);
        when(info.getStackTrace()).thenReturn(new StackTraceElement[]{
                new StackTraceElement(className, "run", className + ".java", 42)
        });
        when(info.getLockedMonitors()).thenReturn(new java.lang.management.MonitorInfo[0]);
        when(info.getLockedSynchronizers()).thenReturn(new java.lang.management.LockInfo[0]);
        return info;
    }
}
