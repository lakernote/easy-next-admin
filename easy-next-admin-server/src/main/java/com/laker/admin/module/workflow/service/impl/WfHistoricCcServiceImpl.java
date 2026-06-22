package com.laker.admin.module.workflow.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.workflow.dto.WorkflowCcSummary;
import com.laker.admin.module.workflow.entity.WfHistoricCc;
import com.laker.admin.module.workflow.mapper.WfHistoricCcMapper;
import com.laker.admin.module.workflow.service.IWfHistoricCcService;
import org.springframework.stereotype.Service;

@Service
public class WfHistoricCcServiceImpl
        extends ServiceImpl<WfHistoricCcMapper, WfHistoricCc>
        implements IWfHistoricCcService {

    @Override
    public WorkflowCcSummary countSummaryByReceiverId(Long receiverId) {
        return baseMapper.selectSummaryByReceiverId(receiverId);
    }
}
