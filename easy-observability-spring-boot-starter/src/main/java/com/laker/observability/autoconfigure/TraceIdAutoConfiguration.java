package com.laker.observability.autoconfigure;

import com.laker.observability.config.EasyObservabilityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;

/** Installs trace ID propagation separately from the optional dashboard. */
@AutoConfiguration(after = ObservabilityAutoConfiguration.class)
@ConditionalOnProperty(prefix = "easy.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "easy.observability.trace", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TraceIdAutoConfiguration {
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
    static class ServletConfiguration {
        @org.springframework.context.annotation.Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean(
                com.laker.observability.web.TraceIdFilter.class)
        org.springframework.boot.web.servlet.FilterRegistrationBean<com.laker.observability.web.TraceIdFilter>
        observabilityTraceIdFilter(EasyObservabilityProperties properties) {
            org.springframework.boot.web.servlet.FilterRegistrationBean<com.laker.observability.web.TraceIdFilter> registration =
                    new org.springframework.boot.web.servlet.FilterRegistrationBean<>();
            registration.setName("easyObservabilityTraceIdFilter");
            registration.setFilter(new com.laker.observability.web.TraceIdFilter(properties));
            registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE);
            registration.setAsyncSupported(true);
            registration.addUrlPatterns("/*");
            return registration;
        }
    }
}
