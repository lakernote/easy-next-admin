package com.laker.admin.module.workflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.workflow.entity.WfHistoricProcessInstance;
import com.laker.admin.module.workflow.mapper.WfHistoricProcessInstanceMapper;
import com.laker.admin.module.workflow.service.IWfHistoricProcessInstanceService;
import org.springframework.stereotype.Service;

@Service
public class WfHistoricProcessInstanceServiceImpl
        extends ServiceImpl<WfHistoricProcessInstanceMapper, WfHistoricProcessInstance>
        implements IWfHistoricProcessInstanceService {
}
