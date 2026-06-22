/**
 * 跨模块通用层。
 *
 * <p>只放稳定、无业务归属的基础对象，例如统一响应、通用异常、工具类和常量。
 * 这里不能反向依赖 {@code module} 或 {@code infrastructure}，避免公共层变成业务杂物间。</p>
 */
package com.laker.admin.common;
