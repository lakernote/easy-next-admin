package com.laker.admin.module.schedule.core.impl;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.infrastructure.observability.metrics.EasyAdminMetrics;
import com.laker.admin.module.schedule.core.ScheduleJobDefinition;
import com.laker.admin.module.schedule.entity.ScheduleJobLog;
import com.laker.admin.module.schedule.service.IScheduleJobLogService;
import io.github.lakernote.meerkat.trace.TraceIdContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScheduleJobLogCallbackTest {
    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void startPreservesExistingTraceIdAndRecordsJobLog() {
        MDC.put(TraceIdContext.MDC_KEY, "existing-trace-id");
        IScheduleJobLogService logService = mock(IScheduleJobLogService.class);
        when(logService.save(any(ScheduleJobLog.class))).thenReturn(true);
        EasyAdminMetrics metrics = new EasyAdminMetrics(new SimpleMeterRegistry());
        ScheduleJobLogCallback callback = callback(logService, metrics);

        callback.start(jobDefinition());
        callback.end(jobDefinition());

        ArgumentCaptor<ScheduleJobLog> saved = ArgumentCaptor.forClass(ScheduleJobLog.class);
        verify(logService).save(saved.capture());
        assertThat(saved.getValue().getTraceId()).isEqualTo("existing-trace-id");
        assertThat(saved.getValue().getEndTime()).isNotNull();
        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isEqualTo("existing-trace-id");
    }

    @Test
    void failedJobPersistsBusinessFailureAndRecordsApplicationMetric() {
        IScheduleJobLogService logService = mock(IScheduleJobLogService.class);
        when(logService.save(any(ScheduleJobLog.class))).thenReturn(true);
        when(logService.updateById(any(ScheduleJobLog.class))).thenReturn(true);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        ScheduleJobLogCallback callback = callback(logService, new EasyAdminMetrics(meterRegistry));
        IllegalStateException failure = new IllegalStateException("job failed");

        callback.start(jobDefinition());
        callback.exception(jobDefinition(), failure);
        callback.end(jobDefinition());

        ArgumentCaptor<ScheduleJobLog> updated = ArgumentCaptor.forClass(ScheduleJobLog.class);
        verify(logService).updateById(updated.capture());
        assertThat(updated.getValue().getStatus()).isEqualTo(2);
        assertThat(updated.getValue().getErrorMessage()).isEqualTo("job failed");
        assertThat(meterRegistry.get("easy.schedule.jobs").timer().count()).isEqualTo(1);
    }

    private static ScheduleJobLogCallback callback(IScheduleJobLogService logService, EasyAdminMetrics metrics) {
        return new ScheduleJobLogCallback(logService, metrics, ObservationRegistry.create());
    }

    private static ScheduleJobDefinition jobDefinition() {
        return ScheduleJobDefinition.builder().jobCode("sample_job").jobName("测试任务").build();
    }
}
