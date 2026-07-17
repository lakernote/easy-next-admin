package com.laker.admin.config.web;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.security.filter.EasyAuthFilter;
import com.laker.admin.infrastructure.security.service.EasyAuthService;
import com.laker.admin.infrastructure.web.filter.EasyCorsFilter;
import com.laker.admin.infrastructure.web.filter.EasyFilterOrders;
import com.laker.admin.infrastructure.web.filter.EasyTraceIdFilter;
import com.laker.admin.infrastructure.web.waf.WafFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Servlet 过滤器统一注册入口。
 *
 * @author laker
 */
@Configuration
public class EasyServletFilterConfig {

    @Bean
    public FilterRegistrationBean<EasyTraceIdFilter> easyTraceFilterRegistration() {
        return register(EasyFilterOrders.TRACE, new EasyTraceIdFilter());
    }

    @Bean
    public FilterRegistrationBean<WafFilter> easyWafFilterRegistration(EasyNextAdminConfig easyNextAdminConfig) {
        EasyNextAdminConfig.Waf waf = easyNextAdminConfig.getWaf();
        return register(EasyFilterOrders.WAF,
                new WafFilter(waf.getExcludes(), waf.isXssEnabled(), waf.isSqlEnabled(),
                        easyNextAdminConfig.getWeb().getSecurityHeaders()));
    }

    @Bean
    public FilterRegistrationBean<EasyCorsFilter> easyCorsFilterRegistration(EasyNextAdminConfig easyNextAdminConfig) {
        return register(EasyFilterOrders.CORS, new EasyCorsFilter(easyNextAdminConfig.getWeb().getCors()));
    }

    @Bean
    public FilterRegistrationBean<EasyAuthFilter> easyAuthFilterRegistration(EasyAuthService authService) {
        return register(EasyFilterOrders.AUTH, new EasyAuthFilter(authService));
    }

    private static <T extends Filter> FilterRegistrationBean<T> register(EasyFilterOrders filterOrder, T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>();
        registration.setDispatcherTypes(DispatcherType.REQUEST);
        registration.setName(filterOrder.getRegistrationName());
        registration.setFilter(filter);
        registration.setOrder(filterOrder.getOrder());
        registration.addUrlPatterns(filterOrder.getUrlPatterns());
        return registration;
    }
}
