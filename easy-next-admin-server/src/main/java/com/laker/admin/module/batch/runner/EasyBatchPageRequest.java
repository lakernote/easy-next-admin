package com.laker.admin.module.batch.runner;

public record EasyBatchPageRequest(String cursor, int pageNumber, int pageSize) {
}
