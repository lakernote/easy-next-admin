package com.laker.admin.infrastructure.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EasyAudit {
    String module() default "";

    String action() default "";

    boolean operation() default true;

    boolean dataChange() default false;

    String bizType() default "";

    String bizId() default "";

    String tableName() default "";

    String changeType() default "";

    String before() default "";

    String after() default "";

    String changedFields() default "";
}
