package com.laker.admin.module.message.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageUnreadCountView {
    private long total;
    private long workflow;
    private long audit;
    private long task;
}
