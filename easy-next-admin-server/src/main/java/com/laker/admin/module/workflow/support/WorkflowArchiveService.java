package com.laker.admin.module.workflow.support;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfHistoricCc;
import com.laker.admin.module.workflow.entity.WfHistoricProcessInstance;
import com.laker.admin.module.workflow.entity.WfHistoricTask;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.service.IWfCcService;
import com.laker.admin.module.workflow.service.IWfHistoricCcService;
import com.laker.admin.module.workflow.service.IWfHistoricProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfHistoricTaskService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfTaskService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class WorkflowArchiveService {
    private final IWfProcessInstanceService instanceService;
    private final IWfTaskService taskService;
    private final IWfCcService ccService;
    private final IWfHistoricProcessInstanceService historicInstanceService;
    private final IWfHistoricTaskService historicTaskService;
    private final IWfHistoricCcService historicCcService;

    public WorkflowArchiveService(IWfProcessInstanceService instanceService,
                                  IWfTaskService taskService,
                                  IWfCcService ccService,
                                  IWfHistoricProcessInstanceService historicInstanceService,
                                  IWfHistoricTaskService historicTaskService,
                                  IWfHistoricCcService historicCcService) {
        this.instanceService = instanceService;
        this.taskService = taskService;
        this.ccService = ccService;
        this.historicInstanceService = historicInstanceService;
        this.historicTaskService = historicTaskService;
        this.historicCcService = historicCcService;
    }

    public void archiveTask(WfTask task) {
        if (!historicTaskService.saveOrUpdate(toHistoricTask(task))) {
            throw new BusinessException("流程任务归档失败");
        }
        if (!taskService.removeById(task.getId())) {
            throw new BusinessException("流程任务归档失败，请刷新后重试");
        }
    }

    public void archiveFinishedInstance(WfProcessInstance instance) {
        if (!historicInstanceService.saveOrUpdate(toHistoricInstance(instance))) {
            throw new BusinessException("流程实例归档失败");
        }
        archiveCc(instance.getId());
        if (!instanceService.removeById(instance.getId())) {
            throw new BusinessException("流程实例归档失败，请刷新后重试");
        }
    }

    public Map<Long, WfProcessInstance> instanceMap(Collection<Long> instanceIds) {
        List<Long> ids = instanceIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, WfProcessInstance> instances = instanceService.listByIds(ids).stream()
                .collect(Collectors.toMap(WfProcessInstance::getId, Function.identity(), (left, right) -> left));
        List<Long> missingIds = ids.stream()
                .filter(id -> !instances.containsKey(id))
                .toList();
        if (!missingIds.isEmpty()) {
            historicInstanceService.listByIds(missingIds).stream()
                    .map(this::toRuntimeInstance)
                    .forEach(instance -> instances.put(instance.getId(), instance));
        }
        return instances;
    }

    public WfProcessInstance toRuntimeInstance(WfHistoricProcessInstance historicInstance) {
        WfProcessInstance instance = new WfProcessInstance();
        BeanUtils.copyProperties(historicInstance, instance);
        return instance;
    }

    public WfTask toRuntimeTask(WfHistoricTask historicTask) {
        WfTask task = new WfTask();
        BeanUtils.copyProperties(historicTask, task);
        return task;
    }

    public WfCc toRuntimeCc(WfHistoricCc historicCc) {
        WfCc cc = new WfCc();
        BeanUtils.copyProperties(historicCc, cc);
        return cc;
    }

    private void archiveCc(Long instanceId) {
        List<WfCc> ccList = ccService.lambdaQuery()
                .eq(WfCc::getInstanceId, instanceId)
                .list();
        if (ccList.isEmpty()) {
            return;
        }
        historicCcService.saveOrUpdateBatch(ccList.stream().map(this::toHistoricCc).toList());
        ccService.removeBatchByIds(ccList.stream().map(WfCc::getId).toList());
    }

    private WfHistoricProcessInstance toHistoricInstance(WfProcessInstance instance) {
        WfHistoricProcessInstance historicInstance = new WfHistoricProcessInstance();
        BeanUtils.copyProperties(instance, historicInstance);
        return historicInstance;
    }

    private WfHistoricTask toHistoricTask(WfTask task) {
        WfHistoricTask historicTask = new WfHistoricTask();
        BeanUtils.copyProperties(task, historicTask);
        return historicTask;
    }

    private WfHistoricCc toHistoricCc(WfCc cc) {
        WfHistoricCc historicCc = new WfHistoricCc();
        BeanUtils.copyProperties(cc, historicCc);
        return historicCc;
    }
}
