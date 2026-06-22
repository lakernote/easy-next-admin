package com.laker.admin.module.schedule.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.persistence.mybatis.EasyPageSupport;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.schedule.core.ScheduleJobManager;
import com.laker.admin.module.schedule.dto.ScheduleJobRequest;
import com.laker.admin.module.schedule.dto.ScheduleJobView;
import com.laker.admin.module.schedule.entity.ScheduleJob;
import com.laker.admin.module.schedule.service.IScheduleJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schedule/jobs")
@ConditionalOnProperty(prefix = "easy.features", name = "scheduler", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ScheduleJobController {
    private final ScheduleJobManager scheduleJobManager;
    private final IScheduleJobService scheduleJobService;

    public ScheduleJobController(ScheduleJobManager scheduleJobManager, IScheduleJobService scheduleJobService) {
        this.scheduleJobManager = scheduleJobManager;
        this.scheduleJobService = scheduleJobService;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.Schedule.JOB_LIST)
    public PageResponse<ScheduleJobView> pageAll(@RequestParam(required = false, defaultValue = "1") long page,
                                                 @RequestParam(required = false, defaultValue = "10") long limit) {
        LambdaQueryWrapper<ScheduleJob> queryWrapper = new QueryWrapper<ScheduleJob>().lambda();
        return EasyPageSupport.response(
                scheduleJobService.page(EasyPageSupport.page(page, limit), queryWrapper),
                ScheduleJobView::from);
    }

    @PostMapping
    @EasyPermission(EasyPermissions.Schedule.JOB_EDIT)
    @EasyAudit(module = "动态定时任务", action = "保存定时任务", dataChange = true, bizType = "SCHEDULE_JOB", changeType = "SAVE")
    public Response<Void> update(@RequestBody ScheduleJobRequest request) {
        scheduleJobService.saveOrUpdate(request.toEntity());
        return Response.ok();
    }

    @GetMapping("/{id}")
    @EasyPermission(EasyPermissions.Schedule.JOB_LIST)
    public Response<ScheduleJobView> get(@PathVariable Long id) {
        return Response.ok(ScheduleJobView.from(scheduleJobService.getById(id)));
    }

    @PutMapping("/{jobCode}/start")
    @EasyPermission(EasyPermissions.Schedule.JOB_EDIT)
    @EasyAudit(module = "动态定时任务", action = "启动定时任务", dataChange = true, bizType = "SCHEDULE_JOB", bizId = "#jobCode", changeType = "START")
    public Response<Void> start(@PathVariable String jobCode) {
        scheduleJobManager.startJob(jobCode);
        return Response.ok();
    }

    @PutMapping("/{jobCode}/stop")
    @EasyPermission(EasyPermissions.Schedule.JOB_EDIT)
    @EasyAudit(module = "动态定时任务", action = "停止定时任务", dataChange = true, bizType = "SCHEDULE_JOB", bizId = "#jobCode", changeType = "STOP")
    public Response<Void> stop(@PathVariable String jobCode) {
        scheduleJobManager.stopJob(jobCode);
        return Response.ok();
    }

}
