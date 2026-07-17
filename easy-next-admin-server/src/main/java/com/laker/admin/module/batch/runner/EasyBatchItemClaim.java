package com.laker.admin.module.batch.runner;

import java.time.LocalDateTime;

/**
 * Worker 对一条批处理明细的限时所有权凭证。
 */
public record EasyBatchItemClaim(Long itemId,
                                 Long taskId,
                                 String itemKey,
                                 String workerId,
                                 String leaseToken,
                                 LocalDateTime leaseUntil) {
}
