package com.laker.admin.infrastructure.persistence.mybatis;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.laker.admin.infrastructure.observability.trace.SpanType;
import com.laker.admin.infrastructure.observability.trace.TraceContext;
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
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
        Logger logger = (Logger) LoggerFactory.getLogger(TraceContext.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        boolean additive = logger.isAdditive();
        logger.setAdditive(false);
        logger.addAppender(appender);
        appender.start();

        TraceContext.startRoot("SysUserController#detail", SpanType.Http, "GET /api/system/users/1", 8, 0);
        try {
            Object result = interceptor.intercept(queryInvocation(statement(
                    "com.laker.admin.module.system.mapper.SysUserMapper.selectById",
                    SqlCommandType.SELECT)));

            assertThat(result).isEqualTo(List.of("ok"));

            Thread.sleep(2);
            TraceContext.stopRoot(1, "capture");

            assertThat(appender.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .anySatisfy(message -> assertThat(message)
                            .contains("[Mapper] SysUserMapper#selectById")
                            .contains("tag=\"SELECT\"")
                            .doesNotContain("layer=mybatis")
                            .doesNotContain("sqlId="));
        } finally {
            TraceContext.clear();
            logger.detachAppender(appender);
            logger.setAdditive(additive);
            appender.stop();
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

    private static Invocation queryInvocation(MappedStatement statement) throws NoSuchMethodException {
        QueryTarget target = new QueryTarget();
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
}
