package com.laker.admin.module.workflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.mapper.WfProcessInstanceMapper;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import org.springframework.stereotype.Service;

@Service
public class WfProcessInstanceServiceImpl
        extends ServiceImpl<WfProcessInstanceMapper, WfProcessInstance>
        implements IWfProcessInstanceService {
}
