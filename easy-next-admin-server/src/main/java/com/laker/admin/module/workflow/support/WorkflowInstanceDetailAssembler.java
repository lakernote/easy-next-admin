package com.laker.admin.module.workflow.support;

import com.laker.admin.infrastructure.security.datascope.context.EasyDataScopeContext;
import com.laker.admin.module.workflow.dto.WfCcView;
import com.laker.admin.module.workflow.dto.WfEventView;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionVersionView;
import com.laker.admin.module.workflow.dto.WfProcessDefinitionView;
import com.laker.admin.module.workflow.dto.WfProcessInstanceDetail;
import com.laker.admin.module.workflow.dto.WfProcessInstanceView;
import com.laker.admin.module.workflow.dto.WfTaskView;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfEvent;
import com.laker.admin.module.workflow.entity.WfHistoricCc;
import com.laker.admin.module.workflow.entity.WfHistoricTask;
import com.laker.admin.module.workflow.entity.WfProcessDefinition;
import com.laker.admin.module.workflow.entity.WfProcessDefinitionVersion;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.service.IWfCcService;
import com.laker.admin.module.workflow.service.IWfEventService;
import com.laker.admin.module.workflow.service.IWfHistoricCcService;
import com.laker.admin.module.workflow.service.IWfHistoricTaskService;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionService;
import com.laker.admin.module.workflow.service.IWfProcessDefinitionVersionService;
import com.laker.admin.module.workflow.service.IWfTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WorkflowInstanceDetailAssembler {
    private final IWfProcessDefinitionService definitionService;
    private final IWfProcessDefinitionVersionService versionService;
    private final IWfTaskService taskService;
    private final IWfHistoricTaskService historicTaskService;
    private final IWfEventService eventService;
    private final IWfCcService ccService;
    private final IWfHistoricCcService historicCcService;
    private final WorkflowArchiveService archiveService;
    private final WorkflowParticipantResolver participantResolver;
    private final WorkflowVariableSnapshotService variableSnapshotService;

    public WfProcessInstanceDetail assemble(WfProcessInstance instance) {
        WfProcessDefinition definition = definitionService.getById(instance.getDefinitionId());
        WfProcessDefinitionVersion version = versionService.getById(instance.getVersionId());
        List<WfTask> tasks = instanceTasks(instance.getId());
        List<WfEvent> events = instanceEvents(instance.getId());
        List<WfCc> ccList = instanceCcList(instance.getId());

        WfProcessInstanceDetail detail = new WfProcessInstanceDetail();
        detail.setInstance(WfProcessInstanceView.from(instance));
        detail.setDefinition(WfProcessDefinitionView.from(definition));
        detail.setVersion(WfProcessDefinitionVersionView.from(version));
        detail.setGraphJson(StringUtils.hasText(instance.getDefinitionSnapshotJson())
                ? instance.getDefinitionSnapshotJson()
                : version == null ? null : version.getGraphJson());
        detail.setVariables(variableSnapshotService.readInstanceVariables(instance));
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
        tasks.sort(Comparator.comparing(WfTask::getStartedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WfTask::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return tasks;
    }

    private List<WfEvent> instanceEvents(Long instanceId) {
        return eventService.lambdaQuery()
                .eq(WfEvent::getInstanceId, instanceId)
                .orderByAsc(WfEvent::getCreatedAt)
                .list();
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
        ccList.sort(Comparator.comparing(WfCc::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(WfCc::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        return ccList;
    }
}
