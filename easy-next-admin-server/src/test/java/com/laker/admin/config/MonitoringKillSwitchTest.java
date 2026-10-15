package com.laker.admin.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.laker.admin.config.database.EasyMybatisConfig;
import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.config.web.EasyMvcInterceptorConfig;
import com.laker.admin.infrastructure.mq.kafka.EasyKafkaConsumerSlowAspect;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import com.laker.admin.infrastructure.observability.trace.AutomaticServiceTracingAspect;
import com.laker.admin.infrastructure.observability.trace.EasyTracingAspect;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import com.laker.admin.infrastructure.observability.trace.TransactionObservationBeanPostProcessor;
import com.laker.admin.infrastructure.observability.trace.TransactionObservationListener;
import com.laker.admin.infrastructure.persistence.mybatis.EasyMybatisTraceInterceptor;
import com.laker.admin.infrastructure.security.datascope.mybatis.EasyDataScopeInnerInterceptor;
import com.laker.admin.infrastructure.security.datascope.resolver.DataScopeResolver;
import com.laker.admin.infrastructure.security.interceptor.EasyPermissionInterceptor;
import com.laker.admin.infrastructure.web.interceptor.EasyHttpSlowRequestInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MonitoringKillSwitchTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MonitoringComponents.class)
            .withBean(EasyNextAdminConfig.class, EasyNextAdminConfig::new)
            .withBean(EasyPermissionInterceptor.class, () -> mock(EasyPermissionInterceptor.class))
            .withBean(RecentTraceStore.class, () -> mock(RecentTraceStore.class))
            .withBean(OperationObservation.class, () -> mock(OperationObservation.class))
            .withBean(MeterRegistry.class, SimpleMeterRegistry::new);

    @Test
    void disabledMonitorDoesNotCreateHotPathInterceptorsOrAspects() {
        contextRunner.withPropertyValues("easy.features.monitor=false", "easy.features.kafka=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(EasyHttpSlowRequestInterceptor.class);
                    assertThat(context).doesNotHaveBean(EasyKafkaConsumerSlowAspect.class);
                    assertThat(context).doesNotHaveBean(AutomaticServiceTracingAspect.class);
                    assertThat(context).doesNotHaveBean(EasyTracingAspect.class);
                    assertThat(context).doesNotHaveBean(TransactionObservationListener.class);
                    assertThat(context).doesNotHaveBean(TransactionObservationBeanPostProcessor.class);
                    assertThat(context).hasSingleBean(EasyMvcInterceptorConfig.class);
                });
    }

    @Test
    void enabledMonitorCreatesHotPathInterceptorsAndAspects() {
        contextRunner.withPropertyValues("easy.features.monitor=true", "easy.features.kafka=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(EasyHttpSlowRequestInterceptor.class);
                    assertThat(context).hasSingleBean(EasyKafkaConsumerSlowAspect.class);
                    assertThat(context).hasSingleBean(AutomaticServiceTracingAspect.class);
                    assertThat(context).hasSingleBean(EasyTracingAspect.class);
                    assertThat(context).hasSingleBean(TransactionObservationListener.class);
                    assertThat(context).hasSingleBean(TransactionObservationBeanPostProcessor.class);
                });
    }

    @Test
    void disabledMonitorKeepsMybatisBusinessPluginsWithoutTraceWrapper() {
        EasyNextAdminConfig properties = new EasyNextAdminConfig();
        properties.getFeatures().setMonitor(false);

        MybatisPlusInterceptor interceptor = new EasyMybatisConfig().mybatisPlusInterceptor(
                mock(DataScopeResolver.class), new SimpleMeterRegistry(),
                mock(OperationObservation.class), properties);

        assertThat(interceptor).isNotInstanceOf(EasyMybatisTraceInterceptor.class);
        assertThat(interceptor.getInterceptors())
                .extracting(Object::getClass)
                .containsExactly(EasyDataScopeInnerInterceptor.class,
                        OptimisticLockerInnerInterceptor.class, PaginationInnerInterceptor.class);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({EasyMvcInterceptorConfig.class, EasyKafkaConsumerSlowAspect.class,
            AutomaticServiceTracingAspect.class, EasyTracingAspect.class,
            TransactionObservationListener.class, TransactionObservationBeanPostProcessor.class})
    static class MonitoringComponents {
        @Bean
        DataScopeResolver unusedDataScopeResolver() {
            return mock(DataScopeResolver.class);
        }
    }
}
