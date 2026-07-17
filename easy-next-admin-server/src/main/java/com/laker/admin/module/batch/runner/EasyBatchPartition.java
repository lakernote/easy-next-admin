package com.laker.admin.module.batch.runner;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

/**
 * 大批量任务的轻量分区。
 *
 * <p>分区本身也是一个 Batch item。Processor 处理到分区时，再在分区范围内分页读取业务数据。
 * 适合每日对账、每周汇总等固定数据集；持续流入的消息应直接 Claim 源队列表。</p>
 */
public record EasyBatchPartition(String partitionKey,
                                 String partitionName,
                                 String rangeStart,
                                 String rangeEnd,
                                 String inputJson) implements EasyBatchPartitionData {

    public EasyBatchPartition {
        if (!StringUtils.hasText(partitionKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "批处理分区键不能为空");
        }
        partitionKey = partitionKey.trim();
        partitionName = StringUtils.hasText(partitionName) ? partitionName.trim() : partitionKey;
        rangeStart = StringUtils.hasText(rangeStart) ? rangeStart.trim() : null;
        rangeEnd = StringUtils.hasText(rangeEnd) ? rangeEnd.trim() : null;
        inputJson = StringUtils.hasText(inputJson) ? inputJson.trim() : null;
    }

    public static EasyBatchPartition idRange(String partitionKey, long startInclusive, long endInclusive) {
        if (endInclusive < startInclusive) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "分区结束 ID 不能小于开始 ID");
        }
        String name = "ID " + startInclusive + "-" + endInclusive;
        String inputJson = "{\"rangeType\":\"ID\",\"start\":" + startInclusive + ",\"end\":" + endInclusive + "}";
        return new EasyBatchPartition(partitionKey, name, String.valueOf(startInclusive), String.valueOf(endInclusive), inputJson);
    }

    public EasyBatchItem<EasyBatchPartition> toItem() {
        String remark = rangeStart == null && rangeEnd == null ? null : "range=" + rangeStart + "-" + rangeEnd;
        return new EasyBatchItem<>(partitionKey, partitionName, this, inputJson, remark);
    }
}
