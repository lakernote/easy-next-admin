package com.laker.admin.module.workflow.purchase.batch;

import com.laker.admin.module.batch.runner.EasyBatchPartitionData;

import java.time.LocalDate;

/** 固定账期内的一个哈希分区。 */
public record PurchaseReconciliationPartition(LocalDate businessDate,
                                              long startId,
                                              long endId) implements EasyBatchPartitionData {
}
