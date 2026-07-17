package com.laker.admin.module.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.module.message.dto.MessageUnreadCountView;
import com.laker.admin.module.message.dto.UserMessageView;
import com.laker.admin.module.message.entity.UserMessage;
import com.laker.admin.module.message.event.UserMessageReadEvent;
import com.laker.admin.module.message.event.UserMessagesReadAllEvent;
import com.laker.admin.module.message.mapper.UserMessageMapper;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysUserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class UserMessageService extends ServiceImpl<UserMessageMapper, UserMessage> {
    private static final String LEVEL_INFO = "INFO";

    private final ISysUserService userService;
    private final ApplicationEventPublisher eventPublisher;

    public UserMessageService(ISysUserService userService,
                              ApplicationEventPublisher eventPublisher) {
        this.userService = userService;
        this.eventPublisher = eventPublisher;
    }

    public PageResponse<UserMessageView> page(long page, long limit, String keyword, Integer readStatus, String category) {
        LambdaQueryWrapper<UserMessage> wrapper = Wrappers.<UserMessage>lambdaQuery()
                .eq(UserMessage::getReceiverId, EasySecurityContext.getUserId())
                .eq(readStatus != null, UserMessage::getReadStatus, readStatus)
                .and(StringUtils.hasText(keyword), query -> query
                        .like(UserMessage::getTitle, keyword)
                        .or()
                        .like(UserMessage::getContent, keyword))
                .orderByAsc(UserMessage::getReadStatus)
                .orderByDesc(UserMessage::getCreatedAt);
        applyCategoryFilter(wrapper, category);
        Page<UserMessage> pageResult = this.page(new Page<>(page, limit), wrapper);
        List<UserMessage> records = pageResult.getRecords();
        fillSenderNames(records);
        return PageResponse.ok(UserMessageView.fromList(records), pageResult.getTotal());
    }

    public long unreadCount() {
        return this.count(Wrappers.<UserMessage>lambdaQuery()
                .eq(UserMessage::getReceiverId, EasySecurityContext.getUserId())
                .eq(UserMessage::getReadStatus, 0));
    }

    public MessageUnreadCountView unreadCountSummary() {
        List<UserMessage> unreadMessages = this.list(Wrappers.<UserMessage>lambdaQuery()
                .select(UserMessage::getCategory)
                .eq(UserMessage::getReceiverId, EasySecurityContext.getUserId())
                .eq(UserMessage::getReadStatus, 0));
        long workflow = unreadMessages.stream().filter(message -> "workflow".equals(normalizedMessageType(message.getCategory()))).count();
        long audit = unreadMessages.stream().filter(message -> "audit".equals(normalizedMessageType(message.getCategory()))).count();
        long task = unreadMessages.stream().filter(message -> "task".equals(normalizedMessageType(message.getCategory()))).count();
        return MessageUnreadCountView.builder()
                .total(unreadMessages.size())
                .workflow(workflow)
                .audit(audit)
                .task(task)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public UserMessage createSystemMessage(Long receiverId, String title, String content, String category, String link) {
        return createSystemMessage(receiverId, 0L, title, content, category, "INFO", null, null, link);
    }

    @Transactional(rollbackFor = Exception.class)
    public UserMessage createSystemMessage(Long receiverId,
                                           Long senderId,
                                           String title,
                                           String content,
                                           String category,
                                           String level,
                                           String bizType,
                                           String bizId,
                                           String link) {
        UserMessage message = new UserMessage();
        message.setReceiverId(receiverId);
        message.setSenderId(senderId == null ? 0L : senderId);
        message.setTitle(title);
        message.setContent(content);
        message.setCategory(category);
        message.setLevel(StringUtils.hasText(level) ? level : LEVEL_INFO);
        message.setBizType(bizType);
        message.setBizId(bizId);
        message.setLink(link);
        message.setReadStatus(0);
        message.setCreatedAt(LocalDateTime.now());
        this.save(message);
        return message;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markRead(Long id) {
        UserMessage message = this.getById(id);
        if (message == null || !Objects.equals(message.getReceiverId(), EasySecurityContext.getUserId())) {
            throw new BusinessException("消息不存在或无权访问");
        }
        if (Objects.equals(message.getReadStatus(), 1)) {
            return true;
        }
        LocalDateTime readAt = LocalDateTime.now();
        message.setReadStatus(1);
        message.setReadAt(readAt);
        boolean updated = this.updateById(message);
        if (updated) {
            eventPublisher.publishEvent(new UserMessageReadEvent(
                    message.getReceiverId(),
                    message.getCategory(),
                    message.getBizType(),
                    message.getBizId(),
                    readAt
            ));
        }
        return updated;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markAllRead() {
        Long receiverId = EasySecurityContext.getUserId();
        LocalDateTime readAt = LocalDateTime.now();
        boolean updated = this.update(Wrappers.<UserMessage>lambdaUpdate()
                .eq(UserMessage::getReceiverId, receiverId)
                .eq(UserMessage::getReadStatus, 0)
                .set(UserMessage::getReadStatus, 1)
                .set(UserMessage::getReadAt, readAt));
        if (updated) {
            eventPublisher.publishEvent(new UserMessagesReadAllEvent(receiverId, readAt));
        }
        return updated;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean markWorkflowCcMessageRead(Long receiverId, Long ccId) {
        if (receiverId == null || ccId == null) {
            return false;
        }
        return this.update(Wrappers.<UserMessage>lambdaUpdate()
                .eq(UserMessage::getReceiverId, receiverId)
                .eq(UserMessage::getBizType, "WORKFLOW_CC")
                .eq(UserMessage::getBizId, String.valueOf(ccId))
                .eq(UserMessage::getReadStatus, 0)
                .set(UserMessage::getReadStatus, 1)
                .set(UserMessage::getReadAt, LocalDateTime.now()));
    }

    private void applyCategoryFilter(LambdaQueryWrapper<UserMessage> wrapper, String category) {
        if (!StringUtils.hasText(category)) {
            return;
        }
        String normalized = category.trim().toUpperCase();
        switch (normalized) {
            case "WORKFLOW" -> wrapper.in(UserMessage::getCategory, List.of("WORKFLOW", "WORKFLOW_CC"));
            case "TASK" -> wrapper.in(UserMessage::getCategory, List.of("TASK", "EXPORT", "IMPORT_EXPORT"));
            case "AUDIT" -> wrapper.in(UserMessage::getCategory, List.of("AUDIT", "SECURITY"));
            default -> wrapper.eq(UserMessage::getCategory, normalized);
        }
    }

    private void fillSenderNames(List<UserMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        Set<Long> senderIds = messages.stream()
                .map(UserMessage::getSenderId)
                .filter(Objects::nonNull)
                .filter(senderId -> senderId > 0)
                .collect(Collectors.toSet());
        Map<Long, SysUser> senderMap = senderIds.isEmpty()
                ? Map.of()
                : userService.listByIds(senderIds).stream()
                .collect(Collectors.toMap(SysUser::getUserId, Function.identity(), (left, right) -> left));
        messages.forEach(message -> message.setSenderName(senderName(message.getSenderId(), senderMap)));
    }

    private String senderName(Long senderId, Map<Long, SysUser> senderMap) {
        if (senderId == null || senderId <= 0) {
            return "系统";
        }
        SysUser sender = senderMap.get(senderId);
        if (sender == null) {
            return "用户 " + senderId;
        }
        if (StringUtils.hasText(sender.getNickName())) {
            return sender.getNickName();
        }
        if (StringUtils.hasText(sender.getRealName())) {
            return sender.getRealName();
        }
        if (StringUtils.hasText(sender.getUserName())) {
            return sender.getUserName();
        }
        return "用户 " + senderId;
    }

    private String normalizedMessageType(String category) {
        String normalized = normalizeOrDefault(category, "");
        return switch (normalized) {
            case "WORKFLOW", "WORKFLOW_CC" -> "workflow";
            case "AUDIT", "SECURITY" -> "audit";
            case "TASK", "EXPORT", "IMPORT_EXPORT" -> "task";
            default -> "unknown";
        };
    }

    private String normalizeOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : fallback;
    }

}
