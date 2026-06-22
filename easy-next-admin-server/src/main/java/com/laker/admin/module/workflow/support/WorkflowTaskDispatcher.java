package com.laker.admin.module.workflow.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.security.datascope.context.EasyDataScopeContext;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.entity.WfTaskDelegation;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysUserService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfTaskDelegationService;
import com.laker.admin.module.workflow.service.IWfTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class WorkflowTaskDispatcher {
    private final IWfProcessInstanceService instanceService;
    private final IWfTaskService taskService;
    private final IWfTaskDelegationService taskDelegationService;
    private final WorkflowAssigneeResolver assigneeResolver;
    private final WorkflowArchiveService archiveService;
    private final ISysUserService userService;

    public void createPendingTasksForNode(WfProcessInstance instance,
                                          WorkflowGraph.NodeInfo node,
                                          Long preferredAssigneeId,
                                          Long initiatorId,
                                          Long operatorId,
        LocalDateTime now) {
        WorkflowAssigneeResolution resolution = resolveAssigneeResolution(node, preferredAssigneeId, initiatorId);
        List<Long> assigneeIds = resolution.assigneeIds();
        for (Long assigneeId : taskPolicy(node).initialAssignees(assigneeIds)) {
            createPendingTask(instance, node, assigneeId, operatorId, now, resolution);
        }
    }

    public WfTask createPendingTask(WfProcessInstance instance,
                                    WorkflowGraph.NodeInfo node,
                                    Long assigneeId,
                                    Long operatorId,
                                    LocalDateTime now) {
        return createPendingTask(instance, node.key(), node.name(), assigneeId, operatorId, now);
    }

    public WfTask createPendingTask(WfProcessInstance instance,
                                    WorkflowGraph.NodeInfo node,
                                    Long assigneeId,
                                    Long operatorId,
                                    LocalDateTime now,
                                    WorkflowAssigneeResolution resolution) {
        return createPendingTask(instance, node.key(), node.name(), assigneeId, operatorId, now, resolution);
    }

    public WfTask createPendingTask(WfProcessInstance instance,
                                    String nodeKey,
                                    String nodeName,
                                    Long assigneeId,
                                    Long operatorId,
                                    LocalDateTime now) {
        return createPendingTask(instance, nodeKey, nodeName, assigneeId, operatorId, now, null);
    }

    public WfTask createPendingTask(WfProcessInstance instance,
                                    String nodeKey,
                                    String nodeName,
                                    Long assigneeId,
                                    Long operatorId,
                                    LocalDateTime now,
                                    WorkflowAssigneeResolution resolution) {
        WfTask task = new WfTask();
        task.setInstanceId(instance.getId());
        task.setNodeKey(nodeKey);
        task.setNodeName(nodeName);
        task.setAssigneeId(assigneeId);
        task.setAssigneeDeptId(resolveAssigneeDeptId(assigneeId));
        if (resolution != null) {
            task.setAssignmentRuleType(resolution.ruleType());
            task.setAssignmentRuleName(resolution.ruleName());
            task.setAssignmentResolvePath(resolution.resolvePath());
        }
        task.setStatus(WorkflowTaskStatus.PENDING.code());
        task.setStartedAt(now);
        task.setCreatedBy(operatorId);
        task.setCreatedAt(now);
        task.setUpdatedBy(operatorId);
        task.setUpdatedAt(now);
        taskService.save(task);
        return task;
    }

    private Long resolveAssigneeDeptId(Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        SysUser user = userService.getById(assigneeId);
        return user == null ? null : user.getDeptId();
    }

    public void transitionPendingTask(WfTask task,
                                      WorkflowTaskStatus targetStatus,
                                      String comment,
                                      Long operatorId,
                                      LocalDateTime now) {
        boolean updated = taskService.update(Wrappers.lambdaUpdate(WfTask.class)
                .eq(WfTask::getId, task.getId())
                .eq(WfTask::getStatus, WorkflowTaskStatus.PENDING.code())
                .set(WfTask::getStatus, targetStatus.code())
                .set(WfTask::getApproveComment, comment)
                .set(WfTask::getFinishedAt, now)
                .set(WfTask::getUpdatedBy, operatorId)
                .set(WfTask::getUpdatedAt, now));
        if (!updated) {
            throw new BusinessException("流程任务已处理，请刷新后重试");
        }
        task.setStatus(targetStatus.code());
        task.setApproveComment(comment);
        task.setFinishedAt(now);
        task.setUpdatedBy(operatorId);
        task.setUpdatedAt(now);
        archiveService.archiveTask(task);
    }

    public boolean continueCurrentApprovalNode(WfProcessInstance instance,
                                               WorkflowGraph.NodeInfo currentNode,
                                               WfTask approvedTask,
                                               Long operatorId,
                                               LocalDateTime now) {
        WorkflowTaskPolicy taskPolicy = taskPolicy(currentNode);
        PendingTaskStats pendingTaskStats = pendingTaskStats(instance.getId(), currentNode.key());
        if (taskPolicy.shouldHoldCurrentNode(
                isAddSignTask(approvedTask.getId()),
                pendingTaskStats.addSignTaskCount(),
                pendingTaskStats.regularTaskCount())) {
            return true;
        }
        if (taskPolicy == WorkflowTaskPolicy.SEQUENTIAL) {
            WorkflowAssigneeResolution resolution = resolveAssigneeResolution(currentNode, null, instance.getInitiatorId());
            List<Long> assigneeIds = resolution.assigneeIds();
            return taskPolicy.nextAssigneeAfter(assigneeIds, WorkflowTaskLineage.sequentialCursorAssigneeId(
                            assigneeIds,
                            approvedTask.getAssigneeId(),
                            taskDelegations(approvedTask.getId())))
	                    .map(nextAssigneeId -> {
	                        createPendingTask(instance, currentNode, nextAssigneeId, operatorId, now, resolution);
	                        instance.setCurrentNodeKey(currentNode.key());
	                        instance.setUpdatedBy(operatorId);
	                        instance.setUpdatedAt(now);
	                        if (!instanceService.updateById(instance)) {
                            throw new BusinessException("流程状态已变化，请刷新后重试");
                        }
                        return true;
                    })
                    .orElse(false);
        }
        cancelPendingSiblingTasks(instance.getId(), currentNode.key(), approvedTask.getId(), now, operatorId);
        return false;
    }

    public void cancelPendingTasks(Long instanceId, LocalDateTime now, Long operatorId) {
        List<WfTask> pendingTasks = EasyDataScopeContext.ignore(() -> taskService.lambdaQuery()
                .eq(WfTask::getInstanceId, instanceId)
                .eq(WfTask::getStatus, WorkflowTaskStatus.PENDING.code())
                .list());
        for (WfTask pendingTask : pendingTasks) {
            transitionPendingTask(pendingTask, WorkflowTaskStatus.CANCELED, null, operatorId, now);
        }
    }

    public void createTaskDelegation(Long taskId,
                                     Long fromUserId,
                                     Long toUserId,
                                     WorkflowTaskDelegationType delegationType,
                                     LocalDateTime now) {
        WfTaskDelegation delegation = new WfTaskDelegation();
        delegation.setTaskId(taskId);
        delegation.setFromUserId(fromUserId);
        delegation.setToUserId(toUserId);
        delegation.setDelegationType(delegationType.code());
        delegation.setStatus("DONE");
        delegation.setCreatedAt(now);
        taskDelegationService.save(delegation);
    }

    public boolean isAddSignTask(Long taskId) {
        return taskDelegationService.lambdaQuery()
                .eq(WfTaskDelegation::getTaskId, taskId)
                .eq(WfTaskDelegation::getDelegationType, WorkflowTaskDelegationType.ADD_SIGN.code())
                .count() > 0;
    }

    public WfTask loadPendingAddSignTask(Long instanceId, String nodeKey, Long targetUserId) {
        List<WfTask> pendingTasks = EasyDataScopeContext.ignore(() -> taskService.lambdaQuery()
                .eq(WfTask::getInstanceId, instanceId)
                .eq(WfTask::getNodeKey, nodeKey)
                .eq(WfTask::getAssigneeId, targetUserId)
                .eq(WfTask::getStatus, WorkflowTaskStatus.PENDING.code())
                .list());
        for (WfTask pendingTask : pendingTasks) {
            if (isAddSignTask(pendingTask.getId())) {
                return pendingTask;
            }
        }
        throw new BusinessException("未找到可减签的待办任务");
    }

    private WorkflowAssigneeResolution resolveAssigneeResolution(WorkflowGraph.NodeInfo node, Long preferredAssigneeId, Long initiatorId) {
        WorkflowAssigneeRule assigneeRule = WorkflowAssigneeRule.of(
                node.propertyText(WorkflowGraphProperty.APPROVER_TYPE),
                nodeUserIds(node, WorkflowGraphProperty.ASSIGNEE_IDS),
                node.propertyText(WorkflowGraphProperty.ROLE_CODE));
        if (preferredAssigneeId != null && assigneeRule.approverType().acceptsPreferredAssignee()) {
            Long activeAssigneeId = validateActiveAssignee(preferredAssigneeId);
            return new WorkflowAssigneeResolution(List.of(activeAssigneeId), assigneeRule.approverType().name(), "发起人选择",
                    "发起人提交时指定处理人：" + activeAssigneeId);
        }
        WorkflowAssigneeResolution resolution = assigneeResolver.resolveWithContext(assigneeRule, initiatorId);
        if (!resolution.assigneeIds().isEmpty()) {
            return new WorkflowAssigneeResolution(distinctIds(resolution.assigneeIds()), resolution.ruleType(), resolution.ruleName(), resolution.resolvePath());
        }
        if (assigneeRule.shouldFailWhenUnresolved()) {
            throw new BusinessException(assigneeRule.approverType().unresolvedMessage());
        }
        throw new BusinessException("未配置审批人");
    }

    private Long validateActiveAssignee(Long userId) {
        SysUser user = userService.getById(userId);
        if (user == null || !Objects.equals(user.getEnable(), 1)) {
            throw new BusinessException("目标处理人不存在或已停用");
        }
        return userId;
    }

    private List<WorkflowTaskLineage.Delegation> taskDelegations(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        return taskDelegationService.lambdaQuery()
                .eq(WfTaskDelegation::getTaskId, taskId)
                .list()
                .stream()
                .map(this::taskDelegation)
                .toList();
    }

    private WorkflowTaskLineage.Delegation taskDelegation(WfTaskDelegation taskDelegation) {
        try {
            return new WorkflowTaskLineage.Delegation(
                    taskDelegation.getFromUserId(),
                    WorkflowTaskDelegationType.of(taskDelegation.getDelegationType()));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
    }

    private PendingTaskStats pendingTaskStats(Long instanceId, String nodeKey) {
        List<WfTask> pendingTasks = EasyDataScopeContext.ignore(() -> taskService.lambdaQuery()
                .eq(WfTask::getInstanceId, instanceId)
                .eq(WfTask::getNodeKey, nodeKey)
                .eq(WfTask::getStatus, WorkflowTaskStatus.PENDING.code())
                .list());
        if (pendingTasks.isEmpty()) {
            return new PendingTaskStats(0, 0);
        }
        List<Long> pendingTaskIds = pendingTasks.stream().map(WfTask::getId).toList();
        long addSignTaskCount = taskDelegationService.lambdaQuery()
                .in(WfTaskDelegation::getTaskId, pendingTaskIds)
                .eq(WfTaskDelegation::getDelegationType, WorkflowTaskDelegationType.ADD_SIGN.code())
                .count();
        return new PendingTaskStats(pendingTasks.size() - addSignTaskCount, addSignTaskCount);
    }

    private void cancelPendingSiblingTasks(Long instanceId, String nodeKey, Long finishedTaskId, LocalDateTime now, Long operatorId) {
        List<WfTask> pendingSiblingTasks = EasyDataScopeContext.ignore(() -> taskService.lambdaQuery()
                .eq(WfTask::getInstanceId, instanceId)
                .eq(WfTask::getNodeKey, nodeKey)
                .eq(WfTask::getStatus, WorkflowTaskStatus.PENDING.code())
                .ne(WfTask::getId, finishedTaskId)
                .list());
        for (WfTask pendingSiblingTask : pendingSiblingTasks) {
            transitionPendingTask(pendingSiblingTask, WorkflowTaskStatus.CANCELED, null, operatorId, now);
        }
    }

    private WorkflowTaskPolicy taskPolicy(WorkflowGraph.NodeInfo node) {
        return WorkflowTaskPolicy.of(approveType(node));
    }

    private WorkflowApproveType approveType(WorkflowGraph.NodeInfo node) {
        try {
            return WorkflowApproveType.of(node.propertyText(WorkflowGraphProperty.APPROVE_TYPE));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ex.getMessage());
        }
    }

    private List<Long> nodeUserIds(WorkflowGraph.NodeInfo node, WorkflowGraphProperty property) {
        List<Long> ids = new ArrayList<>();
        appendLongs(ids, node.property(property));
        return ids;
    }

    private void appendLongs(List<Long> target, JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return;
        }
        if (valueNode.isArray()) {
            valueNode.forEach(item -> appendLongs(target, item));
            return;
        }
        if (valueNode.isNumber()) {
            target.add(valueNode.asLong());
            return;
        }
        if (valueNode.isTextual()) {
            String trimmed = valueNode.asText().trim();
            if (!trimmed.isEmpty()) {
                try {
                    target.add(Long.valueOf(trimmed));
                } catch (NumberFormatException ignored) {
                    // 节点人员配置允许字符串扩展，无法转成用户ID时跳过。
                }
            }
        }
    }

    private List<Long> distinctIds(Collection<Long> ids) {
        return ids.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new));
    }

    private record PendingTaskStats(long regularTaskCount, long addSignTaskCount) {
    }
}
