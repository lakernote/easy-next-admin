package com.laker.admin.infrastructure.idempotency.idempotent;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class IdempotentConfig {

    @Bean
    public IdempotentHandler idempotentHandler(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                               JdbcTemplate jdbcTemplate) {
        // 约定优于配置：启用 Redis 后幂等键进入 Redis，否则回退 MySQL 表。
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        return redisTemplate == null
                ? new MysqlIdempotentHandler(jdbcTemplate)
                : new RedisIdempotentHandler(redisTemplate);
    }
}
