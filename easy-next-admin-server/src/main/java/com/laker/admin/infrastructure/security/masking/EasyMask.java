package com.laker.admin.infrastructure.security.masking;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在响应 DTO、导出 DTO 或审计快照 DTO 字段上，序列化时直接输出脱敏值。
 * 原始请求参数、Map、URI 和日志文本仍应使用 EasySensitiveDataMasker 统一处理。
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@JacksonAnnotationsInside
@JsonSerialize(using = EasyMaskingJsonSerializer.class)
public @interface EasyMask {
    EasyMaskType type() default EasyMaskType.FULL;

    int prefix() default 0;

    int suffix() default 0;
}
