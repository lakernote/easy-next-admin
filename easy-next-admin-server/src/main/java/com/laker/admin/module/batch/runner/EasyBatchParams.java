package com.laker.admin.module.batch.runner;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EasyBatchParams {
    private final String paramsJson;
    private final String initialCursor;
    private final Integer pageSize;
    private final EasyBatchFailureStrategy failureStrategy;
    private final Integer maxFailureCount;
    /** 单条明细租约秒数，Runner 会在执行期间自动续租。 */
    private final Integer itemLeaseSeconds;
    /** 是否由多个实例共享同一个固定账期批次。 */
    private final Boolean distributed;

    public static EasyBatchParams empty() {
        return EasyBatchParams.builder().build();
    }
}
