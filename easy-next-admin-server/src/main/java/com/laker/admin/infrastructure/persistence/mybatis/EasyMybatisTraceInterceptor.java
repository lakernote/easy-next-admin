package com.laker.admin.infrastructure.persistence.mybatis;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.laker.admin.infrastructure.observability.trace.SpanType;
import com.laker.admin.infrastructure.observability.trace.TraceContext;
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

import java.sql.Connection;

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
 * <p>出于可读性、安全和日志体积考虑，MyBatis span 的 tag 只保留 {@link SqlCommandType}，
 * 不记录 SQL 文本、statement id、查询参数或更新参数；排查时先看 Mapper 方法名，连续重复节点
 * 由 Trace Tree 渲染层聚合为 count/total/min/max。</p>
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

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = resolveMappedStatement(invocation);
        if (!TraceContext.active() || statement == null) {
            return super.intercept(invocation);
        }
        TraceContext.addSpan(spanName(statement), SpanType.Mapper, spanTag(statement));
        try {
            return super.intercept(invocation);
        } finally {
            TraceContext.stopSpan();
        }
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
