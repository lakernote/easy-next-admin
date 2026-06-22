/**
 * EasyNextAdmin 自研认证与授权边界。
 *
 * <p>该包只处理登录态、请求身份、菜单/按钮权限、验证码和在线会话，不直接承载业务规则。
 * 业务模块通过 {@code @EasyPermission} 声明权限点，通过 {@code EasySecurityContext}
 * 读取当前请求用户。</p>
 */
package com.laker.admin.infrastructure.security;
