package com.laker.admin.module.schedule.core;

import java.util.List;

public interface ScheduleJobStore {

    void saveDefinition(ScheduleJobDefinition jobDefinition);


    ScheduleJobDefinition findByJobCode(String jobCode);

    List<ScheduleJobDefinition> findAll();

}
