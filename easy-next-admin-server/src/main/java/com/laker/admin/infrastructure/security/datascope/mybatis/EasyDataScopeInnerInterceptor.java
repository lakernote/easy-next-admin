package com.laker.admin.infrastructure.security.datascope.mybatis;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.laker.admin.infrastructure.security.datascope.annotation.DataScope;
import com.laker.admin.infrastructure.security.datascope.context.EasyDataScopeContext;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeCondition;
import com.laker.admin.infrastructure.security.datascope.resolver.DataScopeResolver;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class EasyDataScopeInnerInterceptor implements InnerInterceptor {
    private final DataScopeResolver dataScopeResolver;
    private final DataScopeSqlRewriter sqlRewriter = new DataScopeSqlRewriter();
    private final ConcurrentMap<String, Optional<DataScopeTarget>> targetCache = new ConcurrentHashMap<>();

    public EasyDataScopeInnerInterceptor(DataScopeResolver dataScopeResolver) {
        this.dataScopeResolver = dataScopeResolver;
    }

    @Override
    public void beforeQuery(Executor executor,
                            MappedStatement ms,
                            Object parameter,
                            RowBounds rowBounds,
                            ResultHandler resultHandler,
                            BoundSql boundSql) throws SQLException {
        if (ms.getSqlCommandType() != SqlCommandType.SELECT) {
            return;
        }
        if (EasyDataScopeContext.ignored()) {
            // 认证、授权维护等系统内部查询必须显式进入 ignore 上下文才允许绕过。
            return;
        }
        Optional<DataScopeTarget> targetOptional = resolveTarget(ms.getId());
        if (targetOptional.isEmpty()) {
            return;
        }
        DataScopeTarget target = targetOptional.get();
        DataScopeCondition scope = dataScopeResolver.resolveCurrentUserScope();
        String scopedSql = sqlRewriter.rewrite(boundSql.getSql(), target.annotation().deptColumn(), target.annotation().selfColumn(), scope);
        PluginUtils.mpBoundSql(boundSql).sql(scopedSql);
    }

    Optional<DataScopeTarget> resolveTarget(String statementId) {
        if (!StringUtils.hasText(statementId)) {
            return Optional.empty();
        }
        return targetCache.computeIfAbsent(statementId, this::doResolveTarget);
    }

    private Optional<DataScopeTarget> doResolveTarget(String statementId) {
        int separator = statementId.lastIndexOf('.');
        if (separator < 1 || separator == statementId.length() - 1) {
            return Optional.empty();
        }
        String mapperClassName = statementId.substring(0, separator);
        String methodName = statementId.substring(separator + 1);
        try {
            Class<?> mapperClass = ClassUtils.forName(mapperClassName, ClassUtils.getDefaultClassLoader());
            // 方法级注解优先，便于同一个 Mapper 中只有少数查询使用特殊字段。
            DataScope methodScope = findMethodScope(mapperClass, methodName);
            if (methodScope != null && matches(methodScope, methodName)) {
                return Optional.of(new DataScopeTarget(methodScope));
            }
            DataScope mapperScope = AnnotatedElementUtils.findMergedAnnotation(mapperClass, DataScope.class);
            if (mapperScope != null && matches(mapperScope, methodName)) {
                return Optional.of(new DataScopeTarget(mapperScope));
            }
            return Optional.empty();
        } catch (ClassNotFoundException | LinkageError ex) {
            return Optional.empty();
        }
    }

    private DataScope findMethodScope(Class<?> mapperClass, String methodName) {
        for (Method method : mapperClass.getMethods()) {
            if (!method.getName().equals(methodName)) {
                continue;
            }
            DataScope methodScope = AnnotatedElementUtils.findMergedAnnotation(method, DataScope.class);
            if (methodScope != null) {
                return methodScope;
            }
        }
        return null;
    }

    private boolean matches(DataScope dataScope, String methodName) {
        String[] methods = dataScope.methods();
        return methods.length == 0 || Arrays.asList(methods).contains(methodName);
    }

    record DataScopeTarget(DataScope annotation) {
    }
}
