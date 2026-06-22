package com.laker.admin.module.message.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.message.entity.UserMessage;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内消息对外响应模型，避免将消息表实体直接暴露给前端契约。
 */
@Data
@Builder
public class UserMessageView {
    private Long id;
    private Long receiverId;
    private Long senderId;
    private String senderName;
    private String title;
    private String content;
    private String category;
    private String level;
    private String bizType;
    private String bizId;
    private String link;
    private Integer readStatus;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime readAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    public static UserMessageView from(UserMessage message) {
        if (message == null) {
            return null;
        }
        return UserMessageView.builder()
                .id(message.getId())
                .receiverId(message.getReceiverId())
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .title(message.getTitle())
                .content(message.getContent())
                .category(message.getCategory())
                .level(message.getLevel())
                .bizType(message.getBizType())
                .bizId(message.getBizId())
                .link(message.getLink())
                .readStatus(message.getReadStatus())
                .readAt(message.getReadAt())
                .createdAt(message.getCreatedAt())
                .build();
    }

    public static List<UserMessageView> fromList(List<UserMessage> messages) {
        return messages == null ? List.of() : messages.stream().map(UserMessageView::from).toList();
    }
}
