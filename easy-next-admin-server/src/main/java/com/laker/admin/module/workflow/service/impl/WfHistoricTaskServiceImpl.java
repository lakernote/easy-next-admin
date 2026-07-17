package com.laker.admin.module.workflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.workflow.entity.WfHistoricTask;
import com.laker.admin.module.workflow.mapper.WfHistoricTaskMapper;
import com.laker.admin.module.workflow.service.IWfHistoricTaskService;
import org.springframework.stereotype.Service;

@Service
public class WfHistoricTaskServiceImpl
        extends ServiceImpl<WfHistoricTaskMapper, WfHistoricTask>
        implements IWfHistoricTaskService {
}
