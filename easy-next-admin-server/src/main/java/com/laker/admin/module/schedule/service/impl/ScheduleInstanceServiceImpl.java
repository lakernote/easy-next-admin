package com.laker.admin.module.schedule.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.schedule.entity.ScheduleInstance;
import com.laker.admin.module.schedule.mapper.ScheduleInstanceMapper;
import com.laker.admin.module.schedule.service.IScheduleInstanceService;
import org.springframework.stereotype.Service;

@Service
public class ScheduleInstanceServiceImpl extends ServiceImpl<ScheduleInstanceMapper, ScheduleInstance>
        implements IScheduleInstanceService {
}
