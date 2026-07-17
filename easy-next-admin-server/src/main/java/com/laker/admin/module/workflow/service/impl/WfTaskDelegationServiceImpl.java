package com.laker.admin.module.workflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.workflow.entity.WfTaskDelegation;
import com.laker.admin.module.workflow.mapper.WfTaskDelegationMapper;
import com.laker.admin.module.workflow.service.IWfTaskDelegationService;
import org.springframework.stereotype.Service;

@Service
public class WfTaskDelegationServiceImpl extends ServiceImpl<WfTaskDelegationMapper, WfTaskDelegation> implements IWfTaskDelegationService {
}
