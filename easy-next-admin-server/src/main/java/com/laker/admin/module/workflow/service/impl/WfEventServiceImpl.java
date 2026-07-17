package com.laker.admin.module.workflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.workflow.entity.WfEvent;
import com.laker.admin.module.workflow.mapper.WfEventMapper;
import com.laker.admin.module.workflow.service.IWfEventService;
import org.springframework.stereotype.Service;

@Service
public class WfEventServiceImpl extends ServiceImpl<WfEventMapper, WfEvent> implements IWfEventService {
}
