package com.laker.admin.module.assistant.stream;

import com.laker.admin.module.assistant.dto.AssistantChatStreamEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 非侵入式 SSE 事件出口。
 *
 * <p>浏览器断开只代表事件无法继续投递，不能作为 Agent 业务失败传播到 Orchestrator。
 * 特别是 Tool 已产生写操作后，Runtime 仍必须完成会话状态、幂等结果和 Trace 写回。</p>
 */
@Slf4j
public final class AssistantChatSseEventSink implements Consumer<AssistantChatStreamEvent> {
    private final SseEmitter emitter;
    private final String traceId;
    private final AtomicBoolean connected = new AtomicBoolean(true);
    private final AtomicReference<ScheduledFuture<?>> heartbeatFuture = new AtomicReference<>();

    public AssistantChatSseEventSink(SseEmitter emitter, String traceId) {
        this.emitter = emitter;
        this.traceId = traceId;
        emitter.onCompletion(() -> disconnect("completed"));
        emitter.onTimeout(() -> disconnect("timeout"));
        emitter.onError(error -> disconnect("error"));
    }

    /**
     * 投递失败后只关闭传输出口，不向 Agent 主链抛异常。
     */
    @Override
    public void accept(AssistantChatStreamEvent event) {
        send(SseEmitter.event().name(event.event()).data(event));
    }

    public void startHeartbeat(TaskScheduler scheduler, Duration interval) {
        try {
            ScheduledFuture<?> scheduled = scheduler.scheduleAtFixedRate(
                    () -> send(SseEmitter.event().comment("heartbeat")),
                    interval
            );
            if (!heartbeatFuture.compareAndSet(null, scheduled)) {
                scheduled.cancel(false);
                return;
            }
            if (!connected.get()) {
                cancelHeartbeat();
            }
        } catch (RuntimeException ex) {
            // 心跳是连接保活能力，调度失败不能中断已经提交的 Agent 业务任务。
            log.warn("Assistant SSE heartbeat scheduling failed, traceId={}, failureType={}",
                    traceId, ex.getClass().getSimpleName());
        }
    }

    public void complete() {
        if (!connected.getAndSet(false)) {
            cancelHeartbeat();
            return;
        }
        cancelHeartbeat();
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // Servlet 容器可能已经因超时或断连完成异步请求。
        }
    }

    public void fail(AssistantChatStreamEvent event) {
        boolean delivered = send(SseEmitter.event().name(event.event()).data(event));
        if (delivered) {
            complete();
        }
    }

    boolean isConnected() {
        return connected.get();
    }

    private synchronized boolean send(SseEmitter.SseEventBuilder event) {
        if (!connected.get()) {
            return false;
        }
        try {
            emitter.send(event);
            return true;
        } catch (IOException | IllegalStateException ex) {
            disconnect(ex.getClass().getSimpleName());
            log.debug("Assistant SSE client disconnected, traceId={}, failureType={}",
                    traceId, ex.getClass().getSimpleName());
            return false;
        }
    }

    private void disconnect(String reason) {
        if (connected.getAndSet(false)) {
            log.debug("Assistant SSE transport closed, traceId={}, reason={}", traceId, reason);
        }
        cancelHeartbeat();
    }

    private void cancelHeartbeat() {
        ScheduledFuture<?> scheduled = heartbeatFuture.getAndSet(null);
        if (scheduled != null) {
            scheduled.cancel(false);
        }
    }
}
