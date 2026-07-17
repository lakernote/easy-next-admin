package com.laker.admin.infrastructure.json;

/**
 * JSON 编解码异常。
 */
public class EasyJsonException extends RuntimeException {

    public EasyJsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
