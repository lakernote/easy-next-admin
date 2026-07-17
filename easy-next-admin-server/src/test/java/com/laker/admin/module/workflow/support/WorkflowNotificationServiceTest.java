package com.laker.admin.module.workflow.support;

import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.message.service.UserMessageService;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysUserService;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowNotificationServiceTest {

    private final UserMessageService userMessageService = mock(UserMessageService.class);
    private final ISysUserService sysUserService = mock(ISysUserService.class);
    private final WorkflowNotificationService notificationService =
            new WorkflowNotificationService(userMessageService, sysUserService);

    @AfterEach
    void tearDown() {
        EasySecurityContext.clear();
    }

    @Test
    void shouldCreateReminderMessageWithCurrentPrincipalDisplayName() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(1001L)
                .userName("workflow_initiator")
                .nickName("流程发起人")
                .build());

        WfProcessInstance instance = instance("请假申请");
        WfTask task = task("部门审批");

        notificationService.remindTaskAssignee(instance, task, 1001L, "请今天处理");

        verify(userMessageService).createSystemMessage(
                2002L,
                1001L,
                "流程催办：请假申请",
                "流程发起人 催办了流程「请假申请」，当前节点：部门审批。处理意见：请今天处理",
                "WORKFLOW",
                "INFO",
                "WORKFLOW_INSTANCE",
                "99",
                "/workflow/tasks?tab=pending&instanceId=99");
    }

    @Test
    void shouldCreateReminderMessageWithUserAndFallbackLabels() {
        SysUser operator = new SysUser();
        operator.setUserId(1001L);
        operator.setUserName("workflow_initiator");
        when(sysUserService.getById(1001L)).thenReturn(operator);

        WfProcessInstance instance = instance(null);
        WfTask task = task(null);

        notificationService.remindTaskAssignee(instance, task, 1001L, null);

        verify(userMessageService).createSystemMessage(
                2002L,
                1001L,
                "流程催办：流程 99",
                "workflow_initiator 催办了流程「流程 99」，当前节点：approve。请尽快处理。",
                "WORKFLOW",
                "INFO",
                "WORKFLOW_INSTANCE",
                "99",
                "/workflow/tasks?tab=pending&instanceId=99");
    }

    @Test
    void shouldCreateCcMessageForEachReceiver() {
        WfProcessInstance instance = instance("采购申请");
        WfCc first = cc(701L, 3001L, "审计备案");
        WfCc second = cc(702L, 3002L, "财务备案");

        notificationService.notifyCcReceivers(instance, List.of(first, second), 1001L);

        verify(userMessageService).createSystemMessage(
                3001L,
                1001L,
                "流程抄送：采购申请",
                "流程「采购申请」已抄送给你，抄送节点：审计备案。",
                "WORKFLOW_CC",
                "INFO",
                "WORKFLOW_CC",
                "701",
                "/workflow/tasks?tab=cc&ccId=701&instanceId=99");
        verify(userMessageService).createSystemMessage(
                3002L,
                1001L,
                "流程抄送：采购申请",
                "流程「采购申请」已抄送给你，抄送节点：财务备案。",
                "WORKFLOW_CC",
                "INFO",
                "WORKFLOW_CC",
                "702",
                "/workflow/tasks?tab=cc&ccId=702&instanceId=99");
    }

    private WfProcessInstance instance(String title) {
        WfProcessInstance instance = new WfProcessInstance();
        instance.setId(99L);
        instance.setTitle(title);
        return instance;
    }

    private WfTask task(String nodeName) {
        WfTask task = new WfTask();
        task.setAssigneeId(2002L);
        task.setNodeKey("approve");
        task.setNodeName(nodeName);
        return task;
    }

    private WfCc cc(Long id, Long receiverId, String nodeName) {
        WfCc cc = new WfCc();
        cc.setId(id);
        cc.setReceiverId(receiverId);
        cc.setNodeName(nodeName);
        return cc;
    }
}
