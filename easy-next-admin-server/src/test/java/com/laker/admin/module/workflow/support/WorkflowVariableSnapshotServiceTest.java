package com.laker.admin.module.workflow.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.module.workflow.dto.WfStartProcessRequest;
import com.laker.admin.module.workflow.dto.WfTaskActionRequest;
import com.laker.admin.module.workflow.entity.WfProcessDefinition;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class WorkflowVariableSnapshotServiceTest {

    private final WorkflowInstanceStateGuard stateGuard = mock(WorkflowInstanceStateGuard.class);
    private final EasyJsonCodec jsonCodec = new EasyJsonCodec(new ObjectMapper());
    private final WorkflowVariableSnapshotService snapshotService =
            new WorkflowVariableSnapshotService(jsonCodec, stateGuard);

    @Test
    void shouldBuildStartVariablesWithBusinessContextAndRequestVariables() {
        WfProcessDefinition definition = new WfProcessDefinition();
        definition.setId(10L);
        definition.setProcessKey("leave");

        WfStartProcessRequest request = new WfStartProcessRequest();
        request.setBusinessType("LEAVE");
        request.setBusinessId("L-20260622-001");
        request.setTitle("请假申请");
        request.setVariables(new LinkedHashMap<>(Map.of(
                "days", 3,
                "title", "覆盖标题"
        )));

        Map<String, Object> variables = snapshotService.startVariables(definition, request, 1001L);

        assertThat(variables)
                .containsEntry("definitionId", 10L)
                .containsEntry("processKey", "leave")
                .containsEntry("businessType", "LEAVE")
                .containsEntry("businessId", "L-20260622-001")
                .containsEntry("initiatorId", 1001L)
                .containsEntry("days", 3)
                .containsEntry("title", "覆盖标题");
    }

    @Test
    void shouldPersistActionVariablesWhenRequestSuppliesVariables() {
        WfProcessInstance instance = workflowInstance();
        instance.setVariablesJson("{\"days\":2,\"reason\":\"调休\"}");

        WfTaskActionRequest request = new WfTaskActionRequest();
        request.setVariables(new LinkedHashMap<>(Map.of(
                "days", 4,
                "approvedByHr", true
        )));

        Map<String, Object> variables = snapshotService.actionVariables(instance, request, 2002L);

        assertThat(variables)
                .containsEntry("days", 4)
                .containsEntry("reason", "调休")
                .containsEntry("approvedByHr", true)
                .containsEntry("instanceId", 99L)
                .containsEntry("operatorId", 2002L);

        Map<String, Object> persisted = jsonCodec.fromJson(
                instance.getVariablesJson(),
                new TypeReference<Map<String, Object>>() {
                });
        assertThat(persisted)
                .containsEntry("days", 4)
                .containsEntry("reason", "调休")
                .containsEntry("approvedByHr", true)
                .containsEntry("instanceId", 99)
                .containsEntry("operatorId", 2002);
        verify(stateGuard).updateOrThrow(instance);
    }

    @Test
    void shouldNotPersistActionSnapshotWhenRequestHasNoVariables() {
        WfProcessInstance instance = workflowInstance();
        instance.setVariablesJson("{\"days\":2}");

        WfTaskActionRequest request = new WfTaskActionRequest();

        Map<String, Object> variables = snapshotService.actionVariables(instance, request, 2002L);

        assertThat(variables)
                .containsEntry("days", 2)
                .containsEntry("instanceId", 99L)
                .containsEntry("operatorId", 2002L);
        assertThat(instance.getVariablesJson()).isEqualTo("{\"days\":2}");
        verify(stateGuard, never()).updateOrThrow(instance);
    }

    @Test
    void shouldReturnMutableEmptyVariablesWhenInstanceHasNoSnapshot() {
        WfProcessInstance instance = workflowInstance();

        Map<String, Object> variables = snapshotService.readInstanceVariables(instance);
        variables.put("days", 1);

        assertThat(variables).containsEntry("days", 1);
    }

    private WfProcessInstance workflowInstance() {
        WfProcessInstance instance = new WfProcessInstance();
        instance.setId(99L);
        instance.setProcessKey("leave");
        instance.setBusinessType("LEAVE");
        instance.setBusinessId("L-20260622-001");
        instance.setTitle("请假申请");
        instance.setInitiatorId(1001L);
        return instance;
    }
}
