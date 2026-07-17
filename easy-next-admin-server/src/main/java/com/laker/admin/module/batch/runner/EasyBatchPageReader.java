package com.laker.admin.module.batch.runner;

@FunctionalInterface
public interface EasyBatchPageReader<T> {
    EasyBatchPage<T> read(EasyBatchPageRequest request) throws Exception;
}
