package com.laker.admin.infrastructure.persistence.mybatis;

import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlCommandType;

/** Keeps EasyNextAdmin JFR and local diagnosis records for Mapper calls. */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public final class EasyMybatisDiagnosticInterceptor implements Interceptor {
    private final OperationObservation observations;

    public EasyMybatisDiagnosticInterceptor(OperationObservation observations) {
        this.observations = observations;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        String command = command(statement.getSqlCommandType());
        String name = statement.getId();
        OperationObservation.Scope operation = observations.startDiagnosticOnly("SQL", name, command);
        if (operation != null) operation.beginBusinessBoundary();
        Throwable failure = null;
        long started = System.nanoTime();
        try {
            return invocation.proceed();
        } catch (Throwable ex) {
            failure = ex;
            throw ex;
        } finally {
            if (operation != null) {
                operation.endBusinessBoundary();
                operation.finish(failure == null ? "success" : "failure", failure);
            }
        }
    }

    private static String command(SqlCommandType type) {
        return type == null || type == SqlCommandType.UNKNOWN ? "UNKNOWN" : type.name();
    }
}
