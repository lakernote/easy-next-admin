package com.laker.admin.common.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * @author laker
 */
@Schema(name = "PageData", description = "分页业务数据。list 为当前页记录，total 为匹配条件下的总记录数。")
public record PageData<T>(
        @Schema(description = "当前页记录")
        List<T> list,
        @Schema(description = "总记录数")
        long total
) {
    public PageData {
        list = list == null ? List.of() : list;
        total = Math.max(total, 0);
    }

    public static <T> PageData<T> of(List<T> list, long total) {
        return new PageData<>(list, total);
    }
}
