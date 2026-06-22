package com.laker.admin.module.schedule.core.impl;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.module.schedule.core.ScheduleJobDefinition;
import com.laker.admin.module.schedule.entity.ScheduleJobLog;
import com.laker.admin.module.schedule.service.IScheduleJobLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
        ScheduleJobLogCallback callback = new ScheduleJobLogCallback(logService, config);

        callback.start(jobDefinition());

        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).isEqualTo("existing-trace-id");
    }

    @Test
    void startShouldCreateTraceIdWhenMissing() {
        IScheduleJobLogService logService = mock(IScheduleJobLogService.class);
        when(logService.save(any(ScheduleJobLog.class))).thenReturn(true);
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setEnabled(false);
        ScheduleJobLogCallback callback = new ScheduleJobLogCallback(logService, config);

        callback.start(jobDefinition());

        assertThat(MDC.get(EasyNextAdminConstants.TRACE_ID)).hasSize(32);
    }

    private ScheduleJobDefinition jobDefinition() {
        return ScheduleJobDefinition.builder()
                .jobCode("sample_job")
                .jobName("测试任务")
                .build();
    }
}
