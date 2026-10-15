package com.laker.admin.module.monitor.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JvmDiagnosticsServiceTest {

    @Test
    void initializationShouldNotEnableJvmWideOptionalMeasurements() {
        com.sun.management.ThreadMXBean threadMxBean = mock(com.sun.management.ThreadMXBean.class);
        when(threadMxBean.isThreadContentionMonitoringSupported()).thenReturn(true);
        when(threadMxBean.isThreadCpuTimeSupported()).thenReturn(true);
        when(threadMxBean.isThreadAllocatedMemorySupported()).thenReturn(true);

        new JvmDiagnosticsService(new SimpleMeterRegistry(), threadMxBean);

        verify(threadMxBean, never()).setThreadContentionMonitoringEnabled(true);
        verify(threadMxBean, never()).setThreadCpuTimeEnabled(true);
        verify(threadMxBean, never()).setThreadAllocatedMemoryEnabled(true);
    }

    @Test
    void threadAndDeadlockSnapshotsShouldBoundStackDepthInsideTheJvm() {
        ThreadMXBean threadMxBean = mock(ThreadMXBean.class);
        ThreadInfo shallowInfo = mock(ThreadInfo.class);
        when(shallowInfo.getThreadId()).thenReturn(7L);
        when(shallowInfo.getThreadState()).thenReturn(Thread.State.RUNNABLE);
        ThreadInfo detailedInfo = mock(ThreadInfo.class);
        when(detailedInfo.getThreadId()).thenReturn(7L);
        when(detailedInfo.getThreadName()).thenReturn("worker");
        when(detailedInfo.getThreadState()).thenReturn(Thread.State.RUNNABLE);
        when(detailedInfo.getStackTrace()).thenReturn(new StackTraceElement[0]);
        when(detailedInfo.getLockedMonitors()).thenReturn(new java.lang.management.MonitorInfo[0]);
        when(detailedInfo.getLockedSynchronizers()).thenReturn(new java.lang.management.LockInfo[0]);
        when(detailedInfo.getLockOwnerId()).thenReturn(-1L);
        when(threadMxBean.getAllThreadIds()).thenReturn(new long[]{7L});
        when(threadMxBean.getThreadInfo(any(long[].class), eq(0)))
                .thenReturn(new ThreadInfo[]{shallowInfo});
        when(threadMxBean.getThreadInfo(any(long[].class), eq(true), eq(true), eq(7)))
                .thenReturn(new ThreadInfo[]{detailedInfo});
        when(threadMxBean.isSynchronizerUsageSupported()).thenReturn(true);
        when(threadMxBean.findDeadlockedThreads()).thenReturn(new long[]{42L});
        when(threadMxBean.getThreadInfo(any(long[].class), eq(true), eq(true), eq(5)))
                .thenReturn(new ThreadInfo[]{null});
        JvmDiagnosticsService service = new JvmDiagnosticsService(new SimpleMeterRegistry(), threadMxBean);

        service.threads(10, 7);
        service.deadlocks(10, 5);

        verify(threadMxBean).getThreadInfo(any(long[].class), eq(0));
        verify(threadMxBean).getThreadInfo(any(long[].class), eq(true), eq(true), eq(7));
        verify(threadMxBean, never()).dumpAllThreads(true, true, 7);
        verify(threadMxBean, never()).dumpAllThreads(true, true);
        verify(threadMxBean).getThreadInfo(any(long[].class), eq(true), eq(true), eq(5));
        verify(threadMxBean, never()).getThreadInfo(any(long[].class), eq(true), eq(true));
    }

    @Test
    void threadsShouldReturnBoundedLiveJvmDetails() {
        JvmDiagnosticsService service = new JvmDiagnosticsService(new SimpleMeterRegistry());

        JvmDiagnosticsService.ThreadDump dump = service.threads(10, 3);

        assertThat(dump.totalThreads()).isPositive();
        assertThat(dump.returnedThreads()).isBetween(1, 10);
        assertThat(dump.stackDepth()).isEqualTo(3);
        assertThat(dump.threads()).allSatisfy(thread -> {
            assertThat(thread.name()).isNotBlank();
            assertThat(thread.state()).isNotBlank();
            assertThat(thread.stackTrace()).hasSizeLessThanOrEqualTo(3);
            assertThat(thread.lockedMonitors()).isNotNull();
            assertThat(thread.lockedSynchronizers()).isNotNull();
        });
    }

    @Test
    void threadsShouldEnforceServerSideLimits() {
        JvmDiagnosticsService service = new JvmDiagnosticsService(new SimpleMeterRegistry());

        JvmDiagnosticsService.ThreadDump dump = service.threads(Integer.MAX_VALUE, Integer.MAX_VALUE);

        assertThat(dump.returnedThreads()).isLessThanOrEqualTo(JvmDiagnosticsService.MAX_THREADS);
        assertThat(dump.stackDepth()).isEqualTo(JvmDiagnosticsService.MAX_STACK_DEPTH);
        assertThat(dump.threads()).allSatisfy(thread ->
                assertThat(thread.stackTrace()).hasSizeLessThanOrEqualTo(JvmDiagnosticsService.MAX_STACK_DEPTH));
    }

    @Test
    void deadlocksShouldAlwaysReturnAnExplicitDetectionResult() {
        JvmDiagnosticsService service = new JvmDiagnosticsService(new SimpleMeterRegistry());

        JvmDiagnosticsService.DeadlockReport report = service.deadlocks(100, 8);

        assertThat(report.sampledAt()).isNotBlank();
        assertThat(report.count()).isNotNegative();
        assertThat(report.detected()).isEqualTo(report.count() > 0);
        assertThat(report.threads()).hasSizeLessThanOrEqualTo(JvmDiagnosticsService.MAX_DEADLOCK_THREADS);
    }

    @Test
    void poolsShouldGroupMetricsAndCalculateUtilization() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registerGauge(registry, "hikaricp.connections.active", "pool", "main", 8);
        registerGauge(registry, "hikaricp.connections.idle", "pool", "main", 2);
        registerGauge(registry, "hikaricp.connections.pending", "pool", "main", 1);
        registerGauge(registry, "hikaricp.connections.max", "pool", "main", 10);
        registerGauge(registry, "executor.active", "name", "application", 4);
        registerGauge(registry, "executor.pool.size", "name", "application", 6);
        registerGauge(registry, "executor.pool.max", "name", "application", 20);
        registerGauge(registry, "executor.queued", "name", "application", 3);
        registerGauge(registry, "unrelated.metric", "name", "ignored", 99);
        JvmDiagnosticsService service = new JvmDiagnosticsService(registry);

        JvmDiagnosticsService.PoolDiagnostics diagnostics = service.pools();

        assertThat(diagnostics.pools()).hasSize(2);
        JvmDiagnosticsService.PoolView hikari = diagnostics.pools().stream()
                .filter(pool -> pool.type().equals("HIKARI")).findFirst().orElseThrow();
        assertThat(hikari.name()).isEqualTo("main");
        assertThat(hikari.active()).isEqualTo(8d);
        assertThat(hikari.idle()).isEqualTo(2d);
        assertThat(hikari.pending()).isEqualTo(1d);
        assertThat(hikari.max()).isEqualTo(10d);
        assertThat(hikari.utilizationPercent()).isEqualTo(80d);
        assertThat(hikari.metrics()).extracting(JvmDiagnosticsService.PoolMetric::name)
                .containsExactlyInAnyOrder("hikaricp.connections.active", "hikaricp.connections.idle",
                        "hikaricp.connections.pending", "hikaricp.connections.max");

        JvmDiagnosticsService.PoolView executor = diagnostics.pools().stream()
                .filter(pool -> pool.type().equals("EXECUTOR")).findFirst().orElseThrow();
        assertThat(executor.active()).isEqualTo(4d);
        assertThat(executor.current()).isEqualTo(6d);
        assertThat(executor.pending()).isEqualTo(3d);
        assertThat(executor.utilizationPercent()).isEqualTo(20d);
    }

    @Test
    void heapHistogramShouldParseAndBoundDiagnosticCommandOutput() {
        String output = """
                 num     #instances         #bytes  class name (module)
                -------------------------------------------------------
                   1:           120          9600  [B (java.base@17.0.12)
                   2:            40          3200  com.example.Order
                Total           160         12800
                """;

        JvmDiagnosticsService.HeapHistogram histogram =
                JvmDiagnosticsService.parseHeapHistogram(output, 1);

        assertThat(histogram.status()).isEqualTo("READY");
        assertThat(histogram.totalClasses()).isEqualTo(2);
        assertThat(histogram.returnedClasses()).isEqualTo(1);
        assertThat(histogram.truncated()).isTrue();
        assertThat(histogram.totalInstances()).isEqualTo(160);
        assertThat(histogram.totalBytes()).isEqualTo(12_800);
        assertThat(histogram.classes()).containsExactly(
                new JvmDiagnosticsService.HeapClassHistogramRow(
                        1, 120, 9_600, "[B", "java.base@17.0.12"));
    }

    @Test
    void heapHistogramShouldReturnExplicitUnavailableResultWhenDiagnosticCommandIsMissing() {
        JvmDiagnosticsService service = serviceWithHistogramCommand(new JvmDiagnosticsService.HeapHistogramCommand() {
            @Override
            public boolean available() {
                return false;
            }

            @Override
            public String execute() {
                throw new AssertionError("unsupported command must not execute");
            }
        });

        JvmDiagnosticsService.HeapHistogram histogram = service.heapHistogram(25);

        assertThat(histogram.status()).isEqualTo("UNAVAILABLE");
        assertThat(histogram.supported()).isFalse();
        assertThat(histogram.classes()).isEmpty();
        assertThat(histogram.message()).contains("DiagnosticCommand.gcClassHistogram");
    }

    @Test
    void heapHistogramShouldReturnFailureDetailsWithoutLeakingAnException() {
        JvmDiagnosticsService service = serviceWithHistogramCommand(new JvmDiagnosticsService.HeapHistogramCommand() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String execute() {
                throw new SecurityException("management permission denied");
            }
        });

        JvmDiagnosticsService.HeapHistogram histogram = service.heapHistogram(Integer.MAX_VALUE);

        assertThat(histogram.status()).isEqualTo("FAILED");
        assertThat(histogram.supported()).isTrue();
        assertThat(histogram.limit()).isEqualTo(JvmDiagnosticsService.MAX_HISTOGRAM_CLASSES);
        assertThat(histogram.message()).contains("management permission denied");
    }

    @Test
    void heapHistogramCapabilityProbeMustNotExecuteTheHighImpactCommand() {
        AtomicInteger executions = new AtomicInteger();
        JvmDiagnosticsService service = serviceWithHistogramCommand(new JvmDiagnosticsService.HeapHistogramCommand() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String execute() {
                executions.incrementAndGet();
                return "";
            }
        });

        JvmDiagnosticsService.HeapHistogramCapability capability = service.heapHistogramCapability();

        assertThat(capability.supported()).isTrue();
        assertThat(capability.status()).isEqualTo("AVAILABLE");
        assertThat(capability.impact()).contains("安全点停顿");
        assertThat(executions).hasValue(0);
    }

    @Test
    void heapHistogramShouldRejectConcurrentHighImpactCaptures() throws Exception {
        CountDownLatch commandStarted = new CountDownLatch(1);
        CountDownLatch releaseCommand = new CountDownLatch(1);
        JvmDiagnosticsService service = serviceWithHistogramCommand(new JvmDiagnosticsService.HeapHistogramCommand() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String execute() throws InterruptedException {
                commandStarted.countDown();
                releaseCommand.await(5, TimeUnit.SECONDS);
                return "   1:  1  16  java.lang.Object (java.base@17)\nTotal  1  16\n";
            }
        });
        CompletableFuture<JvmDiagnosticsService.HeapHistogram> first =
                CompletableFuture.supplyAsync(() -> service.heapHistogram(10));
        assertThat(commandStarted.await(5, TimeUnit.SECONDS)).isTrue();

        JvmDiagnosticsService.HeapHistogram concurrent = service.heapHistogram(10);
        releaseCommand.countDown();

        assertThat(concurrent.status()).isEqualTo("BUSY");
        assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo("READY");
    }

    private static JvmDiagnosticsService serviceWithHistogramCommand(
            JvmDiagnosticsService.HeapHistogramCommand command) {
        return new JvmDiagnosticsService(new SimpleMeterRegistry(), ManagementFactory.getThreadMXBean(), command);
    }

    private static void registerGauge(SimpleMeterRegistry registry, String name, String tagName, String tagValue,
                                      int value) {
        Gauge.builder(name, new AtomicInteger(value), AtomicInteger::doubleValue)
                .tag(tagName, tagValue)
                .register(registry);
    }
}
