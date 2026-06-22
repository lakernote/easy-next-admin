package com.laker.admin.infrastructure.security.support;

import com.laker.admin.infrastructure.security.annotation.EasyIgnoreAuth;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;

public final class EasyAuthAnnotations {
    private EasyAuthAnnotations() {
    }

    public static boolean isIgnored(HandlerMethod handlerMethod) {
        return handlerMethod != null
                && (AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), EasyIgnoreAuth.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), EasyIgnoreAuth.class));
    }
}
