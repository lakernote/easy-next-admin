package com.laker.admin.module.workflow.support;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowInstanceStateGuardTest {

    private final IWfProcessInstanceService instanceService = mock(IWfProcessInstanceService.class);
    private final WorkflowInstanceStateGuard stateGuard = new WorkflowInstanceStateGuard(instanceService);

    @Test
    void shouldRejectWhenOptimisticUpdateFails() {
        WfProcessInstance instance = new WfProcessInstance();
        instance.setId(1L);
        instance.setStatus("RUNNING");
        when(instanceService.updateById(instance)).thenReturn(false);

        assertThatThrownBy(() -> stateGuard.updateOrThrow(instance))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程状态已变化");
    }
}
