package com.laker.admin.common.model;

import com.laker.admin.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;

/**
 * @author laker
 */
@Getter
@Schema(name = "PageResponse", description = "统一分页响应体。泛型 T 表示单条记录类型，响应 data 固定为分页数据。")
public class PageResponse<T> extends Response<PageData<T>> {

    private PageResponse(List<T> list, long total) {
        super(ErrorCode.SUCCESS, ErrorCode.SUCCESS.getDefaultMessage(), PageData.of(list, total), null);
    }

    public static <T> PageResponse<T> ok(List<T> list, long total) {
        return new PageResponse<>(list, total);
    }
}
