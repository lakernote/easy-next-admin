package com.laker.admin.module.assistant.stream;

import com.laker.admin.module.assistant.dto.AssistantChatStreamEvent;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AssistantChatSseEventSinkTest {

    @Test
    void shouldNotPropagateClientDisconnectIntoAgentExecution() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        doThrow(new IOException("client disconnected"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));
        AssistantChatSseEventSink sink = new AssistantChatSseEventSink(emitter, "trace-1");
        AssistantChatStreamEvent event = AssistantChatStreamEvent.failed("test");

        assertThatCode(() -> {
            sink.accept(event);
            // 后续业务阶段仍可继续调用事件出口，只是不会再写失效连接。
            sink.accept(event);
        }).doesNotThrowAnyException();

        assertThat(sink.isConnected()).isFalse();
        verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void shouldCancelHeartbeatWhenExecutionCompletes() {
        SseEmitter emitter = mock(SseEmitter.class);
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> heartbeat = mock(ScheduledFuture.class);
        doReturn(heartbeat).when(scheduler)
                .scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofSeconds(15)));
        AssistantChatSseEventSink sink = new AssistantChatSseEventSink(emitter, "trace-2");

        sink.startHeartbeat(scheduler, Duration.ofSeconds(15));
        sink.complete();

        assertThat(sink.isConnected()).isFalse();
        verify(heartbeat).cancel(false);
        verify(emitter).complete();
    }

    @Test
    void shouldStopSendingAfterServletTimeout() throws Exception {
        SseEmitter emitter = mock(SseEmitter.class);
        AtomicReference<Runnable> timeoutHandler = new AtomicReference<>();
        doAnswer(invocation -> {
            timeoutHandler.set(invocation.getArgument(0, Runnable.class));
            return null;
        }).when(emitter).onTimeout(any(Runnable.class));
        AssistantChatSseEventSink sink = new AssistantChatSseEventSink(emitter, "trace-3");

        timeoutHandler.get().run();
        sink.accept(AssistantChatStreamEvent.failed("test"));

        assertThat(sink.isConnected()).isFalse();
        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }
}
