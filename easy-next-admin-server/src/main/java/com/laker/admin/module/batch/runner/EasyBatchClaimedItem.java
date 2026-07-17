package com.laker.admin.module.batch.runner;

/**
 * Worker 已领取的持久化工作单元。
 */
public record EasyBatchClaimedItem(EasyBatchItemClaim claim,
                                   String itemName,
                                   String inputJson,
                                   String remark) {
}
