package com.laker.admin.module.message;

import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.message.dto.UserMessageView;
import com.laker.admin.module.message.entity.UserMessage;
import com.laker.admin.module.message.service.UserMessageService;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class MessageCenterServiceIntegrationTest {

    @Autowired
    private UserMessageService userMessageService;

    @Autowired
    private ISysUserService userService;

    @BeforeEach
    void setPrincipal() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(202604280101000001L)
                .userName("admin")
                .nickName("超级管理员")
                .superAdmin(true)
                .build());
    }

    @AfterEach
    void clearSecurityContext() {
        EasySecurityContext.clear();
    }

    @Test
    void shouldCountUnreadAndMarkMessageReadForCurrentUser() {
        long before = userMessageService.unreadCount();

        UserMessage message = userMessageService.createSystemMessage(
                202604280101000001L,
                "测试待办提醒",
                "你有一条新的测试待办。",
                "WORKFLOW",
                "/workflow/tasks?tab=pending"
        );

        assertThat(userMessageService.unreadCount()).isEqualTo(before + 1);
        userMessageService.markRead(message.getId());
        assertThat(userMessageService.unreadCount()).isEqualTo(before);
    }

    @Test
    void shouldMarkAllMessagesReadForCurrentUserOnly() {
        userMessageService.createSystemMessage(202604280101000001L, "导出完成", "文件已生成。", "EXPORT", "/system/files");
        userMessageService.createSystemMessage(202604280101000026L, "他人消息", "不应被当前用户已读。", "TASK", "/dashboard");

        userMessageService.markAllRead();

        assertThat(userMessageService.unreadCount()).isZero();
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(202604280101000026L)
                .userName("staff")
                .nickName("林员工")
                .build());
        assertThat(userMessageService.unreadCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldTreatWorkflowCategoryFilterAsWorkflowGroup() {
        userMessageService.createSystemMessage(202604280101000001L, "流程抄送", "报修流程抄送给你。", "WORKFLOW_CC", "/workflow/tasks?tab=cc");

        assertThat(userMessageService.page(1, 10, null, null, "WORKFLOW").getData().list())
                .extracting(UserMessageView::getCategory)
                .contains("WORKFLOW_CC");
    }

    @Test
    void shouldFillSenderNameForOperatorMessages() {
        SysUser sender = userService.getById(202604280101000026L);
        assertThat(sender).isNotNull();
        assertThat(sender.getNickName()).isEqualTo("林员工");

        userMessageService.createSystemMessage(
                202604280101000001L,
                202604280101000026L,
                "流程催办",
                "林员工催办了请假流程。",
                "WORKFLOW",
                "INFO",
                "WORKFLOW_INSTANCE",
                "1001",
                "/workflow/tasks?tab=pending&instanceId=1001"
        );

        assertThat(userMessageService.page(1, 10, "流程催办", null, "WORKFLOW").getData().list())
                .extracting(UserMessageView::getSenderName)
                .contains("林员工");
    }
}
