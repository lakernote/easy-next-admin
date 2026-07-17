package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.assistant.context.AssistantRequestContext.AssistantUserContext;
import com.laker.admin.module.workflow.leave.dto.LeaveRequestView;
import com.laker.admin.module.workflow.leave.entity.BizLeaveRequest;
import com.laker.admin.module.workflow.leave.service.ILeaveRequestService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.Optional;

/**
 * 请假单 Tool 的默认查询实现。
 *
 * <p>这里只做精确标识查询；最终可见性继续交给 {@link ILeaveRequestService#detail(Long)}，
 * 不能因为 Tool 已通过权限过滤就绕开本人、参与人或管理员的数据范围判断。</p>
 */
@Service
public class DefaultLeaveRequestToolQueryService implements LeaveRequestToolQueryService {
    private final ILeaveRequestService leaveRequestService;

    public DefaultLeaveRequestToolQueryService(ILeaveRequestService leaveRequestService) {
        this.leaveRequestService = Objects.requireNonNull(leaveRequestService, "leaveRequestService must not be null");
    }

    @Override
    public Optional<LeaveRequestView> visibleLeaveRequest(AssistantUserContext user, String requestIdentifier) {
        if (user == null || user.userId() == null || !StringUtils.hasText(requestIdentifier)) {
            return Optional.empty();
        }
        String identifier = requestIdentifier.trim();
        Long recordId = parseRecordId(identifier);
        BizLeaveRequest request = leaveRequestService.lambdaQuery()
                .and(query -> {
                    if (recordId != null) {
                        query.eq(BizLeaveRequest::getId, recordId)
                                .or()
                                .eq(BizLeaveRequest::getRequestNo, identifier);
                    } else {
                        query.eq(BizLeaveRequest::getRequestNo, identifier);
                    }
                })
                .last("LIMIT 1")
                .one();
        if (request == null) {
            return Optional.empty();
        }
        // detail 是业务模块的权威可见性边界；无权查看时由其拒绝，ToolRunner 会统一脱敏失败。
        return Optional.of(leaveRequestService.detail(request.getId()));
    }

    private static Long parseRecordId(String identifier) {
        try {
            return Long.valueOf(identifier);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
