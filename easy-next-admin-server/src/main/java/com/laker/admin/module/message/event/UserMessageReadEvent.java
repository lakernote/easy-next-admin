package com.laker.admin.module.message.event;

import java.time.LocalDateTime;

public record UserMessageReadEvent(
        Long receiverId,
        String category,
        String bizType,
        String bizId,
        LocalDateTime readAt
) {
}
