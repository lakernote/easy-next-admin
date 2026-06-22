package com.laker.admin.module.schedule.core;

public interface ScheduleJobCallback {
    void start(ScheduleJobDefinition jobDefinition);

    void exception(ScheduleJobDefinition jobDefinition, Exception e);

    void end(ScheduleJobDefinition jobDefinition);
}
