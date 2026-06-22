package com.laker.admin.module.schedule.core.impl;

import com.laker.admin.module.schedule.core.ScheduleJobDefinition;
import com.laker.admin.module.schedule.entity.ScheduleJob;
import com.laker.admin.module.schedule.service.IScheduleJobService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcScheduleJobStoreTest {

    @Test
    void saveDefinitionIgnoresDuplicateInsertRace() {
        IScheduleJobService scheduleJobService = mock(IScheduleJobService.class);
        when(scheduleJobService.count(any())).thenReturn(0L);
        when(scheduleJobService.save(any(ScheduleJob.class))).thenThrow(new DuplicateKeyException("duplicate job_code"));
        JdbcScheduleJobStore store = new JdbcScheduleJobStore(scheduleJobService);

        ScheduleJobDefinition definition = ScheduleJobDefinition.builder()
                .jobCode("sample_job")
                .jobName("测试任务")
                .jobClassName("com.laker.admin.SampleJob")
                .cronExpression("0/1 * * * * ?")
                .build();

        assertThatCode(() -> store.saveDefinition(definition)).doesNotThrowAnyException();
    }
}
