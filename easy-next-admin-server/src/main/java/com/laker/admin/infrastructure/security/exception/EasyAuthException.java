package com.laker.admin.infrastructure.security.exception;

import com.laker.admin.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class EasyAuthException extends RuntimeException {
    private final ErrorCode errorCode;

    public EasyAuthException(String message) {
        this(ErrorCode.UNAUTHORIZED, message);
    }

    public EasyAuthException(ErrorCode errorCode, String message) {
        super(message);
        if (errorCode.getHttpStatus() != HttpStatus.UNAUTHORIZED) {
            throw new IllegalArgumentException("EasyAuthException 只能使用 401 认证错误码");
        }
        this.errorCode = errorCode;
    }
}
