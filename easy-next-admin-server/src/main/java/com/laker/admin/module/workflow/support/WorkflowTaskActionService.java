package com.laker.admin.module.workflow.support;

import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WorkflowTaskActionService {
    private final WorkflowTaskDispatcher taskDispatcher;
    private final WorkflowEventRecorder eventRecorder;
    private final WorkflowInstanceStateGuard instanceStateGuard;

    public void reassignTask(WfProcessInstance instance,
                             WfTask task,
                             Long targetUserId,
                             Long operatorId,
                             String comment,
                             WorkflowEventAction eventAction,
                             WorkflowTaskDelegationType delegationType,
                             WorkflowTaskStatus sourceTaskStatus,
                             LocalDateTime now) {
        taskDispatcher.transitionPendingTask(task, sourceTaskStatus, comment, operatorId, now);

        WfTask reassignedTask = taskDispatcher.createPendingTask(
                instance, task.getNodeKey(), task.getNodeName(), targetUserId, operatorId, now);
        taskDispatcher.createTaskDelegation(task.getId(), operatorId, targetUserId, delegationType, now);
        taskDispatcher.createTaskDelegation(reassignedTask.getId(), operatorId, targetUserId, delegationType, now);
        updateInstanceCurrentNode(instance, task.getNodeKey(), operatorId, now, false);
        eventRecorder.record(instance.getId(), task.getId(), operatorId, eventAction,
                task.getNodeKey(), task.getNodeKey(), targetUserId, comment, now);
    }

    public void returnTask(WfProcessInstance instance,
                           WfTask task,
                           WorkflowGraph.NodeInfo returnNode,
                           Long assigneeId,
                           Long operatorId,
                           String comment,
                           LocalDateTime now) {
        taskDispatcher.transitionPendingTask(task, WorkflowTaskStatus.CANCELED, comment, operatorId, now);
        taskDispatcher.createPendingTask(instance, returnNode.key(), returnNode.name(), assigneeId, operatorId, now);
        updateInstanceCurrentNode(instance, returnNode.key(), operatorId, now, true);
        eventRecorder.record(instance.getId(), task.getId(), operatorId, WorkflowEventAction.RETURN,
                task.getNodeKey(), returnNode.key(), assigneeId, comment, now);
    }

    public void addSignTasks(WfProcessInstance instance,
                             WfTask task,
                             List<Long> targetUserIds,
                             Long operatorId,
                             String comment,
                             LocalDateTime now) {
        for (Long targetUserId : targetUserIds) {
            WfTask addSignTask = taskDispatcher.createPendingTask(
                    instance, task.getNodeKey(), task.getNodeName(), targetUserId, operatorId, now);
            taskDispatcher.createTaskDelegation(addSignTask.getId(), operatorId, targetUserId, WorkflowTaskDelegationType.ADD_SIGN, now);
            eventRecorder.record(instance.getId(), task.getId(), operatorId, WorkflowEventAction.ADD_SIGN,
                    task.getNodeKey(), task.getNodeKey(), targetUserId, comment, now);
        }
        updateInstanceCurrentNode(instance, task.getNodeKey(), operatorId, now, true);
    }

    public void removeSignTask(WfProcessInstance instance,
                               WfTask task,
                               WfTask addSignTask,
                               Long targetUserId,
                               Long operatorId,
                               String comment,
                               LocalDateTime now) {
        taskDispatcher.transitionPendingTask(addSignTask, WorkflowTaskStatus.CANCELED, comment, operatorId, now);
        eventRecorder.record(instance.getId(), task.getId(), operatorId, WorkflowEventAction.REMOVE_SIGN,
                task.getNodeKey(), task.getNodeKey(), targetUserId, comment, now);
    }

    private void updateInstanceCurrentNode(WfProcessInstance instance,
                                           String nodeKey,
                                           Long operatorId,
                                           LocalDateTime now,
                                           boolean updateBy) {
        instance.setCurrentNodeKey(nodeKey);
        if (updateBy) {
            instance.setUpdatedBy(operatorId);
        }
        instance.setUpdatedAt(now);
        instanceStateGuard.updateOrThrow(instance);
    }
}
