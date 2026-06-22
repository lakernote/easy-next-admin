package com.laker.admin.module.workflow.support;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class WorkflowInstanceStateGuard {
    private final IWfProcessInstanceService instanceService;

    public void updateOrThrow(WfProcessInstance instance) {
        if (!instanceService.updateById(instance)) {
            throw new BusinessException("流程状态已变化，请刷新后重试");
        }
    }

    public void finish(WfProcessInstance instance, WorkflowInstanceStatus status, String currentNodeKey, LocalDateTime now) {
        instance.setStatus(status.code());
        instance.setCurrentNodeKey(currentNodeKey);
        instance.setEndedAt(now);
        instance.setUpdatedAt(now);
        updateOrThrow(instance);
    }
}
