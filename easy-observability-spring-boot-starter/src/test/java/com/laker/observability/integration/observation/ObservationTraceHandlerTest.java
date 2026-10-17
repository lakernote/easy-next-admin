package com.laker.observability.integration.observation;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.metrics.OperationRecorder;
import com.laker.observability.trace.SpanKind;
import com.laker.observability.trace.TraceContext;
import com.laker.observability.trace.TraceHistory;
import com.laker.observability.trace.TraceIdContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObservationTraceHandlerTest {
    @AfterEach
    void clearContexts() {
        TraceContext.clear();
        TraceIdContext.clear();
    }

    @Test
    void convertsFrameworkObservationToChildSpanAndOperationRecord() throws Exception {
        EasyObservabilityProperties properties = new EasyObservabilityProperties();
        properties.getTrace().setRedisSlowThresholdMs(1);
        TraceHistory history = new TraceHistory(10, 10);
        OperationRecorder recorder = new OperationRecorder(new SimpleMeterRegistry(), history, properties);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationTraceHandler(recorder, history, properties));
        TraceIdContext.putTraceId("trace-redis-1");
        TraceContext.startRoot("GET /cache", SpanKind.HTTP);

        Observation observation = Observation.createNotStarted("spring.data.redis", registry)
                .contextualName("GET")
                .lowCardinalityKeyValue("db.operation", "GET");
        observation.start();
        Thread.sleep(5);
        observation.stop();

        TraceContext.Completion[] completion = new TraceContext.Completion[1];
        TraceContext.stopRoot(Long.MAX_VALUE, "test", null, result -> completion[0] = result);
        assertThat(completion[0].tree()).contains("[Redis] GET");
        assertThat(history.recentOperations(10, "slow")).singleElement()
                .satisfies(operation -> {
                    assertThat(operation.traceId()).isEqualTo("trace-redis-1");
                    assertThat(operation.spanId()).isNotBlank();
                    assertThat(operation.parentSpanId()).isEqualTo("1");
                });
    }

    @Test
    void recordsStandaloneDependencyWithoutInventingTraceRoot() {
        EasyObservabilityProperties properties = new EasyObservabilityProperties();
        properties.getTrace().setRedisSlowThresholdMs(0);
        TraceHistory history = new TraceHistory(10, 10);
        OperationRecorder recorder = new OperationRecorder(new SimpleMeterRegistry(), history, properties);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationTraceHandler(recorder, history, properties));
        Observation observation = Observation.createNotStarted("spring.data.redis", registry)
                .contextualName("GET")
                .lowCardinalityKeyValue("db.operation", "GET");

        observation.start().stop();

        assertThat(history.recentOperations(10, null)).singleElement()
                .satisfies(operation -> {
                    assertThat(operation.type()).isEqualTo("REDIS");
                    assertThat(operation.name()).isEqualTo("GET");
                    assertThat(operation.traceId()).isNotBlank();
                });
        assertThat(history.recent(10)).isEmpty();
    }

    @Test
    void recordsHttpServerErrorStatusAsFailedTraceWithoutThrowable() {
        EasyObservabilityProperties properties = new EasyObservabilityProperties();
        TraceHistory history = new TraceHistory(10, 10);
        OperationRecorder recorder = new OperationRecorder(new SimpleMeterRegistry(), history, properties);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationTraceHandler(recorder, history, properties));

        Observation.createNotStarted("http.server.requests", registry)
                .lowCardinalityKeyValue("http.request.method", "GET")
                .lowCardinalityKeyValue("http.route", "/orders/{id}")
                .lowCardinalityKeyValue("http.response.status_code", "503")
                .start().stop();

        assertThat(history.recent(10)).singleElement().satisfies(trace -> {
            assertThat(trace.status()).isEqualTo(503);
            assertThat(trace.error()).isTrue();
            assertThat(trace.tree()).isNotBlank();
        });
        assertThat(history.recentOperations(10, "error")).singleElement().satisfies(operation -> {
            assertThat(operation.error()).isTrue();
            assertThat(operation.errorMessage()).isEqualTo("HTTP status 503");
        });
    }

    @Test
    void canLeaveHttpEntryToAHostSpecificAsyncAwareAdapter() {
        EasyObservabilityProperties properties = new EasyObservabilityProperties();
        properties.getTrace().setHttpServerObservationEnabled(false);
        TraceHistory history = new TraceHistory(10, 10);
        OperationRecorder recorder = new OperationRecorder(new SimpleMeterRegistry(), history, properties);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationTraceHandler(recorder, history, properties));

        Observation.createNotStarted("http.server.requests", registry)
                .lowCardinalityKeyValue("http.request.method", "GET")
                .lowCardinalityKeyValue("http.route", "/orders")
                .start().stop();

        assertThat(history.recentOperations(10, null)).isEmpty();
        assertThat(history.recent(10)).isEmpty();
        assertThat(TraceContext.active()).isFalse();
    }

    @Test
    void canDisableFrameworkObservationsWhenNativeAdaptersOwnRedisAndDatabase() {
        EasyObservabilityProperties properties = new EasyObservabilityProperties();
        properties.getTrace().setRedisObservationEnabled(false);
        properties.getTrace().setDatabaseObservationEnabled(false);
        ObservationTraceHandler handler = new ObservationTraceHandler(
                new OperationRecorder(new SimpleMeterRegistry(), new TraceHistory(10, 10), properties),
                new TraceHistory(10, 10), properties);

        Observation.Context redis = new Observation.Context();
        redis.setName("spring.data.redis");
        Observation.Context database = new Observation.Context();
        database.setName("spring.jdbc");
        Observation.Context transaction = new Observation.Context();
        transaction.setName("spring.transaction");

        assertThat(handler.supportsContext(redis)).isFalse();
        assertThat(handler.supportsContext(database)).isFalse();
        assertThat(handler.supportsContext(transaction)).isTrue();
    }

    @Test
    void recordsHttpRouteAsBoundedRequestStatisticTags() {
        EasyObservabilityProperties properties = new EasyObservabilityProperties();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        TraceHistory history = new TraceHistory(10, 10);
        OperationRecorder recorder = new OperationRecorder(meters, history, properties);
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationTraceHandler(recorder, history, properties));

        Observation.createNotStarted("http.server.requests", registry)
                .lowCardinalityKeyValue("http.request.method", "GET")
                .lowCardinalityKeyValue("http.route", "/orders/{id}")
                .start().stop();

        assertThat(meters.find("easy.operations.duration").timers()).singleElement()
                .satisfies(timer -> {
                    assertThat(timer.getId().getTag("source")).isEqualTo("HTTP");
                    assertThat(timer.getId().getTag("operation")).isEqualTo("/orders/{id} · GET");
                });
    }
}
