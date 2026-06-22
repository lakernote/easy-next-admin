package com.laker.admin.module.workflow.support;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.exception.EasyForbiddenException;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfHistoricCc;
import com.laker.admin.module.workflow.entity.WfHistoricTask;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.service.IWfCcService;
import com.laker.admin.module.workflow.service.IWfHistoricCcService;
import com.laker.admin.module.workflow.service.IWfHistoricTaskService;
import com.laker.admin.module.workflow.service.IWfTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class WorkflowVisibilityGuard {

    private final IWfTaskService taskService;
    private final IWfHistoricTaskService historicTaskService;
    private final IWfCcService ccService;
    private final IWfHistoricCcService historicCcService;

    public boolean canManageInstances() {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        return principal != null && principal.hasPermission(EasyPermissions.Workflow.INSTANCE_MANAGE);
    }

    public void assertInstanceVisible(WfProcessInstance instance) {
        if (canManageInstances()) {
            return;
        }
        Long userId = currentUserId();
        if (Objects.equals(instance.getInitiatorId(), userId)) {
            return;
        }
        long relatedTaskCount = taskService.lambdaQuery()
                .eq(WfTask::getInstanceId, instance.getId())
                .eq(WfTask::getAssigneeId, userId)
                .count();
        long relatedHistoricTaskCount = historicTaskService.lambdaQuery()
                .eq(WfHistoricTask::getInstanceId, instance.getId())
                .eq(WfHistoricTask::getAssigneeId, userId)
                .count();
        if (relatedTaskCount + relatedHistoricTaskCount > 0) {
            return;
        }
        long relatedCcCount = ccService.lambdaQuery()
                .eq(WfCc::getInstanceId, instance.getId())
                .eq(WfCc::getReceiverId, userId)
                .count();
        long relatedHistoricCcCount = historicCcService.lambdaQuery()
                .eq(WfHistoricCc::getInstanceId, instance.getId())
                .eq(WfHistoricCc::getReceiverId, userId)
                .count();
        if (relatedCcCount + relatedHistoricCcCount > 0) {
            return;
        }
        throw new EasyForbiddenException("无权访问该流程实例");
    }

    private Long currentUserId() {
        Long userId = EasySecurityContext.getUserId();
        if (userId == null) {
            throw new BusinessException("未获取到当前登录用户");
        }
        return userId;
    }
}
