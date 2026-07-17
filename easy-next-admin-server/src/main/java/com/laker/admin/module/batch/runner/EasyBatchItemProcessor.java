package com.laker.admin.module.batch.runner;

@FunctionalInterface
public interface EasyBatchItemProcessor<T> {
    EasyBatchItemResult process(EasyBatchItem<T> item) throws Exception;
}
