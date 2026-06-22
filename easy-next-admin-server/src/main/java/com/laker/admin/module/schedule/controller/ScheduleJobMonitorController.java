package com.laker.admin.module.schedule.controller;

import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.schedule.core.ScheduleJobManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/schedule/jobs/runtime")
@ConditionalOnProperty(prefix = "easy.features", name = "scheduler", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ScheduleJobMonitorController {

    private final ScheduleJobManager scheduleJobManager;

    public ScheduleJobMonitorController(ScheduleJobManager scheduleJobManager) {
        this.scheduleJobManager = scheduleJobManager;
    }

    @GetMapping("/jvm-task-list")
    @EasyPermission(EasyPermissions.Schedule.JOB_LIST)
    public Response<List<String>> listRunningJobs() {
        return Response.ok(scheduleJobManager.jvmJobList());
    }


}
