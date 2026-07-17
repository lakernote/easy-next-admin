package com.laker.admin.module.assistant.tool.workflow;

import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.service.IWfTaskService;
import com.laker.admin.module.workflow.support.WorkflowArchiveService;
import com.laker.admin.module.workflow.support.WorkflowTaskStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DefaultWorkflowTaskToolQueryService implements WorkflowTaskToolQueryService {
    private final IWfTaskService taskService;
    private final WorkflowArchiveService archiveService;

    public DefaultWorkflowTaskToolQueryService(IWfTaskService taskService,
                                               WorkflowArchiveService archiveService) {
        this.taskService = Objects.requireNonNull(taskService, "taskService must not be null");
        this.archiveService = Objects.requireNonNull(archiveService, "archiveService must not be null");
    }

    @Override
    public List<WorkflowTaskToolItem> myPendingTasks(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 10));
        List<WfTask> tasks = taskService.lambdaQuery()
                .eq(WfTask::getAssigneeId, userId)
                .eq(WfTask::getStatus, WorkflowTaskStatus.PENDING.code())
                .orderByDesc(WfTask::getStartedAt)
                .last("LIMIT " + safeLimit)
                .list();
        Map<Long, WfProcessInstance> instanceMap = archiveService.instanceMap(tasks.stream()
                .map(WfTask::getInstanceId)
                .toList());
        return tasks.stream()
                .map(task -> toItem(task, instanceMap.get(task.getInstanceId())))
                .toList();
    }

    private static WorkflowTaskToolItem toItem(WfTask task, WfProcessInstance instance) {
        return new WorkflowTaskToolItem(
                task.getId(),
                task.getInstanceId(),
                instance == null ? "流程 " + task.getInstanceId() : instance.getTitle(),
                task.getNodeName(),
                task.getStatus(),
                task.getStartedAt()
        );
    }
}
