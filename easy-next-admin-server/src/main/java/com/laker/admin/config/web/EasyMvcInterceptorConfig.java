package com.laker.admin.config.web;

import com.laker.admin.infrastructure.security.interceptor.EasyPermissionInterceptor;
import io.github.lakernote.observability.web.ObservabilityAccessPolicy;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class EasyMvcInterceptorConfig implements WebMvcConfigurer {

    private static final String API_PATH_PATTERN = "/api/**";
    private final EasyPermissionInterceptor easyPermissionInterceptor;

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
        registry.addInterceptor(easyPermissionInterceptor)
                .addPathPatterns(API_PATH_PATTERN)
                .order(0);
    }
}
