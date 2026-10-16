package com.laker.observability.autoconfigure;

import com.laker.observability.web.ObservabilityController;
import com.laker.observability.web.ObservabilityAccessPolicy;
import com.laker.observability.web.ObservabilityDashboardController;
import com.laker.observability.config.EasyObservabilityProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Serves the bundled dashboard and its read-only API in Spring MVC applications. */
@AutoConfiguration(after = ObservabilityAutoConfiguration.class)
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "easy.observability", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "easy.observability.web", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({ObservabilityController.class, ObservabilityDashboardController.class})
public class ObservabilityWebAutoConfiguration implements WebMvcConfigurer {
    @Bean
    @ConditionalOnMissingBean
    ObservabilityAccessPolicy observabilityAccessPolicy(EasyObservabilityProperties properties) {
        return () -> properties.getWeb().isAllowAnonymous();
    }


    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/easy-observability/assets/**")
                .addResourceLocations("classpath:/META-INF/resources/easy-observability/assets/");
    }
}
