package com.laker.admin.infrastructure.observability.trace;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.laker.admin.infrastructure.security.masking.EasySensitiveDataMasker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class RecentTraceStorePendingBudgetTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T04:00:00Z"),
            ZoneOffset.ofHours(8));
    private static final long PENDING_MAX_BYTES = 32 * 1024;

    @TempDir
    Path storageDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void highVolumeIncidentsStayWithinPendingByteBudgetAndReleaseItAfterDrain() throws Exception {
        BlockingStore store = new BlockingStore(objectMapper, storageDirectory, PENDING_MAX_BYTES);
        store.restore();
        String largeDetail = "x".repeat(6_000);
        recordIncident(store, "in-flight", largeDetail);
        assertThat(store.writerEntered.await(5, TimeUnit.SECONDS)).isTrue();

        for (int index = 0; index < 1_000; index++) {
            recordIncident(store, "queued-" + index, largeDetail);
        }

        assertThat(store.pendingBytes()).isPositive().isLessThanOrEqualTo(PENDING_MAX_BYTES);
        assertThat(store.pendingMaxBytes()).isEqualTo(PENDING_MAX_BYTES);
        assertThat(store.pendingWrites()).isLessThan(10);
        assertThat(store.byteBudgetDroppedWrites()).isGreaterThan(900);
        assertThat(store.droppedWrites()).isEqualTo(store.byteBudgetDroppedWrites());

        store.releaseWriter.countDown();
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), store::close);
        assertThat(store.pendingWrites()).isZero();
        assertThat(store.pendingBytes()).isZero();
    }

    @Test
    void pendingBudgetMatchesTheExactSanitizedJsonlBytesWrittenToDisk() throws Exception {
        BlockingStore store = new BlockingStore(objectMapper, storageDirectory, PENDING_MAX_BYTES);
        store.restore();
        recordIncident(store, "exact-size", "password=must-not-leak");
        assertThat(store.writerEntered.await(5, TimeUnit.SECONDS)).isTrue();

        long pendingLineBytes = store.pendingBytes();
        assertThat(pendingLineBytes).isPositive().isLessThanOrEqualTo(PENDING_MAX_BYTES);

        store.releaseWriter.countDown();
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), store::close);

        Path segment = storageDirectory.resolve("runtime-diagnosis-2026-10-14.jsonl");
        assertThat(Files.size(segment)).isEqualTo(pendingLineBytes);
        assertThat(Files.readString(segment, StandardCharsets.UTF_8))
                .contains("password=******")
                .doesNotContain("must-not-leak");
        assertThat(store.pendingBytes()).isZero();
    }

    @Test
    void serializesEachQueuedRecordOnlyOnce() {
        AtomicInteger serializations = new AtomicInteger();
        SimpleModule countingModule = new SimpleModule();
        countingModule.addSerializer(RecentTraceStore.EventSample.class, new JsonSerializer<>() {
            @Override
            public void serialize(RecentTraceStore.EventSample value, JsonGenerator generator,
                                  SerializerProvider serializers) throws IOException {
                serializations.incrementAndGet();
                generator.writeStartObject();
                generator.writeStringField("type", value.type());
                generator.writeStringField("name", value.name());
                generator.writeNumberField("value", value.value());
                generator.writeNumberField("threshold", value.threshold());
                generator.writeStringField("detail", value.detail());
                generator.writeStringField("occurredAt", value.occurredAt().toString());
                generator.writeEndObject();
            }
        });
        ObjectMapper countingMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .registerModule(countingModule);
        RecentTraceStore store = new RecentTraceStore(countingMapper, storageDirectory.toString(), 30,
                10_000_000, CLOCK);
        store.restore();

        store.recordEvent(new RecentTraceStore.EventSample("TEST", "single-serialization", 1, 0,
                "password=must-not-leak", java.time.LocalDateTime.now(CLOCK)));
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), store::close);

        assertThat(serializations).hasValue(1);
    }

    private static void recordIncident(RecentTraceStore store, String name, String tree) {
        store.recordIncident("HTTP", "trace-" + name, name, "GET /pending-budget", 200, tree, 2_000,
                true, false, List.of("SLOW_REQUEST"));
    }

    private static final class BlockingStore extends RecentTraceStore {
        private final CountDownLatch writerEntered = new CountDownLatch(1);
        private final CountDownLatch releaseWriter = new CountDownLatch(1);

        private BlockingStore(ObjectMapper objectMapper, Path directory, long pendingMaxBytes) {
            super(objectMapper, new EasySensitiveDataMasker(objectMapper), directory.toString(), 30,
                    10_000_000, 8_388_608, CLOCK, true, 5_000, pendingMaxBytes);
        }

        @Override
        void beforeAsyncWriteBatch() {
            writerEntered.countDown();
            try {
                releaseWriter.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
