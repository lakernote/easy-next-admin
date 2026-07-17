package com.laker.admin.infrastructure.cache.redis;

import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

import static org.assertj.core.api.Assertions.assertThat;

class EasyRedisConfigTest {

    @Test
    void redissonConfigDisablesDnsMonitoringByDefault() {
        EasyRedisProperties properties = new EasyRedisProperties();

        SingleServerConfig singleServerConfig = new EasyRedisConfig().singleServerConfig(new Config(), properties);

        assertThat(singleServerConfig.getDnsMonitoringInterval()).isEqualTo(-1);
    }

    @Test
    void redissonConfigAllowsDnsMonitoringIntervalOverride() {
        EasyRedisProperties properties = new EasyRedisProperties();
        properties.setDnsMonitoringInterval(30_000);

        SingleServerConfig singleServerConfig = new EasyRedisConfig().singleServerConfig(new Config(), properties);

        assertThat(singleServerConfig.getDnsMonitoringInterval()).isEqualTo(30_000);
    }
}
