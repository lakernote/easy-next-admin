package com.laker.observability.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryPersistenceTest {
    @TempDir Path directory;

    @Test
    void persistsSlowAndErrorResourcesAndTraceTreeAcrossRestart() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Path operationsFile = directory.resolve("incidents.jsonl");
        Path tracesFile = directory.resolve("traces.jsonl");
        OperationRecord operation = new OperationRecord("HTTP", "trace-123", "GET /orders/{id}",
                "http-nio-8080-exec-7", 77, "failure", 845, 51, 2048, 0, 0, 0, 0,
                "GET /orders/{id}", "", "", true, true, List.of("SLOW_HTTP"),
                "HTTP_5XX", "500", Map.of(), Instant.parse("2026-10-15T10:00:00Z"));
        IncidentHistory incidents = new IncidentHistory(mapper, operationsFile.toString(), 1000, 4 * 1024 * 1024);
        incidents.restore();
        incidents.record(operation);

        TraceHistory.TraceRecord trace = new TraceHistory.TraceRecord(1, "trace-123", "GET /orders/{id}",
                "GET /orders/{id}", 500, 845, true, true, "HTTP GET\n  SQL select", operation.finishedAt(),
                operation.threadId(), operation.cpuTimeMs(), operation.allocatedBytes(), List.of(operation));
        TraceArchive archive = new TraceArchive(mapper, tracesFile.toString(), 1000, 4 * 1024 * 1024);
        archive.restore();
        archive.record(trace);

        IncidentHistory restoredIncidents = new IncidentHistory(mapper, operationsFile.toString(), 1000, 4 * 1024 * 1024);
        restoredIncidents.restore();
        TraceArchive restoredTraces = new TraceArchive(mapper, tracesFile.toString(), 1000, 4 * 1024 * 1024);
        restoredTraces.restore();

        assertThat(restoredIncidents.recent(10, "error")).singleElement().satisfies(saved -> {
            assertThat(saved.cpuTimeMs()).isEqualTo(51);
            assertThat(saved.allocatedBytes()).isEqualTo(2048);
            assertThat(saved.threadId()).isEqualTo(77);
            assertThat(saved.threadName()).isEqualTo("http-nio-8080-exec-7");
        });
        assertThat(restoredTraces.find("trace-123")).satisfies(saved -> {
            assertThat(saved).isNotNull();
            assertThat(saved.tree()).contains("SQL select");
            assertThat(saved.operations()).singleElement().satisfies(item ->
                    assertThat(item.allocatedBytes()).isEqualTo(2048));
        });
    }
}
