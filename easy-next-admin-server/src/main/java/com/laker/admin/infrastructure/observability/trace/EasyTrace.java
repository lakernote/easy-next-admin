package com.laker.admin.infrastructure.observability.trace;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface EasyTrace {
    String value() default "";

    String tag() default "";

    SpanType spanType() default SpanType.Others;
}
