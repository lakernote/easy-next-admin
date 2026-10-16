package com.laker.observability.web;

import com.laker.observability.trace.TraceHistory;
import com.laker.observability.trace.OperationRecord;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservabilityControllerTest {
    @Test
    void returnsMeterValuesAndRecentTraces() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.counter("orders.created", "result", "success").increment(3);
        TraceHistory history = new TraceHistory(10);
        history.add("trace-1", "OrdersController#create", "POST /orders", 201, 12, false, false, "");
        ObservabilityController controller = new ObservabilityController(registry, history, () -> true);

        var metrics = controller.metrics(100);
        var traces = controller.traces(100);

        assertThat(metrics).anySatisfy(metric -> {
            assertThat(metric.name()).isEqualTo("orders.created");
            assertThat(metric.tags()).containsEntry("result", "success");
            assertThat(metric.values()).containsEntry("COUNT", 3.0);
        });
        assertThat(traces).hasSize(1);
        assertThat(traces.get(0).traceId()).isEqualTo("trace-1");
    }

    @Test
    void rejectsRequestsWhenTheHostPolicyDeniesAccess() {
        ObservabilityController controller = new ObservabilityController(new SimpleMeterRegistry(),
                new TraceHistory(10), () -> false);

        assertThatThrownBy(() -> controller.metrics(10))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("403 FORBIDDEN");
    }

    @Test
    void slowAndErrorListsReturnRootTracesFilteredByTheirEntrySource() {
        TraceHistory history = new TraceHistory(10, 10);
        history.add("http-slow", "OrdersController#list", "GET /orders", "HTTP",
                200, 900, true, false, "http tree");
        history.add("job-error", "NightlyJob#run", "nightly-job", "JOB",
                500, 120, false, true, "job tree");
        history.addOperation(new OperationRecord("DATABASE", "http-slow", "OrderMapper#select", "worker",
                7, "failure", 800, 10, 100, 0, 0, 0, 0, "SELECT orders", "", "",
                true, true, java.util.List.of("SLOW_DATABASE"), "SQLException", "query failed",
                java.util.Map.of(), java.time.Instant.now()));
        ObservabilityController controller = new ObservabilityController(new SimpleMeterRegistry(), history, () -> true);

        var slow = controller.incidents("slow", "", 100);
        var httpSlow = controller.incidents("slow", "HTTP", 100);
        var errors = controller.incidents("error", "", 100);

        assertThat(slow).extracting(TraceHistory.TraceRecord::traceId).containsExactly("http-slow");
        assertThat(httpSlow).extracting(TraceHistory.TraceRecord::traceId).containsExactly("http-slow");
        assertThat(errors).extracting(TraceHistory.TraceRecord::traceId).containsExactly("job-error");
        assertThat(httpSlow.get(0).sourceType()).isEqualTo("HTTP");
    }
}
