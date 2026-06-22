package com.laker.admin.module.workflow.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysUserService;
import com.laker.admin.module.workflow.dto.WfInstanceActionRequest;
import com.laker.admin.module.workflow.dto.WfProcessInstanceDetail;
import com.laker.admin.module.workflow.dto.WfStartProcessRequest;
import com.laker.admin.module.workflow.dto.WfTaskActionRequest;
import com.laker.admin.module.workflow.entity.WfHistoricProcessInstance;
import com.laker.admin.module.workflow.entity.WfProcessDefinition;
import com.laker.admin.module.workflow.entity.WfProcessDefinitionVersion;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.service.IWfHistoricProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionService;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionVersionService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfTaskService;
import com.laker.admin.module.workflow.service.IWfWorkflowRuntimeService;
import com.laker.admin.module.workflow.support.WorkflowArchiveService;
import com.laker.admin.module.workflow.support.WorkflowCcRecordService;
import com.laker.admin.module.workflow.support.WorkflowEventRecorder;
import com.laker.admin.module.workflow.support.WorkflowGraph;
import com.laker.admin.module.workflow.support.WorkflowGraphNavigator;
import com.laker.admin.module.workflow.support.WorkflowGraphParser;
import com.laker.admin.module.workflow.support.WorkflowGraphProperty;
import com.laker.admin.module.workflow.support.WorkflowGraphValidator;
import com.laker.admin.module.workflow.support.WorkflowInstanceDetailAssembler;
import com.laker.admin.module.workflow.support.WorkflowEventAction;
import com.laker.admin.module.workflow.support.WorkflowInstanceStatus;
import com.laker.admin.module.workflow.support.WorkflowInstanceStateGuard;
import com.laker.admin.module.workflow.support.WorkflowNotificationService;
import com.laker.admin.module.workflow.support.WorkflowTaskActionService;
import com.laker.admin.module.workflow.support.WorkflowTaskDispatcher;
import com.laker.admin.module.workflow.support.WorkflowTaskDelegationType;
import com.laker.admin.module.workflow.support.WorkflowTaskStatus;
import com.laker.admin.module.workflow.support.WorkflowVariableSnapshotService;
import com.laker.admin.module.workflow.support.WorkflowVisibilityGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WfWorkflowRuntimeServiceImpl implements IWfWorkflowRuntimeService {
    private static final String STATUS_ENABLED = "ENABLED";

    private final IWfProcessDefinitionService definitionService;
    private final IWfProcessDefinitionVersionService versionService;
    private final IWfProcessInstanceService instanceService;
    private final IWfTaskService taskService;
    private final IWfHistoricProcessInstanceService historicInstanceService;
    private final WorkflowGraphParser graphParser;
    private final WorkflowGraphValidator graphValidator;
    private final WorkflowGraphNavigator graphNavigator;
    private final WorkflowInstanceDetailAssembler detailAssembler;
    private final WorkflowTaskActionService taskActionService;
    private final WorkflowTaskDispatcher taskDispatcher;
    private final WorkflowEventRecorder eventRecorder;
    private final WorkflowInstanceStateGuard instanceStateGuard;
    private final WorkflowArchiveService archiveService;
    private final WorkflowCcRecordService ccRecordService;
    private final WorkflowVariableSnapshotService variableSnapshotService;
    private final WorkflowVisibilityGuard visibilityGuard;
    private final WorkflowNotificationService notificationService;
    private final EasyJsonCodec jsonCodec;
    private final ISysUserService sysUserService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessInstanceDetail start(WfStartProcessRequest request) {
        Long operatorId = currentUserId();
        WfProcessDefinition definition = loadEnabledDefinition(request);
        WfProcessDefinitionVersion version = loadCurrentVersion(definition);
        graphValidator.validateForEnable(version.getGraphJson());
        WorkflowGraph graph = graphParser.parse(version.getGraphJson());
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> variables = variableSnapshotService.startVariables(definition, request, operatorId);

        WfProcessInstance instance = new WfProcessInstance();
        instance.setDefinitionId(definition.getId());
        instance.setVersionId(version.getId());
        instance.setProcessKey(definition.getProcessKey());
        instance.setBusinessType(request.getBusinessType());
        instance.setBusinessId(request.getBusinessId());
        instance.setTitle(request.getTitle());
        instance.setInitiatorId(operatorId);
        instance.setStatus(WorkflowInstanceStatus.RUNNING.code());
        instance.setVariablesJson(jsonCodec.toJson(variables));
        instance.setDefinitionSnapshotJson(version.getGraphJson());
        instance.setStartedAt(now);
        instance.setCreatedBy(operatorId);
        instance.setCreatedAt(now);
        instance.setUpdatedBy(operatorId);
        instance.setUpdatedAt(now);
        instance.setVersion(0);
        instanceService.save(instance);

        WorkflowGraph.NextStep nextStep = graphNavigator.resolveNextStep(graph, null, variables);
        List<Long> submitCcReceivers = ccRecordService.createRecords(instance, "start", "发起抄送", request.getCcUserIds(), operatorId, now);
        eventRecorder.recordCc(instance.getId(), null, operatorId, "start", "start", submitCcReceivers, request.getComment(), now);
        List<Long> autoCcReceivers = ccRecordService.createRecords(instance, nextStep.ccNodes(), operatorId, now);
        eventRecorder.recordCc(instance.getId(), null, operatorId, null, null, autoCcReceivers, "流程节点抄送", now);
        if (nextStep.approvalNode() == null) {
            finishInstance(instance, WorkflowInstanceStatus.APPROVED, null, operatorId, now);
        } else {
            taskDispatcher.createPendingTasksForNode(instance, nextStep.approvalNode(), request.getAssigneeId(), operatorId, operatorId, now);
            instance.setCurrentNodeKey(nextStep.approvalNode().key());
            instance.setUpdatedBy(operatorId);
            instance.setUpdatedAt(now);
            instanceStateGuard.updateOrThrow(instance);
        }
        eventRecorder.record(instance.getId(), null, operatorId, WorkflowEventAction.SUBMIT, null, instance.getCurrentNodeKey(), null, request.getComment(), now);
        return detail(instance.getId());
    }

    @Override
    public WfProcessInstanceDetail detail(Long instanceId) {
        WfProcessInstance instance = instanceService.getById(instanceId);
        if (instance == null) {
            WfHistoricProcessInstance historicInstance = historicInstanceService.getById(instanceId);
            if (historicInstance == null) {
                throw new BusinessException("流程实例不存在");
            }
            instance = archiveService.toRuntimeInstance(historicInstance);
        }
        visibilityGuard.assertInstanceVisible(instance);
        return detailAssembler.assemble(instance);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessInstanceDetail approve(Long taskId, WfTaskActionRequest request) {
        Long operatorId = currentUserId();
        WfTask task = loadPendingTask(taskId, operatorId);
        WfProcessInstance instance = loadRunningInstance(task.getInstanceId());
        LocalDateTime now = LocalDateTime.now();
        taskDispatcher.transitionPendingTask(task, WorkflowTaskStatus.APPROVED, request.getComment(), operatorId, now);

        WorkflowGraph graph = graphParser.parse(instance.getDefinitionSnapshotJson());
        List<Long> approveCcReceivers = ccRecordService.createRecords(instance, task.getNodeKey(), task.getNodeName(), request.getCcUserIds(), operatorId, now);
        eventRecorder.recordCc(instance.getId(), taskId, operatorId, task.getNodeKey(), task.getNodeKey(), approveCcReceivers, request.getComment(), now);
        WorkflowGraph.NodeInfo currentNode = graph.nodes().get(task.getNodeKey());
        if (currentNode != null && taskDispatcher.continueCurrentApprovalNode(instance, currentNode, task, operatorId, now)) {
            eventRecorder.record(instance.getId(), taskId, operatorId, WorkflowEventAction.APPROVE, task.getNodeKey(), task.getNodeKey(), null, request.getComment(), now);
            return detail(instance.getId());
        }

        Map<String, Object> variables = variableSnapshotService.actionVariables(instance, request, operatorId);
        WorkflowGraph.NextStep nextStep = graphNavigator.resolveNextStep(graph, task.getNodeKey(), variables);
        List<Long> autoCcReceivers = ccRecordService.createRecords(instance, nextStep.ccNodes(), operatorId, now);
        eventRecorder.recordCc(instance.getId(), taskId, operatorId, task.getNodeKey(),
                nextStep.approvalNode() == null ? null : nextStep.approvalNode().key(),
                autoCcReceivers, "流程节点抄送", now);
        if (nextStep.approvalNode() == null) {
            finishInstance(instance, WorkflowInstanceStatus.APPROVED, null, operatorId, now);
        } else {
            taskDispatcher.createPendingTasksForNode(instance, nextStep.approvalNode(), request.getNextAssigneeId(), instance.getInitiatorId(), operatorId, now);
            instance.setCurrentNodeKey(nextStep.approvalNode().key());
            instance.setUpdatedBy(operatorId);
            instance.setUpdatedAt(now);
            instanceStateGuard.updateOrThrow(instance);
        }
        eventRecorder.record(instance.getId(), taskId, operatorId, WorkflowEventAction.APPROVE, task.getNodeKey(), instance.getCurrentNodeKey(), null, request.getComment(), now);
        return detail(instance.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessInstanceDetail reject(Long taskId, WfTaskActionRequest request) {
        Long operatorId = currentUserId();
        WfTask task = loadPendingTask(taskId, operatorId);
        WfProcessInstance instance = loadRunningInstance(task.getInstanceId());
        LocalDateTime now = LocalDateTime.now();
        taskDispatcher.transitionPendingTask(task, WorkflowTaskStatus.REJECTED, request.getComment(), operatorId, now);
        taskDispatcher.cancelPendingTasks(instance.getId(), now, operatorId);
        finishInstance(instance, WorkflowInstanceStatus.REJECTED, task.getNodeKey(), operatorId, now);
        eventRecorder.record(instance.getId(), taskId, operatorId, WorkflowEventAction.REJECT, task.getNodeKey(), null, null, request.getComment(), now);
        return detail(instance.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessInstanceDetail transfer(Long taskId, WfTaskActionRequest request) {
        return reassignTask(taskId, request, WorkflowEventAction.TRANSFER, WorkflowTaskDelegationType.TRANSFER,
                WorkflowTaskStatus.TRANSFERRED, WorkflowGraphProperty.ALLOW_TRANSFER, "转办");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessInstanceDetail delegate(Long taskId, WfTaskActionRequest request) {
        return reassignTask(taskId, request, WorkflowEventAction.DELEGATE, WorkflowTaskDelegationType.DELEGATE,
                WorkflowTaskStatus.DELEGATED, WorkflowGraphProperty.ALLOW_DELEGATE, "委派");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessInstanceDetail returnTask(Long taskId, WfTaskActionRequest request) {
        Long operatorId = currentUserId();
        WfTask task = loadPendingTask(taskId, operatorId);
        WfProcessInstance instance = loadRunningInstance(task.getInstanceId());
        assertNodeActionAllowed(instance, task, WorkflowGraphProperty.ALLOW_RETURN, "退回");
        WorkflowGraph graph = graphParser.parse(instance.getDefinitionSnapshotJson());
        String returnNodeKey = graphNavigator.resolveReturnNodeKey(graph, task.getNodeKey(), request.getReturnNodeKey());
        WorkflowGraph.NodeInfo returnNode = graph.nodes().get(returnNodeKey);
        if (!graphNavigator.canReturnTo(returnNode)) {
            throw new BusinessException("退回节点不存在或不可退回");
        }
        Long assigneeId = validateActiveAssignee(request.getReturnAssigneeId() == null ? instance.getInitiatorId() : request.getReturnAssigneeId());
        LocalDateTime now = LocalDateTime.now();

        taskActionService.returnTask(instance, task, returnNode, assigneeId, operatorId, request.getComment(), now);
        return detail(instance.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessInstanceDetail addSign(Long taskId, WfTaskActionRequest request) {
        Long operatorId = currentUserId();
        WfTask task = loadPendingTask(taskId, operatorId);
        WfProcessInstance instance = loadRunningInstance(task.getInstanceId());
        assertNodeActionAllowed(instance, task, WorkflowGraphProperty.ALLOW_ADD_SIGN, "加签");
        List<Long> targetUserIds = requiredAddSignUserIds(request, operatorId);
        LocalDateTime now = LocalDateTime.now();

        taskActionService.addSignTasks(instance, task, targetUserIds, operatorId, request.getComment(), now);
        return detail(instance.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessInstanceDetail removeSign(Long taskId, WfTaskActionRequest request) {
        Long operatorId = currentUserId();
        Long targetUserId = requiredTargetUserId(request);
        WfTask task = loadPendingTask(taskId, operatorId);
        WfProcessInstance instance = loadRunningInstance(task.getInstanceId());
        assertNodeActionAllowed(instance, task, WorkflowGraphProperty.ALLOW_REMOVE_SIGN, "减签");
        WfTask addSignTask = taskDispatcher.loadPendingAddSignTask(instance.getId(), task.getNodeKey(), targetUserId);
        LocalDateTime now = LocalDateTime.now();

        taskActionService.removeSignTask(instance, task, addSignTask, targetUserId, operatorId, request.getComment(), now);
        return detail(instance.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessInstanceDetail remind(Long taskId, WfTaskActionRequest request) {
        Long operatorId = currentUserId();
        WfTask task = taskService.getById(taskId);
        if (task == null) {
            throw new BusinessException("流程任务不存在");
        }
        if (!WorkflowTaskStatus.PENDING.equalsCode(task.getStatus())) {
            throw new BusinessException("只能催办待处理任务");
        }
        WfProcessInstance instance = loadRunningInstance(task.getInstanceId());
        if (!Objects.equals(instance.getInitiatorId(), operatorId)
                && !Objects.equals(task.getAssigneeId(), operatorId)
                && !visibilityGuard.canManageInstances()) {
            throw new BusinessException("只能催办自己发起或分配给自己的流程任务");
        }
        LocalDateTime now = LocalDateTime.now();
        eventRecorder.record(instance.getId(), taskId, operatorId, WorkflowEventAction.REMIND, task.getNodeKey(), task.getNodeKey(), task.getAssigneeId(), request.getComment(), now);
        notificationService.remindTaskAssignee(instance, task, operatorId, request.getComment());
        return detail(instance.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessInstanceDetail revoke(Long instanceId, WfInstanceActionRequest request) {
        Long operatorId = currentUserId();
        WfProcessInstance instance = loadRunningInstance(instanceId);
        if (!Objects.equals(instance.getInitiatorId(), operatorId) && !visibilityGuard.canManageInstances()) {
            throw new BusinessException("只能撤回自己发起的流程");
        }
        LocalDateTime now = LocalDateTime.now();
        taskDispatcher.cancelPendingTasks(instanceId, now, operatorId);
        finishInstance(instance, WorkflowInstanceStatus.REVOKED, instance.getCurrentNodeKey(), operatorId, now);
        eventRecorder.record(instanceId, null, operatorId, WorkflowEventAction.REVOKE, instance.getCurrentNodeKey(), null, null, request.getComment(), now);
        return detail(instanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessInstanceDetail terminate(Long instanceId, WfInstanceActionRequest request) {
        Long operatorId = currentUserId();
        if (!visibilityGuard.canManageInstances()) {
            throw new BusinessException("缺少流程实例管理权限");
        }
        WfProcessInstance instance = loadRunningInstance(instanceId);
        LocalDateTime now = LocalDateTime.now();
        taskDispatcher.cancelPendingTasks(instanceId, now, operatorId);
        finishInstance(instance, WorkflowInstanceStatus.TERMINATED, instance.getCurrentNodeKey(), operatorId, now);
        eventRecorder.record(instanceId, null, operatorId, WorkflowEventAction.TERMINATE, instance.getCurrentNodeKey(), null, null, request.getComment(), now);
        return detail(instanceId);
    }

    private WfProcessDefinition loadEnabledDefinition(WfStartProcessRequest request) {
        WfProcessDefinition definition = null;
        if (request.getDefinitionId() != null) {
            definition = definitionService.getById(request.getDefinitionId());
        } else if (StringUtils.hasText(request.getProcessKey())) {
            definition = definitionService.lambdaQuery()
                    .eq(WfProcessDefinition::getProcessKey, request.getProcessKey())
                    .one();
        }
        if (definition == null) {
            throw new BusinessException("流程定义不存在");
        }
        if (!STATUS_ENABLED.equals(definition.getStatus())) {
            throw new BusinessException("流程定义未启用");
        }
        return definition;
    }

    private WfProcessDefinitionVersion loadCurrentVersion(WfProcessDefinition definition) {
        WfProcessDefinitionVersion version = versionService.lambdaQuery()
                .eq(WfProcessDefinitionVersion::getDefinitionId, definition.getId())
                .eq(WfProcessDefinitionVersion::getVersion, definition.getCurrentVersion())
                .one();
        if (version == null || !StringUtils.hasText(version.getGraphJson())) {
            throw new BusinessException("流程定义版本未发布");
        }
        return version;
    }

    private WfTask loadPendingTask(Long taskId, Long operatorId) {
        WfTask task = taskService.getById(taskId);
        if (task == null) {
            throw new BusinessException("流程任务不存在");
        }
        if (!WorkflowTaskStatus.PENDING.equalsCode(task.getStatus())) {
            throw new BusinessException("流程任务已处理");
        }
        if (!Objects.equals(task.getAssigneeId(), operatorId) && !visibilityGuard.canManageInstances()) {
            throw new BusinessException("只能处理分配给自己的流程任务");
        }
        return task;
    }

    private WfProcessInstance loadRunningInstance(Long instanceId) {
        WfProcessInstance instance = instanceService.getById(instanceId);
        if (instance == null) {
            throw new BusinessException("流程实例不存在");
        }
        if (!WorkflowInstanceStatus.RUNNING.equalsCode(instance.getStatus())) {
            throw new BusinessException("流程实例已结束");
        }
        return instance;
    }

    private void assertNodeActionAllowed(WfProcessInstance instance,
                                         WfTask task,
                                         WorkflowGraphProperty allowProperty,
                                         String actionName) {
        WorkflowGraph.NodeInfo node = graphParser.parse(instance.getDefinitionSnapshotJson()).nodes().get(task.getNodeKey());
        if (node == null) {
            return;
        }
        JsonNode value = node.property(allowProperty);
        if (value != null && !value.isNull() && !value.asBoolean(true)) {
            throw new BusinessException("当前节点不允许" + actionName);
        }
    }

    private WfProcessInstanceDetail reassignTask(Long taskId,
                                                 WfTaskActionRequest request,
                                                 WorkflowEventAction eventAction,
                                                 WorkflowTaskDelegationType delegationType,
                                                 WorkflowTaskStatus sourceTaskStatus,
                                                 WorkflowGraphProperty allowProperty,
                                                 String actionName) {
        Long operatorId = currentUserId();
        Long targetUserId = requiredTargetUserId(request);
        if (Objects.equals(operatorId, targetUserId)) {
            throw new BusinessException("目标处理人不能是当前处理人");
        }
        WfTask task = loadPendingTask(taskId, operatorId);
        WfProcessInstance instance = loadRunningInstance(task.getInstanceId());
        assertNodeActionAllowed(instance, task, allowProperty, actionName);
        LocalDateTime now = LocalDateTime.now();

        taskActionService.reassignTask(instance, task, targetUserId, operatorId, request.getComment(),
                eventAction, delegationType, sourceTaskStatus, now);
        return detail(instance.getId());
    }

    private void finishInstance(WfProcessInstance instance, WorkflowInstanceStatus status, String currentNodeKey, Long operatorId, LocalDateTime now) {
        instance.setUpdatedBy(operatorId);
        instanceStateGuard.finish(instance, status, currentNodeKey, now);
        archiveService.archiveFinishedInstance(instance);
        eventRecorder.publishStatusChanged(instance, status);
    }

    private Long requiredTargetUserId(WfTaskActionRequest request) {
        if (request == null || request.getTargetUserId() == null) {
            throw new BusinessException("请选择目标处理人");
        }
        return validateActiveAssignee(request.getTargetUserId());
    }

    private List<Long> requiredAddSignUserIds(WfTaskActionRequest request, Long operatorId) {
        if (request == null) {
            throw new BusinessException("请选择加签处理人");
        }
        LinkedHashSet<Long> targetUserIds = new LinkedHashSet<>();
        if (request.getTargetUserId() != null) {
            targetUserIds.add(request.getTargetUserId());
        }
        if (!CollectionUtils.isEmpty(request.getAddSignUserIds())) {
            targetUserIds.addAll(request.getAddSignUserIds());
        }
        targetUserIds.remove(null);
        targetUserIds.remove(operatorId);
        if (targetUserIds.isEmpty()) {
            throw new BusinessException("请选择不同于当前处理人的加签处理人");
        }
        return targetUserIds.stream().map(this::validateActiveAssignee).toList();
    }

    private Long validateActiveAssignee(Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user == null || !Objects.equals(user.getEnable(), 1)) {
            throw new BusinessException("目标处理人不存在或已停用");
        }
        return userId;
    }

    private Long currentUserId() {
        Long userId = EasySecurityContext.getUserId();
        if (userId == null) {
            throw new BusinessException("未获取到当前登录用户");
        }
        return userId;
    }

}
