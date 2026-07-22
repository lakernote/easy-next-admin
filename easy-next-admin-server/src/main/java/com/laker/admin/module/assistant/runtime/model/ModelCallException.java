package com.laker.admin.module.assistant.runtime.model;

import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;

import java.net.ConnectException;
import java.net.HttpRetryException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/** 模型网关和协议失败的稳定异常合同，避免 AgentLoop 依赖具体 Provider 异常。 */
public final class ModelCallException extends RuntimeException {
    private final ModelCallFailureType failureType;
    private final boolean retryable;
    private final Duration retryDelay;

    private ModelCallException(ModelCallFailureType failureType,
                               boolean retryable,
                               Duration retryDelay,
                               String message,
                               Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
        this.retryable = retryable;
        this.retryDelay = retryDelay == null || retryDelay.isNegative() ? Duration.ZERO : retryDelay;
    }

    public ModelCallFailureType failureType() {
        return failureType;
    }

    public boolean retryable() {
        return retryable;
    }

    public Duration retryDelay() {
        return retryDelay;
    }

    public static ModelCallException classify(RuntimeException exception) {
        if (exception instanceof ModelCallException modelCallException) {
            return modelCallException;
        }
        ModelCallException nestedModelFailure = findCause(exception, ModelCallException.class);
        if (nestedModelFailure != null) {
            return nestedModelFailure;
        }
        if (hasCause(exception, ModelCallException::isTimeoutCause)) {
            return retryable(ModelCallFailureType.TIMEOUT, Duration.ofMillis(200), exception);
        }
        if (hasCause(exception, ModelCallException::isNetworkCause)) {
            return retryable(ModelCallFailureType.NETWORK, Duration.ofMillis(200), exception);
        }
        if (hasCause(exception, TransientAiException.class::isInstance)) {
            return retryable(ModelCallFailureType.SERVER_ERROR, Duration.ofMillis(200), exception);
        }
        if (hasCause(exception, NonTransientAiException.class::isInstance)) {
            return nonRetryable(ModelCallFailureType.CLIENT_ERROR, exception.getMessage(), exception);
        }
        if (hasCause(exception, ModelCallException::messageIndicatesTimeout)) {
            return retryable(ModelCallFailureType.TIMEOUT, Duration.ofMillis(200), exception);
        }
        return nonRetryable(ModelCallFailureType.UNKNOWN, exception.getMessage(), exception);
    }

    public static ModelCallException rateLimited(String message, Duration retryDelay) {
        return new ModelCallException(ModelCallFailureType.RATE_LIMIT, true, retryDelay, message, null);
    }

    public static ModelCallException serverError(String message) {
        return new ModelCallException(
                ModelCallFailureType.SERVER_ERROR,
                true,
                Duration.ofMillis(200),
                message,
                null
        );
    }

    public static ModelCallException authentication(String message) {
        return nonRetryable(ModelCallFailureType.AUTHENTICATION, message, null);
    }

    public static ModelCallException clientError(String message) {
        return nonRetryable(ModelCallFailureType.CLIENT_ERROR, message, null);
    }

    public static ModelCallException protocol(String message) {
        return nonRetryable(ModelCallFailureType.PROTOCOL_ERROR, message, null);
    }

    private static boolean isTimeoutCause(Throwable cause) {
        return cause instanceof SocketTimeoutException
                || cause instanceof HttpTimeoutException
                || cause instanceof TimeoutException;
    }

    private static boolean isNetworkCause(Throwable cause) {
        return cause instanceof ResourceAccessException
                || cause instanceof ConnectException
                || cause instanceof UnknownHostException
                || cause instanceof NoRouteToHostException
                || cause instanceof HttpRetryException;
    }

    private static boolean messageIndicatesTimeout(Throwable cause) {
        if (cause == null || cause.getMessage() == null) {
            return false;
        }
        String message = cause.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("timed out")
                || message.contains("timeout on blocking read")
                || message.contains("read timeout")
                || message.contains("connect timeout");
    }

    private static boolean hasCause(Throwable exception, Predicate<Throwable> matcher) {
        Throwable current = exception;
        for (int depth = 0; current != null && depth < 32; depth++) {
            if (matcher.test(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static <T extends Throwable> T findCause(Throwable exception, Class<T> type) {
        Throwable current = exception;
        for (int depth = 0; current != null && depth < 32; depth++) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            current = current.getCause();
        }
        return null;
    }

    private static ModelCallException retryable(ModelCallFailureType type,
                                                Duration delay,
                                                RuntimeException cause) {
        return new ModelCallException(type, true, delay, cause.getMessage(), cause);
    }

    private static ModelCallException nonRetryable(ModelCallFailureType type,
                                                   String message,
                                                   Throwable cause) {
        String safeMessage = message == null || message.isBlank() ? type.name() : message;
        return new ModelCallException(type, false, Duration.ZERO, safeMessage, cause);
    }
}
