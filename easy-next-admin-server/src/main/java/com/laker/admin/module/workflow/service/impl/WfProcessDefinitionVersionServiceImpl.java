package com.laker.admin.module.workflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.workflow.entity.WfProcessDefinitionVersion;
import com.laker.admin.module.workflow.mapper.WfProcessDefinitionVersionMapper;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionVersionService;
import org.springframework.stereotype.Service;

@Service
public class WfProcessDefinitionVersionServiceImpl
        extends ServiceImpl<WfProcessDefinitionVersionMapper, WfProcessDefinitionVersion>
        implements IWfProcessDefinitionVersionService {
}
