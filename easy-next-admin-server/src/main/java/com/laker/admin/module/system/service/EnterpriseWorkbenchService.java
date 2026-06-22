package com.laker.admin.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.infrastructure.security.service.EasyAuthService;
import com.laker.admin.module.system.dto.workbench.EnterpriseWorkbenchOverview;
import com.laker.admin.module.workflow.dto.WorkflowCcSummary;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfHistoricCc;
import com.laker.admin.module.workflow.entity.WfHistoricProcessInstance;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.service.IWfCcService;
import com.laker.admin.module.workflow.service.IWfHistoricCcService;
import com.laker.admin.module.workflow.service.IWfHistoricProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfTaskService;
import com.laker.admin.module.workflow.support.WorkflowArchiveService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class EnterpriseWorkbenchService {
    private static final long WORKBENCH_LIST_LIMIT = 5;

    private final IWfProcessInstanceService workflowInstanceService;
    private final IWfHistoricProcessInstanceService workflowHistoricInstanceService;
    private final IWfTaskService workflowTaskService;
    private final IWfCcService workflowCcService;
    private final IWfHistoricCcService workflowHistoricCcService;
    private final EasyAuthService easyAuthService;
    private final WorkflowArchiveService workflowArchiveService;

    public EnterpriseWorkbenchService(IWfProcessInstanceService workflowInstanceService,
                                      IWfHistoricProcessInstanceService workflowHistoricInstanceService,
                                      IWfTaskService workflowTaskService,
                                      IWfCcService workflowCcService,
                                      IWfHistoricCcService workflowHistoricCcService,
                                      EasyAuthService easyAuthService,
                                      WorkflowArchiveService workflowArchiveService) {
        this.workflowInstanceService = workflowInstanceService;
        this.workflowHistoricInstanceService = workflowHistoricInstanceService;
        this.workflowTaskService = workflowTaskService;
        this.workflowCcService = workflowCcService;
        this.workflowHistoricCcService = workflowHistoricCcService;
        this.easyAuthService = easyAuthService;
        this.workflowArchiveService = workflowArchiveService;
    }

    public EnterpriseWorkbenchOverview buildOverview() {
        AuthPrincipal principal = easyAuthService.currentPrincipal();
        List<EnterpriseWorkbenchOverview.ApplicationEntry> applications = allowedApplications(principal);

        return EnterpriseWorkbenchOverview.builder()
                .workflow(buildWorkflowOverview(principal, applications.size()))
                .applications(applications)
                .build();
    }

    private EnterpriseWorkbenchOverview.WorkflowOverview buildWorkflowOverview(AuthPrincipal principal, int applicationTotal) {
        Long userId = principal.getUserId();
        boolean canViewWorkflow = principal.hasPermission(EasyPermissions.Workflow.VIEW);
        long pendingTotal = 0;
        long startedTotal = 0;
        long ccTotal = 0;
        long unreadCcTotal = 0;
        List<WfTask> pendingTasks = List.of();
        List<WfProcessInstance> startedInstances = List.of();
        List<WfCc> ccItems = List.of();
        Map<Long, WfProcessInstance> relatedInstances = Map.of();

        if (canViewWorkflow) {
            Page<WfTask> pendingPage = workflowTaskService.page(new Page<>(1, WORKBENCH_LIST_LIMIT), pendingTaskQuery(userId));
            pendingTotal = pendingPage.getTotal();
            startedTotal = workflowInstanceService.count(startedInstanceQuery(userId))
                    + workflowHistoricInstanceService.count(startedHistoricInstanceQuery(userId));
            WorkflowCcSummary runningCcSummary = workflowCcService.countSummaryByReceiverId(userId);
            WorkflowCcSummary historicCcSummary = workflowHistoricCcService.countSummaryByReceiverId(userId);
            ccTotal = safeTotal(runningCcSummary) + safeTotal(historicCcSummary);
            unreadCcTotal = safeUnreadTotal(runningCcSummary) + safeUnreadTotal(historicCcSummary);

            pendingTasks = pendingPage.getRecords();
            startedInstances = recentStartedInstances(userId);
            ccItems = recentCcItems(userId);

            relatedInstances = instanceMap(Stream.concat(
                            pendingTasks.stream().map(WfTask::getInstanceId),
                            ccItems.stream().map(WfCc::getInstanceId))
                    .filter(Objects::nonNull)
                    .toList());
        }
        Map<Long, WfProcessInstance> instanceLookup = relatedInstances;

        return EnterpriseWorkbenchOverview.WorkflowOverview.builder()
                .metrics(List.of(
                        workflowMetric("pending", "我的待办", pendingTotal, "需要我处理的流程任务", "warning", "/workflow/tasks?tab=pending", EasyPermissions.Workflow.VIEW),
                        workflowMetric("started", "我发起的流程", startedTotal, "查看申请进度和流转记录", "primary", "/workflow/tasks?tab=started", EasyPermissions.Workflow.VIEW),
                        workflowMetric("cc", "抄送我的", unreadCcTotal + "/" + ccTotal, "未读 / 全部抄送", "info", "/workflow/tasks?tab=cc", EasyPermissions.Workflow.VIEW),
                        workflowMetric("applications", "常用申请", applicationTotal, "可发起的高频流程入口", "success", "/workflow/start", EasyPermissions.Workflow.INSTANCE_START)
                ).stream().filter(metric -> principal.hasPermission(metric.getPermission())).toList())
                .pendingTasks(pendingTasks.stream().map(task -> taskBrief(task, instanceLookup.get(task.getInstanceId()))).toList())
                .startedInstances(startedInstances.stream().map(this::instanceBrief).toList())
                .ccItems(ccItems.stream().map(cc -> ccBrief(cc, instanceLookup.get(cc.getInstanceId()))).toList())
                .build();
    }

    private List<EnterpriseWorkbenchOverview.ApplicationEntry> buildApplications() {
        return List.of(
                application("请假申请", "提交请假、调休等申请，并进入部门审批流。", "/workflow/leave", EasyPermissions.Workflow.INSTANCE_START, "Document"),
                application("采购申请", "提交办公用品、设备和服务采购，按预算金额自动分流。", "/workflow/purchase", EasyPermissions.Workflow.INSTANCE_START, "ShoppingCart"),
                application("报修申请", "提交设备、网络和办公设施报修，由运维受理并留痕。", "/workflow/repair", EasyPermissions.Workflow.INSTANCE_START, "Tools")
        );
    }

    private List<EnterpriseWorkbenchOverview.ApplicationEntry> allowedApplications(AuthPrincipal principal) {
        return buildApplications().stream()
                .filter(item -> principal.hasPermission(item.getPermission()))
                .toList();
    }

    private LambdaQueryWrapper<WfTask> pendingTaskQuery(Long userId) {
        return Wrappers.<WfTask>lambdaQuery()
                .eq(WfTask::getAssigneeId, userId)
                .eq(WfTask::getStatus, "PENDING")
                .orderByDesc(WfTask::getStartedAt);
    }

    private LambdaQueryWrapper<WfProcessInstance> startedInstanceQuery(Long userId) {
        return Wrappers.<WfProcessInstance>lambdaQuery()
                .eq(WfProcessInstance::getInitiatorId, userId)
                .orderByDesc(WfProcessInstance::getStartedAt);
    }

    private LambdaQueryWrapper<WfHistoricProcessInstance> startedHistoricInstanceQuery(Long userId) {
        return Wrappers.<WfHistoricProcessInstance>lambdaQuery()
                .eq(WfHistoricProcessInstance::getInitiatorId, userId)
                .orderByDesc(WfHistoricProcessInstance::getStartedAt);
    }

    private List<WfProcessInstance> recentStartedInstances(Long userId) {
        List<WfProcessInstance> instances = new ArrayList<>(workflowInstanceService
                .page(new Page<>(1, WORKBENCH_LIST_LIMIT, false), startedInstanceQuery(userId))
                .getRecords());
        instances.addAll(workflowHistoricInstanceService
                .page(new Page<>(1, WORKBENCH_LIST_LIMIT, false), startedHistoricInstanceQuery(userId))
                .getRecords()
                .stream()
                .map(workflowArchiveService::toRuntimeInstance)
                .toList());
        return instances.stream()
                .sorted(Comparator.comparing(WfProcessInstance::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(WORKBENCH_LIST_LIMIT)
                .toList();
    }

    private LambdaQueryWrapper<WfCc> ccQuery(Long userId, Integer readStatus) {
        LambdaQueryWrapper<WfCc> queryWrapper = Wrappers.<WfCc>lambdaQuery()
                .eq(WfCc::getReceiverId, userId);
        if (readStatus != null) {
            queryWrapper.eq(WfCc::getReadStatus, readStatus);
        }
        return queryWrapper.orderByDesc(WfCc::getCreatedAt);
    }

    private LambdaQueryWrapper<WfHistoricCc> historicCcQuery(Long userId, Integer readStatus) {
        LambdaQueryWrapper<WfHistoricCc> queryWrapper = Wrappers.<WfHistoricCc>lambdaQuery()
                .eq(WfHistoricCc::getReceiverId, userId);
        if (readStatus != null) {
            queryWrapper.eq(WfHistoricCc::getReadStatus, readStatus);
        }
        return queryWrapper.orderByDesc(WfHistoricCc::getCreatedAt);
    }

    private List<WfCc> recentCcItems(Long userId) {
        List<WfCc> ccItems = new ArrayList<>(workflowCcService
                .page(new Page<>(1, WORKBENCH_LIST_LIMIT, false), ccQuery(userId, null))
                .getRecords());
        ccItems.addAll(workflowHistoricCcService
                .page(new Page<>(1, WORKBENCH_LIST_LIMIT, false), historicCcQuery(userId, null))
                .getRecords()
                .stream()
                .map(workflowArchiveService::toRuntimeCc)
                .toList());
        return ccItems.stream()
                .sorted(Comparator.comparing(WfCc::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(WORKBENCH_LIST_LIMIT)
                .toList();
    }

    private Map<Long, WfProcessInstance> instanceMap(Collection<Long> instanceIds) {
        List<Long> ids = instanceIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return workflowArchiveService.instanceMap(ids);
    }

    private long safeTotal(WorkflowCcSummary summary) {
        return summary == null ? 0 : summary.getTotal();
    }

    private long safeUnreadTotal(WorkflowCcSummary summary) {
        return summary == null ? 0 : summary.getUnreadTotal();
    }

    private EnterpriseWorkbenchOverview.WorkflowMetric workflowMetric(String key,
                                                                      String title,
                                                                      Object value,
                                                                      String hint,
                                                                      String tone,
                                                                      String path,
                                                                      String permission) {
        return EnterpriseWorkbenchOverview.WorkflowMetric.builder()
                .key(key)
                .title(title)
                .value(String.valueOf(value))
                .hint(hint)
                .tone(tone)
                .path(path)
                .permission(permission)
                .build();
    }

    private EnterpriseWorkbenchOverview.WorkflowTaskBrief taskBrief(WfTask task, WfProcessInstance instance) {
        return EnterpriseWorkbenchOverview.WorkflowTaskBrief.builder()
                .id(task.getId())
                .instanceId(task.getInstanceId())
                .title(instance == null ? "流程 " + task.getInstanceId() : instance.getTitle())
                .nodeName(task.getNodeName())
                .businessType(instance == null ? "" : instance.getBusinessType())
                .businessId(instance == null ? "" : instance.getBusinessId())
                .status(task.getStatus())
                .startedAt(task.getStartedAt())
                .build();
    }

    private EnterpriseWorkbenchOverview.WorkflowInstanceBrief instanceBrief(WfProcessInstance instance) {
        return EnterpriseWorkbenchOverview.WorkflowInstanceBrief.builder()
                .id(instance.getId())
                .title(instance.getTitle())
                .businessType(instance.getBusinessType())
                .businessId(instance.getBusinessId())
                .status(instance.getStatus())
                .startedAt(instance.getStartedAt())
                .build();
    }

    private EnterpriseWorkbenchOverview.WorkflowCcBrief ccBrief(WfCc cc, WfProcessInstance instance) {
        return EnterpriseWorkbenchOverview.WorkflowCcBrief.builder()
                .id(cc.getId())
                .instanceId(cc.getInstanceId())
                .title(instance == null ? "流程 " + cc.getInstanceId() : instance.getTitle())
                .nodeKey(cc.getNodeKey())
                .nodeName(cc.getNodeName())
                .readStatus(cc.getReadStatus())
                .createdAt(cc.getCreatedAt())
                .build();
    }

    private EnterpriseWorkbenchOverview.ApplicationEntry application(String title,
                                                                    String description,
                                                                    String path,
                                                                    String permission,
                                                                    String icon) {
        return EnterpriseWorkbenchOverview.ApplicationEntry.builder()
                .title(title)
                .description(description)
                .path(path)
                .permission(permission)
                .icon(icon)
                .build();
    }

}
