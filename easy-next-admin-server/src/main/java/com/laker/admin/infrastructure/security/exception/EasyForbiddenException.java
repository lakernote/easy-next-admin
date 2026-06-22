package com.laker.admin.infrastructure.security.exception;

import com.laker.admin.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class EasyForbiddenException extends RuntimeException {
    private final ErrorCode errorCode;

    public EasyForbiddenException(String message) {
        this(ErrorCode.FORBIDDEN, message);
    }

    public EasyForbiddenException(ErrorCode errorCode, String message) {
        super(message);
        if (errorCode.getHttpStatus() != HttpStatus.FORBIDDEN) {
            throw new IllegalArgumentException("EasyForbiddenException 只能使用 403 授权错误码");
        }
        this.errorCode = errorCode;
    }
}
