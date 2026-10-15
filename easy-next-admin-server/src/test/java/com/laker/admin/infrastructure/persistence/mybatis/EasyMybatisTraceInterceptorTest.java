package com.laker.admin.infrastructure.persistence.mybatis;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.observability.jfr.ActiveOperationRegistry;
import com.laker.admin.infrastructure.observability.jfr.LocalJfrRecorder;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import com.laker.admin.infrastructure.observability.trace.SpanType;
import com.laker.admin.infrastructure.observability.trace.TraceContext;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.BatchResult;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EasyMybatisTraceInterceptorTest {

    private final EasyMybatisTraceInterceptor interceptor = new EasyMybatisTraceInterceptor();

    @Test
    void shouldResolveReadableSpanNameAndSafeTag() {
        MappedStatement statement = statement("com.laker.admin.module.system.mapper.SysUserMapper.selectPage",
                SqlCommandType.SELECT);

        assertThat(EasyMybatisTraceInterceptor.spanName(statement)).isEqualTo("SysUserMapper#selectPage");
        assertThat(EasyMybatisTraceInterceptor.spanTag(statement)).isEqualTo("SELECT");
    }

    @Test
    void shouldAppendMybatisSpanToActiveTrace() throws Throwable {
        TraceContext.startRoot("SysUserController#detail", SpanType.Http, "GET /api/system/users/1", 8, 0);
        try {
            Object result = interceptor.intercept(queryInvocation(statement(
                    "com.laker.admin.module.system.mapper.SysUserMapper.selectById",
                    SqlCommandType.SELECT)));

            assertThat(result).isEqualTo(List.of("ok"));

            Thread.sleep(2);
            AtomicReference<TraceContext.Completion> completion = new AtomicReference<>();
            TraceContext.stopRoot(1, "capture", null, completion::set);

            assertThat(completion.get().tree())
                    .contains("[Mapper] SysUserMapper#selectById")
                    .contains("tag=\"SELECT\"")
                    .doesNotContain("layer=mybatis")
                    .doesNotContain("sqlId=");
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void shouldPassThroughStatementHandlerInvocationWhenArgsAreNull() throws Throwable {
        TraceContext.startRoot("WfTaskController#approve", SpanType.Http,
                "PUT /api/workflow/tasks/1/approve", 8, 0);
        try {
            BoundSql boundSql = (BoundSql) interceptor.intercept(statementHandlerBoundSqlInvocation());

            assertThat(boundSql.getSql()).isEqualTo("select 1");
        } finally {
            TraceContext.clear();
        }
    }

    @Test
    void shouldRecordMapperExecutionCountAndDurationWithoutAnActiveTrace() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicInteger meterMappings = new AtomicInteger();
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if ("easy.sql.queries".equals(id.getName())) meterMappings.incrementAndGet();
                return id;
            }
        });
        EasyMybatisTraceInterceptor instrumented = new EasyMybatisTraceInterceptor(registry);
        MappedStatement statement = statement(
                "com.laker.admin.module.system.mapper.SysUserMapper.selectById", SqlCommandType.SELECT);

        instrumented.intercept(queryInvocation(statement));
        int registrationsAfterFirstQuery = meterMappings.get();
        instrumented.intercept(queryInvocation(statement));

        var timer = registry.get("easy.sql.queries")
                .tag("mapper", "SysUserMapper#selectById")
                .tag("command", "SELECT")
                .tag("result", "success")
                .timer();
        assertThat(timer.count()).isEqualTo(2);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isGreaterThanOrEqualTo(0);
        assertThat(timer.takeSnapshot().percentileValues()).isEmpty();
        assertThat(registrationsAfterFirstQuery).isEqualTo(1);
        assertThat(meterMappings).hasValue(registrationsAfterFirstQuery);
    }

    @Test
    void shouldReuseOneOverflowMeterAfterTheStatementCacheIsFull() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicInteger meterMappings = new AtomicInteger();
        registry.config().meterFilter(new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                if ("easy.sql.queries".equals(id.getName())) meterMappings.incrementAndGet();
                return id;
            }
        });
        EasyMybatisTraceInterceptor instrumented = new EasyMybatisTraceInterceptor(registry);

        for (int index = 0; index <= 494; index++) {
            instrumented.intercept(queryInvocation(statement(
                    "com.example.Mapper.select" + index, SqlCommandType.SELECT)));
        }
        int registrationsAfterFirstOverflow = meterMappings.get();
        instrumented.intercept(queryInvocation(statement(
                "com.example.Mapper.select494", SqlCommandType.SELECT)));

        assertThat(meterMappings).hasValue(registrationsAfterFirstOverflow);
        assertThat(registry.get("easy.sql.queries")
                .tags("mapper", "unknown", "command", "SELECT", "result", "success")
                .timer().count()).isEqualTo(2);
    }

    @Test
    void shouldNotAddAnotherBoundSqlReadForFastSuccessfulObservation() throws Throwable {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setSqlSlowThresholdMs(60_000);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyMybatisTraceInterceptor instrumented = new EasyMybatisTraceInterceptor(
                meters, observation(config, meters, store));
        AtomicInteger boundSqlReads = new AtomicInteger();

        instrumented.intercept(queryInvocation(countingStatement(
                "com.laker.admin.module.order.mapper.OrderMapper.selectById", boundSqlReads)));

        // MyBatis-Plus itself resolves BoundSql once before invoking the executor. The deferred incident detail must
        // not cause a second resolution on the normal fast path.
        assertThat(boundSqlReads).hasValue(1);
        verify(store, never()).recordOperation(any());
    }

    @Test
    void shouldResolveSanitizedBoundSqlForSlowObservation() throws Throwable {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setSqlSlowThresholdMs(1);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyMybatisTraceInterceptor instrumented = new EasyMybatisTraceInterceptor(
                meters, observation(config, meters, store));
        AtomicInteger boundSqlReads = new AtomicInteger();

        instrumented.intercept(queryInvocation(countingStatement(
                "com.laker.admin.module.order.mapper.OrderMapper.selectById", boundSqlReads),
                new SlowQueryTarget()));

        assertThat(boundSqlReads).hasValue(2);
        var sample = org.mockito.ArgumentCaptor.forClass(RecentTraceStore.OperationSample.class);
        verify(store).recordOperation(sample.capture());
        assertThat(sample.getValue().detail())
                .isEqualTo("SELECT · select * from orders where id = ? and tenant = ?");
    }

    @Test
    void shouldResolveSanitizedBoundSqlForFailedObservation() throws Throwable {
        EasyNextAdminConfig config = new EasyNextAdminConfig();
        config.getTrace().setSqlSlowThresholdMs(60_000);
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        RecentTraceStore store = mock(RecentTraceStore.class);
        EasyMybatisTraceInterceptor instrumented = new EasyMybatisTraceInterceptor(
                meters, observation(config, meters, store));
        AtomicInteger boundSqlReads = new AtomicInteger();
        Invocation invocation = queryInvocation(countingStatement(
                "com.laker.admin.module.order.mapper.OrderMapper.selectById", boundSqlReads),
                new FailingQueryTarget());

        assertThatThrownBy(() -> instrumented.intercept(invocation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        assertThat(boundSqlReads).hasValue(2);
        var sample = org.mockito.ArgumentCaptor.forClass(RecentTraceStore.OperationSample.class);
        verify(store).recordOperation(sample.capture());
        assertThat(sample.getValue().detail())
                .isEqualTo("SELECT · select * from orders where id = ? and tenant = ?");
    }

    @Test
    void monitoringErrorMustNotReplaceSqlBusinessFailure() throws Throwable {
        OperationObservation observation = mock(OperationObservation.class);
        OperationObservation.Scope scope = mock(OperationObservation.Scope.class);
        when(observation.start(eq("SQL"), eq("SysUserMapper#selectById"), eq("SELECT"),
                org.mockito.ArgumentMatchers.<java.util.function.Supplier<String>>any(), eq(false)))
                .thenReturn(scope);
        doThrow(new AssertionError("monitoring finalizer failed"))
                .when(scope).finish(eq("failure"), any(Throwable.class));
        EasyMybatisTraceInterceptor instrumented = new EasyMybatisTraceInterceptor(
                new SimpleMeterRegistry(), observation);

        assertThatThrownBy(() -> instrumented.intercept(queryInvocation(statement(
                "com.laker.admin.module.system.mapper.SysUserMapper.selectById", SqlCommandType.SELECT),
                new FailingQueryTarget())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(scope).discard();
    }

    private static Invocation queryInvocation(MappedStatement statement) throws NoSuchMethodException {
        return queryInvocation(statement, new QueryTarget());
    }

    private static Invocation queryInvocation(MappedStatement statement, Executor target)
            throws NoSuchMethodException {
        Method method = Executor.class.getMethod("query", MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class);
        return new Invocation(target, method, new Object[]{statement, null, RowBounds.DEFAULT, null});
    }

    private static Invocation statementHandlerBoundSqlInvocation() throws NoSuchMethodException {
        Method method = StatementHandler.class.getMethod("getBoundSql");
        return new Invocation(new BoundSqlTarget(), method, null);
    }

    private static MappedStatement statement(String id, SqlCommandType commandType) {
        Configuration configuration = new Configuration();
        return new MappedStatement.Builder(configuration, id,
                new StaticSqlSource(configuration, "select 1"), commandType).build();
    }

    private static MappedStatement countingStatement(String id, AtomicInteger reads) {
        Configuration configuration = new Configuration();
        SqlSource source = parameterObject -> {
            reads.incrementAndGet();
            return new BoundSql(configuration,
                    "select * from orders where id = 42 and tenant = 'secret'", List.of(), parameterObject);
        };
        return new MappedStatement.Builder(configuration, id, source, SqlCommandType.SELECT).build();
    }

    private static OperationObservation observation(EasyNextAdminConfig config, SimpleMeterRegistry meters,
                                                    RecentTraceStore store) {
        ActiveOperationRegistry activeOperations = new ActiveOperationRegistry(meters, config);
        @SuppressWarnings("unchecked")
        ObjectProvider<LocalJfrRecorder> recorderProvider = mock(ObjectProvider.class);
        return new OperationObservation(store, config, activeOperations, meters, recorderProvider);
    }

    public static class BoundSqlTarget implements StatementHandler {
        @Override
        public Statement prepare(Connection connection, Integer transactionTimeout) {
            return null;
        }

        @Override
        public void parameterize(Statement statement) {
        }

        @Override
        public void batch(Statement statement) {
        }

        @Override
        public int update(Statement statement) {
            return 0;
        }

        @Override
        public <E> List<E> query(Statement statement, ResultHandler resultHandler) {
            return List.of();
        }

        @Override
        public <E> Cursor<E> queryCursor(Statement statement) {
            return null;
        }

        @Override
        public BoundSql getBoundSql() {
            return new BoundSql(new Configuration(), "select 1", List.of(), null);
        }

        @Override
        public ParameterHandler getParameterHandler() {
            return null;
        }
    }

    public static class QueryTarget implements Executor {
        @Override
        public int update(MappedStatement ms, Object parameter) {
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds,
                                 ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) {
            return (List<E>) List.of("ok");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds,
                                 ResultHandler resultHandler) {
            return (List<E>) List.of("ok");
        }

        @Override
        public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) {
            return null;
        }

        @Override
        public List<BatchResult> flushStatements() {
            return List.of();
        }

        @Override
        public void commit(boolean required) {
        }

        @Override
        public void rollback(boolean required) {
        }

        @Override
        public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds,
                                       BoundSql boundSql) {
            return new CacheKey();
        }

        @Override
        public boolean isCached(MappedStatement ms, CacheKey key) {
            return false;
        }

        @Override
        public void clearLocalCache() {
        }

        @Override
        public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key,
                              Class<?> targetType) {
        }

        @Override
        public Transaction getTransaction() {
            return null;
        }

        @Override
        public void close(boolean forceRollback) {
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void setExecutorWrapper(Executor executor) {
        }
    }

    public static final class SlowQueryTarget extends QueryTarget {
        @Override
        public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds,
                                 ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) {
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            return super.query(ms, parameter, rowBounds, resultHandler, cacheKey, boundSql);
        }

        @Override
        public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds,
                                 ResultHandler resultHandler) {
            java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(5));
            return super.query(ms, parameter, rowBounds, resultHandler);
        }
    }

    public static final class FailingQueryTarget extends QueryTarget {
        @Override
        public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds,
                                 ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) {
            throw new IllegalStateException("database unavailable");
        }

        @Override
        public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds,
                                 ResultHandler resultHandler) {
            throw new IllegalStateException("database unavailable");
        }
    }
}
