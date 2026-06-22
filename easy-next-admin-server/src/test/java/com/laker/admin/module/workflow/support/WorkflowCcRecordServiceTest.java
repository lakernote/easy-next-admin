package com.laker.admin.module.workflow.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.service.IWfCcService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class WorkflowCcRecordServiceTest {

    private final IWfCcService ccService = mock(IWfCcService.class);
    private final WorkflowNotificationService notificationService = mock(WorkflowNotificationService.class);
    private final WorkflowCcRecordService ccRecordService =
            new WorkflowCcRecordService(ccService, notificationService);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateManualCcRecordsAndNotifyReceivers() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 16, 30);
        WfProcessInstance instance = instance();

        List<Long> receiverIds = ccRecordService.createRecords(
                instance, "approve", "部门审批", Arrays.asList(3001L, null, 3002L, 3001L), 1001L, now);

        assertThat(receiverIds).containsExactly(3001L, 3002L);
        ArgumentCaptor<Collection<WfCc>> captor = ArgumentCaptor.captor();
        verify(ccService).saveBatch(captor.capture());
        List<WfCc> savedRecords = List.copyOf(captor.getValue());
        assertThat(savedRecords).extracting(WfCc::getInstanceId).containsExactly(99L, 99L);
        assertThat(savedRecords).extracting(WfCc::getNodeKey).containsExactly("approve", "approve");
        assertThat(savedRecords).extracting(WfCc::getNodeName).containsExactly("部门审批", "部门审批");
        assertThat(savedRecords).extracting(WfCc::getReceiverId).containsExactly(3001L, 3002L);
        assertThat(savedRecords).extracting(WfCc::getReadStatus).containsExactly(0, 0);
        assertThat(savedRecords).extracting(WfCc::getCreatedAt).containsExactly(now, now);
        verify(notificationService).notifyCcReceivers(instance, savedRecords, 1001L);
    }

    @Test
    void shouldCreateNodeCcRecordsFromConfiguredUsersAndReturnDistinctReceivers() throws JsonProcessingException {
        LocalDateTime now = LocalDateTime.of(2026, 6, 22, 16, 35);
        WfProcessInstance instance = instance();
        WorkflowGraph.NodeInfo auditNode = ccNode("cc-audit", "审计备案", "[3001, \"3002\", \"bad\", null]");
        WorkflowGraph.NodeInfo financeNode = ccNode("cc-finance", "财务备案", "[\"3002\", 3003]");

        List<Long> receiverIds = ccRecordService.createRecords(instance, List.of(auditNode, financeNode), 1001L, now);

        assertThat(receiverIds).containsExactly(3001L, 3002L, 3003L);
        ArgumentCaptor<Collection<WfCc>> captor = ArgumentCaptor.captor();
        verify(ccService, times(2)).saveBatch(captor.capture());
        List<List<WfCc>> savedBatches = captor.getAllValues().stream()
                .map(List::copyOf)
                .toList();
        assertThat(savedBatches.get(0)).extracting(WfCc::getNodeKey).containsExactly("cc-audit", "cc-audit");
        assertThat(savedBatches.get(0)).extracting(WfCc::getReceiverId).containsExactly(3001L, 3002L);
        assertThat(savedBatches.get(1)).extracting(WfCc::getNodeKey).containsExactly("cc-finance", "cc-finance");
        assertThat(savedBatches.get(1)).extracting(WfCc::getReceiverId).containsExactly(3002L, 3003L);
    }

    @Test
    void shouldSkipPersistenceAndNotificationWhenNoReceivers() {
        List<Long> receiverIds = ccRecordService.createRecords(
                instance(), "approve", "部门审批", List.of(), 1001L, LocalDateTime.now());

        assertThat(receiverIds).isEmpty();
        verify(ccService, never()).saveBatch(org.mockito.ArgumentMatchers.anyCollection());
        verify(notificationService, never()).notifyCcReceivers(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    private WfProcessInstance instance() {
        WfProcessInstance instance = new WfProcessInstance();
        instance.setId(99L);
        return instance;
    }

    private WorkflowGraph.NodeInfo ccNode(String key, String name, String ccUserIdsJson) throws JsonProcessingException {
        return new WorkflowGraph.NodeInfo(
                key,
                name,
                WorkflowNodeShape.RECT,
                WorkflowNodeKind.CC,
                objectMapper.readTree("{\"properties\":{\"ccUserIds\":" + ccUserIdsJson + "}}")
        );
    }
}
