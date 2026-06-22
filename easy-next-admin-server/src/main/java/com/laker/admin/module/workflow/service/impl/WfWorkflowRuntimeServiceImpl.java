package com.laker.admin.module.workflow.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.datascope.context.EasyDataScopeContext;
import com.laker.admin.infrastructure.security.exception.EasyForbiddenException;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.message.service.UserMessageService;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysUserService;
import com.laker.admin.module.workflow.dto.WfCcView;
import com.laker.admin.module.workflow.dto.WfEventView;
import com.laker.admin.module.workflow.dto.WfInstanceActionRequest;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionVersionView;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionView;
import com.laker.admin.module.workflow.dto.WfProcessInstanceDetail;
import com.laker.admin.module.workflow.dto.WfProcessInstanceView;
import com.laker.admin.module.workflow.dto.WfStartProcessRequest;
import com.laker.admin.module.workflow.dto.WfTaskActionRequest;
import com.laker.admin.module.workflow.dto.WfTaskView;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfEvent;
import com.laker.admin.module.workflow.entity.WfHistoricCc;
import com.laker.admin.module.workflow.entity.WfHistoricProcessInstance;
import com.laker.admin.module.workflow.entity.WfHistoricTask;
import com.laker.admin.module.workflow.entity.WfProcessDefinition;
import com.laker.admin.module.workflow.entity.WfProcessDefinitionVersion;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.service.IWfCcService;
import com.laker.admin.module.workflow.service.IWfEventService;
import com.laker.admin.module.workflow.service.IWfHistoricCcService;
import com.laker.admin.module.workflow.service.IWfHistoricProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfHistoricTaskService;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionService;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionVersionService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfTaskService;
import com.laker.admin.module.workflow.service.IWfWorkflowRuntimeService;
import com.laker.admin.module.workflow.support.WorkflowArchiveService;
import com.laker.admin.module.workflow.support.WorkflowEventRecorder;
import com.laker.admin.module.workflow.support.WorkflowGraph;
import com.laker.admin.module.workflow.support.WorkflowGraphProperty;
import com.laker.admin.module.workflow.support.WorkflowGraphNavigator;
import com.laker.admin.module.workflow.support.WorkflowGraphParser;
import com.laker.admin.module.workflow.support.WorkflowGraphValidator;
import com.laker.admin.module.workflow.support.WorkflowEventAction;
import com.laker.admin.module.workflow.support.WorkflowInstanceStatus;
import com.laker.admin.module.workflow.support.WorkflowInstanceStateGuard;
import com.laker.admin.module.workflow.support.WorkflowParticipantResolver;
import com.laker.admin.module.workflow.support.WorkflowTaskDispatcher;
import com.laker.admin.module.workflow.support.WorkflowTaskDelegationType;
import com.laker.admin.module.workflow.support.WorkflowTaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class WfWorkflowRuntimeServiceImpl implements IWfWorkflowRuntimeService {
    private static final String STATUS_ENABLED = "ENABLED";

    private final IWfProcessDefinitionService definitionService;
    private final IWfProcessDefinitionVersionService versionService;
    private final IWfProcessInstanceService instanceService;
    private final IWfTaskService taskService;
    private final IWfEventService eventService;
    private final IWfCcService ccService;
    private final IWfHistoricProcessInstanceService historicInstanceService;
    private final IWfHistoricTaskService historicTaskService;
    private final IWfHistoricCcService historicCcService;
    private final WorkflowGraphParser graphParser;
    private final WorkflowGraphValidator graphValidator;
    private final WorkflowGraphNavigator graphNavigator;
    private final WorkflowTaskDispatcher taskDispatcher;
    private final WorkflowEventRecorder eventRecorder;
    private final WorkflowInstanceStateGuard instanceStateGuard;
    private final WorkflowArchiveService archiveService;
    private final WorkflowParticipantResolver participantResolver;
    private final EasyJsonCodec jsonCodec;
    private final ISysUserService sysUserService;
    private final UserMessageService userMessageService;

    public WfWorkflowRuntimeServiceImpl(IWfProcessDefinitionService definitionService,
                                        IWfProcessDefinitionVersionService versionService,
                                        IWfProcessInstanceService instanceService,
                                        IWfTaskService taskService,
                                        IWfEventService eventService,
                                        IWfCcService ccService,
                                        IWfHistoricProcessInstanceService historicInstanceService,
                                        IWfHistoricTaskService historicTaskService,
                                        IWfHistoricCcService historicCcService,
                                        WorkflowGraphParser graphParser,
                                        WorkflowGraphValidator graphValidator,
                                        WorkflowGraphNavigator graphNavigator,
                                        WorkflowTaskDispatcher taskDispatcher,
                                        WorkflowEventRecorder eventRecorder,
                                        WorkflowInstanceStateGuard instanceStateGuard,
                                        WorkflowArchiveService archiveService,
                                        WorkflowParticipantResolver participantResolver,
                                        EasyJsonCodec jsonCodec,
                                        ISysUserService sysUserService,
                                        UserMessageService userMessageService) {
        this.definitionService = definitionService;
        this.versionService = versionService;
        this.instanceService = instanceService;
        this.taskService = taskService;
        this.eventService = eventService;
        this.ccService = ccService;
        this.historicInstanceService = historicInstanceService;
        this.historicTaskService = historicTaskService;
        this.historicCcService = historicCcService;
        this.graphParser = graphParser;
        this.graphValidator = graphValidator;
        this.graphNavigator = graphNavigator;
        this.taskDispatcher = taskDispatcher;
        this.eventRecorder = eventRecorder;
        this.instanceStateGuard = instanceStateGuard;
        this.archiveService = archiveService;
        this.participantResolver = participantResolver;
        this.jsonCodec = jsonCodec;
        this.sysUserService = sysUserService;
        this.userMessageService = userMessageService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessInstanceDetail start(WfStartProcessRequest request) {
        Long operatorId = currentUserId();
        WfProcessDefinition definition = loadEnabledDefinition(request);
        WfProcessDefinitionVersion version = loadCurrentVersion(definition);
        graphValidator.validateForEnable(version.getGraphJson());
        WorkflowGraph graph = graphParser.parse(version.getGraphJson());
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> variables = startVariables(definition, request, operatorId);

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
        List<Long> submitCcReceivers = createCcRecords(instance, "start", "发起抄送", request.getCcUserIds(), operatorId, now);
        eventRecorder.recordCc(instance.getId(), null, operatorId, "start", "start", submitCcReceivers, request.getComment(), now);
        List<Long> autoCcReceivers = createCcRecords(instance, nextStep.ccNodes(), operatorId, now);
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
        assertInstanceVisible(instance);
        WfProcessInstanceDetail detail = new WfProcessInstanceDetail();
        WfProcessDefinition definition = definitionService.getById(instance.getDefinitionId());
        WfProcessDefinitionVersion version = versionService.getById(instance.getVersionId());
        detail.setInstance(WfProcessInstanceView.from(instance));
        detail.setDefinition(WfProcessDefinitionView.from(definition));
        detail.setVersion(WfProcessDefinitionVersionView.from(version));
        detail.setGraphJson(StringUtils.hasText(instance.getDefinitionSnapshotJson())
                ? instance.getDefinitionSnapshotJson()
                : version == null ? null : version.getGraphJson());
        detail.setVariables(readInstanceVariables(instance));
        List<WfTask> tasks = instanceTasks(instanceId);
        List<WfEvent> events = eventService.lambdaQuery()
                .eq(WfEvent::getInstanceId, instanceId)
                .orderByAsc(WfEvent::getCreatedAt)
                .list();
        List<WfCc> ccList = instanceCcList(instanceId);
        detail.setParticipants(participantResolver.resolve(instance, tasks, events, ccList));
        detail.setTasks(WfTaskView.fromList(tasks));
        detail.setEvents(WfEventView.fromList(events));
        detail.setCcList(WfCcView.fromList(ccList));
        return detail;
    }

    private List<WfTask> instanceTasks(Long instanceId) {
        List<WfTask> tasks = new ArrayList<>();
        tasks.addAll(historicTaskService.lambdaQuery()
                .eq(WfHistoricTask::getInstanceId, instanceId)
                .orderByAsc(WfHistoricTask::getStartedAt)
                .list()
                .stream()
                .map(archiveService::toRuntimeTask)
                .toList());
        tasks.addAll(EasyDataScopeContext.ignore(() -> taskService.lambdaQuery()
                .eq(WfTask::getInstanceId, instanceId)
                .orderByAsc(WfTask::getStartedAt)
                .list()));
        tasks.sort(java.util.Comparator.comparing(WfTask::getStartedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparing(WfTask::getId, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return tasks;
    }

    private List<WfCc> instanceCcList(Long instanceId) {
        List<WfCc> ccList = new ArrayList<>();
        ccList.addAll(historicCcService.lambdaQuery()
                .eq(WfHistoricCc::getInstanceId, instanceId)
                .orderByAsc(WfHistoricCc::getCreatedAt)
                .list()
                .stream()
                .map(archiveService::toRuntimeCc)
                .toList());
        ccList.addAll(ccService.lambdaQuery()
                .eq(WfCc::getInstanceId, instanceId)
                .orderByAsc(WfCc::getCreatedAt)
                .list());
        ccList.sort(java.util.Comparator.comparing(WfCc::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparing(WfCc::getId, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        return ccList;
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
        List<Long> approveCcReceivers = createCcRecords(instance, task.getNodeKey(), task.getNodeName(), request.getCcUserIds(), operatorId, now);
        eventRecorder.recordCc(instance.getId(), taskId, operatorId, task.getNodeKey(), task.getNodeKey(), approveCcReceivers, request.getComment(), now);
        WorkflowGraph.NodeInfo currentNode = graph.nodes().get(task.getNodeKey());
        if (currentNode != null && taskDispatcher.continueCurrentApprovalNode(instance, currentNode, task, operatorId, now)) {
            eventRecorder.record(instance.getId(), taskId, operatorId, WorkflowEventAction.APPROVE, task.getNodeKey(), task.getNodeKey(), null, request.getComment(), now);
            return detail(instance.getId());
        }

        Map<String, Object> variables = actionVariables(instance, request, operatorId);
        WorkflowGraph.NextStep nextStep = graphNavigator.resolveNextStep(graph, task.getNodeKey(), variables);
        List<Long> autoCcReceivers = createCcRecords(instance, nextStep.ccNodes(), operatorId, now);
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

        // 退回不是结束流程，而是关闭当前待办并在目标节点重新生成待办，实例仍保持运行态。
        taskDispatcher.transitionPendingTask(task, WorkflowTaskStatus.CANCELED, request.getComment(), operatorId, now);

        taskDispatcher.createPendingTask(instance, returnNode.key(), returnNode.name(), assigneeId, operatorId, now);
        instance.setCurrentNodeKey(returnNode.key());
        instance.setUpdatedBy(operatorId);
        instance.setUpdatedAt(now);
        instanceStateGuard.updateOrThrow(instance);
        eventRecorder.record(instance.getId(), taskId, operatorId, WorkflowEventAction.RETURN, task.getNodeKey(), returnNode.key(), assigneeId, request.getComment(), now);
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

        // 加签不关闭原待办，而是在同一个审批节点追加待办；原审批人和加签人都完成后才继续流转。
        for (Long targetUserId : targetUserIds) {
            WfTask addSignTask = taskDispatcher.createPendingTask(instance, task.getNodeKey(), task.getNodeName(), targetUserId, operatorId, now);
            taskDispatcher.createTaskDelegation(addSignTask.getId(), operatorId, targetUserId, WorkflowTaskDelegationType.ADD_SIGN, now);
            eventRecorder.record(instance.getId(), taskId, operatorId, WorkflowEventAction.ADD_SIGN,
                    task.getNodeKey(), task.getNodeKey(), targetUserId, request.getComment(), now);
        }
        instance.setCurrentNodeKey(task.getNodeKey());
        instance.setUpdatedBy(operatorId);
        instance.setUpdatedAt(now);
        instanceStateGuard.updateOrThrow(instance);
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

        taskDispatcher.transitionPendingTask(addSignTask, WorkflowTaskStatus.CANCELED, request.getComment(), operatorId, now);
        eventRecorder.record(instance.getId(), taskId, operatorId, WorkflowEventAction.REMOVE_SIGN, task.getNodeKey(), task.getNodeKey(), targetUserId, request.getComment(), now);
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
                && !canManageInstances()) {
            throw new BusinessException("只能催办自己发起或分配给自己的流程任务");
        }
        LocalDateTime now = LocalDateTime.now();
        eventRecorder.record(instance.getId(), taskId, operatorId, WorkflowEventAction.REMIND, task.getNodeKey(), task.getNodeKey(), task.getAssigneeId(), request.getComment(), now);
        notifyTaskAssigneeForReminder(instance, task, operatorId, request.getComment());
        return detail(instance.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WfProcessInstanceDetail revoke(Long instanceId, WfInstanceActionRequest request) {
        Long operatorId = currentUserId();
        WfProcessInstance instance = loadRunningInstance(instanceId);
        if (!Objects.equals(instance.getInitiatorId(), operatorId) && !canManageInstances()) {
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
        if (!canManageInstances()) {
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
        if (!Objects.equals(task.getAssigneeId(), operatorId) && !canManageInstances()) {
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

    private void assertInstanceVisible(WfProcessInstance instance) {
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

        // 转办/委派保持在当前节点，只改变待办责任人；原待办关闭，新待办承接后续流转。
        taskDispatcher.transitionPendingTask(task, sourceTaskStatus, request.getComment(), operatorId, now);

        WfTask reassignedTask = taskDispatcher.createPendingTask(instance, task.getNodeKey(), task.getNodeName(), targetUserId, operatorId, now);
        taskDispatcher.createTaskDelegation(taskId, operatorId, targetUserId, delegationType, now);
        taskDispatcher.createTaskDelegation(reassignedTask.getId(), operatorId, targetUserId, delegationType, now);
        instance.setCurrentNodeKey(task.getNodeKey());
        instance.setUpdatedAt(now);
        instanceStateGuard.updateOrThrow(instance);
        eventRecorder.record(instance.getId(), taskId, operatorId, eventAction, task.getNodeKey(), task.getNodeKey(), targetUserId, request.getComment(), now);
        return detail(instance.getId());
    }

    private List<Long> createCcRecords(WfProcessInstance instance,
                                       String nodeKey,
                                       String nodeName,
                                       Collection<Long> userIds,
                                       Long operatorId,
                                       LocalDateTime now) {
        if (CollectionUtils.isEmpty(userIds)) {
            return List.of();
        }
        Set<Long> receiverIds = new LinkedHashSet<>(userIds);
        List<WfCc> ccList = new ArrayList<>();
        for (Long receiverId : receiverIds) {
            if (receiverId == null) {
                continue;
            }
            WfCc cc = new WfCc();
            cc.setInstanceId(instance.getId());
            cc.setNodeKey(nodeKey);
            cc.setNodeName(nodeName);
            cc.setReceiverId(receiverId);
            cc.setReadStatus(0);
            cc.setCreatedAt(now);
            ccList.add(cc);
        }
        if (!ccList.isEmpty()) {
            ccService.saveBatch(ccList);
            notifyCcReceivers(instance, ccList, operatorId);
        }
        return ccList.stream().map(WfCc::getReceiverId).toList();
    }

    private List<Long> createCcRecords(WfProcessInstance instance,
                                       List<WorkflowGraph.NodeInfo> ccNodes,
                                       Long operatorId,
                                       LocalDateTime now) {
        if (CollectionUtils.isEmpty(ccNodes)) {
            return List.of();
        }
        List<Long> receiverIds = new ArrayList<>();
        for (WorkflowGraph.NodeInfo ccNode : ccNodes) {
            List<Long> nodeReceiverIds = nodeUserIds(ccNode, WorkflowGraphProperty.CC_USER_IDS);
            receiverIds.addAll(createCcRecords(instance, ccNode.key(), ccNode.name(), nodeReceiverIds, operatorId, now));
        }
        return distinctIds(receiverIds);
    }

    private void notifyCcReceivers(WfProcessInstance instance, List<WfCc> ccList, Long operatorId) {
        String instanceTitle = instanceTitle(instance);
        for (WfCc cc : ccList) {
            userMessageService.createSystemMessage(
                    cc.getReceiverId(),
                    operatorId,
                    "流程抄送：" + instanceTitle,
                    "流程「%s」已抄送给你，抄送节点：%s。".formatted(instanceTitle, cc.getNodeName()),
                    "WORKFLOW_CC",
                    "INFO",
                    "WORKFLOW_CC",
                    String.valueOf(cc.getId()),
                    "/workflow/tasks?tab=cc&ccId=" + cc.getId() + "&instanceId=" + instance.getId()
            );
        }
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

    private void notifyTaskAssigneeForReminder(WfProcessInstance instance, WfTask task, Long operatorId, String comment) {
        String title = "流程催办：" + instanceTitle(instance);
        String content = "%s 催办了流程「%s」，当前节点：%s。%s".formatted(
                userDisplayName(operatorId),
                instanceTitle(instance),
                StringUtils.hasText(task.getNodeName()) ? task.getNodeName() : task.getNodeKey(),
                StringUtils.hasText(comment) ? "处理意见：" + comment : "请尽快处理。"
        );
        userMessageService.createSystemMessage(
                task.getAssigneeId(),
                operatorId,
                title,
                content,
                "WORKFLOW",
                "INFO",
                "WORKFLOW_INSTANCE",
                String.valueOf(instance.getId()),
                "/workflow/tasks?tab=pending&instanceId=" + instance.getId()
        );
    }

    private String userDisplayName(Long userId) {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        if (principal != null && Objects.equals(principal.getUserId(), userId)) {
            if (StringUtils.hasText(principal.getNickName())) {
                return principal.getNickName();
            }
            if (StringUtils.hasText(principal.getUserName())) {
                return principal.getUserName();
            }
        }
        SysUser user = sysUserService.getById(userId);
        if (user != null) {
            if (StringUtils.hasText(user.getNickName())) {
                return user.getNickName();
            }
            if (StringUtils.hasText(user.getUserName())) {
                return user.getUserName();
            }
        }
        return "用户" + userId;
    }

    private String instanceTitle(WfProcessInstance instance) {
        return StringUtils.hasText(instance.getTitle()) ? instance.getTitle() : "流程 " + instance.getId();
    }

    private List<Long> distinctIds(Collection<Long> ids) {
        return ids.stream()
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new));
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

    private Long currentUserId() {
        Long userId = EasySecurityContext.getUserId();
        if (userId == null) {
            throw new BusinessException("未获取到当前登录用户");
        }
        return userId;
    }

    private boolean canManageInstances() {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        return principal != null && principal.hasPermission(EasyPermissions.Workflow.INSTANCE_MANAGE);
    }

    private Map<String, Object> startVariables(WfProcessDefinition definition, WfStartProcessRequest request, Long operatorId) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("definitionId", definition.getId());
        variables.put("processKey", definition.getProcessKey());
        variables.put("businessType", request.getBusinessType());
        variables.put("businessId", request.getBusinessId());
        variables.put("title", request.getTitle());
        variables.put("initiatorId", operatorId);
        if (request.getVariables() != null) {
            variables.putAll(request.getVariables());
        }
        return variables;
    }

    private Map<String, Object> actionVariables(WfProcessInstance instance, WfTaskActionRequest request, Long operatorId) {
        Map<String, Object> variables = readInstanceVariables(instance);
        variables.put("instanceId", instance.getId());
        variables.put("processKey", instance.getProcessKey());
        variables.put("businessType", instance.getBusinessType());
        variables.put("businessId", instance.getBusinessId());
        variables.put("title", instance.getTitle());
        variables.put("initiatorId", instance.getInitiatorId());
        variables.put("operatorId", operatorId);
        if (request.getVariables() != null) {
            variables.putAll(request.getVariables());
            instance.setVariablesJson(jsonCodec.toJson(variables));
            instanceStateGuard.updateOrThrow(instance);
        }
        return variables;
    }

    private Map<String, Object> readInstanceVariables(WfProcessInstance instance) {
        if (!StringUtils.hasText(instance.getVariablesJson())) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(jsonCodec.fromJson(instance.getVariablesJson(), new TypeReference<Map<String, Object>>() {
        }));
    }

}
