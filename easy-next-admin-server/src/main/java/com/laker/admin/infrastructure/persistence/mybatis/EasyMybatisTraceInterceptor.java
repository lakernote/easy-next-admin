package com.laker.admin.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.laker.admin.infrastructure.observability.trace.SpanType;
import com.laker.admin.infrastructure.observability.trace.TraceContext;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import com.laker.admin.infrastructure.observability.trace.ObservationSuppression;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.sql.Connection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * 带 Trace Tree 的 MyBatis-Plus 插件链。
 *
 * <p>这里继承 {@link MybatisPlusInterceptor}，而不是实现一个单独的
 * {@code InnerInterceptor}。原因是 MyBatis-Plus 的 {@code InnerInterceptor}
 * 只有 before 回调，适合改写 SQL、乐观锁、分页等场景；Trace Tree 需要包住
 * SQL 执行前后，在 finally 中关闭 span，才能得到真实执行耗时并保证异常时不泄漏栈。</p>
 *
 * <p>这个类仍然是项目唯一的 MyBatis-Plus 插件链容器，数据权限、乐观锁和分页继续
 * 通过 {@code addInnerInterceptor(...)} 按配置顺序执行。追踪只在 Executor 的
 * query/update 调用上增加一个 {@link SpanType#Mapper} 节点，不新增 Mybatis span 类型，
 * 避免和现有 Mapper 语义重复。</p>
 *
 * <p>出于可读性、安全和日志体积考虑，MyBatis span 的 tag 只保留 {@link SqlCommandType}；
 * 慢调用或错误操作会额外保留移除字符串、数字字面量后的归一化 SQL，不记录绑定参数。
 * 排查时先看 Mapper 方法名，连续重复节点由 Trace Tree 渲染层聚合为 count/total/min/max。</p>
 *
 * @author laker
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}),
        @Signature(type = StatementHandler.class, method = "getBoundSql", args = {}),
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class})
})
public class EasyMybatisTraceInterceptor extends MybatisPlusInterceptor {
    private static final int MAX_SQL_DETAIL_LENGTH = 2_000;
    private static final int MAX_SQL_NORMALIZE_INPUT = 8_000;
    /**
     * The meter filter admits 1,000 SQL series. Reserve two outcomes for every SqlCommandType on the shared
     * {@code mapper=unknown} fallback, leaving 494 mapper names with success/failure series.
     */
    private static final int MAX_SQL_TIMER_CACHE_SIZE = 494;
    private static final java.util.regex.Pattern SQL_STRING_LITERAL =
            java.util.regex.Pattern.compile("'(?:''|[^'])*'");
    private static final java.util.regex.Pattern SQL_NUMBER_LITERAL =
            java.util.regex.Pattern.compile("\\b(?:0x[0-9a-fA-F]+|\\d+(?:\\.\\d+)?)\\b");
    private static final java.util.regex.Pattern SQL_WHITESPACE = java.util.regex.Pattern.compile("\\s+");
    private final MeterRegistry meterRegistry;
    private final OperationObservation operationObservation;
    private final ConcurrentMap<String, SqlMetric> sqlTimerCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SqlMetric> overflowSqlMetrics = new ConcurrentHashMap<>();
    private final Object sqlTimerCacheMonitor = new Object();
    private volatile boolean sqlTimerCacheFull;

    public EasyMybatisTraceInterceptor() {
        this.meterRegistry = null;
        this.operationObservation = null;
    }

