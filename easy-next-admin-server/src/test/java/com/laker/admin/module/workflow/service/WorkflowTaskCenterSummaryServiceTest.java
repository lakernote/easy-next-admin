package com.laker.admin.module.workflow.service;

import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.workflow.dto.WfTaskCenterSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowTaskCenterSummaryServiceTest {
    private final IWfTaskService taskService = mock(IWfTaskService.class);
    private final IWfHistoricTaskService historicTaskService = mock(IWfHistoricTaskService.class);
    private final IWfProcessInstanceService instanceService = mock(IWfProcessInstanceService.class);
    private final IWfHistoricProcessInstanceService historicInstanceService = mock(IWfHistoricProcessInstanceService.class);
    private final IWfCcService ccService = mock(IWfCcService.class);
    private final IWfHistoricCcService historicCcService = mock(IWfHistoricCcService.class);
    private final WorkflowTaskCenterSummaryService service = new WorkflowTaskCenterSummaryService(
            taskService,
            historicTaskService,
            instanceService,
            historicInstanceService,
            ccService,
            historicCcService
    );

    @AfterEach
    void tearDown() {
        EasySecurityContext.clear();
    }

    @Test
    void summaryShouldUseOnlyCountQueriesForAllTaskCenterTabs() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(7L)
                .permissions(List.of(EasyPermissions.Workflow.VIEW))
                .build());
        when(taskService.count(any())).thenReturn(2L);
        when(historicTaskService.count(any())).thenReturn(3L);
        when(instanceService.count(any())).thenReturn(4L);
        when(historicInstanceService.count(any())).thenReturn(5L);
        when(ccService.count(any())).thenReturn(6L);
        when(historicCcService.count(any())).thenReturn(7L);

        WfTaskCenterSummary summary = service.summary();

        assertThat(summary.getPendingTotal()).isEqualTo(2);
        assertThat(summary.getDoneTotal()).isEqualTo(3);
        assertThat(summary.getStartedTotal()).isEqualTo(9);
        assertThat(summary.getCcTotal()).isEqualTo(13);
        verify(taskService, never()).page(any(), any());
        verify(historicTaskService, never()).page(any(), any());
        verify(instanceService, never()).page(any(), any());
        verify(historicInstanceService, never()).page(any(), any());
        verify(ccService, never()).page(any(), any());
        verify(historicCcService, never()).page(any(), any());
    }
}
