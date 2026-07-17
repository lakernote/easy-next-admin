package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.context.AssistantRequestContext.AssistantUserContext;
import com.laker.admin.module.workflow.leave.dto.LeaveRequestView;

import java.util.Optional;

/** 请假单 Tool 的可替换查询边界，负责复用业务服务的数据可见性。 */
public interface LeaveRequestToolQueryService {
    Optional<LeaveRequestView> visibleLeaveRequest(AssistantUserContext user, String requestIdentifier);
}
