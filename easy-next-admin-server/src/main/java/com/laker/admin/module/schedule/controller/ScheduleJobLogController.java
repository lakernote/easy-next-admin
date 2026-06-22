package com.laker.admin.module.schedule.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.schedule.dto.ScheduleJobLogView;
import com.laker.admin.module.schedule.entity.ScheduleJobLog;
import com.laker.admin.module.schedule.service.IScheduleJobLogService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author easynext
 * @since 2021-08-18
 */
@RestController
@RequestMapping("/api/schedule/job-logs")
@ConditionalOnProperty(prefix = "easy.features", name = "scheduler", havingValue = "true", matchIfMissing = true)
public class ScheduleJobLogController {
    private final IScheduleJobLogService scheduleJobLogService;

    public ScheduleJobLogController(IScheduleJobLogService scheduleJobLogService) {
        this.scheduleJobLogService = scheduleJobLogService;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.Schedule.JOB_LIST)
    public PageResponse<ScheduleJobLogView> pageAll(@RequestParam(required = false, defaultValue = "1") long page,
                                                    @RequestParam(required = false, defaultValue = "10") long limit,
                                                    String jobCode) {
        Page<ScheduleJobLog> roadPage = new Page<>(page, limit);
        LambdaQueryWrapper<ScheduleJobLog> queryWrapper = new QueryWrapper<ScheduleJobLog>().lambda();
        if (StringUtils.hasText(jobCode)) {
            queryWrapper.eq(ScheduleJobLog::getJobCode, jobCode);
        }
        queryWrapper.orderByDesc(ScheduleJobLog::getStartTime);
        Page<ScheduleJobLog> pageList = scheduleJobLogService.page(roadPage, queryWrapper);
        return PageResponse.ok(ScheduleJobLogView.fromList(pageList.getRecords()), pageList.getTotal());
    }

}
