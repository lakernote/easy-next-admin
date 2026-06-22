package com.laker.admin.module.workflow.support;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.exception.EasyForbiddenException;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfHistoricCc;
import com.laker.admin.module.workflow.entity.WfHistoricTask;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.service.IWfCcService;
import com.laker.admin.module.workflow.service.IWfHistoricCcService;
import com.laker.admin.module.workflow.service.IWfHistoricTaskService;
import com.laker.admin.module.workflow.service.IWfTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

class WorkflowVisibilityGuardTest {

    private final IWfTaskService taskService = mock(IWfTaskService.class);
    private final IWfHistoricTaskService historicTaskService = mock(IWfHistoricTaskService.class);
    private final IWfCcService ccService = mock(IWfCcService.class);
    private final IWfHistoricCcService historicCcService = mock(IWfHistoricCcService.class);
    private final WorkflowVisibilityGuard guard = new WorkflowVisibilityGuard(
            taskService,
            historicTaskService,
            ccService,
            historicCcService);

    @AfterEach
    void tearDown() {
        EasySecurityContext.clear();
    }

    @Test
    void shouldAllowInstanceManagerWithoutParticipantRelation() {
        loginAs(7L, EasyPermissions.Workflow.INSTANCE_MANAGE);

        assertThat(guard.canManageInstances()).isTrue();
        assertThatCode(() -> guard.assertInstanceVisible(instance()))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowInitiatorWithoutParticipantRelation() {
        loginAs(1001L);

        assertThatCode(() -> guard.assertInstanceVisible(instance()))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowRuntimeTaskAssignee() {
        loginAs(2002L);
        stubTaskCount(1);
        stubHistoricTaskCount(0);

        assertThatCode(() -> guard.assertInstanceVisible(instance()))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowHistoricCcReceiver() {
        loginAs(3003L);
        stubTaskCount(0);
        stubHistoricTaskCount(0);
        stubCcCount(0);
        stubHistoricCcCount(1);

        assertThatCode(() -> guard.assertInstanceVisible(instance()))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectUnrelatedUser() {
        loginAs(4004L);
        stubTaskCount(0);
        stubHistoricTaskCount(0);
        stubCcCount(0);
        stubHistoricCcCount(0);

        assertThatThrownBy(() -> guard.assertInstanceVisible(instance()))
                .isInstanceOf(EasyForbiddenException.class)
                .hasMessageContaining("无权访问该流程实例");
    }

    @Test
    void shouldRequireCurrentUserForVisibilityCheck() {
        assertThatThrownBy(() -> guard.assertInstanceVisible(instance()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未获取到当前登录用户");
    }

    private void loginAs(Long userId, String... permissions) {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(userId)
                .permissions(List.of(permissions))
                .build());
    }

    private WfProcessInstance instance() {
        WfProcessInstance instance = new WfProcessInstance();
        instance.setId(99L);
        instance.setInitiatorId(1001L);
        return instance;
    }

    private void stubTaskCount(long count) {
        LambdaQueryChainWrapper<WfTask> query = countQuery(count);
        doReturn(query).when(taskService).lambdaQuery();
    }

    private void stubHistoricTaskCount(long count) {
        LambdaQueryChainWrapper<WfHistoricTask> query = countQuery(count);
        doReturn(query).when(historicTaskService).lambdaQuery();
    }

    private void stubCcCount(long count) {
        LambdaQueryChainWrapper<WfCc> query = countQuery(count);
        doReturn(query).when(ccService).lambdaQuery();
    }

    private void stubHistoricCcCount(long count) {
        LambdaQueryChainWrapper<WfHistoricCc> query = countQuery(count);
        doReturn(query).when(historicCcService).lambdaQuery();
    }

    @SuppressWarnings("unchecked")
    private <T> LambdaQueryChainWrapper<T> countQuery(long count) {
        LambdaQueryChainWrapper<T> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        doReturn(query).when(query).eq(any(), any());
        doReturn(count).when(query).count();
        return query;
    }
}
