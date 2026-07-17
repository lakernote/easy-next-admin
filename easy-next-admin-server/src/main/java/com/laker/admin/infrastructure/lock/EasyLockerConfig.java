package com.laker.admin.infrastructure.lock;

import com.laker.admin.config.thread.EasyThreadPoolConfig;
import com.laker.admin.config.thread.EasyThreadPoolProperties;
import com.laker.admin.infrastructure.lock.impl.MysqlEasyLocker;
import com.laker.admin.infrastructure.lock.impl.RedisEasyLocker;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * @author: easynext
 * @date: 2022/11/1
 **/
@Configuration
public class EasyLockerConfig {

    @Bean
    @ConditionalOnMissingBean
    public IEasyLocker easyLocker(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                  JdbcTemplate jdbcTemplate,
                                  ThreadPoolTaskScheduler easyLockTaskThreadPool) {
        // 约定优于配置：启用 Redis 后自动使用 Redis 分布式锁，否则回退 MySQL 锁。
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        return redisTemplate == null
                ? new MysqlEasyLocker(jdbcTemplate, easyLockTaskThreadPool)
                : new RedisEasyLocker(redisTemplate, easyLockTaskThreadPool);
    }

    @Bean
    public ThreadPoolTaskScheduler easyLockTaskThreadPool(EasyThreadPoolProperties properties) {
        EasyThreadPoolProperties.Lock lock = properties.getLock();
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(lock.getPoolSize());
        threadPoolTaskScheduler.setThreadNamePrefix(lock.getThreadNamePrefix());
        threadPoolTaskScheduler.setWaitForTasksToCompleteOnShutdown(lock.isWaitForTasksToCompleteOnShutdown());
        threadPoolTaskScheduler.setAwaitTerminationSeconds(lock.getAwaitTerminationSeconds());
        threadPoolTaskScheduler.setRejectedExecutionHandler(new EasyThreadPoolConfig.CustomRejectedExecutionHandler());
        return threadPoolTaskScheduler;
    }
}
