package com.laker.admin.module.assistant.tool.workflow;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.assistant.context.AssistantRequestContext.AssistantUserContext;
import com.laker.admin.module.workflow.dto.WfProcessInstanceDetail;
import com.laker.admin.module.workflow.dto.WfProcessInstanceView;
import com.laker.admin.module.workflow.dto.WfTaskActionRequest;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfTaskService;
import com.laker.admin.module.workflow.service.IWfWorkflowRuntimeService;
import com.laker.admin.module.workflow.support.WorkflowInstanceStatus;
import com.laker.admin.module.workflow.support.WorkflowTaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Service
public class DefaultWorkflowTaskActionToolService implements WorkflowTaskActionToolService {
    private final IWfTaskService taskService;
    private final IWfProcessInstanceService instanceService;
    private final IWfWorkflowRuntimeService runtimeService;

    public DefaultWorkflowTaskActionToolService(IWfTaskService taskService,
                                                IWfProcessInstanceService instanceService,
                                                IWfWorkflowRuntimeService runtimeService) {
        this.taskService = Objects.requireNonNull(taskService, "taskService must not be null");
        this.instanceService = Objects.requireNonNull(instanceService, "instanceService must not be null");
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService must not be null");
    }

    @Override
    public WorkflowTaskActionTarget resolveTarget(AssistantUserContext user, String taskIdentifier) {
        if (user == null || user.userId() == null) {
            throw new BusinessException("未获取到当前登录用户");
        }
        String identifier = normalizeIdentifier(taskIdentifier);
        Long numericIdentifier = parseLong(identifier);
        if (numericIdentifier != null) {
            WfTask exactTask = taskService.getById(numericIdentifier);
            if (exactTask != null) {
                return requireActionableTarget(user, exactTask);
            }
        }

        WfProcessInstance instance = numericIdentifier == null
                ? findInstanceByBusinessId(identifier)
                : instanceService.getById(numericIdentifier);
        if (instance == null) {
            throw new BusinessException("没有找到可审批的流程任务");
        }
        return resolveSinglePendingTask(user, instance);
    }

    @Override
    @EasyAudit(module = "流程任务", action = "助手同意流程任务", dataChange = true,
            bizType = "WORKFLOW_TASK", bizId = "#taskId", changeType = "APPROVE")
    public WorkflowTaskActionResult approve(Long taskId, String comment) {
        WfTaskActionRequest request = new WfTaskActionRequest();
        request.setComment(normalizeComment(comment));
        WfProcessInstanceDetail detail = runtimeService.approve(taskId, request);
        return toResult(WorkflowTaskDecision.APPROVE, taskId, detail);
    }

    @Override
    @EasyAudit(module = "流程任务", action = "助手驳回流程任务", dataChange = true,
            bizType = "WORKFLOW_TASK", bizId = "#taskId", changeType = "REJECT")
    public WorkflowTaskActionResult reject(Long taskId, String comment) {
        WfTaskActionRequest request = new WfTaskActionRequest();
        request.setComment(normalizeComment(comment));
        WfProcessInstanceDetail detail = runtimeService.reject(taskId, request);
        return toResult(WorkflowTaskDecision.REJECT, taskId, detail);
    }

    private static WorkflowTaskActionResult toResult(WorkflowTaskDecision decision,
                                                     Long taskId,
                                                     WfProcessInstanceDetail detail) {
        WfProcessInstanceView instance = detail == null ? null : detail.getInstance();
        if (instance == null || instance.getId() == null) {
            throw new BusinessException("流程审批已执行，但未返回流程实例结果");
        }
        return new WorkflowTaskActionResult(
                decision,
                taskId,
                instance.getId(),
                instance.getBusinessId(),
                instance.getTitle(),
                instance.getCurrentNodeKey(),
                instance.getStatus()
        );
    }

    private WorkflowTaskActionTarget resolveSinglePendingTask(AssistantUserContext user,
                                                               WfProcessInstance instance) {
        requireRunningInstance(instance);
        List<WfTask> pendingTasks = taskService.list(Wrappers.<WfTask>lambdaQuery()
                .eq(WfTask::getInstanceId, instance.getId())
                .eq(WfTask::getStatus, WorkflowTaskStatus.PENDING.code())
                .orderByDesc(WfTask::getStartedAt));
        List<WfTask> ownTasks = pendingTasks.stream()
                .filter(task -> Objects.equals(task.getAssigneeId(), user.userId()))
                .toList();
        if (ownTasks.size() == 1) {
            return toTarget(ownTasks.get(0), instance);
        }
        if (ownTasks.size() > 1) {
            throw new BusinessException("该流程有多个分配给你的待办，请指定待办任务号");
        }
        if (canManageInstances(user) && pendingTasks.size() == 1) {
            return toTarget(pendingTasks.get(0), instance);
        }
        if (canManageInstances(user) && pendingTasks.size() > 1) {
            throw new BusinessException("该流程有多个待办，请指定待办任务号");
        }
        throw new BusinessException("没有找到当前用户可审批的待办任务");
    }

    private WorkflowTaskActionTarget requireActionableTarget(AssistantUserContext user, WfTask task) {
        if (!WorkflowTaskStatus.PENDING.equalsCode(task.getStatus())) {
            throw new BusinessException("流程任务已处理");
        }
        if (!Objects.equals(task.getAssigneeId(), user.userId()) && !canManageInstances(user)) {
            throw new BusinessException("只能处理分配给自己的流程任务");
        }
        WfProcessInstance instance = instanceService.getById(task.getInstanceId());
        requireRunningInstance(instance);
        return toTarget(task, instance);
    }

    private WfProcessInstance findInstanceByBusinessId(String businessId) {
        List<WfProcessInstance> instances = instanceService.list(Wrappers.<WfProcessInstance>lambdaQuery()
                .eq(WfProcessInstance::getBusinessId, businessId)
                .orderByDesc(WfProcessInstance::getStartedAt));
        if (instances.size() > 1) {
            throw new BusinessException("该业务单号对应多个运行流程，请指定待办任务号或流程实例号");
        }
        return instances.isEmpty() ? null : instances.get(0);
    }

    private static WorkflowTaskActionTarget toTarget(WfTask task, WfProcessInstance instance) {
        return new WorkflowTaskActionTarget(
                task.getId(),
                task.getInstanceId(),
                instance.getBusinessId(),
                instance.getTitle(),
                task.getNodeName()
        );
    }

    private static void requireRunningInstance(WfProcessInstance instance) {
        if (instance == null) {
            throw new BusinessException("流程实例不存在");
        }
        if (!WorkflowInstanceStatus.RUNNING.equalsCode(instance.getStatus())) {
            throw new BusinessException("流程实例已结束");
        }
    }

    private static boolean canManageInstances(AssistantUserContext user) {
        return user.superAdmin()
                || (user.permissions() != null
                && user.permissions().contains(EasyPermissions.Workflow.INSTANCE_MANAGE));
    }

    private static String normalizeIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("缺少待审批任务标识");
        }
        return value.trim();
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            return null;
        }
        String normalized = comment.trim();
        if (normalized.length() > 1000) {
            throw new BusinessException("审批意见不能超过1000个字符");
        }
        return normalized;
    }
}
