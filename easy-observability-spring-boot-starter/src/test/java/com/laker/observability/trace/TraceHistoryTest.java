package com.laker.observability.trace;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TraceHistoryTest {
    @Test
    void onlyKeepsInboundEntryPointsAsTraceRoots() {
        TraceHistory history = new TraceHistory(10);
        history.add("http-1", "GET /orders", "GET", "HTTP", 200, 8, false, false, "");
        history.add("redis-1", "Redis get", "get", "REDIS", 200, 1, false, false, "");
        history.add("transaction-1", "OrderService#create", "write", "TRANSACTION",
                200, 4, false, false, "");

        assertThat(history.recent(10)).extracting(TraceHistory.TraceRecord::traceId)
                .containsExactly("http-1");
        assertThat(history.find("redis-1")).isNull();
        assertThat(history.find("transaction-1")).isNull();
    }

    @Test
    void keepsOnlyTheNewestRecordsAndFindsTraceById() {
        TraceHistory history = new TraceHistory(2);
        history.add("trace-1", "first", "GET /first", 200, 5, false, false, "");
        history.add("trace-2", "second", "GET /second", 500, 40, true, true, "tree");
        history.add("trace-3", "third", "GET /third", 200, 7, false, false, "");

        List<TraceHistory.TraceRecord> records = history.recent(10);

        assertThat(records).extracting(TraceHistory.TraceRecord::traceId).containsExactly("trace-3", "trace-2");
        assertThat(history.find("trace-2").tree()).isEqualTo("tree");
        assertThat(history.find("trace-1")).isNull();
    }
}
