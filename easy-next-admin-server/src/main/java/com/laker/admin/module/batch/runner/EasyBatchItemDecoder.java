package com.laker.admin.module.batch.runner;

/**
 * 将持久化工作单元的输入快照恢复为 Processor 所需的业务对象。
 */
@FunctionalInterface
public interface EasyBatchItemDecoder<T> {
    T decode(EasyBatchClaimedItem item) throws Exception;
}
