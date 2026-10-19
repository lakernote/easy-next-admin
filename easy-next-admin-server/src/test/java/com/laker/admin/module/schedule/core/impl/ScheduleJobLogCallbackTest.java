package com.laker.admin.module.schedule.core.impl;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import io.github.lakernote.observability.metrics.BusinessMetrics;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import com.laker.admin.module.schedule.core.ScheduleJobDefinition;
import com.laker.admin.module.schedule.entity.ScheduleJobLog;
import com.laker.admin.module.schedule.service.IScheduleJobLogService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleJobLogCallbackTest {

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void startShouldPreserveExistingTraceId() {
        MDC.put(EasyNextAdminConstants.TRACE_ID, "existing-trace-id");
        IScheduleJobLogService logService = mock(IScheduleJobLogService.class);
        when(logService.save(any(ScheduleJobLog.class))).thenReturn(true);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setEnabled(false);
        ScheduleJobLogCallback callback = new ScheduleJobLogCallback(logService, config, businessMetrics());

        callback.start(jobDefinition());

        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isEqualTo("existing-trace-id");
    }

    @Test
    void startShouldCreateTraceIdWhenMissing() {
        IScheduleJobLogService logService = mock(IScheduleJobLogService.class);
        when(logService.save(any(ScheduleJobLog.class))).thenReturn(true);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setEnabled(false);
        ScheduleJobLogCallback callback = new ScheduleJobLogCallback(logService, config, businessMetrics());

        callback.start(jobDefinition());

        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).hasSize(32);
    }

    @Test
    void outOfMemoryErrorShouldPersistFailedJobAndCanonicalIncident() {
        MDC.put(EasyNextAdminConstants.TRACE_ID, "trace-job-oom");
        IScheduleJobLogService logService = mock(IScheduleJobLogService.class);
        when(logService.save(any(ScheduleJobLog.class))).thenReturn(true);
        when(logService.updateById(any(ScheduleJobLog.class))).thenReturn(true);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setEnabled(true);
        config.getTrace().setScheduleSlowThresholdMs(60_000);
        BusinessMetrics metrics = mock(BusinessMetrics.class);
        RecentTraceStore traceStore = mock(RecentTraceStore.class);
        OperationObservation observation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        when(observation.start("JOB", "sample_job")).thenReturn(scope);
        AtomicBoolean resourceFrozen = new AtomicBoolean();
        doAnswer(invocation -> {
            resourceFrozen.set(true);
            return null;
        }).when(scope).finish(eq("failure"), any(Throwable.class));
        ScheduleJobLogCallback callback = new ScheduleJobLogCallback(
                logService, config, metrics, observation, traceStore);
        OutOfMemoryError failure = new BoundaryCheckedOutOfMemoryError(
                "downloaded file exhausted heap", resourceFrozen);

        callback.start(jobDefinition());
        callback.exception(jobDefinition(), failure);
        callback.end(jobDefinition());

        ArgumentCaptor<ScheduleJobLog> logCaptor = ArgumentCaptor.forClass(ScheduleJobLog.class);
        verify(logService).updateById(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(2);
        assertThat(logCaptor.getValue().getErrorMessage()).isEqualTo("downloaded file exhausted heap");
        assertThat(logCaptor.getValue().getEndTime()).isNotNull();

        verify(traceStore).recordIncident(eq("JOB"), eq("trace-job-oom"), eq("ScheduleJob"),
                eq("sample_job"), eq(500), contains("ScheduleJob"), anyLong(), eq(false), eq(true), any(),
                eq(failure.getClass().getName()), eq("downloaded file exhausted heap"),
                contains("OutOfMemoryError"), isNull(), isNull(), isNull(), isNull());
        verify(metrics).recordScheduleJob(eq("sample_job"), eq(false), anyInt());
        verify(metrics, never()).recordScheduleJob(anyString(), eq(true), any());
    }

    private static final class BoundaryCheckedOutOfMemoryError extends OutOfMemoryError {
        private final AtomicBoolean resourceFrozen;

        private BoundaryCheckedOutOfMemoryError(String message, AtomicBoolean resourceFrozen) {
            super(message);
            this.resourceFrozen = resourceFrozen;
        }

        @Override
        public String getMessage() {
            assertThat(resourceFrozen)
                    .as("job resources must be frozen before failure evidence/logging")
                    .isTrue();
            return super.getMessage();
        }
    }

    private ScheduleJobDefinition jobDefinition() {
        return ScheduleJobDefinition.builder()
                .jobCode("sample_job")
                .jobName("测试任务")
                .build();
    }

    private BusinessMetrics businessMetrics() {
        return new BusinessMetrics(new SimpleMeterRegistry());
    }
}
