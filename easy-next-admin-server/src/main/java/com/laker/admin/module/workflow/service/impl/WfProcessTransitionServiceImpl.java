package com.laker.admin.module.workflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.workflow.entity.WfProcessTransition;
import com.laker.admin.module.workflow.mapper.WfProcessTransitionMapper;
import com.laker.admin.module.workflow.service.IWfProcessTransitionService;
import org.springframework.stereotype.Service;

@Service
public class WfProcessTransitionServiceImpl
        extends ServiceImpl<WfProcessTransitionMapper, WfProcessTransition>
        implements IWfProcessTransitionService {
}
