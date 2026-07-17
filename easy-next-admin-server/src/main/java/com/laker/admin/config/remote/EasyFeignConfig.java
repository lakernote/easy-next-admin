package com.laker.admin.config.remote;

import com.laker.admin.common.constant.EasyNextAdminConstants;
import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import feign.*;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.CircuitBreakerNameResolver;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Feign 全局配置。
 */
@Configuration
@EnableFeignClients(basePackages = "com.laker.admin.module")
@ConditionalOnProperty(prefix = "easy.features", name = "feign", havingValue = "true", matchIfMissing = true)
@Slf4j
public class EasyFeignConfig {

    /**
     * 拦截器可以有多个
     */
    @Order(1)
    @Bean
    RequestInterceptor traceIdRequestInterceptor() {
        return requestTemplate ->
                requestTemplate.header(EasyNextAdminConstants.TRACE_ID_HEADER, EasyTraceIdContext.getOrCreateTraceId());
    }

    @Order(2)
    @Bean
    RequestInterceptor viaRequestInterceptor() {
        return requestTemplate -> requestTemplate.header("via", "easy-feign");
    }

    /**
     * 配置超时时间
     */
    @Bean
    Request.Options feignOptions() {
        // 默认连接超时时间为10秒，读取超时时间为60秒
        return new Request.Options(5, TimeUnit.SECONDS, 10, TimeUnit.SECONDS, true);
    }

    /**
     * 配置日志级别
     */
    @Bean
    Logger.Level feignLogger() {
        return Logger.Level.BASIC;
    }

    /**
     * 配置重试
     * <p>
     * 在Feign重试行为中，它将自动重试IOException，将它们视为与网络临时相关的异常，
     * 以及从 ErrorDecoder 抛出的任何 RetryableException。
     */
    @Bean
    Retryer feignRetryer() {
        //最大请求次数为3，初始间隔时间为100ms，下次间隔时间1.5倍递增，重试间最大间隔时间为1s，
        return new Retryer.Default(100, 1000, 3);
    }

    /**
     * 配置错误解码器
     */
    @Bean
    public ErrorDecoder feignError() {
        // ErrorDecoder的默认实现
        // 响应包含“Retry-After”标头时创建RetryableException实例。
        // 最常见的是，我们可以在 503 服务不可用响应中找到这个标头。
        final ErrorDecoder errorDecoder = new ErrorDecoder.Default();
        return (methodKey, response) -> {

            if (response.status() == 400) {
                log.error("远程服务 400 参数错误, 返回:{}", response.body());
            }

            if (response.status() == 404) {
                log.error("远程服务 404 异常, 返回:{}", response.body());
            }

            Exception defaultException = errorDecoder.decode(methodKey, response);
            if (defaultException instanceof RetryableException) {
                // Requirement 3: retry when Retry-After header is set
                // 默认的重试逻辑
                return defaultException;
            }

            // 扩展的重试逻辑
            // 5xx 服务器错误 重试
            if (HttpStatus.valueOf(response.status()).is5xxServerError()) {
                // 重试
                return new RetryableException(
                        response.status(),
                        defaultException.getMessage(),
                        response.request().httpMethod(),
                        defaultException,
                        (Long) null,
                        response.request());
            }

            // 默认处理
            return defaultException;
        };
    }

    /**
     * 配置熔断器名称解析器
     */
    @Bean
    public CircuitBreakerNameResolver circuitBreakerNameResolver() {
        return (String feignClientName, Target<?> target, Method method) -> feignClientName + "_" + method.getName();
    }
}
