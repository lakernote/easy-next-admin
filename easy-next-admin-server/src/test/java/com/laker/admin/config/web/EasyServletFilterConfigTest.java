package com.laker.admin.config.web;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.infrastructure.security.filter.EasyAuthFilter;
import com.laker.admin.infrastructure.web.filter.EasyCorsFilter;
import com.laker.admin.infrastructure.web.filter.EasyFilterOrders;
import com.laker.admin.infrastructure.web.filter.EasyTraceIdFilter;
import com.laker.admin.infrastructure.web.waf.WafFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.assertj.core.api.Assertions.assertThat;

class EasyServletFilterConfigTest {
    private final EasyServletFilterConfig config = new EasyServletFilterConfig();

    @Test
    void shouldRegisterServletFiltersWithEnumMetadata() {
        EasyNextAdminConfig easyNextAdminConfig = new EasyNextAdminConfig();

        assertRegistration(config.easyTraceFilterRegistration(), EasyFilterOrders.TRACE, EasyTraceIdFilter.class);
        assertRegistration(config.easyWafFilterRegistration(easyNextAdminConfig), EasyFilterOrders.WAF, WafFilter.class);
        assertRegistration(config.easyCorsFilterRegistration(easyNextAdminConfig), EasyFilterOrders.CORS, EasyCorsFilter.class);
        assertRegistration(config.easyAuthFilterRegistration(null), EasyFilterOrders.AUTH, EasyAuthFilter.class);
    }

    private static void assertRegistration(FilterRegistrationBean<? extends Filter> registration,
                                           EasyFilterOrders filterOrder,
                                           Class<? extends Filter> filterType) {
        assertThat(registration.getFilterName()).isEqualTo(filterOrder.getRegistrationName());
        assertThat(registration.getOrder()).isEqualTo(filterOrder.getOrder());
        assertThat(registration.getUrlPatterns()).containsExactly(filterOrder.getUrlPatterns());
        assertThat(registration.determineDispatcherTypes()).containsExactly(DispatcherType.REQUEST);
        assertThat(registration.getFilter()).isInstanceOf(filterType);
    }
}
