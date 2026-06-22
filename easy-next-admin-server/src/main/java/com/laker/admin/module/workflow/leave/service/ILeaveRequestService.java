package com.laker.admin.module.workflow.leave.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.module.workflow.leave.dto.LeaveApplyRequest;
import com.laker.admin.module.workflow.leave.dto.LeaveRequestView;
import com.laker.admin.module.workflow.leave.entity.BizLeaveRequest;

public interface ILeaveRequestService extends IService<BizLeaveRequest> {
    LeaveRequestView apply(LeaveApplyRequest request);

    LeaveRequestView detail(Long id);
}
