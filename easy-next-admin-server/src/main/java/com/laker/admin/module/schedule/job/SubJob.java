package com.laker.admin.module.schedule.job;

import com.laker.admin.module.schedule.core.EasyJobHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

//@EasyJob(jobCode = "subJob", jobName = "子任务", parentJobCode = "parentJob")
@Slf4j
public class SubJob implements EasyJobHandler {

    @Override
    public void execute(Map map) throws Exception {
        log.info("子任务执行");
    }
}