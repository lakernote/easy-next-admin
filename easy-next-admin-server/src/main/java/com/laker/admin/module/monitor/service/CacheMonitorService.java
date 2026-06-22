package com.laker.admin.module.monitor.service;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.config.cache.EasyCacheConfig;
import com.laker.admin.infrastructure.security.masking.EasySensitiveDataMasker;
import com.laker.admin.module.monitor.dto.CacheEntryView;
import com.laker.admin.module.monitor.dto.CacheMonitorOverview;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import org.redisson.api.RMapCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class CacheMonitorService {
    private static final DateTimeFormatter SAMPLE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int LOW_HIT_REQUEST_THRESHOLD = 20;
    private static final int LOW_HIT_RATE_THRESHOLD = 50;
    private static final int CAPACITY_WARNING_THRESHOLD = 90;
    private static final int DEFAULT_ENTRY_LIMIT = 100;
    private static final int MAX_ENTRY_LIMIT = 200;
    private static final int VALUE_PREVIEW_LIMIT = 12_000;
    private final CacheManager cacheManager;
    private final Map<String, EasyCacheConfig.CacheSpec> cacheSpecs;
    private final ObjectMapper objectMapper;
    private final EasySensitiveDataMasker sensitiveDataMasker;
    private final MeterRegistry meterRegistry;

    @Autowired
    public CacheMonitorService(CacheManager cacheManager,
                               ObjectMapper objectMapper,
                               EasySensitiveDataMasker sensitiveDataMasker,
                               @Autowired(required = false) MeterRegistry meterRegistry) {
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.meterRegistry = meterRegistry;
        this.cacheSpecs = EasyCacheConfig.cacheSpecs().stream()
                .collect(Collectors.toUnmodifiableMap(EasyCacheConfig.CacheSpec::cacheName, spec -> spec));
    }

    public CacheMonitorService(CacheManager cacheManager, ObjectMapper objectMapper) {
        this(cacheManager, objectMapper, new EasySensitiveDataMasker(objectMapper), null);
    }

    public CacheMonitorService(CacheManager cacheManager, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
        this(cacheManager, objectMapper, new EasySensitiveDataMasker(objectMapper), meterRegistry);
    }

    public CacheMonitorOverview overview() {
        List<CacheMonitorOverview.CacheItem> items = cacheManager.getCacheNames().stream()
                .map(this::cacheItem)
                .sorted(Comparator.comparing(CacheMonitorOverview.CacheItem::getName))
                .toList();

        long totalEstimatedSize = items.stream()
                .map(CacheMonitorOverview.CacheItem::getEstimatedSize)
                .filter(value -> value != null && value >= 0)
                .mapToLong(Long::longValue)
                .sum();
        long totalMaximumSize = items.stream()
                .map(CacheMonitorOverview.CacheItem::getMaximumSize)
                .filter(value -> value != null && value > 0)
                .mapToLong(Long::longValue)
                .sum();
        boolean statisticsAvailable = items.stream()
                .anyMatch(item -> Boolean.TRUE.equals(item.getStatisticsAvailable()));
        long requestCount = statisticsAvailable ? items.stream().mapToLong(item -> safeLong(item.getRequestCount())).sum() : 0L;
        long hitCount = statisticsAvailable ? items.stream().mapToLong(item -> safeLong(item.getHitCount())).sum() : 0L;
        long missCount = statisticsAvailable ? items.stream().mapToLong(item -> safeLong(item.getMissCount())).sum() : 0L;
        long evictionCount = statisticsAvailable ? items.stream().mapToLong(item -> safeLong(item.getEvictionCount())).sum() : 0L;

        return CacheMonitorOverview.builder()
                .provider(cacheManager.getClass().getSimpleName())
                .scope(resolveScope())
                .sampleTime(LocalDateTime.now().format(SAMPLE_TIME_FORMATTER))
                .statisticsAvailable(statisticsAvailable)
                .cacheCount(items.size())
                .totalEstimatedSize(totalEstimatedSize)
                .totalMaximumSize(totalMaximumSize > 0 ? totalMaximumSize : null)
                .totalRequestCount(statisticsAvailable ? requestCount : null)
                .totalHitCount(statisticsAvailable ? hitCount : null)
                .totalMissCount(statisticsAvailable ? missCount : null)
                .totalEvictionCount(statisticsAvailable ? evictionCount : null)
                .hitRate(statisticsAvailable ? percent(hitCount, requestCount) : null)
                .missRate(statisticsAvailable ? percent(missCount, requestCount) : null)
                .usageRate(percent(totalEstimatedSize, totalMaximumSize))
                .warningCount((int) items.stream().filter(this::isWarning).count())
                .busiestCacheName(busiestCacheName(items))
                .largestCacheName(largestCacheName(items))
                .weakestCacheName(weakestCacheName(items))
                .recommendations(recommendations(items, statisticsAvailable, requestCount))
                .caches(items)
                .build();
    }

    public void clear(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "缓存不存在：" + cacheName);
        }
        cache.clear();
    }

    public CacheEntryView entries(String cacheName, String keyword, String selectedKey, Integer limit) {
        Cache cache = requiredCache(cacheName);
        int normalizedLimit = normalizeEntryLimit(limit);
        Map<Object, Object> nativeEntries = nativeEntries(cache);
        List<CacheEntryView.CacheEntryItem> matchedEntries = new ArrayList<>();
        CacheEntryView.CacheEntryItem selected = null;
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        int matchedCount = 0;

        for (Map.Entry<Object, Object> entry : nativeEntries.entrySet()) {
            String key = stringifyKey(entry.getKey());
            if (StringUtils.hasText(normalizedKeyword) && !key.contains(normalizedKeyword)) {
                continue;
            }
            matchedCount++;
            CacheEntryView.CacheEntryItem item = cacheEntryItem(key, entry.getKey(), entry.getValue());
            if (Objects.equals(key, selectedKey)) {
                selected = item;
            }
            if (matchedEntries.size() < normalizedLimit) {
                matchedEntries.add(item);
            }
        }

        if (selected == null && StringUtils.hasText(selectedKey)) {
            selected = nativeEntries.entrySet().stream()
                    .filter(entry -> Objects.equals(stringifyKey(entry.getKey()), selectedKey))
                    .findFirst()
                    .map(entry -> cacheEntryItem(stringifyKey(entry.getKey()), entry.getKey(), entry.getValue()))
                    .orElse(null);
        }

        return CacheEntryView.builder()
                .cacheName(cacheName)
                .provider(cacheManager.getClass().getSimpleName())
                .nativeClass(targetCache(cache).getNativeCache().getClass().getSimpleName())
                .scope(resolveScope())
                .total(matchedCount)
                .limit(normalizedLimit)
                .truncated(matchedCount > normalizedLimit)
                .selectedKey(selected == null ? null : selected.getKey())
                .selected(selected)
                .entries(matchedEntries)
                .build();
    }

    public void evictEntry(String cacheName, String key) {
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(ErrorCode.PARAM_MISSING, "缓存键不能为空");
        }
        Cache cache = requiredCache(cacheName);
        Object nativeKey = nativeEntries(cache).keySet().stream()
                .filter(candidate -> Objects.equals(stringifyKey(candidate), key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "缓存键不存在：" + key));
        cache.evict(nativeKey);
    }

    private CacheMonitorOverview.CacheItem cacheItem(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        EasyCacheConfig.CacheSpec spec = cacheSpecs.get(cacheName);
        Long configuredMaximumSize = spec == null ? null : spec.maximumSize();
        Long configuredTtlSeconds = spec == null ? null : spec.ttlHours() * 3600;
        if (cache == null) {
            CacheMonitorOverview.CacheItem item = CacheMonitorOverview.CacheItem.builder()
                    .name(cacheName)
                    .provider(cacheManager.getClass().getSimpleName())
                    .maximumSize(configuredMaximumSize)
                    .ttlSeconds(configuredTtlSeconds)
                    .statisticsAvailable(false)
                    .build();
            return withHealth(item);
        }

        CacheMonitorOverview.CacheItem.CacheItemBuilder builder = CacheMonitorOverview.CacheItem.builder()
                .name(cacheName)
                .provider(cacheManager.getClass().getSimpleName())
                .nativeClass(targetCache(cache).getNativeCache().getClass().getSimpleName())
                .maximumSize(configuredMaximumSize)
                .ttlSeconds(configuredTtlSeconds)
                .statisticsAvailable(false);

        Cache targetCache = targetCache(cache);
        if (targetCache instanceof CaffeineCache caffeineCache) {
            com.github.benmanes.caffeine.cache.Cache<Object, Object> nativeCache = caffeineCache.getNativeCache();
            long estimatedSize = nativeCache.estimatedSize();
            CacheStats stats = nativeCache.stats();
            long requestCount = stats.requestCount();
            long maximumSize = nativeCache.policy().eviction()
                    .map(policy -> policy.getMaximum())
                    .orElse(configuredMaximumSize == null ? 0L : configuredMaximumSize);
            long ttlSeconds = nativeCache.policy().expireAfterWrite()
                    .map(policy -> policy.getExpiresAfter(java.util.concurrent.TimeUnit.SECONDS))
                    .orElse(configuredTtlSeconds == null ? 0L : configuredTtlSeconds);
            builder.estimatedSize(estimatedSize)
                    .maximumSize(maximumSize > 0 ? maximumSize : null)
                    .ttlSeconds(ttlSeconds > 0 ? ttlSeconds : null)
                    .requestCount(requestCount)
                    .hitCount(stats.hitCount())
                    .missCount(stats.missCount())
                    .evictionCount(stats.evictionCount())
                    .hitRate(percent(stats.hitCount(), requestCount))
                    .missRate(percent(stats.missCount(), requestCount))
                    .usageRate(percent(estimatedSize, maximumSize))
                    .statisticsAvailable(true);
        } else if (targetCache.getNativeCache() instanceof RMapCache<?, ?> mapCache) {
            Long hitCount = cacheMetric("cache.gets", cacheName, Statistic.COUNT, "result", "hit");
            Long missCount = cacheMetric("cache.gets", cacheName, Statistic.COUNT, "result", "miss");
            Long evictionCount = cacheMetric("cache.evictions", cacheName, Statistic.COUNT);
            boolean statisticsAvailable = hitCount != null || missCount != null || evictionCount != null;
            long requestCount = safeLong(hitCount) + safeLong(missCount);
            builder.estimatedSize((long) mapCache.size())
                    .ttlSeconds(configuredTtlSeconds)
                    .maximumSize(configuredMaximumSize)
                    .maxIdleSeconds(configuredTtlSeconds)
                    .usageRate(percent(mapCache.size(), configuredMaximumSize));
            if (statisticsAvailable) {
                builder.requestCount(requestCount)
                        .hitCount(safeLong(hitCount))
                        .missCount(safeLong(missCount))
                        .evictionCount(safeLong(evictionCount))
                        .hitRate(percent(safeLong(hitCount), requestCount))
                        .missRate(percent(safeLong(missCount), requestCount))
                        .statisticsAvailable(true);
            }
        }

        return withHealth(builder.build());
    }

    private Cache requiredCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "缓存不存在：" + cacheName);
        }
        return cache;
    }

    private Map<Object, Object> nativeEntries(Cache cache) {
        Cache targetCache = targetCache(cache);
        if (targetCache instanceof CaffeineCache caffeineCache) {
            return new LinkedHashMap<>(caffeineCache.getNativeCache().asMap());
        }
        if (targetCache.getNativeCache() instanceof RMapCache<?, ?> mapCache) {
            Map<Object, Object> result = new LinkedHashMap<>();
            mapCache.readAllMap().forEach((key, value) -> result.put(key, value));
            return result;
        }
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前缓存实现不支持查看键值：" + targetCache.getNativeCache().getClass().getSimpleName());
    }

    private int normalizeEntryLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_ENTRY_LIMIT;
        }
        return Math.min(limit, MAX_ENTRY_LIMIT);
    }

    private CacheEntryView.CacheEntryItem cacheEntryItem(String key, Object nativeKey, Object value) {
        ValuePreview preview = valuePreview(value);
        return CacheEntryView.CacheEntryItem.builder()
                .key(key)
                .keyType(nativeKey == null ? null : nativeKey.getClass().getSimpleName())
                .valueType(value == null ? "null" : value.getClass().getSimpleName())
                .valuePreview(preview.content())
                .valueTruncated(preview.truncated())
                .build();
    }

    private String stringifyKey(Object key) {
        return key == null ? "null" : String.valueOf(key);
    }

    private ValuePreview valuePreview(Object value) {
        String content;
        if (value == null) {
            content = "null";
        } else if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            content = String.valueOf(value);
        } else {
            try {
                content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
            } catch (JsonProcessingException ex) {
                content = String.valueOf(value);
            }
        }
        boolean truncated = content.length() > VALUE_PREVIEW_LIMIT;
        String sanitized = sensitiveDataMasker.sanitizeJsonText(content, VALUE_PREVIEW_LIMIT);
        return new ValuePreview(sanitized, truncated);
    }

    private static long safeLong(Long value) {
        return value == null ? 0L : value;
    }

    private Long cacheMetric(String meterName, String cacheName, Statistic statistic, String... tags) {
        if (meterRegistry == null) {
            return null;
        }
        Iterable<Meter> meters = meterRegistry.find(meterName)
                .tag("cache", cacheName)
                .tags(tags)
                .meters();
        boolean found = false;
        double total = 0;
        for (Meter meter : meters) {
            for (Measurement measurement : meter.measure()) {
                double value = measurement.getValue();
                if (measurement.getStatistic() == statistic && Double.isFinite(value)) {
                    found = true;
                    total += value;
                }
            }
        }
        return found ? Math.round(total) : null;
    }

    private static Integer percent(long numerator, Long denominator) {
        if (denominator == null) {
            return null;
        }
        return percent(numerator, denominator.longValue());
    }

    private static Integer percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (int) Math.round(numerator * 100.0 / denominator);
    }

    private String resolveScope() {
        boolean distributed = cacheManager.getCacheNames().stream()
                .map(cacheManager::getCache)
                .filter(Objects::nonNull)
                .map(this::targetCache)
                .anyMatch(cache -> cache.getNativeCache() instanceof RMapCache<?, ?>);
        return distributed ? "DISTRIBUTED" : "LOCAL";
    }

    private Cache targetCache(Cache cache) {
        if (cache instanceof TransactionAwareCacheDecorator transactionAwareCache) {
            // 监控读取真实缓存实现，业务清理仍走外层缓存以保留事务提交后失效语义。
            return transactionAwareCache.getTargetCache();
        }
        return cache;
    }

    private CacheMonitorOverview.CacheItem withHealth(CacheMonitorOverview.CacheItem item) {
        Integer usageRate = item.getUsageRate();
        long requestCount = safeLong(item.getRequestCount());
        Integer hitRate = item.getHitRate();
        long evictionCount = safeLong(item.getEvictionCount());

        if (item.getNativeClass() == null) {
            item.setHealthStatus("UNKNOWN");
            item.setHealthLabel("未加载");
            item.setRiskLevel("info");
            item.setDescription("缓存名称已注册，但当前 CacheManager 未返回实例。");
            return item;
        }
        if (usageRate != null && usageRate >= CAPACITY_WARNING_THRESHOLD) {
            item.setHealthStatus("CAPACITY_RISK");
            item.setHealthLabel("容量风险");
            item.setRiskLevel("danger");
            item.setDescription("缓存容量接近上限，建议评估 key 数量、TTL 或扩容策略。");
            return item;
        }
        if (evictionCount > 0) {
            item.setHealthStatus("EVICTING");
            item.setHealthLabel("存在淘汰");
            item.setRiskLevel("warning");
            item.setDescription("已发生缓存淘汰，建议结合命中率判断是否需要调整容量。");
            return item;
        }
        if (Boolean.TRUE.equals(item.getStatisticsAvailable())
                && requestCount >= LOW_HIT_REQUEST_THRESHOLD
                && hitRate != null
                && hitRate < LOW_HIT_RATE_THRESHOLD) {
            item.setHealthStatus("LOW_HIT");
            item.setHealthLabel("命中偏低");
            item.setRiskLevel("warning");
            item.setDescription("请求量已达到观察阈值，但命中率偏低，建议检查 key 设计和写后失效逻辑。");
            return item;
        }
        if (Boolean.TRUE.equals(item.getStatisticsAvailable()) && requestCount == 0) {
            item.setHealthStatus("IDLE");
            item.setHealthLabel("等待预热");
            item.setRiskLevel("info");
            item.setDescription("暂未产生缓存访问，先通过业务流量预热后再观察命中率。");
            return item;
        }

        item.setHealthStatus("HEALTHY");
        item.setHealthLabel("运行正常");
        item.setRiskLevel("success");
        item.setDescription("当前缓存容量、淘汰和命中表现未触发风险规则。");
        return item;
    }

    private boolean isWarning(CacheMonitorOverview.CacheItem item) {
        return List.of("CAPACITY_RISK", "EVICTING", "LOW_HIT").contains(item.getHealthStatus());
    }

    private String busiestCacheName(List<CacheMonitorOverview.CacheItem> items) {
        return items.stream()
                .filter(item -> item.getRequestCount() != null)
                .max(Comparator.comparingLong(item -> safeLong(item.getRequestCount())))
                .map(CacheMonitorOverview.CacheItem::getName)
                .orElse(null);
    }

    private String largestCacheName(List<CacheMonitorOverview.CacheItem> items) {
        return items.stream()
                .filter(item -> item.getEstimatedSize() != null)
                .max(Comparator.comparingLong(item -> safeLong(item.getEstimatedSize())))
                .map(CacheMonitorOverview.CacheItem::getName)
                .orElse(null);
    }

    private String weakestCacheName(List<CacheMonitorOverview.CacheItem> items) {
        return items.stream()
                .filter(item -> Boolean.TRUE.equals(item.getStatisticsAvailable()))
                .filter(item -> safeLong(item.getRequestCount()) >= LOW_HIT_REQUEST_THRESHOLD)
                .filter(item -> item.getHitRate() != null)
                .min(Comparator.comparingInt(CacheMonitorOverview.CacheItem::getHitRate))
                .map(CacheMonitorOverview.CacheItem::getName)
                .orElse(null);
    }

    private List<String> recommendations(List<CacheMonitorOverview.CacheItem> items, boolean statisticsAvailable, long requestCount) {
        List<String> result = new java.util.ArrayList<>();
        if (items.isEmpty()) {
            result.add("当前没有命名缓存，请先确认 CacheManager 是否完成初始化。");
            return result;
        }
        if ("LOCAL".equals(resolveScope())) {
            result.add("当前为节点本地缓存，多实例部署时只清理当前节点；需要集群一致性时建议启用 Redis/Redisson。");
        } else {
            result.add("当前为分布式缓存，清理操作会影响共享缓存命中，建议在低峰期执行。");
        }
        if (!statisticsAvailable) {
            result.add("当前缓存实现未暴露命中统计，建议接入 Micrometer 或 Redis 指标来补齐命中率观测。");
        } else if (requestCount == 0) {
            result.add("暂无缓存访问，请通过用户详情、菜单树等读路径预热后再评估命中率。");
        }
        long warningCount = items.stream().filter(this::isWarning).count();
        if (warningCount > 0) {
            result.add("存在 " + warningCount + " 个缓存触发风险规则，请优先查看容量风险、淘汰和命中偏低的缓存。");
        }
        if (result.size() < 3) {
            result.add("缓存 key 应带业务前缀，写操作优先精确驱逐，避免直接清理整组缓存。");
        }
        return result;
    }

    private record ValuePreview(String content, boolean truncated) {
    }
}
