package com.laker.admin.module.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.infrastructure.security.service.EasyAuthService;
import com.laker.admin.module.system.dto.workbench.EnterpriseWorkbenchOverview;
import com.laker.admin.module.workflow.dto.WorkflowCcSummary;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.service.IWfCcService;
import com.laker.admin.module.workflow.service.IWfHistoricCcService;
import com.laker.admin.module.workflow.service.IWfHistoricProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfTaskService;
import com.laker.admin.module.workflow.support.WorkflowArchiveService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnterpriseWorkbenchServiceTest {

    private final IWfProcessInstanceService workflowInstanceService = mock(IWfProcessInstanceService.class);
    private final IWfHistoricProcessInstanceService workflowHistoricInstanceService = mock(IWfHistoricProcessInstanceService.class);
    private final IWfTaskService workflowTaskService = mock(IWfTaskService.class);
    private final IWfCcService workflowCcService = mock(IWfCcService.class);
    private final IWfHistoricCcService workflowHistoricCcService = mock(IWfHistoricCcService.class);
    private final EasyAuthService easyAuthService = mock(EasyAuthService.class);
    private final WorkflowArchiveService workflowArchiveService = mock(WorkflowArchiveService.class);
    private final EnterpriseWorkbenchService service = new EnterpriseWorkbenchService(
            workflowInstanceService,
            workflowHistoricInstanceService,
            workflowTaskService,
            workflowCcService,
            workflowHistoricCcService,
            easyAuthService,
            workflowArchiveService
    );

    @Test
    void buildOverviewShouldHideApplicationMetricWithoutStartPermission() {
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(1001L)
                .permissions(List.of(EasyPermissions.Dashboard.VIEW, EasyPermissions.Workflow.VIEW))
                .build();
        when(easyAuthService.currentPrincipal()).thenReturn(principal);
        when(workflowTaskService.page(any(), any())).thenReturn(new Page<WfTask>());
        when(workflowInstanceService.page(any(), any())).thenReturn(new Page<WfProcessInstance>());
        when(workflowCcService.page(any(), any())).thenReturn(new Page<WfCc>());
        when(workflowHistoricInstanceService.page(any(), any())).thenReturn(new Page<>());
        when(workflowHistoricCcService.page(any(), any())).thenReturn(new Page<>());
        when(workflowArchiveService.instanceMap(any())).thenReturn(Map.of());

        EnterpriseWorkbenchOverview overview = service.buildOverview();

        assertThat(overview.getApplications()).isEmpty();
        assertThat(overview.getWorkflow().getMetrics())
                .extracting(EnterpriseWorkbenchOverview.WorkflowMetric::getKey)
                .containsExactly("pending", "started", "cc");
    }

    @Test
    void buildOverviewShouldExposeCoreWorkflowApplicationsWhenUserCanStart() {
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(1001L)
                .permissions(List.of(EasyPermissions.Dashboard.VIEW, EasyPermissions.Workflow.VIEW, EasyPermissions.Workflow.INSTANCE_START))
                .build();
        when(easyAuthService.currentPrincipal()).thenReturn(principal);
        when(workflowTaskService.page(any(), any())).thenReturn(new Page<WfTask>());
        when(workflowInstanceService.page(any(), any())).thenReturn(new Page<WfProcessInstance>());
        when(workflowCcService.page(any(), any())).thenReturn(new Page<WfCc>());
        when(workflowHistoricInstanceService.page(any(), any())).thenReturn(new Page<>());
        when(workflowHistoricCcService.page(any(), any())).thenReturn(new Page<>());
        when(workflowArchiveService.instanceMap(any())).thenReturn(Map.of());

        EnterpriseWorkbenchOverview overview = service.buildOverview();

        assertThat(overview.getApplications())
                .extracting(EnterpriseWorkbenchOverview.ApplicationEntry::getTitle)
                .containsExactly("请假申请", "采购申请", "报修申请");
        assertThat(overview.getApplications())
                .extracting(EnterpriseWorkbenchOverview.ApplicationEntry::getPath)
                .containsExactly("/workflow/leave", "/workflow/purchase", "/workflow/repair");
        assertThat(overview.getWorkflow().getMetrics().get(3).getValue()).isEqualTo("3");
        assertThat(overview.getWorkflow().getMetrics().get(3).getPath()).isEqualTo("/workflow/start");
        assertThat(overview.getWorkflow().getMetrics().get(3).getPermission()).isEqualTo(EasyPermissions.Workflow.INSTANCE_START);
    }

    @Test
    void buildOverviewShouldUseCcSummaryInsteadOfSeparateCcCounts() {
        AuthPrincipal principal = AuthPrincipal.builder()
                .userId(1001L)
                .permissions(List.of(EasyPermissions.Dashboard.VIEW, EasyPermissions.Workflow.VIEW))
                .build();
        WorkflowCcSummary runningCc = new WorkflowCcSummary();
        runningCc.setTotal(3L);
        runningCc.setUnreadTotal(1L);
        WorkflowCcSummary historicCc = new WorkflowCcSummary();
        historicCc.setTotal(2L);
        historicCc.setUnreadTotal(1L);
        when(easyAuthService.currentPrincipal()).thenReturn(principal);
        when(workflowTaskService.page(any(), any())).thenReturn(new Page<WfTask>());
        when(workflowCcService.countSummaryByReceiverId(1001L)).thenReturn(runningCc);
        when(workflowHistoricCcService.countSummaryByReceiverId(1001L)).thenReturn(historicCc);
        when(workflowInstanceService.page(any(), any())).thenReturn(new Page<WfProcessInstance>());
        when(workflowCcService.page(any(), any())).thenReturn(new Page<WfCc>());
        when(workflowHistoricInstanceService.page(any(), any())).thenReturn(new Page<>());
        when(workflowHistoricCcService.page(any(), any())).thenReturn(new Page<>());
        when(workflowArchiveService.instanceMap(any())).thenReturn(Map.of());

        EnterpriseWorkbenchOverview overview = service.buildOverview();

        assertThat(overview.getWorkflow().getMetrics())
                .filteredOn(metric -> "cc".equals(metric.getKey()))
                .singleElement()
                .extracting(EnterpriseWorkbenchOverview.WorkflowMetric::getValue)
                .isEqualTo("2/5");
        verify(workflowCcService).countSummaryByReceiverId(1001L);
        verify(workflowHistoricCcService).countSummaryByReceiverId(1001L);
        verify(workflowCcService, never()).count(any());
        verify(workflowHistoricCcService, never()).count(any());
    }
}
