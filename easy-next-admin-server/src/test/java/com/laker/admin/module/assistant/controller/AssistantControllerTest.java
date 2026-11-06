package com.laker.admin.module.assistant.controller;

import com.laker.admin.common.model.Response;
import io.github.lakernote.meerkat.trace.TraceIdContext;
import io.github.lakernote.meerkat.trace.TraceContextTaskDecorator;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.assistant.config.AssistantRuntimeProperties;
import com.laker.admin.module.assistant.dto.AssistantEntryResponse;
import com.laker.admin.module.assistant.dto.AssistantMessageRequest;
import com.laker.admin.module.assistant.turn.AgentTurnService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantControllerTest {

    @AfterEach
    void clearContexts() {
        EasySecurityContext.clear();
        MDC.clear();
    }

    @Test
    void shouldDelegateSynchronousRequestToAgentTurnService() {
        AgentTurnService agentTurnService = mock(AgentTurnService.class);
        AssistantEntryResponse expected = mock(AssistantEntryResponse.class);
        AssistantMessageRequest request = new AssistantMessageRequest("查询我的待办", 1001L);
        when(agentTurnService.run(request)).thenReturn(expected);

        Response<AssistantEntryResponse> response = controller(agentTurnService).runDebugTurn(request);

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).isSameAs(expected);
        verify(agentTurnService).run(request);
    }

    @Test
    void shouldPropagateAndClearSecurityAndTraceContextsForRealSseWorker() throws Exception {
        AgentTurnService agentTurnService = mock(AgentTurnService.class);
        AssistantEntryResponse expected = mock(AssistantEntryResponse.class);
        AssistantMessageRequest request = new AssistantMessageRequest("查询流程进度", 1002L);
        AuthPrincipal principal = AuthPrincipal.builder().userId(7L).userName("admin").build();
        AtomicReference<AuthPrincipal> workerPrincipal = new AtomicReference<>();
        AtomicReference<String> workerAccessToken = new AtomicReference<>();
        AtomicReference<String> workerTraceId = new AtomicReference<>();
        CountDownLatch executionCompleted = new CountDownLatch(1);
        EasySecurityContext.setPrincipal(principal);
        EasySecurityContext.setAccessToken("token-for-test");
        String requestTraceId = "0123456789abcdef0123456789abcdef";
        MDC.put(TraceIdContext.MDC_KEY, requestTraceId);
        doAnswer(invocation -> {
            try {
                workerPrincipal.set(EasySecurityContext.getPrincipal());
                workerAccessToken.set(EasySecurityContext.getAccessToken());
                workerTraceId.set(TraceIdContext.currentTraceId());
                return expected;
            } finally {
                executionCompleted.countDown();
            }
        }).when(agentTurnService).run(eq(request), any());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            controller(agentTurnService, executor::execute).streamUserChat(request);

            assertThat(executionCompleted.await(2, TimeUnit.SECONDS)).isTrue();
            verify(agentTurnService, timeout(2_000)).run(eq(request), any());
            assertThat(workerPrincipal.get()).isSameAs(principal);
            assertThat(workerAccessToken.get()).as("原始登录 Token 不应传播给 Agent 线程").isNull();
            assertThat(workerTraceId.get()).isEqualTo(requestTraceId);

            Future<?> contextCheck = executor.submit(() -> {
                assertThat(EasySecurityContext.getPrincipal()).isNull();
                assertThat(EasySecurityContext.getAccessToken()).isNull();
                assertThat(TraceIdContext.currentTraceId()).isNull();
            });
            contextCheck.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static AssistantController controller(AgentTurnService agentTurnService) {
        return controller(agentTurnService, Runnable::run);
    }

    private static AssistantController controller(AgentTurnService agentTurnService,
                                                  org.springframework.core.task.TaskExecutor executor) {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> heartbeat = mock(ScheduledFuture.class);
        doReturn(heartbeat).when(scheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
        TraceContextTaskDecorator traceDecorator = new TraceContextTaskDecorator();
        org.springframework.core.task.TaskExecutor decoratedExecutor =
                task -> executor.execute(traceDecorator.decorate(task));
        return new AssistantController(agentTurnService, decoratedExecutor, scheduler,
                new AssistantRuntimeProperties());
    }
}
