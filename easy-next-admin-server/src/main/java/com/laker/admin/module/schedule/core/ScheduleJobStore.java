package com.laker.admin.module.schedule.core;

public interface ScheduleJobStore {

    void saveDefinition(ScheduleJobDefinition jobDefinition);


    ScheduleJobDefinition findByJobCode(String jobCode);

}
