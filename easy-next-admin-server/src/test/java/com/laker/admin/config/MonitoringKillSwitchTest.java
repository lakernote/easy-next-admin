package com.laker.admin.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.laker.admin.config.database.EasyMybatisConfig;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.config.web.EasyMvcInterceptorConfig;
import com.laker.admin.infrastructure.mq.kafka.EasyKafkaConsumerSlowAspect;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import io.github.lakernote.observability.trace.TraceSpanAspect;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import com.laker.admin.infrastructure.observability.trace.TransactionDiagnosticBeanPostProcessor;
import com.laker.admin.infrastructure.observability.trace.TransactionDiagnosticListener;
import com.laker.admin.infrastructure.security.datascope.mybatis.EasyDataScopeInnerInterceptor;
import com.laker.admin.infrastructure.security.datascope.resolver.DataScopeResolver;
import com.laker.admin.infrastructure.security.interceptor.EasyPermissionInterceptor;
import com.laker.admin.infrastructure.web.interceptor.EasyHttpDiagnosticInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MonitoringKillSwitchTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    io.github.lakernote.observability.autoconfigure.ObservabilityAutoConfiguration.class,
                    io.github.lakernote.observability.autoconfigure.TraceSpanAutoConfiguration.class))
            .withUserConfiguration(MonitoringComponents.class)
            .withBean(EasyNextAdminConfig.class, EasyNextAdminConfig::new)
            .withBean(EasyPermissionInterceptor.class, () -> mock(EasyPermissionInterceptor.class))
            .withBean(RecentTraceStore.class, () -> mock(RecentTraceStore.class))
            .withBean(OperationObservation.class, () -> mock(OperationObservation.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void disabledMonitorDoesNotCreateHotPathInterceptorsOrAspects() {
        contextRunner.withPropertyValues("easy.features.monitor=false", "easy.features.kafka=true",
                        "easy.observability.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(EasyHttpDiagnosticInterceptor.class);
                    assertThat(context).doesNotHaveBean(EasyKafkaConsumerSlowAspect.class);
                    assertThat(context).doesNotHaveBean(TraceSpanAspect.class);
                    assertThat(context).doesNotHaveBean(TransactionDiagnosticListener.class);
                    assertThat(context).doesNotHaveBean(TransactionDiagnosticBeanPostProcessor.class);
                    assertThat(context).hasSingleBean(EasyMvcInterceptorConfig.class);
                });
    }

    @Test
    void enabledMonitorCreatesHotPathInterceptorsAndAspects() {
        contextRunner.withPropertyValues("easy.features.monitor=true", "easy.features.kafka=true",
                        "easy.observability.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(EasyHttpDiagnosticInterceptor.class);
                    assertThat(context).hasSingleBean(EasyKafkaConsumerSlowAspect.class);
                    assertThat(context).hasSingleBean(TraceSpanAspect.class);
                    assertThat(context).hasSingleBean(TransactionDiagnosticListener.class);
                    assertThat(context).hasSingleBean(TransactionDiagnosticBeanPostProcessor.class);
                });
    }

    @Test
    void mybatisTraceIsOwnedByStarterAndHostKeepsBusinessPlugins() {
        MybatisPlusInterceptor interceptor = new EasyMybatisConfig().mybatisPlusInterceptor(
                mock(DataScopeResolver.class));
        assertThat(interceptor.getInterceptors())
                .extracting(Object::getClass)
                .containsExactly(EasyDataScopeInnerInterceptor.class,
                        OptimisticLockerInnerInterceptor.class, PaginationInnerInterceptor.class);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({EasyMvcInterceptorConfig.class, EasyKafkaConsumerSlowAspect.class,
            TransactionDiagnosticListener.class, TransactionDiagnosticBeanPostProcessor.class})
    static class MonitoringComponents {
        @Bean
        DataScopeResolver unusedDataScopeResolver() {
            return mock(DataScopeResolver.class);
        }
    }
}
