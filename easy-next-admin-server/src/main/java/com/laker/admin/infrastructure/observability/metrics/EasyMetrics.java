
package com.laker.admin.infrastructure.observability.metrics;

import java.lang.annotation.*;

/**
 * <pre>
 *     用于监控方法执行时间
 * </pre>
 *
 * @author easynext
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EasyMetrics {

}
