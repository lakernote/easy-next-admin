package com.laker.admin.module.workflow.support;

import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.message.service.UserMessageService;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysUserService;
import com.laker.admin.module.workflow.entity.WfCc;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.entity.WfTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class WorkflowNotificationService {

    private final UserMessageService userMessageService;
    private final ISysUserService sysUserService;

    public void remindTaskAssignee(WfProcessInstance instance, WfTask task, Long operatorId, String comment) {
        String title = "流程催办：" + instanceTitle(instance);
        String content = "%s 催办了流程「%s」，当前节点：%s。%s".formatted(
                userDisplayName(operatorId),
                instanceTitle(instance),
                StringUtils.hasText(task.getNodeName()) ? task.getNodeName() : task.getNodeKey(),
                StringUtils.hasText(comment) ? "处理意见：" + comment : "请尽快处理。"
        );
        userMessageService.createSystemMessage(
                task.getAssigneeId(),
                operatorId,
                title,
                content,
                "WORKFLOW",
                "INFO",
                "WORKFLOW_INSTANCE",
                String.valueOf(instance.getId()),
                "/workflow/tasks?tab=pending&instanceId=" + instance.getId()
        );
    }

    public void notifyCcReceivers(WfProcessInstance instance, List<WfCc> ccList, Long operatorId) {
        String instanceTitle = instanceTitle(instance);
        for (WfCc cc : ccList) {
            userMessageService.createSystemMessage(
                    cc.getReceiverId(),
                    operatorId,
                    "流程抄送：" + instanceTitle,
                    "流程「%s」已抄送给你，抄送节点：%s。".formatted(instanceTitle, cc.getNodeName()),
                    "WORKFLOW_CC",
                    "INFO",
                    "WORKFLOW_CC",
                    String.valueOf(cc.getId()),
                    "/workflow/tasks?tab=cc&ccId=" + cc.getId() + "&instanceId=" + instance.getId()
            );
        }
    }

    private String userDisplayName(Long userId) {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        if (principal != null && Objects.equals(principal.getUserId(), userId)) {
            if (StringUtils.hasText(principal.getNickName())) {
                return principal.getNickName();
            }
            if (StringUtils.hasText(principal.getUserName())) {
                return principal.getUserName();
            }
        }
        SysUser user = sysUserService.getById(userId);
        if (user != null) {
            if (StringUtils.hasText(user.getNickName())) {
                return user.getNickName();
            }
            if (StringUtils.hasText(user.getUserName())) {
                return user.getUserName();
            }
        }
        return "用户" + userId;
    }

    private String instanceTitle(WfProcessInstance instance) {
        return StringUtils.hasText(instance.getTitle()) ? instance.getTitle() : "流程 " + instance.getId();
    }
}
