package com.laker.admin.module.message.controller;

import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.message.dto.MessageUnreadCountView;
import com.laker.admin.module.message.dto.UserMessageView;
import com.laker.admin.module.message.service.UserMessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "消息中心")
@RestController
@RequestMapping("/api/messages")
public class UserMessageController {
    private final UserMessageService userMessageService;

    public UserMessageController(UserMessageService userMessageService) {
        this.userMessageService = userMessageService;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.Message.VIEW)
    @Operation(summary = "查询当前用户站内消息")
    public PageResponse<UserMessageView> page(@RequestParam(required = false, defaultValue = "1") long page,
                                              @RequestParam(required = false, defaultValue = "10") long limit,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer readStatus,
                                              @RequestParam(required = false) String category) {
        return userMessageService.page(page, limit, keyword, readStatus, category);
    }

    @GetMapping("/unread-count")
    @EasyPermission(EasyPermissions.Message.VIEW)
    @Operation(summary = "查询当前用户未读消息数")
    public Response<MessageUnreadCountView> unreadCount() {
        return Response.ok(userMessageService.unreadCountSummary());
    }

    @PutMapping("/{id}/read")
    @EasyPermission(EasyPermissions.Message.READ)
    @EasyAudit(module = "消息中心", action = "标记消息已读", dataChange = true, bizType = "USER_MESSAGE", bizId = "#id", changeType = "READ")
    @Operation(summary = "标记消息已读")
    public Response<Boolean> markRead(@PathVariable Long id) {
        return Response.ok(userMessageService.markRead(id));
    }

    @PutMapping("/read-all")
    @EasyPermission(EasyPermissions.Message.READ)
    @EasyAudit(module = "消息中心", action = "标记全部消息已读", dataChange = true, bizType = "USER_MESSAGE", changeType = "READ_ALL")
    @Operation(summary = "标记全部消息已读")
    public Response<Boolean> markAllRead() {
        return Response.ok(userMessageService.markAllRead());
    }
}
