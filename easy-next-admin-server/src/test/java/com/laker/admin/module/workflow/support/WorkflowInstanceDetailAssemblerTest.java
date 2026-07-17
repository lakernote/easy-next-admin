package com.laker.admin.module.workflow.support;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.laker.admin.module.workflow.dto.WfParticipantView;
import com.laker.admin.module.workflow.dto.WfProcessInstanceDetail;
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
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowInstanceDetailAssemblerTest {

    private final IWfProcessDefinitionService definitionService = mock(IWfProcessDefinitionService.class);
    private final IWfProcessDefinitionVersionService versionService = mock(IWfProcessDefinitionVersionService.class);
    private final IWfTaskService taskService = mock(IWfTaskService.class);
    private final IWfHistoricTaskService historicTaskService = mock(IWfHistoricTaskService.class);
    private final IWfEventService eventService = mock(IWfEventService.class);
    private final IWfCcService ccService = mock(IWfCcService.class);
    private final IWfHistoricCcService historicCcService = mock(IWfHistoricCcService.class);
    private final WorkflowArchiveService archiveService = mock(WorkflowArchiveService.class);
    private final WorkflowParticipantResolver participantResolver = mock(WorkflowParticipantResolver.class);
    private final WorkflowVariableSnapshotService variableSnapshotService = mock(WorkflowVariableSnapshotService.class);
    private final WorkflowInstanceDetailAssembler assembler = new WorkflowInstanceDetailAssembler(
            definitionService,
            versionService,
            taskService,
            historicTaskService,
            eventService,
            ccService,
            historicCcService,
            archiveService,
            participantResolver,
            variableSnapshotService);

    @Test
    void shouldAssembleDetailWithSortedRuntimeAndHistoricRecords() {
        WfProcessInstance instance = instance();
        WfProcessDefinition definition = definition();
        WfProcessDefinitionVersion version = version();
        WfHistoricTask historicTask = historicTask(10L, LocalDateTime.of(2026, 6, 22, 9, 0));
        WfTask runtimeTask = task(11L, LocalDateTime.of(2026, 6, 22, 10, 0));
        WfTask archivedTask = task(10L, historicTask.getStartedAt());
        WfHistoricCc historicCc = historicCc(20L, LocalDateTime.of(2026, 6, 22, 9, 30));
        WfCc runtimeCc = cc(21L, LocalDateTime.of(2026, 6, 22, 10, 30));
        WfCc archivedCc = cc(20L, historicCc.getCreatedAt());
        WfEvent event = event(30L);
        List<WfParticipantView> participants = List.of(WfParticipantView.builder()
                .name("审批人")
                .value("2001")
                .userName("manager")
                .build());
        Map<String, Object> variables = Map.of("amount", 1000);

        when(definitionService.getById(1L)).thenReturn(definition);
        when(versionService.getById(2L)).thenReturn(version);
        when(archiveService.toRuntimeTask(historicTask)).thenReturn(archivedTask);
        when(archiveService.toRuntimeCc(historicCc)).thenReturn(archivedCc);
        when(variableSnapshotService.readInstanceVariables(instance)).thenReturn(variables);
        when(participantResolver.resolve(eq(instance), anyList(), anyList(), anyList())).thenReturn(participants);
        stubList(historicTaskService, List.of(historicTask));
        stubList(taskService, List.of(runtimeTask));
        stubList(eventService, List.of(event));
        stubList(historicCcService, List.of(historicCc));
        stubList(ccService, List.of(runtimeCc));

        WfProcessInstanceDetail detail = assembler.assemble(instance);

        assertThat(detail.getInstance().getId()).isEqualTo(99L);
        assertThat(detail.getDefinition().getProcessKey()).isEqualTo("purchase");
        assertThat(detail.getVersion().getGraphJson()).isEqualTo("{\"version\":true}");
        assertThat(detail.getGraphJson()).isEqualTo("{\"snapshot\":true}");
        assertThat(detail.getVariables()).isEqualTo(variables);
        assertThat(detail.getParticipants()).isEqualTo(participants);
        assertThat(detail.getTasks()).extracting("id").containsExactly(10L, 11L);
        assertThat(detail.getEvents()).extracting("id").containsExactly(30L);
        assertThat(detail.getCcList()).extracting("id").containsExactly(20L, 21L);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> void stubList(Object service, List<T> records) {
        LambdaQueryChainWrapper<T> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        doReturn(query).when(query).eq(any(), any());
        doReturn(query).when(query).orderByAsc((SFunction<T, ?>) any(SFunction.class));
        doReturn(records).when(query).list();
        if (service instanceof IWfHistoricTaskService typedService) {
            doReturn(query).when(typedService).lambdaQuery();
        } else if (service instanceof IWfTaskService typedService) {
            doReturn(query).when(typedService).lambdaQuery();
        } else if (service instanceof IWfEventService typedService) {
            doReturn(query).when(typedService).lambdaQuery();
        } else if (service instanceof IWfHistoricCcService typedService) {
            doReturn(query).when(typedService).lambdaQuery();
        } else if (service instanceof IWfCcService typedService) {
            doReturn(query).when(typedService).lambdaQuery();
        }
    }

    private WfProcessInstance instance() {
        WfProcessInstance instance = new WfProcessInstance();
        instance.setId(99L);
        instance.setDefinitionId(1L);
        instance.setVersionId(2L);
        instance.setProcessKey("purchase");
        instance.setTitle("采购申请");
        instance.setDefinitionSnapshotJson("{\"snapshot\":true}");
        return instance;
    }

    private WfProcessDefinition definition() {
        WfProcessDefinition definition = new WfProcessDefinition();
        definition.setId(1L);
        definition.setProcessKey("purchase");
        definition.setProcessName("采购审批");
        return definition;
    }

    private WfProcessDefinitionVersion version() {
        WfProcessDefinitionVersion version = new WfProcessDefinitionVersion();
        version.setId(2L);
        version.setDefinitionId(1L);
        version.setVersion(3);
        version.setGraphJson("{\"version\":true}");
        return version;
    }

    private WfHistoricTask historicTask(Long id, LocalDateTime startedAt) {
        WfHistoricTask task = new WfHistoricTask();
        task.setId(id);
        task.setInstanceId(99L);
        task.setNodeKey("archive");
        task.setStartedAt(startedAt);
        return task;
    }

    private WfTask task(Long id, LocalDateTime startedAt) {
        WfTask task = new WfTask();
        task.setId(id);
        task.setInstanceId(99L);
        task.setNodeKey("runtime");
        task.setStartedAt(startedAt);
        return task;
    }

    private WfHistoricCc historicCc(Long id, LocalDateTime createdAt) {
        WfHistoricCc cc = new WfHistoricCc();
        cc.setId(id);
        cc.setInstanceId(99L);
        cc.setCreatedAt(createdAt);
        return cc;
    }

    private WfCc cc(Long id, LocalDateTime createdAt) {
        WfCc cc = new WfCc();
        cc.setId(id);
        cc.setInstanceId(99L);
        cc.setCreatedAt(createdAt);
        return cc;
    }

    private WfEvent event(Long id) {
        WfEvent event = new WfEvent();
        event.setId(id);
        event.setInstanceId(99L);
        event.setCreatedAt(LocalDateTime.of(2026, 6, 22, 11, 0));
        return event;
    }
}
