package com.laker.observability.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Bounded JSONL history for slow and failed operations, including per-thread resource measurements. */
public final class IncidentHistory {
    private static final Logger log = LoggerFactory.getLogger(IncidentHistory.class);
    private final ObjectMapper mapper;
    private final Path file;
    private final int capacity;
    private final long maxBytes;
    private final ConcurrentLinkedDeque<OperationRecord> records = new ConcurrentLinkedDeque<>();
    private final Object writeLock = new Object();

    public IncidentHistory(ObjectMapper mapper, String file, int capacity, long maxBytes) {
        this.mapper = mapper.copy();
        this.file = Path.of(file).toAbsolutePath().normalize();
        this.capacity = Math.max(100, capacity);
        this.maxBytes = Math.max(1024 * 1024, maxBytes);
    }

    @PostConstruct
    public void restore() {
        synchronized (writeLock) {
            if (!Files.isRegularFile(file)) return;
            try {
                if (Files.size(file) > maxBytes) trimFile();
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (line.isBlank()) continue;
                    try { addInMemory(mapper.readValue(line, OperationRecord.class)); }
                    catch (Exception invalid) { log.debug("跳过损坏的 Easy Observability incident 记录", invalid); }
                }
            } catch (IOException failure) {
                log.warn("无法恢复 Easy Observability slow/error 历史：{}", file, failure);
            }
        }
    }

    public void record(OperationRecord operation) {
        if (operation == null || (!operation.slow() && !operation.error())) return;
        addInMemory(operation);
        synchronized (writeLock) {
            try {
                Files.createDirectories(file.getParent());
                byte[] line = (mapper.writeValueAsString(operation) + "\n").getBytes(StandardCharsets.UTF_8);
                if (Files.exists(file) && Files.size(file) + line.length > maxBytes) trimFile();
                Files.write(file, line, java.nio.file.StandardOpenOption.CREATE,
                        java.nio.file.StandardOpenOption.WRITE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception failure) {
                log.warn("写入 Easy Observability slow/error 历史失败", failure);
            }
        }
    }

    public List<OperationRecord> recent(int limit, String kind) {
        int safeLimit = Math.max(1, Math.min(limit, capacity));
        List<OperationRecord> result = new ArrayList<>(Math.min(safeLimit, 256));
        for (OperationRecord record : records) {
            if ("slow".equalsIgnoreCase(kind) && !record.slow()) continue;
            if (("error".equalsIgnoreCase(kind) || "errors".equalsIgnoreCase(kind)) && !record.error()) continue;
            if (result.size() >= safeLimit) break;
            result.add(record);
        }
        return List.copyOf(result);
    }

    private void addInMemory(OperationRecord operation) {
        records.addFirst(operation);
        while (records.size() > capacity) records.pollLast();
    }

    private void trimFile() throws IOException {
        if (!Files.isRegularFile(file)) return;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        long bytes = 0;
        List<String> retained = new ArrayList<>();
        for (int index = lines.size() - 1; index >= 0 && retained.size() < capacity; index--) {
            String line = lines.get(index);
            long lineBytes = line.getBytes(StandardCharsets.UTF_8).length + 1L;
            if (bytes + lineBytes > maxBytes / 2) break;
            retained.add(line);
            bytes += lineBytes;
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
