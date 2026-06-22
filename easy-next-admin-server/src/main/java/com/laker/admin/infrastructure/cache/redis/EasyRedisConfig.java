package com.laker.admin.infrastructure.cache.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty(prefix = "easy.features", name = "redis", havingValue = "true")
public class EasyRedisConfig {

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<Object, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<Object, Object> template = new RedisTemplate<Object, Object>();
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }

    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public RedissonConnectionFactory redissonConnectionFactory(RedissonClient redisson) {
        return new RedissonConnectionFactory(redisson);
    }


    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redisson(EasyRedisProperties easyRedisProperties) {
        Config config = new Config();
        singleServerConfig(config, easyRedisProperties);
        return Redisson.create(config);
    }

    SingleServerConfig singleServerConfig(Config config, EasyRedisProperties easyRedisProperties) {
        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress(easyRedisProperties.getAddress())
                .setDatabase(easyRedisProperties.getDatabase())
                .setTimeout(easyRedisProperties.getTimeout())
                .setConnectionMinimumIdleSize(easyRedisProperties.getConnectionMinimumIdleSize())
                .setConnectionPoolSize(easyRedisProperties.getConnectionPoolSize())
                .setDnsMonitoringInterval(easyRedisProperties.getDnsMonitoringInterval())
                .setSubscriptionConnectionMinimumIdleSize(easyRedisProperties.getSubscriptionConnectionMinimumIdleSize())
                .setSubscriptionConnectionPoolSize(easyRedisProperties.getSubscriptionConnectionPoolSize())
                .setConnectTimeout(easyRedisProperties.getConnectTimeout())
                .setClientName(easyRedisProperties.getClientName());
        // Redis 没有密码时不要传空字符串，否则部分 Redis 服务会按“空密码认证”处理。
        if (StringUtils.hasText(easyRedisProperties.getPassword())) {
            serverConfig.setPassword(easyRedisProperties.getPassword());
        }
        return serverConfig;
    }

}