    public EasyMybatisTraceInterceptor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.operationObservation = null;
    }

    public EasyMybatisTraceInterceptor(MeterRegistry meterRegistry, OperationObservation operationObservation) {
        this.meterRegistry = meterRegistry;
        this.operationObservation = operationObservation;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (ObservationSuppression.active()) return super.intercept(invocation);
        MappedStatement statement = resolveMappedStatement(invocation);
        if (statement == null) {
            return super.intercept(invocation);
        }
        SqlMetric metric = null;
        OperationObservation.Scope observation = null;
        boolean tracing = false;
        try {
            metric = sqlMetric(statement);
            String operationName = metric.overflow ? spanName(statement) : metric.mapper;
            if (TraceContext.active()) {
                TraceContext.addSpan(operationName, SpanType.Mapper, metric.command);
                tracing = true;
            }
            observation = startObservation(statement, operationName, metric.command, invocation.getArgs());
            if (observation != null) observation.beginBusinessBoundary();
            if (tracing) TraceContext.beginCurrentSpanBoundary();
        } catch (Throwable monitoringFailure) {
            safeDiscard(observation);
            TraceContext.clear();
            observation = null;
            tracing = false;
            metric = null;
        }
        long startedAt = System.nanoTime();
        boolean success = false;
        Throwable failure = null;
        try {
            Object result = super.intercept(invocation);
            success = true;
            return result;
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            long elapsed = Math.max(0, System.nanoTime() - startedAt);
            if (observation != null) observation.endBusinessBoundary();
            if (tracing) {
                try {
                    TraceContext.stopSpan();
                } catch (Throwable monitoringFailure) {
                    TraceContext.clear();
                    tracing = false;
                    retainOriginalFailure(failure, monitoringFailure);
                }
            }
            try {
                if (observation != null) observation.finish(success ? "success" : "failure", failure);
            } catch (Throwable monitoringFailure) {
                safeDiscard(observation);
                retainOriginalFailure(failure, monitoringFailure);
            }
            recordQuery(metric, success, elapsed);
        }
    }

    private OperationObservation.Scope startObservation(MappedStatement statement, String operationName,
                                                        String command, Object[] args) {
        if (operationObservation == null) return null;
        try {
            Object parameter = args != null && args.length > 1 ? args[1] : null;
            return operationObservation.start("SQL", operationName, command,
                    () -> command + " · " + normalizedSql(statement.getBoundSql(parameter).getSql()), false);
        } catch (Throwable ignored) {
            try {
                return operationObservation.start("SQL", operationName, command, false);
            } catch (Throwable ignoredAgain) {
                return null;
            }
        }
    }

    private void recordQuery(SqlMetric metric, boolean success, long elapsedNanos) {
        if (meterRegistry == null || metric == null) return;
        try {
            metric.timer(success).record(elapsedNanos, TimeUnit.NANOSECONDS);
        } catch (Throwable ignored) {
            // Observability must never change the database call result.
        }
    }

    private static void safeDiscard(OperationObservation.Scope observation) {
        if (observation == null) return;
        try { observation.discard(); }
        catch (Throwable ignored) { }
    }

    private static void retainOriginalFailure(Throwable businessFailure, Throwable monitoringFailure) {
        if (businessFailure == null || businessFailure instanceof VirtualMachineError
                || monitoringFailure instanceof VirtualMachineError) return;
        try { businessFailure.addSuppressed(monitoringFailure); }
        catch (Throwable ignored) { }
    }

    private SqlMetric sqlMetric(MappedStatement statement) {
        String statementId = statement.getId();
        SqlMetric cached = sqlTimerCache.get(statementId);
        if (cached != null) return cached;
        if (sqlTimerCacheFull) return overflowSqlMetric(statement);
        synchronized (sqlTimerCacheMonitor) {
            cached = sqlTimerCache.get(statementId);
            if (cached != null) return cached;
            if (sqlTimerCacheFull) return overflowSqlMetric(statement);
            SqlMetric created = new SqlMetric(spanName(statement), spanTag(statement), false);
            if (sqlTimerCache.size() < MAX_SQL_TIMER_CACHE_SIZE) {
                sqlTimerCache.put(statementId, created);
                return created;
            }
            sqlTimerCacheFull = true;
            return overflowSqlMetric(statement);
        }
    }

    /** A bounded fallback avoids one contended monitor and repeated denied meter registration per overflow query. */
    private SqlMetric overflowSqlMetric(MappedStatement statement) {
        String command = spanTag(statement);
        return overflowSqlMetrics.computeIfAbsent(command,
                key -> new SqlMetric("unknown", key, true));
    }

    private final class SqlMetric {
        private final String mapper;
        private final String command;
        private final boolean overflow;
        private volatile Timer success;
        private volatile Timer failure;

        private SqlMetric(String mapper, String command, boolean overflow) {
            this.mapper = mapper;
            this.command = command;
            this.overflow = overflow;
        }

        private Timer timer(boolean succeeded) {
            Timer cached = succeeded ? success : failure;
            if (cached != null) return cached;
            synchronized (this) {
                cached = succeeded ? success : failure;
                if (cached != null) return cached;
                cached = Timer.builder("easy.sql.queries")
                        .description("MyBatis Mapper 执行次数与耗时")
                        .tag("mapper", mapper)
                        .tag("command", command)
                        .tag("result", succeeded ? "success" : "failure")
                        .register(meterRegistry);
                if (succeeded) success = cached;
                else failure = cached;
                return cached;
            }
        }
    }

    static String normalizedSql(String sql) {
        if (sql == null || sql.isBlank()) return "";
        String input = sql.length() <= MAX_SQL_NORMALIZE_INPUT ? sql : sql.substring(0, MAX_SQL_NORMALIZE_INPUT);
        String normalized = SQL_WHITESPACE.matcher(SQL_NUMBER_LITERAL.matcher(
                SQL_STRING_LITERAL.matcher(input).replaceAll("?")).replaceAll("?")).replaceAll(" ").trim();
        return normalized.length() <= MAX_SQL_DETAIL_LENGTH
                ? normalized : normalized.substring(0, MAX_SQL_DETAIL_LENGTH) + "…";
    }

    static String spanName(MappedStatement statement) {
        String statementId = statement.getId();
        int methodSeparator = statementId.lastIndexOf('.');
        if (methodSeparator < 0) {
            return statementId;
        }
        String mapperName = statementId.substring(0, methodSeparator);
        int mapperSeparator = mapperName.lastIndexOf('.');
        String mapperSimpleName = mapperSeparator < 0 ? mapperName : mapperName.substring(mapperSeparator + 1);
        return mapperSimpleName + "#" + statementId.substring(methodSeparator + 1);
    }

    static String spanTag(MappedStatement statement) {
        SqlCommandType commandType = statement.getSqlCommandType();
        return commandType == null ? "UNKNOWN" : commandType.name();
    }

    private static MappedStatement resolveMappedStatement(Invocation invocation) {
        Object[] args = invocation.getArgs();
        // StatementHandler#getBoundSql 是零参数方法，MyBatis 在 batch 场景下可能传入 null 参数数组。
        if (args == null || args.length == 0 || !(args[0] instanceof MappedStatement statement)) {
            return null;
        }
        return statement;
    }
}
