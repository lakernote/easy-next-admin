package com.laker.admin.config.web;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.security.interceptor.EasyPermissionInterceptor;
import com.laker.admin.infrastructure.web.interceptor.EasyHttpSlowRequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class EasyMvcInterceptorConfig implements WebMvcConfigurer {

    private static final String API_PATH_PATTERN = "/api/**";
    private static final int HTTP_SLOW_INTERCEPTOR_ORDER = 0;
    private static final int PERMISSION_INTERCEPTOR_ORDER = 1;

    private final EasyNextAdminConfig easyNextAdminConfig;
    private final EasyPermissionInterceptor easyPermissionInterceptor;

    @Bean
    public EasyHttpSlowRequestInterceptor easyHttpSlowRequestInterceptor() {
        return new EasyHttpSlowRequestInterceptor(
                easyNextAdminConfig.getTrace().isEnabled(),
                easyNextAdminConfig.getTrace().getHttpSlowThresholdMs(),
                easyNextAdminConfig.getTrace().getMaxDepth(),
                easyNextAdminConfig.getTrace().getMinNodeCostMs());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(easyHttpSlowRequestInterceptor())
                .addPathPatterns(API_PATH_PATTERN)
                .order(HTTP_SLOW_INTERCEPTOR_ORDER);
        registry.addInterceptor(easyPermissionInterceptor)
                .addPathPatterns(API_PATH_PATTERN)
                .order(PERMISSION_INTERCEPTOR_ORDER);
    }
}
