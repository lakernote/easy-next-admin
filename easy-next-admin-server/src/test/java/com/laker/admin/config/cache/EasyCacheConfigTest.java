package com.laker.admin.config.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class EasyCacheConfigTest {

    @Test
    void shouldUseCacheNameSpecificTtlForCaffeineFallback() {
        CacheManager cacheManager = new EasyCacheConfig().cacheManager(null);

        assertThat(cacheManager).isInstanceOf(TransactionAwareCacheManagerProxy.class);
        assertThat(expiresAfterHours(cacheManager, EasyCacheConfig.CACHE_NAME_1H)).isEqualTo(1);
        assertThat(expiresAfterHours(cacheManager, EasyCacheConfig.CACHE_NAME_12H)).isEqualTo(12);
        assertThat(expiresAfterHours(cacheManager, EasyCacheConfig.CACHE_NAME_24H)).isEqualTo(24);
        assertThat(expiresAfterHours(cacheManager, EasyCacheConfig.CACHE_DATA_SCOPE_DEPT_TREE)).isEqualTo(12);
        assertThat(expiresAfterHours(cacheManager, EasyCacheConfig.CACHE_DATA_SCOPE_CUSTOM_DEPT_IDS)).isEqualTo(1);
    }

    @Test
    void shouldDeferCacheEvictUntilTransactionCommit() {
        CacheManager cacheManager = new EasyCacheConfig().cacheManager(null);
        org.springframework.cache.Cache cache = cacheManager.getCache(EasyCacheConfig.CACHE_DATA_SCOPE_CUSTOM_DEPT_IDS);
        org.springframework.cache.Cache targetCache = targetCache(cache);
        cache.put(9L, Set.of(20L));

        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evict(9L);

            assertThat(targetCache.get(9L)).isNotNull();
            TransactionSynchronizationUtils.triggerAfterCommit();
            assertThat(targetCache.get(9L)).isNull();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private long expiresAfterHours(CacheManager cacheManager, String cacheName) {
        org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);
        springCache = targetCache(springCache);
        assertThat(springCache).isInstanceOf(CaffeineCache.class);
        Cache<?, ?> nativeCache = (Cache<?, ?>) springCache.getNativeCache();
        return nativeCache.policy()
                .expireAfterWrite()
                .orElseThrow()
                .getExpiresAfter(TimeUnit.HOURS);
    }

    private org.springframework.cache.Cache targetCache(org.springframework.cache.Cache springCache) {
        if (springCache instanceof TransactionAwareCacheDecorator transactionAwareCache) {
            return transactionAwareCache.getTargetCache();
        }
        return springCache;
    }
}
