package com.laker.admin.module.monitor.controller;

import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.monitor.dto.CacheEntryView;
import com.laker.admin.module.monitor.dto.CacheMonitorOverview;
import com.laker.admin.module.monitor.service.CacheMonitorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "缓存监控")
@RestController
@RequestMapping("/api/monitor/cache")
@ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
public class CacheMonitorController {
    private final CacheMonitorService cacheMonitorService;

    public CacheMonitorController(CacheMonitorService cacheMonitorService) {
        this.cacheMonitorService = cacheMonitorService;
    }

    @GetMapping("/overview")
    @EasyPermission(EasyPermissions.Monitor.CACHE_VIEW)
    @Operation(summary = "缓存监控概览", description = "查看本地缓存名称、容量、请求数、命中率和淘汰次数。")
    public Response<CacheMonitorOverview> overview() {
        return Response.ok(cacheMonitorService.overview());
    }

    @GetMapping("/{cacheName}/entries")
    @EasyPermission(EasyPermissions.Monitor.CACHE_VIEW)
    @Operation(summary = "缓存键值列表", description = "查看指定缓存下的 key 和 value 预览。")
    public Response<CacheEntryView> entries(@PathVariable String cacheName,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String selectedKey,
                                            @RequestParam(required = false) Integer limit) {
        return Response.ok(cacheMonitorService.entries(cacheName, keyword, selectedKey, limit));
    }

    @DeleteMapping("/{cacheName}")
    @EasyPermission(EasyPermissions.Monitor.CACHE_CLEAR)
    @EasyAudit(module = "运行监控", action = "清理缓存", dataChange = true)
    @Operation(summary = "清理缓存", description = "按缓存名称清理本地缓存。")
    public Response<Void> clear(@PathVariable String cacheName) {
        cacheMonitorService.clear(cacheName);
        return Response.ok();
    }

    @DeleteMapping("/{cacheName}/entries")
    @EasyPermission(EasyPermissions.Monitor.CACHE_CLEAR)
    @EasyAudit(module = "运行监控", action = "清理缓存键", dataChange = true)
    @Operation(summary = "清理缓存键", description = "按缓存名称和 key 精确清理缓存项。")
    public Response<Void> evictEntry(@PathVariable String cacheName, @RequestParam String key) {
        cacheMonitorService.evictEntry(cacheName, key);
        return Response.ok();
    }
}
