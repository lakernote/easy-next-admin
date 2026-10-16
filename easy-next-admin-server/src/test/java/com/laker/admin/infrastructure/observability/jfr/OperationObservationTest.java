package com.laker.admin.infrastructure.observability.jfr;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.observability.trace.TraceIdContext;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import com.laker.admin.infrastructure.security.masking.EasySensitiveDataMasker;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationObservationTest {

    @AfterEach
    void clearTraceId() {
        TraceIdContext.clear();
    }

    @Test
    void shouldAggregateButNotPersistNormalRootByDefault() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(meters, config);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<LocalJfrRecorder> recorderProvider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        OperationObservation observation = new OperationObservation(store, config, registry, meters, recorderProvider);

        observation.start("HTTP", "Demo#normal").finish("success");

        verify(store, never()).recordOperation(org.mockito.ArgumentMatchers.any());
        assertThat(meters.get("easy.operations.duration").tag("type", "HTTP")
                .tag("outcome", "success").timer().count()).isEqualTo(1);
    }

    @Test
    void shouldReuseBoundedOperationMetersAndMapUnknownTypesToOther() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        AtomicInteger meterMappings = new AtomicInteger();
        meters.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if (id.getName().startsWith("easy.operations.")
                        && !"easy.operations.active".equals(id.getName())) {
                    meterMappings.incrementAndGet();
                }
                return id;
            }
        });
        ActiveOperationRegistry registry = new ActiveOperationRegistry(meters, config);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<LocalJfrRecorder> recorderProvider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        OperationObservation observation = new OperationObservation(store, config, registry, meters, recorderProvider);

        observation.start("CUSTOM", "Demo#first").finish("success");
        int registrationsAfterFirstCall = meterMappings.get();
        observation.start("ANOTHER_CUSTOM", "Demo#second").finish("success");

        assertThat(registrationsAfterFirstCall).isEqualTo(7);
        assertThat(meterMappings).hasValue(registrationsAfterFirstCall);
        assertThat(meters.get("easy.operations.duration").tag("type", "OTHER")
                .tag("outcome", "success").timer().count()).isEqualTo(2);
        assertThat(meters.find("easy.operations.duration").tag("type", "CUSTOM").timer()).isNull();
    }

    @Test
    void shouldKeepFastSuccessfulRemoteOperationAsAggregateMetricOnly() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setRemoteSlowThresholdMs(60_000);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(meters, config);
        OperationObservation observation = new OperationObservation(store, config, registry);

        observation.start("REMOTE", "PaymentClient#query", "outbound", false).finish("success");

        verify(store, never()).recordOperation(org.mockito.ArgumentMatchers.any());
        assertThat(org.mockito.Mockito.mockingDetails(store).getInvocations())
                .noneMatch(invocation -> "recordIncident".equals(invocation.getMethod().getName()));
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void fastNonRootScopesShouldKeepLifecycleAndIncidentStateAllocationFree() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setHttpSlowThresholdMs(60_000);
        config.getTrace().setTransactionSlowThresholdMs(60_000);
        config.getTrace().setSqlSlowThresholdMs(60_000);
        config.getTrace().setRemoteSlowThresholdMs(60_000);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);

        OperationObservation.Scope root = observation.start("HTTP", "Demo#normal", "GET /normal", false);
        OperationObservation.Completion emptyCompletion = root.completion();
        assertThat(root.lazyStateAllocationCount()).isZero();

        for (String type : java.util.List.of("TRANSACTION", "SQL", "REMOTE")) {
            OperationObservation.Scope child = observation.start(type, "Demo#" + type, "summary", false);
            assertThat(child.lazyStateAllocationCount()).isZero();
            child.finish("success");
            assertThat(child.lazyStateAllocationCount()).isZero();
            assertThat(child.completion()).isSameAs(emptyCompletion);
        }

        // The aggregate root creates one child-summary map on first use; no incident set is needed.
        assertThat(root.lazyStateAllocationCount()).isEqualTo(1);
        root.finish("success");
        assertThat(root.lazyStateAllocationCount()).isEqualTo(1);
        assertThat(root.completion()).isSameAs(emptyCompletion);
        assertThat(java.util.Arrays.stream(OperationObservation.Scope.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("lifecycleMonitor", "initialAsyncHandoff");
        assertThat(java.util.Arrays.stream(OperationObservation.Scope.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getType))
                .doesNotContain(java.util.concurrent.atomic.AtomicBoolean.class, CountDownLatch.class);
        assertThat(java.util.Arrays.stream(OperationObservation.Scope.class.getDeclaredFields())
                .filter(field -> "finished".equals(field.getName())))
                .singleElement()
                .satisfies(field -> assertThat(field.getType()).isEqualTo(boolean.class));
    }

    @Test
    void shouldResolveDeferredDetailOnlyWhenOperationIsRetained() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setSqlSlowThresholdMs(60_000);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        AtomicInteger detailReads = new AtomicInteger();

        observation.start("SQL", "DemoMapper#fast", "SELECT",
                () -> {
                    detailReads.incrementAndGet();
                    return "SELECT · select 1";
                }, false).finish("success");
        assertThat(detailReads).hasValue(0);

        observation.start("SQL", "DemoMapper#error", "SELECT",
                () -> {
                    detailReads.incrementAndGet();
                    return "SELECT · select 1";
                }, false).finish("failure", new IllegalStateException("database unavailable"));
        assertThat(detailReads).hasValue(1);
    }

    @Test
    void shouldPersistSlowSuccessfulRemoteOperationAndCanonicalIncident() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setRemoteSlowThresholdMs(1);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        TraceIdContext.putTraceId("slow-remote-jsonl");

        OperationObservation.Scope remote =
                observation.start("REMOTE", "PaymentClient#query", "outbound", false);
        java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        remote.finish("success");

        ArgumentCaptor<RecentTraceStore.OperationSample> operation =
                ArgumentCaptor.forClass(RecentTraceStore.OperationSample.class);
        verify(store).recordOperation(operation.capture());
        assertThat(operation.getValue()).satisfies(sample -> {
            assertThat(sample.type()).isEqualTo("REMOTE");
            assertThat(sample.traceId()).isEqualTo("slow-remote-jsonl");
            assertThat(sample.outcome()).isEqualTo("success");
            assertThat(sample.durationMs()).isGreaterThanOrEqualTo(1);
        });
        assertThat(org.mockito.Mockito.mockingDetails(store).getInvocations())
                .filteredOn(invocation -> "recordIncident".equals(invocation.getMethod().getName()))
                .singleElement()
                .satisfies(invocation -> {
                    assertThat(invocation.getArgument(0, String.class)).isEqualTo("REMOTE");
                    assertThat(invocation.getArgument(1, String.class)).isEqualTo("slow-remote-jsonl");
                });
    }

    @Test
    void standaloneSlowSqlShouldOwnCorrelatableTraceAndClearItAfterCompletion() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setSqlSlowThresholdMs(1);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);

        OperationObservation.Scope sql = observation.start("SQL", "DemoMapper#query", "SELECT demo", false);
        String generatedTraceId = TraceIdContext.currentTraceId();
        java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        sql.finish("success");

        assertThat(generatedTraceId).isNotBlank();
        assertThat(TraceIdContext.currentTraceId()).isNull();
        ArgumentCaptor<RecentTraceStore.OperationSample> operation =
                ArgumentCaptor.forClass(RecentTraceStore.OperationSample.class);
        verify(store).recordOperation(operation.capture());
        assertThat(operation.getValue().traceId()).isEqualTo(generatedTraceId);
        assertThat(org.mockito.Mockito.mockingDetails(store).getInvocations())
                .filteredOn(invocation -> "recordIncident".equals(invocation.getMethod().getName()))
                .singleElement()
                .satisfies(invocation -> assertThat(invocation.getArgument(1, String.class))
                        .isEqualTo(generatedTraceId));
    }

    @Test
    void shouldPersistAccumulatedResourcesForAsyncOperation() throws Exception {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        OperationObservation.Scope scope = observation.start("HTTP", "Demo#async", "GET /async", true);

        burnCpu();
        scope.markAsyncWaiting();
        Thread worker = new Thread(() -> {
            scope.rebindCurrentThread();
            burnCpu();
            scope.markAsyncWaiting();
        }, "async-operation-test");
        worker.start();
        worker.join();
        scope.finishAsync("success", null);

        ArgumentCaptor<RecentTraceStore.OperationSample> sample =
                ArgumentCaptor.forClass(RecentTraceStore.OperationSample.class);
        verify(store).recordOperation(sample.capture());
        assertThat(sample.getValue().cpuTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(sample.getValue().allocatedBytes()).isGreaterThanOrEqualTo(0);
        assertThat(sample.getValue().blockedCount()).isEqualTo(-1);
        assertThat(sample.getValue().waitedCount()).isEqualTo(-1);
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void shouldNeverWaitForInitialAsyncHandoffBeforeRebindingWorker() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        OperationObservation.Scope scope = observation.start("HTTP", "Demo#callable", "GET /callable", true);
        assertThat(scope.rebindAfterInitialAsyncHandoff()).isFalse();
        scope.markAsyncWaiting();
        assertThat(scope.rebindAfterInitialAsyncHandoff()).isTrue();
        scope.markAsyncWaiting();
        scope.finishAsync("success", null);
        assertThat(scope.rebindAfterInitialAsyncHandoff()).isFalse();
    }

    @Test
    void shouldAttachSqlAndTransactionSummaryToRootOperation() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        TraceIdContext.putTraceId("trace-summary");

        OperationObservation.Scope root = observation.start("HTTP", "Demo#query", "GET /query", true);
        OperationObservation.Scope firstSql = observation.start("SQL", "DemoMapper#first", "SELECT", false);
        firstSql.finish("success");
        OperationObservation.Scope secondSql = observation.start("SQL", "DemoMapper#second", "SELECT", false);
        secondSql.finish("failure", new IllegalStateException("db unavailable"));
        OperationObservation.Scope transaction = observation.start("TRANSACTION", "DemoService#query", "", false);
        transaction.finish("success");
        root.finish("success");

        ArgumentCaptor<RecentTraceStore.OperationSample> samples =
                ArgumentCaptor.forClass(RecentTraceStore.OperationSample.class);
        verify(store, org.mockito.Mockito.atLeastOnce()).recordOperation(samples.capture());
        RecentTraceStore.OperationSample rootSample = samples.getAllValues().stream()
                .filter(sample -> "HTTP".equals(sample.type())).findFirst().orElseThrow();
        assertThat(rootSample.childSummaries().get("SQL").count()).isEqualTo(2);
        assertThat(rootSample.childSummaries().get("SQL").errorCount()).isEqualTo(1);
        assertThat(rootSample.childSummaries().get("TRANSACTION").count()).isEqualTo(1);
        assertThat(rootSample.childSummaries().get("TRANSACTION").totalDurationMs()).isNotNegative();
    }

    @Test
    void shouldPromoteDiagnosticChildAsCanonicalIncidentCause() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setHttpSlowThresholdMs(60_000);
        config.getTrace().setTransactionSlowThresholdMs(1);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        TraceIdContext.putTraceId("trace-primary-cause");

        OperationObservation.Scope root = observation.start("HTTP", "OrderController#create", "POST /orders", true);
        OperationObservation.Scope transaction = observation.start(
                "TRANSACTION", "OrderService#create", "write", false);
        java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        transaction.finish("success");
        root.finish("success");

        OperationObservation.Completion completion = root.completion();
        assertThat(completion.slow()).isTrue();
        assertThat(completion.primaryCause()).isEqualTo(completion.slowCause());
        assertThat(completion.primaryCause()).satisfies(cause -> {
            assertThat(cause.type()).isEqualTo("TRANSACTION");
            assertThat(cause.name()).isEqualTo("OrderService#create");
            assertThat(cause.detail()).isEqualTo("write");
            assertThat(cause.durationMs()).isGreaterThanOrEqualTo(1);
            assertThat(cause.thresholdMs()).isEqualTo(1);
            assertThat(cause.reason()).isEqualTo("LONG_TRANSACTION");
        });
    }

    @Test
    void shouldRetainSeparateSlowAndErrorCausesForMixedIncident() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setHttpSlowThresholdMs(60_000);
        config.getTrace().setTransactionSlowThresholdMs(1);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        TraceIdContext.putTraceId("trace-mixed-causes");

        OperationObservation.Scope root = observation.start("HTTP", "OrderController#create", "POST /orders", true);
        OperationObservation.Scope transaction = observation.start("TRANSACTION", "OrderService#create", "", false);
        java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
        transaction.finish("success");
        OperationObservation.Scope sql = observation.start("SQL", "OrderMapper#insert", "INSERT orders", false);
        sql.finish("failure", new IllegalStateException("database unavailable"));
        root.finish("success");

        OperationObservation.Completion completion = root.completion();
        assertThat(completion.slowCause().type()).isEqualTo("TRANSACTION");
        assertThat(completion.errorCause().type()).isEqualTo("SQL");
        assertThat(completion.primaryCause()).isEqualTo(completion.errorCause());
        assertThat(completion.primaryCause().reason()).isEqualTo("SQL_ERROR");
    }

    @Test
    void shouldCarryRootResourcesAndChildSummariesToApplicationManagedWorker() throws Exception {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        TraceIdContext.putTraceId("trace-worker-context");

        OperationObservation.Scope root = observation.start("HTTP", "AssistantController#streamUserChat",
                "POST /api/assistant/chat/messages/stream", true);
        OperationObservation.ContextSnapshot context = observation.captureContext();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch workerStarted = new CountDownLatch(1);
        try {
            Future<?> worker = executor.submit(() -> {
                TraceIdContext.putTraceId("trace-worker-context");
                workerStarted.countDown();
                try (OperationObservation.ContextBinding ignored = context.bind()) {
                    burnCpu();
                    OperationObservation.Scope sql = observation.start("SQL", "AssistantMapper#load", "SELECT", false);
                    sql.finish("success");
                } finally {
                    TraceIdContext.clear();
                }
            });
            assertThat(workerStarted.await(2, TimeUnit.SECONDS)).isTrue();
            root.markAsyncWaiting();
            worker.get(2, TimeUnit.SECONDS);
            root.finishAsync("success", null);
        } finally {
            executor.shutdownNow();
        }

        ArgumentCaptor<RecentTraceStore.OperationSample> samples =
                ArgumentCaptor.forClass(RecentTraceStore.OperationSample.class);
        verify(store, org.mockito.Mockito.atLeastOnce()).recordOperation(samples.capture());
        RecentTraceStore.OperationSample rootSample = samples.getAllValues().stream()
                .filter(sample -> "HTTP".equals(sample.type())).findFirst().orElseThrow();
        assertThat(rootSample.childSummaries().get("SQL").count()).isEqualTo(1);
        assertThat(rootSample.cpuTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(rootSample.allocatedBytes()).isGreaterThanOrEqualTo(0);
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void shouldAggregateConcurrentWorkerChildrenWithLazyRootState() throws Exception {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setHttpSlowThresholdMs(60_000);
        config.getTrace().setSqlSlowThresholdMs(60_000);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        TraceIdContext.putTraceId("trace-concurrent-children");

        OperationObservation.Scope root = observation.start("HTTP", "Demo#parallel", "GET /parallel", true);
        OperationObservation.ContextSnapshot context = observation.captureContext();
        root.markAsyncWaiting();
        int workerCount = 12;
        CountDownLatch workersReady = new CountDownLatch(workerCount);
        CountDownLatch startWorkers = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        try {
            java.util.List<Future<?>> workers = new java.util.ArrayList<>();
            for (int index = 0; index < workerCount; index++) {
                int childIndex = index;
                workers.add(executor.submit(() -> {
                    TraceIdContext.putTraceId("trace-concurrent-children");
                    workersReady.countDown();
                    try {
                        if (!startWorkers.await(2, TimeUnit.SECONDS)) throw new AssertionError("timed out");
                        try (OperationObservation.ContextBinding ignored = context.bind()) {
                            OperationObservation.Scope sql = observation.start(
                                    "SQL", "Mapper#query" + childIndex, "SELECT", false);
                            sql.finish("success");
                        }
                    } finally {
                        TraceIdContext.clear();
                    }
                    return null;
                }));
            }
            assertThat(workersReady.await(2, TimeUnit.SECONDS)).isTrue();
            startWorkers.countDown();
            for (Future<?> worker : workers) worker.get(3, TimeUnit.SECONDS);
            root.finishAsync("success", null);
        } finally {
            executor.shutdownNow();
        }

        ArgumentCaptor<RecentTraceStore.OperationSample> samples =
                ArgumentCaptor.forClass(RecentTraceStore.OperationSample.class);
        verify(store, org.mockito.Mockito.atLeastOnce()).recordOperation(samples.capture());
        RecentTraceStore.OperationSample rootSample = samples.getAllValues().stream()
                .filter(sample -> "HTTP".equals(sample.type())).findFirst().orElseThrow();
        assertThat(rootSample.childSummaries().get("SQL").count()).isEqualTo(workerCount);
        assertThat(root.lazyStateAllocationCount()).isEqualTo(1);
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void concurrentWorkersMustNotOverwriteEachOthersResourceSegment() throws Exception {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        TraceIdContext.putTraceId("trace-parallel-resource");
        OperationObservation.Scope root = observation.start(
                "HTTP", "Demo#parallelResource", "GET /parallel-resource", true);
        OperationObservation.ContextSnapshot context = observation.captureContext();
        root.markAsyncWaiting();
        CountDownLatch firstBound = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> {
                try (OperationObservation.ContextBinding binding = context.bind()) {
                    firstBound.countDown();
                    if (!releaseFirst.await(2, TimeUnit.SECONDS)) throw new AssertionError("timed out");
                    return binding.resourceBound();
                }
            });
            Future<Boolean> second = executor.submit(() -> {
                if (!firstBound.await(2, TimeUnit.SECONDS)) throw new AssertionError("timed out");
                try (OperationObservation.ContextBinding binding = context.bind()) {
                    return binding.resourceBound();
                }
            });

            assertThat(second.get(2, TimeUnit.SECONDS)).isFalse();
            releaseFirst.countDown();
            assertThat(first.get(2, TimeUnit.SECONDS)).isTrue();
            root.finishAsync("success", null);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }

        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void shouldKeepConcurrentRootsSeparateWhenClientsReuseTraceId() throws Exception {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        CountDownLatch rootsStarted = new CountDownLatch(2);
        CountDownLatch releaseChildren = new CountDownLatch(1);

        Runnable request = () -> {
            TraceIdContext.putTraceId("client-reused-trace");
            OperationObservation.Scope root = observation.start("HTTP", Thread.currentThread().getName(), "GET /same", true);
            rootsStarted.countDown();
            try {
                if (!releaseChildren.await(5, TimeUnit.SECONDS)) throw new AssertionError("timed out");
                OperationObservation.Scope sql = observation.start("SQL", "Mapper#query", "SELECT", false);
                sql.finish("success");
                root.finish("success");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            } finally {
                TraceIdContext.clear();
            }
        };
        Thread first = new Thread(request, "request-one");
        Thread second = new Thread(request, "request-two");
        first.start();
        second.start();
        assertThat(rootsStarted.await(5, TimeUnit.SECONDS)).isTrue();
        releaseChildren.countDown();
        first.join(5_000);
        second.join(5_000);

        ArgumentCaptor<RecentTraceStore.OperationSample> samples =
                ArgumentCaptor.forClass(RecentTraceStore.OperationSample.class);
        verify(store, org.mockito.Mockito.atLeast(2)).recordOperation(samples.capture());
        assertThat(samples.getAllValues().stream().filter(sample -> "HTTP".equals(sample.type())).toList())
                .hasSize(2)
                .allSatisfy(sample -> assertThat(sample.childSummaries().get("SQL").count()).isEqualTo(1));
    }

    @Test
    void shouldKeepBasicSamplesWhenDetailedResourceSamplingIsDisabled() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getMonitor().getDiagnosis().setDetailSamplingEnabled(false);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);

        OperationObservation.Scope scope = observation.start("HTTP", "Demo#basic", "GET /basic", true);
        scope.finish("success");

        ArgumentCaptor<RecentTraceStore.OperationSample> sample =
                ArgumentCaptor.forClass(RecentTraceStore.OperationSample.class);
        verify(store).recordOperation(sample.capture());
        assertThat(sample.getValue().cpuTimeMs()).isEqualTo(-1);
        assertThat(sample.getValue().allocatedBytes()).isEqualTo(-1);
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void shouldPersistMxBeanSampleWithoutEmittingJfrEventWhenJfrIsDisabled() throws Exception {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getMonitor().getDiagnosis().setJfrEnabled(false);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        Path recordingFile = Files.createTempFile("easy-operation-jfr-disabled-", ".jfr");

        try (Recording recording = new Recording()) {
            recording.enable(RuntimeOperationJfrEvent.class);
            recording.start();
            OperationObservation.Scope scope = observation.start("HTTP", "Demo#withoutJfr", "GET /without-jfr", true);
            burnCpu();
            scope.finish("success");
            recording.stop();
            recording.dump(recordingFile);
        }

        ArgumentCaptor<RecentTraceStore.OperationSample> sample =
                ArgumentCaptor.forClass(RecentTraceStore.OperationSample.class);
        verify(store).recordOperation(sample.capture());
        assertThat(sample.getValue().cpuTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(sample.getValue().allocatedBytes()).isGreaterThanOrEqualTo(0);
        assertThat(RecordingFile.readAllEvents(recordingFile)).noneMatch(event ->
                "com.laker.admin.RuntimeOperation".equals(event.getEventType().getName())
                        && "Demo#withoutJfr".equals(event.getString("operation")));
        Files.deleteIfExists(recordingFile);
    }

    @Test
    void shouldEmitCustomEventWhenJfrIsEnabled() throws Exception {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getMonitor().getDiagnosis().setJfrEnabled(true);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        Path recordingFile = Files.createTempFile("easy-operation-jfr-enabled-", ".jfr");

        try (Recording recording = new Recording()) {
            recording.enable(RuntimeOperationJfrEvent.class);
            recording.start();
            observation.start("HTTP", "Demo#withJfr", "GET /with-jfr", true)
                    .finish("failure", new OutOfMemoryError("test-only"));
            recording.stop();
            recording.dump(recordingFile);
        }

        assertThat(RecordingFile.readAllEvents(recordingFile))
                .filteredOn(event -> "com.laker.admin.RuntimeOperation".equals(event.getEventType().getName())
                        && "Demo#withJfr".equals(event.getString("operation")))
                .singleElement().satisfies(event ->
                        assertThat(event.getString("errorType")).isEqualTo(OutOfMemoryError.class.getName()));
        Files.deleteIfExists(recordingFile);
    }

    @Test
    void shouldKeepFastSuccessfulSqlJfrEventOnCheapDetail() throws Exception {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getMonitor().getDiagnosis().setJfrEnabled(true);
        config.getTrace().setSqlSlowThresholdMs(60_000);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        AtomicInteger detailResolutions = new AtomicInteger();
        Path recordingFile = Files.createTempFile("easy-operation-jfr-fast-sql-", ".jfr");

        try (Recording recording = new Recording()) {
            recording.enable(RuntimeOperationJfrEvent.class);
            recording.start();
            observation.start("SQL", "OrderMapper#selectById", "SELECT", () -> {
                detailResolutions.incrementAndGet();
                return "SELECT · select * from orders where id = ?";
            }, false).finish("success");
            recording.stop();
            recording.dump(recordingFile);
        }

        assertThat(detailResolutions).hasValue(0);
        assertThat(RecordingFile.readAllEvents(recordingFile))
                .filteredOn(event -> "com.laker.admin.RuntimeOperation".equals(event.getEventType().getName())
                        && "OrderMapper#selectById".equals(event.getString("operation")))
                .singleElement().satisfies(event -> assertThat(event.getString("detail")).isEqualTo("SELECT"));
        verify(store, never()).recordOperation(org.mockito.ArgumentMatchers.any());
        Files.deleteIfExists(recordingFile);
    }

    @Test
    void shouldResolveDeferredSqlDetailForFailedJfrEvent() throws Exception {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getMonitor().getDiagnosis().setJfrEnabled(true);
        config.getTrace().setSqlSlowThresholdMs(60_000);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        AtomicInteger detailResolutions = new AtomicInteger();
        Path recordingFile = Files.createTempFile("easy-operation-jfr-failed-sql-", ".jfr");

        try (Recording recording = new Recording()) {
            recording.enable(RuntimeOperationJfrEvent.class);
            recording.start();
            observation.start("SQL", "OrderMapper#selectById", "SELECT", () -> {
                detailResolutions.incrementAndGet();
                return "SELECT · select * from orders where id = ?";
            }, false).finish("failure", new IllegalStateException("database unavailable"));
            recording.stop();
            recording.dump(recordingFile);
        }

        assertThat(detailResolutions).hasValue(1);
        assertThat(RecordingFile.readAllEvents(recordingFile))
                .filteredOn(event -> "com.laker.admin.RuntimeOperation".equals(event.getEventType().getName())
                        && "OrderMapper#selectById".equals(event.getString("operation")))
                .singleElement().satisfies(event -> assertThat(event.getString("detail"))
                        .isEqualTo("SELECT · select * from orders where id = ?"));
        verify(store).recordOperation(org.mockito.ArgumentMatchers.any());
        Files.deleteIfExists(recordingFile);
    }

    @Test
    void shouldEndJfrDurationBeforeResolvingFinishingDiagnostics() throws Exception {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getMonitor().getDiagnosis().setJfrEnabled(true);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        Path recordingFile = Files.createTempFile("easy-operation-jfr-duration-", ".jfr");
        AtomicReference<Instant> detailStartedAt = new AtomicReference<>();

        try (Recording recording = new Recording()) {
            recording.enable(RuntimeOperationJfrEvent.class);
            recording.start();
            OperationObservation.Scope scope = observation.start("HTTP", "Demo#duration", "GET /duration", () -> {
                detailStartedAt.set(Instant.now());
                return "GET /duration · resolved detail";
            }, true);
            scope.finish("failure", new IllegalStateException("boom"));
            recording.stop();
            recording.dump(recordingFile);
        }

        assertThat(detailStartedAt).doesNotHaveNullValue();
        assertThat(RecordingFile.readAllEvents(recordingFile))
                .filteredOn(event -> "com.laker.admin.RuntimeOperation".equals(event.getEventType().getName())
                        && "Demo#duration".equals(event.getString("operation")))
                .singleElement().satisfies(event ->
                        assertThat(event.getEndTime().compareTo(detailStartedAt.get())).isLessThanOrEqualTo(0));
        Files.deleteIfExists(recordingFile);
    }

    @Test
    void shouldTimeJfrOnlyAcrossTheExplicitBusinessBoundary() throws Exception {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getMonitor().getDiagnosis().setJfrEnabled(true);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        Path recordingFile = Files.createTempFile("easy-operation-jfr-boundary-", ".jfr");

        Instant beforeBusiness;
        Instant afterBusiness;
        try (Recording recording = new Recording()) {
            recording.enable(RuntimeOperationJfrEvent.class);
            recording.start();
            OperationObservation.Scope scope = observation.start(
                    "HTTP", "Demo#boundary", "GET /boundary", true);
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
            beforeBusiness = Instant.now().minusMillis(5);
            scope.beginBusinessBoundary();
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(2));
            scope.endBusinessBoundary();
            afterBusiness = Instant.now().plusMillis(5);
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25));
            scope.finish("success");
            recording.stop();
            recording.dump(recordingFile);
        }

        Instant expectedStart = beforeBusiness;
        Instant expectedEnd = afterBusiness;
        assertThat(RecordingFile.readAllEvents(recordingFile))
                .filteredOn(event -> "com.laker.admin.RuntimeOperation".equals(event.getEventType().getName())
                        && "Demo#boundary".equals(event.getString("operation")))
                .singleElement().satisfies(event -> {
                    assertThat(event.getStartTime()).isAfterOrEqualTo(expectedStart);
                    assertThat(event.getEndTime()).isBeforeOrEqualTo(expectedEnd);
                });
        Files.deleteIfExists(recordingFile);
    }

    @Test
    void shouldPersistTheBusinessFinishedAtInsteadOfTheLaterFinalizationTime() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        OperationObservation.Scope scope = observation.start("HTTP", "Demo#timestamp", "GET /timestamp", true);

        scope.beginBusinessBoundary();
        scope.endBusinessBoundary();
        java.time.LocalDateTime businessFinishedAt = scope.finishedAt();
        java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        scope.finish("success");

        ArgumentCaptor<RecentTraceStore.OperationSample> sample =
                ArgumentCaptor.forClass(RecentTraceStore.OperationSample.class);
        verify(store).recordOperation(sample.capture());
        assertThat(sample.getValue().finishedAt()).isEqualTo(businessFinishedAt);
    }

    @Test
    void shouldBoundFailureStackBeforePersistence() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);
        IllegalStateException failure = new IllegalStateException(
                "x".repeat(EasySensitiveDataMasker.DEFAULT_TEXT_LIMIT * 2));
        failure.setStackTrace(java.util.stream.IntStream.range(0, 1_000)
                .mapToObj(index -> new StackTraceElement("Example" + index, "call", "Example.java", index + 1))
                .toArray(StackTraceElement[]::new));

        OperationObservation.Scope scope = observation.start("HTTP", "Demo#boundedFailure", "GET /failure", true);
        scope.finish("failure", failure);

        assertThat(scope.completion().stackTrace())
                .startsWith(IllegalStateException.class.getName())
                .hasSizeLessThanOrEqualTo(EasySensitiveDataMasker.DEFAULT_TEXT_LIMIT);
    }

    @Test
    void shouldEmitNormalOperationWhenRecorderIsEnabledAtRuntimeWithoutPersistingJsonlDetail() throws Exception {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getMonitor().getDiagnosis().setJfrEnabled(false);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        ActiveOperationRegistry registry = new ActiveOperationRegistry(meters, config);
        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);
        when(recorder.isRecording()).thenReturn(true);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<LocalJfrRecorder> recorderProvider =
                mock(org.springframework.beans.factory.ObjectProvider.class);
        when(recorderProvider.getIfAvailable()).thenReturn(recorder);
        OperationObservation observation = new OperationObservation(store, config, registry, meters, recorderProvider);
        Path recordingFile = Files.createTempFile("easy-operation-jfr-runtime-enabled-", ".jfr");

        try (Recording recording = new Recording()) {
            recording.enable(RuntimeOperationJfrEvent.class);
            recording.start();
            observation.start("HTTP", "Demo#runtimeJfr").finish("success");
            recording.stop();
            recording.dump(recordingFile);
        }

        assertThat(RecordingFile.readAllEvents(recordingFile)).anyMatch(event ->
                "com.laker.admin.RuntimeOperation".equals(event.getEventType().getName())
                        && "Demo#runtimeJfr".equals(event.getString("operation")));
        verify(store, never()).recordOperation(org.mockito.ArgumentMatchers.any());
        Files.deleteIfExists(recordingFile);
    }

    @Test
    void shouldCaptureFinishingStackWhenPeriodicSamplerHasNotRun() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setHttpSlowThresholdMs(1);
        ActiveOperationRegistry registry = new ActiveOperationRegistry(new SimpleMeterRegistry(), config);
        OperationObservation observation = new OperationObservation(store, config, registry);

        OperationObservation.Scope error = observation.start("HTTP", "Demo#error", "GET /error", true);
        error.finish("failure");
        OperationObservation.Scope slow = observation.start("HTTP", "Demo#slow", "GET /slow", true);
        java.util.concurrent.locks.LockSupport.parkNanos(java.time.Duration.ofMillis(5).toNanos());
        slow.finish("success");

        ArgumentCaptor<RecentTraceStore.OperationSample> samples =
                ArgumentCaptor.forClass(RecentTraceStore.OperationSample.class);
        verify(store, times(2)).recordOperation(samples.capture());
        assertThat(samples.getAllValues())
                .extracting(RecentTraceStore.OperationSample::stackTrace)
                .allSatisfy(stack -> assertThat(stack).contains("OperationObservation"));
    }

    @Test
    void shouldExcludeFinishingDiagnosticAndDeferredDetailFromPersistedResources() {
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        com.sun.management.ThreadMXBean threadBean = mock(com.sun.management.ThreadMXBean.class);
        long threadId = Thread.currentThread().getId();
        AtomicLong cpuNanos = new AtomicLong(10_000_000L);
        AtomicLong allocatedBytes = new AtomicLong(1_000L);
        when(threadBean.isThreadCpuTimeSupported()).thenReturn(true);
        when(threadBean.isThreadCpuTimeEnabled()).thenReturn(true);
        when(threadBean.getThreadCpuTime(threadId)).thenAnswer(ignored -> cpuNanos.get());
        when(threadBean.isThreadAllocatedMemorySupported()).thenReturn(true);
        when(threadBean.isThreadAllocatedMemoryEnabled()).thenReturn(true);
        when(threadBean.getThreadAllocatedBytes(threadId)).thenAnswer(ignored -> allocatedBytes.get());
        java.lang.management.ThreadInfo diagnosticInfo = mock(java.lang.management.ThreadInfo.class);
        when(diagnosticInfo.getThreadState()).thenReturn(Thread.State.RUNNABLE);
        when(diagnosticInfo.getStackTrace()).thenReturn(new StackTraceElement[]{
                new StackTraceElement("Demo", "run", "Demo.java", 42)
        });
        when(diagnosticInfo.getLockedMonitors()).thenReturn(new java.lang.management.MonitorInfo[0]);
        when(diagnosticInfo.getLockedSynchronizers()).thenReturn(new java.lang.management.LockInfo[0]);
        when(threadBean.getThreadInfo(org.mockito.ArgumentMatchers.any(long[].class),
                org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq(48))).thenAnswer(ignored -> {
                    cpuNanos.addAndGet(5_000_000L);
                    allocatedBytes.addAndGet(5_000L);
                    return new java.lang.management.ThreadInfo[]{diagnosticInfo};
                });
        ActiveOperationRegistry registry = new ActiveOperationRegistry(
                new SimpleMeterRegistry(), config, threadBean);
        OperationObservation observation = new OperationObservation(store, config, registry);

        OperationObservation.Scope scope = observation.start("HTTP", "Demo#error", "GET /error", () -> {
            cpuNanos.addAndGet(7_000_000L);
            allocatedBytes.addAndGet(7_000L);
            return "GET /error · resolved detail";
        }, true);
        scope.finish("failure", new IllegalStateException("boom"));

        ArgumentCaptor<RecentTraceStore.OperationSample> sample =
                ArgumentCaptor.forClass(RecentTraceStore.OperationSample.class);
        verify(store).recordOperation(sample.capture());
        assertThat(sample.getValue().cpuTimeMs()).isZero();
        assertThat(sample.getValue().allocatedBytes()).isZero();
        assertThat(sample.getValue().detail()).isEqualTo("GET /error · resolved detail");
        assertThat(cpuNanos).hasValue(22_000_000L);
        assertThat(allocatedBytes).hasValue(13_000L);
    }

    private static void burnCpu() {
        long value = 0;
        for (int index = 0; index < 200_000; index++) value += index;
        if (value == Long.MIN_VALUE) throw new AssertionError();
    }
}
