/**
 * 认证运行态存储策略。
 *
 * <p>登录会话、验证码和登录失败风险标记都是短生命周期数据，不进入业务数据库。
 * 容器中存在 Redis 客户端时自动使用 Redis，否则回退到本机内存，降低本地开发配置成本。</p>
 */
package com.laker.admin.infrastructure.security.store;
