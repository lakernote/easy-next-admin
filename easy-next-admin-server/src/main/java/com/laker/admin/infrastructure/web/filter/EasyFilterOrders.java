package com.laker.admin.infrastructure.web.filter;

import lombok.Getter;
import org.springframework.core.Ordered;

/**
 * Servlet 过滤器注册元数据集中定义，避免过滤器类、配置类和注解各自声明顺序与 URL pattern。
 *
 * @author laker
 */
public enum EasyFilterOrders implements Ordered {
    TRACE("easyTraceFilter", Ordered.HIGHEST_PRECEDENCE, "/*"),
    WAF("easyWafFilter", Ordered.HIGHEST_PRECEDENCE + 10, "/*"),
    CORS("easyCorsFilter", Ordered.HIGHEST_PRECEDENCE + 20, "/*"),
    AUTH("easyAuthFilter", Ordered.HIGHEST_PRECEDENCE + 30, "/api/*");

    @Getter
    private final String registrationName;
    private final int order;
    private final String[] urlPatterns;

    EasyFilterOrders(String registrationName, int order, String... urlPatterns) {
        this.registrationName = registrationName;
        this.order = order;
        this.urlPatterns = urlPatterns.clone();
    }

    @Override
    public int getOrder() {
        return order;
    }

    public String[] getUrlPatterns() {
        return urlPatterns.clone();
    }
}
