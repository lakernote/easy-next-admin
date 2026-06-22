package com.laker.admin.module.message.event;

import java.time.LocalDateTime;

public record UserMessagesReadAllEvent(Long receiverId, LocalDateTime readAt) {
}
