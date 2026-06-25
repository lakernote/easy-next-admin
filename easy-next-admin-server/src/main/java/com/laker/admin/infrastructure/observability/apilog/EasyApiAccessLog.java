package com.laker.admin.infrastructure.observability.apilog;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要写入 audit_api_log 的控制器或方法。
 *
 * <p>该注解只表达 API 访问日志职责，标准指标统一由 Micrometer 采集和导出。</p>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EasyApiAccessLog {
}
