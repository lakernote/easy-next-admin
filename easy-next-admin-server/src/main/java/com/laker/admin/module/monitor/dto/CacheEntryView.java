package com.laker.admin.module.monitor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CacheEntryView", description = "缓存键值视图")
public class CacheEntryView {
    private String cacheName;
    private String provider;
    private String nativeClass;
    private String scope;
    private Integer total;
    private Integer limit;
    private Boolean truncated;
    private String selectedKey;
    private CacheEntryItem selected;
    private List<CacheEntryItem> entries;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "CacheEntryItem", description = "缓存键值项")
    public static class CacheEntryItem {
        private String key;
        private String keyType;
        private String valueType;
        private String valuePreview;
        private Boolean valueTruncated;
    }
}
