
package com.laker.admin.infrastructure.web.handler;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.common.model.ApiErrorDetail;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.ratelimit.RateLimitException;
import com.laker.admin.infrastructure.security.exception.EasyAuthException;
import com.laker.admin.infrastructure.security.exception.EasyForbiddenException;
import com.laker.admin.infrastructure.web.context.EasyRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.internal.engine.path.NodeImpl;
import org.hibernate.validator.internal.engine.path.PathImpl;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * 异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    /**
     * 处理自定义异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Response<Void>> handleRRException(BusinessException e, HttpServletRequest request) {
        logRequestInfo(request);
        log.error("发生业务异常: {}", e.getMsg());
        return error(e.getErrorCode(), e.getMsg());
    }

    /**
     * 400 - Bad Request
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Response<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        logRequestInfo(request);
        log.error("方法参数类型不匹配", e);
        return error(ErrorCode.PARAM_TYPE_MISMATCH);
    }

    /**
     * 400 - Bad Request
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Response<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e, HttpServletRequest request) {
        logRequestInfo(request);
        log.error("缺少请求参数", e);
        return error(ErrorCode.PARAM_MISSING);
    }

    /**
     * 400 - Bad Request
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException e, HttpServletRequest request) {
        logRequestInfo(request);
        log.error("参数解析失败", e);
        return error(ErrorCode.REQUEST_BODY_INVALID);
    }

    /**
     * 400 - Bad Request
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Response<Void>> handleValidationException(ValidationException e, HttpServletRequest request) {
        logRequestInfo(request);
        log.error("参数验证失败", e);
        return error(ErrorCode.VALIDATION_FAILED);
    }

    /**
     * 405 - Method Not Allowed
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Response<Void>> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        logRequestInfo(request);
        log.error("不支持当前请求方法", e);
        return error(ErrorCode.METHOD_NOT_SUPPORTED);
    }

    /**
     * 415 - Unsupported Media Type
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Response<Void>> handleHttpMediaTypeNotSupportedException(Exception e, HttpServletRequest request) {
        logRequestInfo(request);
        log.error("不支持当前媒体类型", e);
        return error(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Response<Void>> handleDuplicateKeyException(DuplicateKeyException e, HttpServletRequest request) {
        logRequestInfo(request);
        log.error(e.getMessage(), e);
        return error(ErrorCode.DUPLICATE_RESOURCE, "数据库中已存在该记录");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Response<Void>> handleMaxSizeException(MaxUploadSizeExceededException e, HttpServletRequest request) {
        logRequestInfo(request);
        log.error(e.getMessage(), e);
        return error(ErrorCode.PAYLOAD_TOO_LARGE, "上传文件过大");
    }

    @ExceptionHandler(EasyAuthException.class)
    public ResponseEntity<Response<Void>> handleEasyAuthException(EasyAuthException e, HttpServletRequest request) {
        log.warn("uri:{}, httpMethod:{}, errMsg:{}", requestUri(request), requestMethod(request), e.getMessage());
        return error(e.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(EasyForbiddenException.class)
    public ResponseEntity<Response<Void>> handleEasyForbiddenException(EasyForbiddenException e, HttpServletRequest request) {
        log.warn("uri:{}, httpMethod:{}, errMsg:{}", requestUri(request), requestMethod(request), e.getMessage());
        return error(e.getErrorCode(), e.getMessage());
    }

    /**
     * 验证bean类型
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException e,
                                                                               HttpServletRequest request) {
        log.warn("参数校验失败, uri:{}, method:{}, errorCount:{}",
                requestUri(request),
                requestMethod(request),
                e.getBindingResult().getFieldErrorCount());
        List<ApiErrorDetail> result = new ArrayList<>();
        e.getBindingResult().getFieldErrors().forEach(
                (fieldError) -> result.add(validationError(fieldError.getField(), fieldError.getDefaultMessage())));
        return error(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultMessage(), result);
    }

    /**
     * 参数校验
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Response<Void>> handleConstraintViolationException(ConstraintViolationException e,
                                                                            HttpServletRequest request) {
        log.warn("参数约束校验失败, uri:{}, method:{}, errorCount:{}",
                requestUri(request),
                requestMethod(request),
                e.getConstraintViolations().size());
        List<ApiErrorDetail> result = new ArrayList<>();
        e.getConstraintViolations().forEach((constraintViolation) -> {
            PathImpl path = (PathImpl) constraintViolation.getPropertyPath();
            NodeImpl leafNode = path.getLeafNode();
            String leafNodeName = leafNode.getName();
            result.add(validationError(leafNodeName, constraintViolation.getMessage()));
        });
        return error(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.getDefaultMessage(), result);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Response<Void>> handlerNoFoundException(Exception e) {
        log.error(e.getMessage(), e);
        return error(ErrorCode.RESOURCE_NOT_FOUND, "路径不存在，请检查路径是否正确");
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<Response<Void>> handleRateLimitException(RateLimitException e) {
        log.error(e.getMessage(), e);
        return error(ErrorCode.TOO_MANY_REQUESTS, e.getMessage());
    }


    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Response<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.error("uri:{}, method:{}, detail:{}", e.getResourcePath(), e.getHttpMethod(), e.getBody().getDetail());
        return error(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleException(Exception e, HttpServletRequest request) {
        logRequestInfo(request);
        log.error(e.getMessage(), e);
        return error(ErrorCode.INTERNAL_ERROR);
    }

    private void logRequestInfo(HttpServletRequest request) {
        if (request == null) {
            log.info("请求详情不可用");
            return;
        }
        log.info("请求详情为：\nRemoteAddress: {}\nMethod: {}\nURI: {}",
                EasyRequestContext.remoteIp(request),
                request.getMethod(),
                requestUri(request));
    }

    private String requestUri(HttpServletRequest request) {
        if (request == null) {
            return "-";
        }
        String queryString = request.getQueryString();
        if (queryString == null || queryString.isBlank()) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + queryString;
    }

    private String requestMethod(HttpServletRequest request) {
        return request == null ? "-" : request.getMethod();
    }

    private ResponseEntity<Response<Void>> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getDefaultMessage());
    }

    private ResponseEntity<Response<Void>> error(ErrorCode errorCode, String message) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(Response.error(errorCode, message));
    }

    private ResponseEntity<Response<Void>> error(ErrorCode errorCode, String message, List<ApiErrorDetail> details) {
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(Response.error(errorCode, message, details));
    }

    private ApiErrorDetail validationError(String field, String message) {
        return ApiErrorDetail.of(field, message);
    }
}
