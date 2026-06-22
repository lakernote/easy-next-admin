package com.laker.admin.module.schedule.job;

import com.laker.admin.module.schedule.core.EasyJobHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

//@EasyJob(jobCode = "parentJob", jobName = "父任务", fixedDelay = 30)
@Slf4j
public class ParentJob implements EasyJobHandler {

    @Override
    public void execute(Map map) throws Exception {
        log.info("父任务执行");
    }
}