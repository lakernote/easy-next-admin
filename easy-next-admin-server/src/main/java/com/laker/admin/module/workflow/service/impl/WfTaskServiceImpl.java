package com.laker.admin.module.workflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.mapper.WfTaskMapper;
import com.laker.admin.module.workflow.service.IWfTaskService;
import org.springframework.stereotype.Service;

@Service
public class WfTaskServiceImpl extends ServiceImpl<WfTaskMapper, WfTask> implements IWfTaskService {
}
