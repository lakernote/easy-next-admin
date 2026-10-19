package com.laker.admin.config.web;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.security.interceptor.EasyPermissionInterceptor;
import com.laker.admin.infrastructure.web.interceptor.EasyHttpDiagnosticInterceptor;
import com.laker.admin.infrastructure.observability.trace.RecentTraceStore;
import com.laker.admin.infrastructure.observability.jfr.OperationObservation;
import io.github.lakernote.observability.web.ObservabilityAccessPolicy;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class EasyMvcInterceptorConfig implements WebMvcConfigurer {

    private static final String API_PATH_PATTERN = "/api/**";
    private static final int HTTP_DIAGNOSTIC_INTERCEPTOR_ORDER = 0;
    private static final int PERMISSION_INTERCEPTOR_ORDER = 1;

    private final EasyNextAdminConfig easyNextAdminConfig;
    private final EasyPermissionInterceptor easyPermissionInterceptor;
    private final ObjectProvider<EasyHttpDiagnosticInterceptor> httpDiagnosticInterceptorProvider;

    @Bean
    @ConditionalOnProperty(prefix = "easy.features", name = "monitor", havingValue = "true", matchIfMissing = true)
    public EasyHttpDiagnosticInterceptor easyHttpDiagnosticInterceptor(
            RecentTraceStore recentTraceStore, OperationObservation operationObservation,
            io.github.lakernote.observability.trace.store.RecentTraceStore starterTraceStore) {
        return new EasyHttpDiagnosticInterceptor(
                easyNextAdminConfig.getTrace().isEnabled(),
                easyNextAdminConfig.getTrace().getHttpSlowThresholdMs(),
                easyNextAdminConfig.getTrace().getMaxDepth(),
                easyNextAdminConfig.getTrace().getMinNodeCostMs(), recentTraceStore, operationObservation, starterTraceStore);
    }

    @Bean
    public ObservabilityAccessPolicy observabilityAccessPolicy() {
        return () -> {
            var principal = EasySecurityContext.getPrincipal();
            return principal != null && (principal.isSuperAdmin()
                    || principal.hasPermission(EasyPermissions.Monitor.DIAGNOSIS_VIEW));
        };
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        httpDiagnosticInterceptorProvider.ifAvailable(interceptor -> registry.addInterceptor(interceptor)
                .addPathPatterns(API_PATH_PATTERN)
                .order(HTTP_DIAGNOSTIC_INTERCEPTOR_ORDER));
        registry.addInterceptor(easyPermissionInterceptor)
                .addPathPatterns(API_PATH_PATTERN)
                .order(PERMISSION_INTERCEPTOR_ORDER);
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        httpDiagnosticInterceptorProvider.ifAvailable(interceptor -> configurer.registerCallableInterceptors(
                interceptor.callableTraceInterceptor()));
    }
}
