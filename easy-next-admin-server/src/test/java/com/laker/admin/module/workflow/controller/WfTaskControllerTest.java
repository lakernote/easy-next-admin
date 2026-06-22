package com.laker.admin.module.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.service.IWfHistoricTaskService;
import com.laker.admin.module.workflow.service.IWfTaskService;
import com.laker.admin.module.workflow.service.IWfWorkflowRuntimeService;
import com.laker.admin.module.workflow.support.WorkflowArchiveService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WfTaskControllerTest {

    private final IWfTaskService taskService = mock(IWfTaskService.class);
    private final IWfHistoricTaskService historicTaskService = mock(IWfHistoricTaskService.class);
    private final IWfWorkflowRuntimeService runtimeService = mock(IWfWorkflowRuntimeService.class);
    private final WorkflowArchiveService archiveService = mock(WorkflowArchiveService.class);
    private final WfTaskController controller = new WfTaskController(
            taskService,
            historicTaskService,
            runtimeService,
            archiveService);

    @AfterEach
    void tearDown() {
        EasySecurityContext.clear();
    }

    @Test
    void pageShouldReturnTaskRowsWithInstanceBrief() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(7L)
                .permissions(List.of(EasyPermissions.Workflow.VIEW))
                .build());
        WfTask task = new WfTask();
        task.setId(1L);
        task.setInstanceId(11L);
        task.setNodeKey("dept_approval");
        task.setNodeName("部门审批");
        task.setStatus("PENDING");
        Page<WfTask> taskPage = new Page<>(1, 10);
        taskPage.setRecords(List.of(task));
        taskPage.setTotal(1);
        when(taskService.page(any(), any())).thenReturn(taskPage);

        WfProcessInstance instance = new WfProcessInstance();
        instance.setId(11L);
        instance.setTitle("请假申请 L001");
        instance.setBusinessType("LEAVE");
        instance.setBusinessId("L001");
        instance.setStatus("RUNNING");
        when(archiveService.instanceMap(List.of(11L))).thenReturn(Map.of(11L, instance));

        PageResponse<?> response = controller.page(1, 10, "PENDING", null, null, null, true);

        Object row = response.getData().list().get(0);
        assertThat(row)
                .hasFieldOrPropertyWithValue("instanceTitle", "请假申请 L001")
                .hasFieldOrPropertyWithValue("businessType", "LEAVE")
                .hasFieldOrPropertyWithValue("businessId", "L001")
                .hasFieldOrPropertyWithValue("instanceStatus", "RUNNING");
    }

    @Test
    void pageShouldRejectMixedPendingAndHistoricStatuses() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(7L)
                .permissions(List.of(EasyPermissions.Workflow.VIEW))
                .build());

        assertThatThrownBy(() -> controller.page(1, 10, null, "PENDING,APPROVED", null, null, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待办任务和已办任务请分开查询");

        verifyNoInteractions(taskService, historicTaskService, runtimeService, archiveService);
    }
}
