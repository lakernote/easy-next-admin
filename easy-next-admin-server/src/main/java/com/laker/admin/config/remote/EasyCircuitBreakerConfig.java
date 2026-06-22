package com.laker.admin.config.remote;

import com.laker.admin.infrastructure.thread.EasyNextAdminMDCThreadPoolExecutor;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 为远程调用断路器提供统一默认配置。
 */
@Configuration
public class EasyCircuitBreakerConfig {
    @Bean
    public Customizer<Resilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> {
            factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                    .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(Duration.ofSeconds(10)).build())
                    .circuitBreakerConfig(CircuitBreakerConfig.ofDefaults())
                    .build());

            factory.configureGroupExecutorService(group -> new EasyNextAdminMDCThreadPoolExecutor(3, 3, group));
            factory.configureExecutorService(new EasyNextAdminMDCThreadPoolExecutor(3, 3, "easy-feign"));
        };
    }
}
