package com.laker.admin.module.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.module.workflow.dto.WorkflowCcSummary;
import com.laker.admin.module.workflow.entity.WfCc;

public interface IWfCcService extends IService<WfCc> {

    WorkflowCcSummary countSummaryByReceiverId(Long receiverId);
}
