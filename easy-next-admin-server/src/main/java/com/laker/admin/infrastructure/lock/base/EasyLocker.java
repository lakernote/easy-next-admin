package com.laker.admin.infrastructure.lock.base;

import lombok.Builder;
import lombok.Data;

import java.util.concurrent.ScheduledFuture;

/**
 * @author: easynext
 * @date: 2022/11/2
 **/
@Data
@Builder
public class EasyLocker {
    private String key;
    private String token;
    private ScheduledFuture<?> scheduledFuture;
}
