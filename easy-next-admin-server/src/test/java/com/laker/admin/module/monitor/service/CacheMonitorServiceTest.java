package com.laker.admin.module.monitor.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.config.cache.EasyCacheConfig;
import com.laker.admin.module.monitor.dto.CacheEntryView;
import com.laker.admin.module.monitor.dto.CacheMonitorOverview;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.redisson.api.RMapCache;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheMonitorServiceTest {

    @Test
    void overviewIncludesPolicyStatisticsAndHealth() {
        CaffeineCache cache = new CaffeineCache(EasyCacheConfig.CACHE_NAME_1H, Caffeine.newBuilder()
                .recordStats()
                .expireAfterWrite(1, TimeUnit.HOURS)
                .maximumSize(10)
                .build(), false);
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(List.of(cache));
        cacheManager.initializeCaches();

        cache.getNativeCache().put("system:user:detail:1", "admin");
        cache.getNativeCache().getIfPresent("system:user:detail:1");
        cache.getNativeCache().getIfPresent("system:user:detail:2");

        CacheMonitorService cacheMonitorService = new CacheMonitorService(cacheManager, new ObjectMapper());
        CacheMonitorOverview overview = cacheMonitorService.overview();

        assertThat(overview.getProvider()).isEqualTo("SimpleCacheManager");
        assertThat(overview.getScope()).isEqualTo("LOCAL");
        assertThat(overview.getStatisticsAvailable()).isTrue();
        assertThat(overview.getCacheCount()).isEqualTo(1);
        assertThat(overview.getTotalEstimatedSize()).isEqualTo(1);
        assertThat(overview.getTotalMaximumSize()).isEqualTo(10);
        assertThat(overview.getTotalRequestCount()).isEqualTo(2);
        assertThat(overview.getTotalHitCount()).isEqualTo(1);
        assertThat(overview.getTotalMissCount()).isEqualTo(1);
        assertThat(overview.getHitRate()).isEqualTo(50);
        assertThat(overview.getUsageRate()).isEqualTo(10);
        assertThat(overview.getRecommendations()).isNotEmpty();

        CacheMonitorOverview.CacheItem item = overview.getCaches().get(0);
        assertThat(item.getName()).isEqualTo(EasyCacheConfig.CACHE_NAME_1H);
        assertThat(item.getMaximumSize()).isEqualTo(10);
        assertThat(item.getTtlSeconds()).isEqualTo(3600);
        assertThat(item.getRequestCount()).isEqualTo(2);
        assertThat(item.getHitRate()).isEqualTo(50);
        assertThat(item.getMissRate()).isEqualTo(50);
        assertThat(item.getUsageRate()).isEqualTo(10);
        assertThat(item.getHealthStatus()).isEqualTo("HEALTHY");
        assertThat(item.getHealthLabel()).isEqualTo("运行正常");

        CacheEntryView entries = cacheMonitorService.entries(EasyCacheConfig.CACHE_NAME_1H, "system:user", "system:user:detail:1", 100);
        assertThat(entries.getTotal()).isEqualTo(1);
        assertThat(entries.getSelected().getKey()).isEqualTo("system:user:detail:1");
        assertThat(entries.getSelected().getValuePreview()).isEqualTo("admin");
    }

    @Test
    void overviewReadsRedissonStatisticsFromMicrometer() {
        Cache cache = mock(Cache.class);
        RMapCache<Object, Object> nativeCache = mock(RMapCache.class);
        CacheManager cacheManager = mock(CacheManager.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        when(cacheManager.getCacheNames()).thenReturn(List.of(EasyCacheConfig.CACHE_NAME_12H));
        when(cacheManager.getCache(EasyCacheConfig.CACHE_NAME_12H)).thenReturn(cache);
        when(cache.getNativeCache()).thenReturn(nativeCache);
        when(nativeCache.size()).thenReturn(3);

        Counter.builder("cache.gets")
                .tag("cache", EasyCacheConfig.CACHE_NAME_12H)
                .tag("result", "hit")
                .register(meterRegistry)
                .increment(7);
        Counter.builder("cache.gets")
                .tag("cache", EasyCacheConfig.CACHE_NAME_12H)
                .tag("result", "miss")
                .register(meterRegistry)
                .increment(3);
        Counter.builder("cache.evictions")
                .tag("cache", EasyCacheConfig.CACHE_NAME_12H)
                .register(meterRegistry)
                .increment(2);

        CacheMonitorService cacheMonitorService = new CacheMonitorService(cacheManager, new ObjectMapper(), meterRegistry);
        CacheMonitorOverview overview = cacheMonitorService.overview();

        assertThat(overview.getStatisticsAvailable()).isTrue();
        assertThat(overview.getTotalEstimatedSize()).isEqualTo(3);
        assertThat(overview.getTotalRequestCount()).isEqualTo(10);
        assertThat(overview.getTotalHitCount()).isEqualTo(7);
        assertThat(overview.getTotalMissCount()).isEqualTo(3);
        assertThat(overview.getTotalEvictionCount()).isEqualTo(2);
        assertThat(overview.getHitRate()).isEqualTo(70);

        CacheMonitorOverview.CacheItem item = overview.getCaches().get(0);
        assertThat(item.getStatisticsAvailable()).isTrue();
        assertThat(item.getRequestCount()).isEqualTo(10);
        assertThat(item.getHitCount()).isEqualTo(7);
        assertThat(item.getMissCount()).isEqualTo(3);
        assertThat(item.getEvictionCount()).isEqualTo(2);
    }
}
