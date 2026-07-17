package com.laker.admin.module.batch.runner;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EasyBatchRunRequest<T> {
    private final String taskType;
    private final String taskName;
    private final String businessKey;
    private final Integer runNo;
    private final String triggerType;
    private final String triggerRefId;
    private final String traceId;
    private final String remark;
    private final EasyBatchParams batchParams;
    private final EasyBatchPageReader<T> reader;
    private final EasyBatchItemDecoder<T> itemDecoder;
    private final EasyBatchItemProcessor<T> processor;
}
