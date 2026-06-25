package com.laker.admin.infrastructure.ratelimit;

import com.laker.admin.infrastructure.observability.metrics.EasyBusinessMetrics;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.web.context.EasyRequestContext;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class EasyRateLimiterAspect {

    private final EasyRateLimiterFactory easyRateLimiterFactory;
    private final EasyBusinessMetrics businessMetrics;

    @Around("@annotation(rateLimit)")
    public Object handleRateLimit(ProceedingJoinPoint joinPoint, EasyRateLimit rateLimit) throws Throwable {
        String key = generateKey(rateLimit); // 动态生成 key
        EasyRateLimiter rateLimiter = easyRateLimiterFactory.getRateLimiter(); // 可切换策略
        if (!rateLimiter.tryAcquire(key, rateLimit.limit(), rateLimit.timeUnit().toSeconds(rateLimit.timeWindow()))) {
            businessMetrics.recordRateLimitBlocked(rateLimit.key(), rateLimit.type().name());
            throw new RateLimitException(rateLimit.message()); // 使用注解中的自定义消息
        }
        return joinPoint.proceed();
    }

    private String generateKey(EasyRateLimit rateLimit) {
        String key = rateLimit.key();
        // 根据限流类型生成键
        return switch (rateLimit.type()) {
            case CLIENT_IP -> key + ":" + getClientIp();
            case USER -> key + ":" + getUserId();
            default -> key + ":" + "global";
        };
    }

    // 获取客户端 IP 地址
    private String getClientIp() {
        return EasyRequestContext.currentRemoteIp();
    }

    private String getUserId() {
        Long userId = EasySecurityContext.getUserId();
        return userId == null ? "anonymous" : String.valueOf(userId);
    }
}
