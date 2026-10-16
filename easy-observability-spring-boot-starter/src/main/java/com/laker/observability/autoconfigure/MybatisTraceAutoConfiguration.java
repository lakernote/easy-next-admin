package com.laker.observability.autoconfigure;

import com.laker.observability.config.EasyObservabilityProperties;
import com.laker.observability.integration.mybatis.MybatisTraceInterceptor;
import com.laker.observability.metrics.OperationRecorder;
import org.apache.ibatis.plugin.Interceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Adds mapper-level trace spans when MyBatis is present. */
@AutoConfiguration(after = ObservabilityAutoConfiguration.class)
@ConditionalOnClass(Interceptor.class)
@ConditionalOnProperty(prefix = "easy.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "easy.observability.mybatis", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "easy.observability.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MybatisTraceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "easyObservabilityMybatisTraceInterceptor")
    MybatisTraceInterceptor easyObservabilityMybatisTraceInterceptor(
            OperationRecorder recorder, EasyObservabilityProperties properties) {
        return new MybatisTraceInterceptor(recorder, properties);
    }
}
