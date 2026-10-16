package com.laker.observability.trace;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface TraceSpan {
    /** Span name. Empty uses the target class and method name. */
    String name() default "";

    /** Optional bounded diagnostic tag. Do not put secrets or personal data here. */
    String tag() default "";

    /** Span category. */
    SpanKind kind() default SpanKind.OTHER;
}
