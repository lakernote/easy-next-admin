package com.laker.admin.config.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * OpenAPI 3 接口文档配置。
 */
@Configuration
public class OpenApiConfig {
    private static final String BEARER_AUTH = "BearerAuth";

    /**
     * @return the global open api customizer
     */
    @Bean
    public GlobalOpenApiCustomizer orderGlobalOpenApiCustomizer() {
        return openApi -> {
            // 获取所有 Paths 并按自定义规则排序
            Paths sortedPaths = sortPathsByCustomRule(openApi.getPaths());
            openApi.setPaths(sortedPaths);
        };
    }

    private Paths sortPathsByCustomRule(Paths originalPaths) {
        if (originalPaths == null || originalPaths.isEmpty()) {
            return new Paths();
        }
        // 使用 LinkedHashMap 保持顺序
        Map<String, PathItem> sortedPathItems = originalPaths.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // 自定义排序规则
                .collect(Collectors.toMap(
                        Map.Entry::getKey, // 显式指定泛型
                        Map.Entry::getValue,
                        (existing, replacement) -> existing,
                        Paths::new
                ));

        // 创建新的 Paths 对象
        Paths sortedPaths = new Paths();
        sortedPathItems.forEach(sortedPaths::addPathItem);
        return sortedPaths;
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("opaque")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH))
                .info(new Info()
                        .title("EasyNextAdmin Enterprise API")
                        .contact(new Contact()
                                .name("laker")
                                .email("935009066@qq.com"))
                        .version("1.0")
                        .description("EasyNextAdmin 企业级开发脚手架 OpenAPI 3 接口文档")
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }

    @Bean
    public GroupedOpenApi systemApi() {
        return GroupedOpenApi.builder()
                .group("1.system")
                .displayName("1.系统管理")
                .pathsToMatch("/api/auth/**", "/api/system/**")
                .packagesToScan("com.laker.admin.module.system")
                .build();
    }

    @Bean
    public GroupedOpenApi monitorApi() {
        return GroupedOpenApi.builder()
                .group("2.monitor")
                .displayName("2.运行监控")
                .pathsToMatch("/api/monitor/**")
                .packagesToScan("com.laker.admin.module.monitor")
                .build();
    }

    @Bean
    public GroupedOpenApi auditApi() {
        return GroupedOpenApi.builder()
                .group("3.audit")
                .displayName("3.审计中心")
                .pathsToMatch("/api/audit/**")
                .packagesToScan("com.laker.admin.module.audit")
                .build();
    }

    @Bean
    public GroupedOpenApi reportApi() {
        return GroupedOpenApi.builder()
                .group("4.report")
                .displayName("4.企业报表")
                .pathsToMatch("/api/reports/**")
                .packagesToScan("com.laker.admin.module.report")
                .build();
    }

    @Bean
    public GroupedOpenApi scheduleApi() {
        return GroupedOpenApi.builder()
                .group("5.schedule")
                .displayName("5.任务调度")
                .pathsToMatch("/api/schedule/**")
                .packagesToScan("com.laker.admin.module.schedule")
                .build();
    }

    @Bean
    public GroupedOpenApi workflowApi() {
        return GroupedOpenApi.builder()
                .group("6.workflow")
                .displayName("6.工作流")
                .pathsToMatch("/api/workflow/**")
                .packagesToScan("com.laker.admin.module.workflow")
                .build();
    }

}
