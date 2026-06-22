package com.laker.admin.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.model.PageResponse;

import java.util.List;
import java.util.function.Function;

/**
 * MyBatis-Plus 分页小工具。
 *
 * <p>Controller 和 Service 统一从这里创建分页对象、转换统一响应，避免散落重复分页样板代码。</p>
 */
public final class EasyPageSupport {
    private static final long DEFAULT_PAGE = 1;
    private static final long DEFAULT_LIMIT = 10;

    private EasyPageSupport() {
    }

    public static <T> Page<T> page(long page, long limit) {
        return new Page<>(positiveOrDefault(page, DEFAULT_PAGE), positiveOrDefault(limit, DEFAULT_LIMIT));
    }

    public static <T, R> PageResponse<R> response(Page<T> page, Function<T, R> mapper) {
        List<T> records = page == null || page.getRecords() == null ? List.of() : page.getRecords();
        return PageResponse.ok(records.stream().map(mapper).toList(), page == null ? 0 : page.getTotal());
    }

    public static <T> PageResponse<T> response(Page<T> page) {
        List<T> records = page == null || page.getRecords() == null ? List.of() : page.getRecords();
        return PageResponse.ok(records, page == null ? 0 : page.getTotal());
    }

    private static long positiveOrDefault(long value, long defaultValue) {
        return value > 0 ? value : defaultValue;
    }
}
