package com.laker.admin.module.assistant.tool.workflow;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.assistant.context.AssistantRequestContext.AssistantUserContext;
import com.laker.admin.module.workflow.dto.WfProcessInstanceDetail;
import com.laker.admin.module.workflow.dto.WfProcessInstanceView;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfTaskService;
import com.laker.admin.module.workflow.service.IWfWorkflowRuntimeService;
import com.laker.admin.module.workflow.support.WorkflowInstanceStatus;
import com.laker.admin.module.workflow.support.WorkflowTaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultWorkflowTaskActionToolServiceTest {

    @Test
    void shouldResolveExactPendingTaskAssignedToCurrentUser() {
        IWfTaskService taskService = mock(IWfTaskService.class);
        IWfProcessInstanceService instanceService = mock(IWfProcessInstanceService.class);
        IWfWorkflowRuntimeService runtimeService = mock(IWfWorkflowRuntimeService.class);
        WfTask task = task(7001L, 9001L, 1001L);
        WfProcessInstance instance = instance(9001L, "LV-20260713-000002");
        when(taskService.getById(7001L)).thenReturn(task);
        when(instanceService.getById(9001L)).thenReturn(instance);

        DefaultWorkflowTaskActionToolService service = new DefaultWorkflowTaskActionToolService(
                taskService, instanceService, runtimeService
        );

        assertThat(service.resolveTarget(user(1001L, false), "7001"))
                .isEqualTo(new WorkflowTaskActionTarget(
                        7001L, 9001L, "LV-20260713-000002", "张三请假申请", "经理审批"
                ));
    }

    @Test
    void shouldResolveBusinessIdToTheCurrentUsersPendingTask() {
        IWfTaskService taskService = mock(IWfTaskService.class);
        IWfProcessInstanceService instanceService = mock(IWfProcessInstanceService.class);
        IWfWorkflowRuntimeService runtimeService = mock(IWfWorkflowRuntimeService.class);
        WfProcessInstance instance = instance(9001L, "LV-20260713-000002");
        WfTask ownTask = task(7001L, 9001L, 1001L);
        WfTask otherTask = task(7002L, 9001L, 1002L);
        when(instanceService.list(any(Wrapper.class))).thenReturn(List.of(instance));
        when(taskService.list(any(Wrapper.class))).thenReturn(List.of(otherTask, ownTask));

        DefaultWorkflowTaskActionToolService service = new DefaultWorkflowTaskActionToolService(
                taskService, instanceService, runtimeService
        );

        WorkflowTaskActionTarget target = service.resolveTarget(
                user(1001L, false),
                "LV-20260713-000002"
        );

        assertThat(target.taskId()).isEqualTo(7001L);
        assertThat(target.businessId()).isEqualTo("LV-20260713-000002");
    }

    @Test
    void shouldRejectAmbiguousBusinessIdInsteadOfChoosingAnArbitraryProcess() {
        IWfTaskService taskService = mock(IWfTaskService.class);
        IWfProcessInstanceService instanceService = mock(IWfProcessInstanceService.class);
        IWfWorkflowRuntimeService runtimeService = mock(IWfWorkflowRuntimeService.class);
        when(instanceService.list(any(Wrapper.class))).thenReturn(List.of(
                instance(9001L, "REQ-001"),
                instance(9002L, "REQ-001")
        ));
        DefaultWorkflowTaskActionToolService service = new DefaultWorkflowTaskActionToolService(
                taskService, instanceService, runtimeService
        );

        assertThatThrownBy(() -> service.resolveTarget(user(1001L, false), "REQ-001"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("该业务单号对应多个运行流程，请指定待办任务号或流程实例号");
    }

    @Test
    void shouldRejectTaskAssignedToAnotherUserWithoutManagementPermission() {
        IWfTaskService taskService = mock(IWfTaskService.class);
        IWfProcessInstanceService instanceService = mock(IWfProcessInstanceService.class);
        IWfWorkflowRuntimeService runtimeService = mock(IWfWorkflowRuntimeService.class);
        when(taskService.getById(7001L)).thenReturn(task(7001L, 9001L, 1002L));

        DefaultWorkflowTaskActionToolService service = new DefaultWorkflowTaskActionToolService(
                taskService, instanceService, runtimeService
        );

        assertThatThrownBy(() -> service.resolveTarget(user(1001L, false), "7001"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只能处理分配给自己的流程任务");
    }

    @Test
    void shouldDelegateConfirmedApprovalToWorkflowRuntime() {
        IWfTaskService taskService = mock(IWfTaskService.class);
        IWfProcessInstanceService instanceService = mock(IWfProcessInstanceService.class);
        IWfWorkflowRuntimeService runtimeService = mock(IWfWorkflowRuntimeService.class);
        WfProcessInstanceDetail detail = new WfProcessInstanceDetail();
        detail.setInstance(WfProcessInstanceView.builder()
                .id(9001L)
                .businessId("LV-20260713-000002")
                .title("张三请假申请")
                .status(WorkflowInstanceStatus.APPROVED.code())
                .build());
        when(runtimeService.approve(eq(7001L), any())).thenReturn(detail);
        DefaultWorkflowTaskActionToolService service = new DefaultWorkflowTaskActionToolService(
                taskService, instanceService, runtimeService
        );

        WorkflowTaskActionResult result = service.approve(7001L, "  同意  ");

        assertThat(result.taskId()).isEqualTo(7001L);
        assertThat(result.status()).isEqualTo("APPROVED");
        verify(runtimeService).approve(eq(7001L), org.mockito.ArgumentMatchers.argThat(
                request -> "同意".equals(request.getComment())
        ));
    }

    @Test
    void shouldDelegateConfirmedRejectionToWorkflowRuntime() {
        IWfTaskService taskService = mock(IWfTaskService.class);
        IWfProcessInstanceService instanceService = mock(IWfProcessInstanceService.class);
        IWfWorkflowRuntimeService runtimeService = mock(IWfWorkflowRuntimeService.class);
        WfProcessInstanceDetail detail = new WfProcessInstanceDetail();
        detail.setInstance(WfProcessInstanceView.builder()
                .id(9001L)
                .businessId("LV-20260713-000002")
                .title("张三请假申请")
                .status(WorkflowInstanceStatus.REJECTED.code())
                .build());
        when(runtimeService.reject(eq(7001L), any())).thenReturn(detail);
        DefaultWorkflowTaskActionToolService service = new DefaultWorkflowTaskActionToolService(
                taskService, instanceService, runtimeService
        );

        WorkflowTaskActionResult result = service.reject(7001L, "  信息不完整  ");

        assertThat(result.decision()).isEqualTo(WorkflowTaskDecision.REJECT);
        assertThat(result.status()).isEqualTo("REJECTED");
        verify(runtimeService).reject(eq(7001L), org.mockito.ArgumentMatchers.argThat(
                request -> "信息不完整".equals(request.getComment())
        ));
    }

    private static WfTask task(Long id, Long instanceId, Long assigneeId) {
        WfTask task = new WfTask();
        task.setId(id);
        task.setInstanceId(instanceId);
        task.setAssigneeId(assigneeId);
        task.setNodeName("经理审批");
        task.setStatus(WorkflowTaskStatus.PENDING.code());
        return task;
    }

    private static WfProcessInstance instance(Long id, String businessId) {
        WfProcessInstance instance = new WfProcessInstance();
        instance.setId(id);
        instance.setBusinessId(businessId);
        instance.setTitle("张三请假申请");
        instance.setStatus(WorkflowInstanceStatus.RUNNING.code());
        return instance;
    }

    private static AssistantUserContext user(Long userId, boolean superAdmin) {
        return new AssistantUserContext(
                1L, userId, "zhangsan", "张三", 10L, "产品部", Set.of(10L),
                List.of("employee"), List.of("员工"),
                List.of(EasyPermissions.Workflow.TASK_APPROVE), superAdmin
        );
    }
}
