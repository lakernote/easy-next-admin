package com.laker.admin.module.workflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.workflow.entity.WfProcessDefinition;
import com.laker.admin.module.workflow.mapper.WfProcessDefinitionMapper;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionService;
import org.springframework.stereotype.Service;

@Service
public class WfProcessDefinitionServiceImpl
        extends ServiceImpl<WfProcessDefinitionMapper, WfProcessDefinition>
        implements IWfProcessDefinitionService {
}
