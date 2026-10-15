package com.laker.admin.infrastructure.web.context;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Bridges exceptions resolved by Spring MVC's {@code @ExceptionHandler} methods to the request-completion
 * interceptor. Spring normally invokes {@code HandlerInterceptor.afterCompletion(...)} with a {@code null}
 * exception after an exception has been converted to a response, which would otherwise lose the real failure
 * type and stack from diagnosis incidents.
 */
public final class EasyResolvedExceptionContext {
    private static final String ATTRIBUTE = EasyResolvedExceptionContext.class.getName() + ".throwable";

    private EasyResolvedExceptionContext() {
    }

    public static void capture(HttpServletRequest request, Throwable failure) {
        if (request != null && failure != null && request.getAttribute(ATTRIBUTE) == null) {
            request.setAttribute(ATTRIBUTE, failure);
        }
    }

    public static Throwable get(HttpServletRequest request) {
        if (request == null) return null;
        Object value = request.getAttribute(ATTRIBUTE);
        return value instanceof Throwable throwable ? throwable : null;
    }

    public static void clear(HttpServletRequest request) {
        if (request != null) request.removeAttribute(ATTRIBUTE);
    }
}
