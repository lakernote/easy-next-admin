package com.laker.admin.module.workflow.support;

import com.laker.admin.module.workflow.entity.WfEvent;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.event.WfProcessInstanceStatusChangedEvent;
import com.laker.admin.module.workflow.service.IWfEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.Collection;

@Component
@RequiredArgsConstructor
public class WorkflowEventRecorder {
    private final IWfEventService eventService;
    private final ApplicationEventPublisher eventPublisher;

    public void record(Long instanceId,
                       Long taskId,
                       Long operatorId,
                       WorkflowEventAction action,
                       String fromNodeKey,
                       String toNodeKey,
                       Long targetUserId,
                       String comment,
                       LocalDateTime now) {
        WfEvent event = new WfEvent();
        event.setInstanceId(instanceId);
        event.setTaskId(taskId);
        event.setOperatorId(operatorId);
        event.setAction(action.code());
        event.setFromNodeKey(fromNodeKey);
        event.setToNodeKey(toNodeKey);
        event.setTargetUserId(targetUserId);
        event.setComment(comment);
        event.setCreatedAt(now);
        eventService.save(event);
    }

    public void recordCc(Long instanceId,
                         Long taskId,
                         Long operatorId,
                         String fromNodeKey,
                         String toNodeKey,
                         Collection<Long> receiverIds,
                         String comment,
                         LocalDateTime now) {
        if (CollectionUtils.isEmpty(receiverIds)) {
            return;
        }
        for (Long receiverId : receiverIds) {
            record(instanceId, taskId, operatorId, WorkflowEventAction.CC, fromNodeKey, toNodeKey, receiverId, comment, now);
        }
    }

    public void publishStatusChanged(WfProcessInstance instance, WorkflowInstanceStatus status) {
        eventPublisher.publishEvent(new WfProcessInstanceStatusChangedEvent(
                instance.getId(),
                instance.getBusinessType(),
                instance.getBusinessId(),
                status.code()
        ));
    }
}
