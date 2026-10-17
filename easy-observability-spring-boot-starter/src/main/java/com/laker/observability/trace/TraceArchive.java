package com.laker.observability.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

/** Persists completed slow/error Trace trees and their per-operation resource details. */
public final class TraceArchive {
    private static final Logger log = LoggerFactory.getLogger(TraceArchive.class);
    private final ObjectMapper mapper;
    private final Path file;
    private final int capacity;
    private final long maxBytes;
    private final Consumer<Runnable> writeExecutor;
    private final ConcurrentLinkedDeque<TraceHistory.TraceRecord> records = new ConcurrentLinkedDeque<>();
    private final Object lock = new Object();

    public TraceArchive(ObjectMapper mapper, String file, int capacity, long maxBytes) {
        this(mapper, file, capacity, maxBytes, Runnable::run);
    }

    public TraceArchive(ObjectMapper mapper, String file, int capacity, long maxBytes,
                        BoundedHistoryWriter writer) {
        this(mapper, file, capacity, maxBytes, writer::execute);
    }

    private TraceArchive(ObjectMapper mapper, String file, int capacity, long maxBytes,
                         Consumer<Runnable> writeExecutor) {
        this.mapper = mapper.copy();
        this.file = Path.of(file).toAbsolutePath().normalize();
        this.capacity = Math.max(100, capacity);
        this.maxBytes = Math.max(1024 * 1024, maxBytes);
        this.writeExecutor = writeExecutor;
    }

    @PostConstruct
    public void restore() {
        synchronized (lock) {
            if (!Files.isRegularFile(file)) return;
            try {
                if (Files.size(file) > maxBytes) trim();
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    try {
                        TraceHistory.TraceRecord trace = mapper.readValue(line, TraceHistory.TraceRecord.class);
                        if (TraceHistory.isEntrySource(trace.sourceType())) add(trace);
                    }
                    catch (Exception invalid) { log.debug("跳过损坏的 Easy Observability Trace 记录", invalid); }
                }
            } catch (Exception failure) { log.warn("无法恢复 Easy Observability Trace 历史：{}", file, failure); }
        }
    }

    public void record(TraceHistory.TraceRecord trace) {
        if (trace == null || !TraceHistory.isEntrySource(trace.sourceType())
                || (!trace.slow() && !trace.error())) return;
        add(trace);
        writeExecutor.accept(() -> append(trace));
    }

    private void append(TraceHistory.TraceRecord trace) {
        synchronized (lock) {
            try {
                Files.createDirectories(file.getParent());
                byte[] bytes = (mapper.writeValueAsString(trace) + "\n").getBytes(StandardCharsets.UTF_8);
                if (Files.exists(file) && Files.size(file) + bytes.length > maxBytes) trim();
                Files.write(file, bytes, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception failure) { log.warn("写入 Easy Observability Trace 历史失败", failure); }
        }
    }

    public List<TraceHistory.TraceRecord> recent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, capacity));
        List<TraceHistory.TraceRecord> result = new ArrayList<>(Math.min(safeLimit, 256));
        for (TraceHistory.TraceRecord trace : records) {
            if (!TraceHistory.isEntrySource(trace.sourceType())) continue;
            if (result.size() >= safeLimit) break;
            result.add(trace);
        }
        return List.copyOf(result);
    }

    public TraceHistory.TraceRecord find(String traceId) {
        if (traceId == null) return null;
        return records.stream().filter(trace -> traceId.equals(trace.traceId())
                && TraceHistory.isEntrySource(trace.sourceType())).findFirst().orElse(null);
    }

    private void add(TraceHistory.TraceRecord trace) {
        if (trace == null || !TraceHistory.isEntrySource(trace.sourceType())) return;
        records.removeIf(item -> trace.traceId().equals(item.traceId()));
        records.addFirst(trace);
        while (records.size() > capacity) records.pollLast();
    }

    private void trim() throws Exception {
        if (!Files.isRegularFile(file)) return;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        long bytes = 0;
        List<String> retained = new ArrayList<>();
        for (int index = lines.size() - 1; index >= 0 && retained.size() < capacity; index--) {
            String line = lines.get(index);
            long size = line.getBytes(StandardCharsets.UTF_8).length + 1L;
            if (bytes + size > maxBytes / 2) break;
            retained.add(line);
            bytes += size;
        }
        java.util.Collections.reverse(retained);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temporary, retained, StandardCharsets.UTF_8);
        try { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
