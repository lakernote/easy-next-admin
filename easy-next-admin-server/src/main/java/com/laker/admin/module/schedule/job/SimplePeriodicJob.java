package com.laker.admin.module.schedule.job;

import com.laker.admin.module.schedule.core.EasyJobHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

//@EasyJob(jobCode = "simplePeriodicJob", jobName = "简单周期性任务", fixedRate = 10)
@Slf4j
public class SimplePeriodicJob implements EasyJobHandler {
    @Override
    public void execute(Map map) {
        log.info("简单周期性任务执行");
    }
}
