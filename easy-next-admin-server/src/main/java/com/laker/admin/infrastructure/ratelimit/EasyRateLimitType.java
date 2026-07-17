package com.laker.admin.infrastructure.ratelimit;

public enum EasyRateLimitType {
    CLIENT_IP, // 按客户端 IP 限流
    USER,      // 按用户 ID 限流
    GLOBAL     // 全局限流
}