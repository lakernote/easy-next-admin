package com.laker.admin.infrastructure.web.filter;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

class EasyFilterOrdersTest {

    @Test
    void shouldExposeFilterRegistrationMetadataAsEnum() {
        assertThat(EasyFilterOrders.class.isEnum()).isTrue();
        assertThat(EasyFilterOrders.values())
                .containsExactly(EasyFilterOrders.TRACE, EasyFilterOrders.WAF, EasyFilterOrders.CORS,
                        EasyFilterOrders.AUTH);

        assertMetadata(EasyFilterOrders.TRACE, "easyTraceFilter", Ordered.HIGHEST_PRECEDENCE, "/*");
        assertMetadata(EasyFilterOrders.WAF, "easyWafFilter", Ordered.HIGHEST_PRECEDENCE + 10, "/*");
        assertMetadata(EasyFilterOrders.CORS, "easyCorsFilter", Ordered.HIGHEST_PRECEDENCE + 20, "/*");
        assertMetadata(EasyFilterOrders.AUTH, "easyAuthFilter", Ordered.HIGHEST_PRECEDENCE + 30, "/api/*");
    }

    private static void assertMetadata(EasyFilterOrders filterOrder,
                                       String registrationName,
                                       int order,
                                       String urlPattern) {
        assertThat(filterOrder.getRegistrationName()).isEqualTo(registrationName);
        assertThat(filterOrder.getOrder()).isEqualTo(order);
        assertThat(filterOrder.getUrlPatterns()).containsExactly(urlPattern);
    }
}
