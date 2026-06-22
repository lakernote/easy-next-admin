package com.laker.admin.module.schedule.core.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.infrastructure.security.datascope.context.EasyDataScopeContext;
import com.laker.admin.module.enums.JobStateEnum;
import com.laker.admin.module.schedule.core.ScheduleJobStore;
import com.laker.admin.module.schedule.core.ScheduleJobDefinition;
import com.laker.admin.module.schedule.entity.ScheduleJob;
import com.laker.admin.module.schedule.service.IScheduleJobService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@ConditionalOnProperty(prefix = "easy.features", name = "scheduler", havingValue = "true", matchIfMissing = true)
@Slf4j
public class JdbcScheduleJobStore implements ScheduleJobStore {
    private final IScheduleJobService scheduleJobService;

    public JdbcScheduleJobStore(IScheduleJobService scheduleJobService) {
        this.scheduleJobService = scheduleJobService;
    }

    @Override
    public void saveDefinition(ScheduleJobDefinition jobDefinition) {
        ScheduleJob job = new ScheduleJob();
        job.setEnable(true);

        BeanUtils.copyProperties(jobDefinition, job);
        long count = EasyDataScopeContext.ignore(() ->
                scheduleJobService.count(Wrappers.<ScheduleJob>lambdaQuery().eq(ScheduleJob::getJobCode, jobDefinition.getJobCode())));
        if (count == 0) {
            job.setJobState(JobStateEnum.START);
            try {
                EasyDataScopeContext.ignore(() -> scheduleJobService.save(job));
            } catch (DuplicateKeyException e) {
                log.info("Schedule job definition already exists, jobCode={}", jobDefinition.getJobCode());
            }
        }
    }


    @Override
    public ScheduleJobDefinition findByJobCode(String jobCode) {
        ScheduleJob job = EasyDataScopeContext.ignore(() ->
                scheduleJobService.getOne(Wrappers.<ScheduleJob>lambdaQuery().eq(ScheduleJob::getJobCode, jobCode)));
        if (job == null) {
            throw new IllegalStateException("未找到任务定义：" + jobCode);
        }
        ScheduleJobDefinition jobDefinition = new ScheduleJobDefinition();
        BeanUtils.copyProperties(job, jobDefinition);
        return jobDefinition;
    }


}
