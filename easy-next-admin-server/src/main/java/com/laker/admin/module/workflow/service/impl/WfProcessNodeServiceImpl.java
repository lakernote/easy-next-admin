package com.laker.admin.module.workflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.workflow.entity.WfProcessNode;
import com.laker.admin.module.workflow.mapper.WfProcessNodeMapper;
import com.laker.admin.module.workflow.service.IWfProcessNodeService;
import org.springframework.stereotype.Service;

@Service
public class WfProcessNodeServiceImpl
        extends ServiceImpl<WfProcessNodeMapper, WfProcessNode>
        implements IWfProcessNodeService {
}
