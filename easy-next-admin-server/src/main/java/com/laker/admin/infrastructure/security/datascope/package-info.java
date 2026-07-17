/**
 * 数据权限基础设施。
 *
 * <p>该组件只处理“接口已经放行后，当前账号能看哪些数据”。接口能否访问仍由
 * {@code @EasyPermission} 负责，业务查询是否应用数据权限由 Mapper 上的 {@code @DataScope} 声明。</p>
 */
package com.laker.admin.infrastructure.security.datascope;
