/**
 * Spring Boot 装配层入口。
 *
 * <p>顶层包只保留入口说明，具体配置按职责放入子包：</p>
 *
 * <ul>
 *   <li>{@code properties}: 项目自定义配置属性。</li>
 *   <li>{@code web}: Servlet Filter、Web MVC、静态资源和 WebSocket。</li>
 *   <li>{@code database}: MyBatis-Plus 和事务管理。</li>
 *   <li>{@code cache}: 缓存管理器。</li>
 *   <li>{@code thread}: 线程池和调度线程池。</li>
 *   <li>{@code remote}: Feign、熔断和远程调用基础配置。</li>
 *   <li>{@code observability}: 追踪和可观测性配置。</li>
 *   <li>{@code api}: OpenAPI 文档配置。</li>
 *   <li>{@code jackson}: JSON 序列化定制。</li>
 * </ul>
 *
 * <p>业务规则应放在 {@code module}，技术实现应放在 {@code infrastructure}。</p>
 */
package com.laker.admin.config;
