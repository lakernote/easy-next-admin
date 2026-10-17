package com.laker.observability.trace;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Runs local history file writes away from application request and listener threads. */
public final class BoundedHistoryWriter implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(BoundedHistoryWriter.class);
    private static final int QUEUE_CAPACITY = 2_048;

    private final AtomicLong droppedTasks = new AtomicLong();
    private final AtomicBoolean dropWarningLogged = new AtomicBoolean();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_CAPACITY), task -> {
        Thread thread = new Thread(task, "easy-observability-history-writer");
        thread.setDaemon(true);
        return thread;
    }, new ThreadPoolExecutor.AbortPolicy());

    public void execute(Runnable write) {
        if (write == null) return;
        try {
            executor.execute(write);
        } catch (RejectedExecutionException saturated) {
            long dropped = droppedTasks.incrementAndGet();
            if (dropWarningLogged.compareAndSet(false, true)) {
                log.warn("Easy Observability 历史写入队列已满，后续记录可能丢弃；queueCapacity={}, dropped={}",
                        QUEUE_CAPACITY, dropped);
            }
        }
    }

    public long droppedTaskCount() {
        return droppedTasks.get();
    }

    @PreDestroy
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                int abandoned = executor.shutdownNow().size();
                if (abandoned > 0) log.warn("Easy Observability 关闭时丢弃了 {} 条待写历史记录", abandoned);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            int abandoned = executor.shutdownNow().size();
            if (abandoned > 0) log.warn("Easy Observability 关闭时丢弃了 {} 条待写历史记录", abandoned);
        }
    }
}
