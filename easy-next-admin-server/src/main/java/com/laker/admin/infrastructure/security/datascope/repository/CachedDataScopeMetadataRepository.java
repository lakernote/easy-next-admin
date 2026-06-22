package com.laker.admin.infrastructure.security.datascope.repository;

import com.laker.admin.config.cache.EasyCacheConfig;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeDeptNode;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * 带缓存的数据权限元数据仓储。
 *
 * <p>热点查询先走 Spring Cache，缓存未命中时再回源 JDBC 仓储。</p>
 */
@Repository
@Primary
public class CachedDataScopeMetadataRepository implements DataScopeMetadataRepository {
    private final JdbcDataScopeMetadataRepository delegate;

    public CachedDataScopeMetadataRepository(JdbcDataScopeMetadataRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    @Cacheable(cacheNames = EasyCacheConfig.CACHE_DATA_SCOPE_DEPT_TREE, key = "'activeDeptNodes'")
    public List<DataScopeDeptNode> listActiveDeptNodes() {
        // 缓存未命中时才回源 JDBC，避免 MyBatis 拦截器链内递归。
        return delegate.listActiveDeptNodes();
    }

    @Override
    @Cacheable(cacheNames = EasyCacheConfig.CACHE_DATA_SCOPE_CUSTOM_DEPT_IDS,
            key = "#root.args[0]",
            condition = "#root.args[0] != null")
    public Set<Long> listCustomDeptIdsByUserId(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return delegate.listCustomDeptIdsByUserId(userId);
    }
}
