package com.laker.admin.common.model;

import com.laker.admin.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * @author laker
 */
@Getter
@Schema(name = "ApiResponse", description = "统一业务响应体。链路追踪号通过 X-Trace-Id 响应头返回，不进入业务数据。")
public class Response<T> {
    @Schema(description = "业务响应码。0 表示成功；非 0 为稳定错误码，不等同 HTTP 状态码。", example = "0")
    private final int code;
    @Schema(description = "响应消息", example = "操作成功")
    private final String message;
    @Schema(description = "响应数据")
    private final T data;
    @Schema(description = "错误明细。业务数据始终放 data，校验失败等错误明细放 details。")
    private final List<ApiErrorDetail> details;

    protected Response(ErrorCode errorCode, String message, T data, List<ApiErrorDetail> details) {
        ErrorCode resolvedErrorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.code = resolvedErrorCode.getCode();
        this.message = hasText(message) ? message.trim() : resolvedErrorCode.getDefaultMessage();
        this.data = data;
        this.details = details == null || details.isEmpty() ? null : List.copyOf(details);
    }

    public static <T> Response<T> ok(T data) {
        return new Response<>(ErrorCode.SUCCESS, ErrorCode.SUCCESS.getDefaultMessage(), data, null);
    }

    public static Response<Void> ok() {
        return new Response<>(ErrorCode.SUCCESS, ErrorCode.SUCCESS.getDefaultMessage(), null, null);
    }

    public static <T> Response<T> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getDefaultMessage());
    }

    public static <T> Response<T> error(ErrorCode errorCode, String message) {
        return new Response<>(errorCode, message, null, null);
    }

    public static <T> Response<T> error(ErrorCode errorCode, String message, List<ApiErrorDetail> details) {
        return new Response<>(errorCode, message, null, details);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
