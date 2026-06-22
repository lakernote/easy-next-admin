package com.laker.admin.module.workflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.workflow.dto.WorkflowCcSummary;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.mapper.WfCcMapper;
import com.laker.admin.module.workflow.service.IWfCcService;
import org.springframework.stereotype.Service;

@Service
public class WfCcServiceImpl extends ServiceImpl<WfCcMapper, WfCc> implements IWfCcService {

    @Override
    public WorkflowCcSummary countSummaryByReceiverId(Long receiverId) {
        return baseMapper.selectSummaryByReceiverId(receiverId);
    }
}
