package com.laker.admin.common.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiErrorDetail", description = "API 错误明细，常用于参数校验失败")
public record ApiErrorDetail(
        @Schema(description = "错误字段或参数名", example = "userName")
        String field,
        @Schema(description = "错误说明", example = "用户名不能为空")
        String message
) {
    public static ApiErrorDetail of(String field, String message) {
        return new ApiErrorDetail(field, message);
    }
}
