package com.laker.admin.module.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.module.workflow.dto.WorkflowCcSummary;
import com.laker.admin.module.workflow.entity.WfHistoricCc;

public interface IWfHistoricCcService extends IService<WfHistoricCc> {

    WorkflowCcSummary countSummaryByReceiverId(Long receiverId);
}
