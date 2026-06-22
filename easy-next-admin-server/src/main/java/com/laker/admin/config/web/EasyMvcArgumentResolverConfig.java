package com.laker.admin.config.web;

import com.laker.admin.infrastructure.web.mvc.PageRequestArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class EasyMvcArgumentResolverConfig implements WebMvcConfigurer {

    private final PageRequestArgumentResolver pageRequestArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(pageRequestArgumentResolver);
    }
}
