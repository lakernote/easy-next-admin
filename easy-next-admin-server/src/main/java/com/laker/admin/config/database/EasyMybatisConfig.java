package com.laker.admin.config.database;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.laker.admin.infrastructure.persistence.mybatis.EasyMybatisTraceInterceptor;
import com.laker.admin.infrastructure.security.datascope.resolver.DataScopeResolver;
import com.laker.admin.infrastructure.security.datascope.mybatis.EasyDataScopeInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author laker
 */
@Configuration
@MapperScan("com.laker.admin.**.mapper")
public class EasyMybatisConfig {

    /**
     * mybatis-plus插件
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(DataScopeResolver dataScopeResolver) {
        MybatisPlusInterceptor interceptor = new EasyMybatisTraceInterceptor();
        // 数据权限插件必须在分页前执行，保证分页总数和列表数据使用同一套范围条件。
        interceptor.addInnerInterceptor(new EasyDataScopeInnerInterceptor(dataScopeResolver));
        // 乐观锁插件：实体携带 version 时才参与更新校验，默认不增加前端理解成本。
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
