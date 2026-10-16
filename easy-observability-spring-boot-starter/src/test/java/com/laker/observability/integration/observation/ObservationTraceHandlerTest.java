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
                .satisfies(operation -> assertThat(operation.traceId()).isEqualTo("trace-redis-1"));
    }

    @Test
    void recordsStandaloneObservationAsATraceRoot() {
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
        assertThat(history.recent(10)).singleElement()
                .satisfies(trace -> assertThat(trace.name()).isEqualTo("GET"));
    }
}
