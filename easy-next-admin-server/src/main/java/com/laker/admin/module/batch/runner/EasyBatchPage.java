package com.laker.admin.module.batch.runner;

import java.util.List;

public record EasyBatchPage<T>(List<EasyBatchItem<T>> items, String nextCursor, boolean hasNext) {
    public static <T> EasyBatchPage<T> last(List<EasyBatchItem<T>> items) {
        return new EasyBatchPage<>(items, null, false);
    }

    public static <T> EasyBatchPage<T> next(List<EasyBatchItem<T>> items, String nextCursor) {
        return new EasyBatchPage<>(items, nextCursor, true);
    }
}
