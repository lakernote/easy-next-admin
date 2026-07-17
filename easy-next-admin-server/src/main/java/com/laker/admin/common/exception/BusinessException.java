
package com.laker.admin.common.exception;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>默认归类为 {@link ErrorCode#BUSINESS_ERROR}。需要表达资源不存在、文件过大等稳定错误语义时，
 * 直接传入对应 {@link ErrorCode}。认证和授权失败分别使用安全模块的专用异常。</p>
 */
@EqualsAndHashCode(callSuper = true)
@Getter
public class BusinessException extends RuntimeException {

    private final String msg;
    private final ErrorCode errorCode;

    public BusinessException(String msg) {
        this(ErrorCode.BUSINESS_ERROR, msg);
    }

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String msg) {
        super(msg);
        this.msg = msg;
        this.errorCode = errorCode;
    }

    public BusinessException(String msg, Throwable e) {
        this(ErrorCode.BUSINESS_ERROR, msg, e);
    }

    public BusinessException(ErrorCode errorCode, String msg, Throwable e) {
        super(msg, e);
        this.msg = msg;
        this.errorCode = errorCode;
    }

    public int getCode() {
        return errorCode.getCode();
    }
}
