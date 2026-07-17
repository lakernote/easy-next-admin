package com.laker.admin.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.laker.admin.infrastructure.cache.redis.EasyRedisProperties;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.spring.cache.CacheConfig;
import org.redisson.spring.cache.RedissonCacheMeterBinderProvider;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author laker
 * 企业缓存配置。业务侧通过稳定的 cacheName 表达过期语义，底层可切换 Redis 或本地 Caffeine fallback。
 */
@Configuration
@EnableCaching // 启用缓存
@Slf4j
public class EasyCacheConfig {

    public static final String CACHE_NAME_1H = "CACHE_NAME_1H";
    public static final String CACHE_NAME_12H = "CACHE_NAME_12H";
    public static final String CACHE_NAME_24H = "CACHE_NAME_24H";
    public static final String CACHE_DATA_SCOPE_DEPT_TREE = "DATA_SCOPE_DEPT_TREE";
    public static final String CACHE_DATA_SCOPE_CUSTOM_DEPT_IDS = "DATA_SCOPE_CUSTOM_DEPT_IDS";
    public static final int CACHE_MAX_SIZE = 100_000;
    private static final List<CacheSpec> CACHE_SPECS = List.of(
            new CacheSpec(CACHE_NAME_1H, 1, CACHE_MAX_SIZE),
            new CacheSpec(CACHE_NAME_12H, 12, CACHE_MAX_SIZE),
            new CacheSpec(CACHE_NAME_24H, 24, CACHE_MAX_SIZE),
            new CacheSpec(CACHE_DATA_SCOPE_DEPT_TREE, 12, 10_000),
            new CacheSpec(CACHE_DATA_SCOPE_CUSTOM_DEPT_IDS, 1, CACHE_MAX_SIZE)
    );

    public static List<CacheSpec> cacheSpecs() {
        return CACHE_SPECS;
    }

    @Bean
    public CacheManager cacheManager(@Autowired(required = false) RedissonClient redissonClient) {
        CacheManager targetCacheManager;
        if (redissonClient == null) {
            log.info("Using Caffeine cache manager");
            targetCacheManager = getCaffeineCacheManager();
        } else {
            log.info("Using Redisson cache manager");
            targetCacheManager = getRedissonSpringCacheManager(redissonClient);
        }
        // 统一把缓存写入和失效延迟到事务提交后，避免业务事务回滚但缓存已被更新。
        return new TransactionAwareCacheManagerProxy(targetCacheManager);
    }

    private CacheManager getCaffeineCacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(CACHE_SPECS.stream()
                .map(this::caffeineCache)
                .toList());
        cacheManager.initializeCaches();
        return cacheManager;
    }

    private CaffeineCache caffeineCache(CacheSpec cacheSpec) {
        return new CaffeineCache(cacheSpec.cacheName(), Caffeine.newBuilder()
                .recordStats()
                .expireAfterWrite(cacheSpec.ttlHours(), TimeUnit.HOURS)
                .maximumSize(cacheSpec.maximumSize())
                .build(), false);
    }

    // ----- redisson-spring-cache -----
    private RedissonSpringCacheManager getRedissonSpringCacheManager(RedissonClient redissonClient) {
        Map<String, CacheConfig> cacheConfigMap = CACHE_SPECS.stream()
                .collect(Collectors.toUnmodifiableMap(
                        CacheSpec::cacheName,
                        this::redissonCacheConfig
                ));
        return getRedissonSpringCacheManager(redissonClient, cacheConfigMap);
    }

    private CacheConfig redissonCacheConfig(CacheSpec cacheSpec) {
        long ttlMillis = TimeUnit.HOURS.toMillis(cacheSpec.ttlHours());
        CacheConfig cacheConfig = new CacheConfig(ttlMillis, ttlMillis);
        cacheConfig.setMaxSize(Math.toIntExact(cacheSpec.maximumSize()));
        return cacheConfig;
    }

    @Bean
    @ConditionalOnProperty(prefix = "easy.features", name = "redis", havingValue = "true")
    public RedissonCacheMeterBinderProvider redissonCacheMeterBinderProvider() {
        return new RedissonCacheMeterBinderProvider();
    }

    private RedissonSpringCacheManager getRedissonSpringCacheManager(RedissonClient redissonClient, Map<String, CacheConfig> cacheConfigMap) {
        RedissonSpringCacheManager redissonSpringCacheManager = new RedissonSpringCacheManager(redissonClient);
        // 设置是否允许缓存的值为null。默认值为false，即缓存的值不允许为null。
        redissonSpringCacheManager.setAllowNullValues(false);
        // 事务感知统一由 TransactionAwareCacheManagerProxy 处理，避免不同缓存实现语义不一致。
        redissonSpringCacheManager.setTransactionAware(false);
        // 设置缓存的配置信息
        redissonSpringCacheManager.setConfig(cacheConfigMap);
        // 设置缓存的名称 定义“固定”缓存名称。对于未定义的名称，不会动态创建新的缓存实例。会返回null
        redissonSpringCacheManager.setCacheNames(cacheConfigMap.keySet());
        // 设置缓存的编码方式
        redissonSpringCacheManager.setCodec(new JsonJacksonCodec());
        return redissonSpringCacheManager;
    }

    public record CacheSpec(String cacheName, long ttlHours, long maximumSize) {
    }

}
