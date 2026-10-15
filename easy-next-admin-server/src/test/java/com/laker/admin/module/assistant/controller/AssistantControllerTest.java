package com.laker.admin.module.assistant.controller;

import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.assistant.config.AssistantRuntimeProperties;
import com.laker.admin.module.assistant.dto.AssistantEntryResponse;
import com.laker.admin.module.assistant.dto.AssistantMessageRequest;
import com.laker.admin.module.assistant.turn.AgentTurnService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
        EasyTraceIdContext.clear();
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
        EasyTraceIdContext.putTraceId("trace-sse-test");
        doAnswer(invocation -> {
            try {
                workerPrincipal.set(EasySecurityContext.getPrincipal());
                workerAccessToken.set(EasySecurityContext.getAccessToken());
                workerTraceId.set(EasyTraceIdContext.currentTraceId());
                return expected;
            } finally {
                executionCompleted.countDown();
            }
        }).when(agentTurnService).run(eq(request), any());
        OperationObservation operationObservation = mock(OperationObservation.class);
        OperationObservation.ContextSnapshot observationContext = mock(OperationObservation.ContextSnapshot.class);
        OperationObservation.ContextBinding observationBinding = mock(OperationObservation.ContextBinding.class);
        when(operationObservation.captureContext()).thenReturn(observationContext);
        when(observationContext.bind()).thenReturn(observationBinding);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            controller(agentTurnService, executor::execute, operationObservation).streamUserChat(request);

            assertThat(executionCompleted.await(2, TimeUnit.SECONDS)).isTrue();
            verify(agentTurnService, timeout(2_000)).run(eq(request), any());
            assertThat(workerPrincipal.get()).isSameAs(principal);
            assertThat(workerAccessToken.get()).as("原始登录 Token 不应传播给 Agent 线程").isNull();
            assertThat(workerTraceId.get()).isEqualTo("trace-sse-test");
            verify(operationObservation).captureContext();
            verify(observationContext).bind();
            verify(observationBinding, timeout(2_000)).close();

            Future<?> contextCheck = executor.submit(() -> {
                assertThat(EasySecurityContext.getPrincipal()).isNull();
                assertThat(EasySecurityContext.getAccessToken()).isNull();
                assertThat(EasyTraceIdContext.currentTraceId()).isNull();
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
        OperationObservation operationObservation = mock(OperationObservation.class);
        OperationObservation.ContextSnapshot observationContext = mock(OperationObservation.ContextSnapshot.class);
        OperationObservation.ContextBinding observationBinding = mock(OperationObservation.ContextBinding.class);
        when(operationObservation.captureContext()).thenReturn(observationContext);
        when(observationContext.bind()).thenReturn(observationBinding);
        return controller(agentTurnService, executor, operationObservation);
    }

    private static AssistantController controller(AgentTurnService agentTurnService,
                                                  org.springframework.core.task.TaskExecutor executor,
                                                  OperationObservation operationObservation) {
        TaskScheduler scheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> heartbeat = mock(ScheduledFuture.class);
        doReturn(heartbeat).when(scheduler)
                .scheduleAtFixedRate(any(Runnable.class), any(Duration.class));
        return new AssistantController(agentTurnService, executor, scheduler, new AssistantRuntimeProperties(),
                operationObservation);
    }
}
