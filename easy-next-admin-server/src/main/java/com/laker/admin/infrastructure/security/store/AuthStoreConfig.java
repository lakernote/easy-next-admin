package com.laker.admin.infrastructure.security.store;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 认证运行态存储自动配置。
 *
 * <p>企业环境启用 Redis 后，会话和验证码自动使用 Redis 以支持多实例部署；
 * 没有 Redis 时使用本地内存，保持脚手架开箱即用。</p>
 */
@Configuration
public class AuthStoreConfig {

    @Bean
    public AuthSessionStore authSessionStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                             EasyJsonCodec jsonCodec,
                                             EasyNextAdminConfig easyNextAdminConfig) {
        // 约定优于配置：存在 Redis Bean 就使用 Redis，否则回退内存。
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        return redisTemplate == null
                ? new MemoryAuthSessionStore()
                : new RedisAuthSessionStore(redisTemplate, jsonCodec, sessionIdleTimeout(easyNextAdminConfig));
    }

    @Bean
    public CaptchaStore captchaStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                     EasyJsonCodec jsonCodec) {
        // 验证码和登录失败风险标记与会话使用同一套运行态存储策略。
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        return redisTemplate == null
                ? new MemoryCaptchaStore()
                : new RedisCaptchaStore(redisTemplate, jsonCodec);
    }

    private Duration sessionIdleTimeout(EasyNextAdminConfig config) {
        if (config == null || config.getAuth() == null || config.getAuth().getSession() == null) {
            return Duration.ofMinutes(30);
        }
        Duration idleTimeout = config.getAuth().getSession().getIdleTimeout();
        return idleTimeout == null || idleTimeout.isZero() || idleTimeout.isNegative()
                ? Duration.ofMinutes(30)
                : idleTimeout;
    }
}
