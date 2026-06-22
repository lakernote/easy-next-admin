package com.laker.admin.infrastructure.cache.redis;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 自定义配置
 *
 * @author laker
 */
@Configuration
@Data
@ConfigurationProperties(prefix = "easy.spring.redis")
@ConditionalOnProperty(prefix = "easy.features", name = "redis", havingValue = "true")
public class EasyRedisProperties {
    private String address = "redis://localhost:6379";
    private String password;
    private int database = 0;
    private String clientName = "easy-next-admin";
    private int timeout = 3000;
    private int connectTimeout = 3000;
    private int connectionMinimumIdleSize = 1;
    private int connectionPoolSize = 5;
    private int subscriptionConnectionMinimumIdleSize = 1;
    private int subscriptionConnectionPoolSize = 5;
    /**
     * Redisson 默认 5000ms 会启动 DNSMonitor；单节点内网 Redis 默认关闭，避免无意义的 PRO/DNSMonitor 噪声。
     */
    private long dnsMonitoringInterval = -1;

}
