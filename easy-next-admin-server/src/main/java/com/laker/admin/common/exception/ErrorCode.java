package com.laker.admin.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 企业级 API 错误码定义。
 *
 * <p>HTTP 状态表达协议结果，业务错误码表达可检索、可告警、可文档化的失败原因。
 * 数字编码采用 {@code HTTP状态码 + 三位业务序号}，例如 400004 表示 400 类参数校验失败。</p>
 */
@Getter
public enum ErrorCode {
    SUCCESS(0, HttpStatus.OK, "操作成功"),

    BAD_REQUEST(400000, HttpStatus.BAD_REQUEST, "请求参数错误"),
    PARAM_TYPE_MISMATCH(400001, HttpStatus.BAD_REQUEST, "方法参数类型不匹配"),
    PARAM_MISSING(400002, HttpStatus.BAD_REQUEST, "缺少请求参数"),
    REQUEST_BODY_INVALID(400003, HttpStatus.BAD_REQUEST, "参数解析失败"),
    VALIDATION_FAILED(400004, HttpStatus.BAD_REQUEST, "参数校验失败"),
    BUSINESS_ERROR(400100, HttpStatus.BAD_REQUEST, "业务处理失败"),

    UNAUTHORIZED(401000, HttpStatus.UNAUTHORIZED, "未登录或登录已过期"),
    AUTH_INVALID_CREDENTIALS(401001, HttpStatus.UNAUTHORIZED, "用户名或密码不正确"),
    AUTH_CAPTCHA_INVALID(401002, HttpStatus.UNAUTHORIZED, "验证码不正确"),
    AUTH_SESSION_EXPIRED(401003, HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录"),
    AUTH_PERMISSION_CHANGED(401004, HttpStatus.UNAUTHORIZED, "权限已变更，请重新登录"),
    FORBIDDEN(403000, HttpStatus.FORBIDDEN, "无访问权限"),
    AUTH_ACCOUNT_DISABLED(403001, HttpStatus.FORBIDDEN, "账号已被禁用"),
    RESOURCE_NOT_FOUND(404000, HttpStatus.NOT_FOUND, "资源不存在"),
    METHOD_NOT_SUPPORTED(405000, HttpStatus.METHOD_NOT_ALLOWED, "不支持当前请求方法"),
    DUPLICATE_RESOURCE(409000, HttpStatus.CONFLICT, "资源已存在"),
    PAYLOAD_TOO_LARGE(413000, HttpStatus.PAYLOAD_TOO_LARGE, "上传文件过大"),
    MEDIA_TYPE_NOT_SUPPORTED(415000, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持当前媒体类型"),
    TOO_MANY_REQUESTS(429000, HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),

    INTERNAL_ERROR(500000, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部发生未知异常");

    private final int code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public static ErrorCode fromHttpStatus(int status) {
        return switch (status) {
            case 400 -> BAD_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_SUPPORTED;
            case 409 -> DUPLICATE_RESOURCE;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> MEDIA_TYPE_NOT_SUPPORTED;
            case 429 -> TOO_MANY_REQUESTS;
            default -> status >= 500 ? INTERNAL_ERROR : BUSINESS_ERROR;
        };
    }
}
