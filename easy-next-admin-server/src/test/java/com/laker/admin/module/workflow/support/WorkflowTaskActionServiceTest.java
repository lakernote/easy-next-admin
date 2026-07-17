package com.laker.admin.module.workflow.support;

import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowTaskActionServiceTest {

    private final WorkflowTaskDispatcher taskDispatcher = mock(WorkflowTaskDispatcher.class);
    private final WorkflowEventRecorder eventRecorder = mock(WorkflowEventRecorder.class);
    private final WorkflowInstanceStateGuard instanceStateGuard = mock(WorkflowInstanceStateGuard.class);
    private final WorkflowTaskActionService taskActionService =
            new WorkflowTaskActionService(taskDispatcher, eventRecorder, instanceStateGuard);

    @Test
    void shouldReassignTaskAndRecordDelegations() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 16, 0);
        WfProcessInstance instance = instance();
        WfTask task = task(10L, 2001L);
        WfTask reassignedTask = task(11L, 3001L);
        when(taskDispatcher.createPendingTask(instance, "approve", "审批", 3001L, 1001L, now))
                .thenReturn(reassignedTask);

        taskActionService.reassignTask(
                instance,
                task,
                3001L,
                1001L,
                "转给业务负责人",
                WorkflowEventAction.TRANSFER,
                WorkflowTaskDelegationType.TRANSFER,
                WorkflowTaskStatus.TRANSFERRED,
                now);

        verify(taskDispatcher).transitionPendingTask(task, WorkflowTaskStatus.TRANSFERRED, "转给业务负责人", 1001L, now);
        verify(taskDispatcher).createPendingTask(instance, "approve", "审批", 3001L, 1001L, now);
        verify(taskDispatcher).createTaskDelegation(10L, 1001L, 3001L, WorkflowTaskDelegationType.TRANSFER, now);
        verify(taskDispatcher).createTaskDelegation(11L, 1001L, 3001L, WorkflowTaskDelegationType.TRANSFER, now);
        verify(instanceStateGuard).updateOrThrow(instance);
        verify(eventRecorder).record(99L, 10L, 1001L, WorkflowEventAction.TRANSFER,
                "approve", "approve", 3001L, "转给业务负责人", now);
    }

    @Test
    void shouldReturnTaskToSelectedNode() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 16, 5);
        WfProcessInstance instance = instance();
        WfTask task = task(10L, 2001L);
        WorkflowGraph.NodeInfo returnNode = new WorkflowGraph.NodeInfo(
                "draft", "发起调整", WorkflowNodeShape.RECT, WorkflowNodeKind.APPROVAL, null);

        taskActionService.returnTask(instance, task, returnNode, 3001L, 1001L, "资料不完整", now);

        verify(taskDispatcher).transitionPendingTask(task, WorkflowTaskStatus.CANCELED, "资料不完整", 1001L, now);
        verify(taskDispatcher).createPendingTask(instance, "draft", "发起调整", 3001L, 1001L, now);
        verify(instanceStateGuard).updateOrThrow(instance);
        verify(eventRecorder).record(99L, 10L, 1001L, WorkflowEventAction.RETURN,
                "approve", "draft", 3001L, "资料不完整", now);
    }

    @Test
    void shouldAddSignTasksAndRecordEachTarget() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 16, 10);
        WfProcessInstance instance = instance();
        WfTask task = task(10L, 2001L);
        WfTask firstAddSignTask = task(21L, 3001L);
        WfTask secondAddSignTask = task(22L, 3002L);
        when(taskDispatcher.createPendingTask(instance, "approve", "审批", 3001L, 1001L, now))
                .thenReturn(firstAddSignTask);
        when(taskDispatcher.createPendingTask(instance, "approve", "审批", 3002L, 1001L, now))
                .thenReturn(secondAddSignTask);

        taskActionService.addSignTasks(instance, task, List.of(3001L, 3002L), 1001L, "增加专业审批", now);

        verify(taskDispatcher).createTaskDelegation(21L, 1001L, 3001L, WorkflowTaskDelegationType.ADD_SIGN, now);
        verify(taskDispatcher).createTaskDelegation(22L, 1001L, 3002L, WorkflowTaskDelegationType.ADD_SIGN, now);
        verify(eventRecorder).record(99L, 10L, 1001L, WorkflowEventAction.ADD_SIGN,
                "approve", "approve", 3001L, "增加专业审批", now);
        verify(eventRecorder).record(99L, 10L, 1001L, WorkflowEventAction.ADD_SIGN,
                "approve", "approve", 3002L, "增加专业审批", now);
        verify(instanceStateGuard).updateOrThrow(instance);
    }

    @Test
    void shouldRemoveAddSignTaskAndRecordEvent() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 16, 15);
        WfProcessInstance instance = instance();
        WfTask task = task(10L, 2001L);
        WfTask addSignTask = task(21L, 3001L);

        taskActionService.removeSignTask(instance, task, addSignTask, 3001L, 1001L, "不需要法务审批", now);

        verify(taskDispatcher).transitionPendingTask(addSignTask, WorkflowTaskStatus.CANCELED, "不需要法务审批", 1001L, now);
        verify(eventRecorder).record(99L, 10L, 1001L, WorkflowEventAction.REMOVE_SIGN,
                "approve", "approve", 3001L, "不需要法务审批", now);
    }

    private WfProcessInstance instance() {
        WfProcessInstance instance = new WfProcessInstance();
        instance.setId(99L);
        instance.setCurrentNodeKey("approve");
        return instance;
    }

    private WfTask task(Long id, Long assigneeId) {
        WfTask task = new WfTask();
        task.setId(id);
        task.setInstanceId(99L);
        task.setNodeKey("approve");
        task.setNodeName("审批");
        task.setAssigneeId(assigneeId);
        task.setStatus(WorkflowTaskStatus.PENDING.code());
        return task;
    }
}
