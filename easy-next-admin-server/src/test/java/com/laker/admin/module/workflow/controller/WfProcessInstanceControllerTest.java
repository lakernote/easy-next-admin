package com.laker.admin.module.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.annotation.EasyPermissionMode;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.exception.EasyForbiddenException;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.service.IWfHistoricProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfWorkflowRuntimeService;
import com.laker.admin.module.workflow.support.WorkflowArchiveService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class WfProcessInstanceControllerTest {

    private final IWfProcessInstanceService instanceService = mock(IWfProcessInstanceService.class);
    private final IWfHistoricProcessInstanceService historicInstanceService = mock(IWfHistoricProcessInstanceService.class);
    private final IWfWorkflowRuntimeService runtimeService = mock(IWfWorkflowRuntimeService.class);
    private final WorkflowArchiveService archiveService = mock(WorkflowArchiveService.class);
    private final WfProcessInstanceController controller = new WfProcessInstanceController(
            instanceService,
            historicInstanceService,
            runtimeService,
            archiveService);

    @AfterEach
    void tearDown() {
        EasySecurityContext.clear();
    }

    @Test
    void instanceReadEndpointsShouldAcceptViewOrInstanceManagePermission() throws Exception {
        Method pageMethod = WfProcessInstanceController.class.getMethod("page",
                long.class, long.class, String.class, String.class, String.class, boolean.class, boolean.class, String.class);
        Method detailMethod = WfProcessInstanceController.class.getMethod("detail", Long.class);

        assertReadPermission(pageMethod);
        assertReadPermission(detailMethod);
    }

    @Test
    void pageShouldAllowManagePermissionForNonSuperAdmin() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(7L)
                .superAdmin(false)
                .permissions(List.of(EasyPermissions.Workflow.VIEW, EasyPermissions.Workflow.INSTANCE_MANAGE))
                .build());
        when(instanceService.page(any(Page.class), any())).thenReturn(new Page<WfProcessInstance>(1, 10));

        var response = controller.page(1, 10, null, null, null, false, true, "RUNTIME");

        assertThat(response.getData().total()).isZero();
    }

    @Test
    void pageShouldQueryRuntimeOnlyWhenScopeIsRuntime() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(7L)
                .superAdmin(false)
                .permissions(List.of(EasyPermissions.Workflow.VIEW, EasyPermissions.Workflow.INSTANCE_MANAGE))
                .build());
        when(instanceService.page(any(Page.class), any())).thenReturn(new Page<WfProcessInstance>(1, 10));

        controller.page(1, 10, null, null, null, false, true, "RUNTIME");

        verify(instanceService).page(any(Page.class), any());
        verifyNoInteractions(historicInstanceService);
    }

    @Test
    void pageShouldQueryHistoryOnlyWhenScopeIsHistory() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(7L)
                .superAdmin(false)
                .permissions(List.of(EasyPermissions.Workflow.VIEW, EasyPermissions.Workflow.INSTANCE_MANAGE))
                .build());
        when(historicInstanceService.page(any(Page.class), any())).thenReturn(new Page<>(1, 10));

        controller.page(1, 10, null, null, null, false, true, "HISTORY");

        verify(historicInstanceService).page(any(Page.class), any());
        verifyNoInteractions(instanceService);
    }

    @Test
    void pageShouldRejectUnsupportedScope() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(7L)
                .superAdmin(false)
                .permissions(List.of(EasyPermissions.Workflow.VIEW, EasyPermissions.Workflow.INSTANCE_MANAGE))
                .build());

        assertThatThrownBy(() -> controller.page(1, 10, null, null, null, false, true, "ALL"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程实例范围");

        verifyNoInteractions(instanceService, historicInstanceService, runtimeService, archiveService);
    }

    @Test
    void pageShouldRejectMissingScope() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(7L)
                .superAdmin(false)
                .permissions(List.of(EasyPermissions.Workflow.VIEW, EasyPermissions.Workflow.INSTANCE_MANAGE))
                .build());

        assertThatThrownBy(() -> controller.page(1, 10, null, null, null, false, true, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程实例范围");

        verifyNoInteractions(instanceService, historicInstanceService, runtimeService, archiveService);
    }

    @Test
    void pageShouldRejectManageModeWithoutManagePermission() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(7L)
                .superAdmin(false)
                .permissions(List.of(EasyPermissions.Workflow.VIEW))
                .build());

        assertThatThrownBy(() -> controller.page(1, 10, null, null, null, false, true, "RUNTIME"))
                .isInstanceOf(EasyForbiddenException.class)
                .hasMessageContaining("缺少流程实例管理权限");

        verifyNoInteractions(instanceService, historicInstanceService, runtimeService, archiveService);
    }

    private void assertReadPermission(Method method) {
        EasyPermission permission = method.getAnnotation(EasyPermission.class);
        assertThat(permission.mode()).isEqualTo(EasyPermissionMode.ANY);
        assertThat(permission.value()).containsExactlyInAnyOrder(
                EasyPermissions.Workflow.VIEW,
                EasyPermissions.Workflow.INSTANCE_MANAGE);
    }
}
