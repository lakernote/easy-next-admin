package com.laker.admin.module.workflow.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.module.system.service.ISysUserService;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfTaskDelegationService;
import com.laker.admin.module.workflow.service.IWfTaskService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowTaskDispatcherTest {

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WfTask.class);
    }

    private final IWfTaskService taskService = mock(IWfTaskService.class);
    private final WorkflowTaskDispatcher dispatcher = new WorkflowTaskDispatcher(
            mock(IWfProcessInstanceService.class),
            taskService,
            mock(IWfTaskDelegationService.class),
            mock(WorkflowAssigneeResolver.class),
            mock(WorkflowArchiveService.class),
            mock(ISysUserService.class)
    );

    @Test
    void shouldRejectWhenPendingTaskWasAlreadyHandled() {
        WfTask task = pendingTask();
        when(taskService.update(any())).thenReturn(false);

        assertThatThrownBy(() -> dispatcher.transitionPendingTask(task, WorkflowTaskStatus.APPROVED, "同意", 2L, LocalDateTime.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程任务已处理");
    }

    @Test
    void shouldUpdateInMemoryTaskAfterAtomicTransition() {
        LocalDateTime now = LocalDateTime.now();
        WfTask task = pendingTask();
        when(taskService.update(any())).thenReturn(true);

        dispatcher.transitionPendingTask(task, WorkflowTaskStatus.APPROVED, "同意", 2L, now);

        assertThat(task.getStatus()).isEqualTo("APPROVED");
        assertThat(task.getApproveComment()).isEqualTo("同意");
        assertThat(task.getFinishedAt()).isEqualTo(now);
        assertThat(task.getUpdatedBy()).isEqualTo(2L);
        assertThat(task.getUpdatedAt()).isEqualTo(now);
    }

    private WfTask pendingTask() {
        WfTask task = new WfTask();
        task.setId(1L);
        task.setInstanceId(10L);
        task.setNodeKey("approve");
        task.setNodeName("审批");
        task.setAssigneeId(2L);
        task.setStatus("PENDING");
        return task;
    }
}
