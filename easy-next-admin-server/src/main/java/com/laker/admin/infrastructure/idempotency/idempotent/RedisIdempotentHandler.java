package com.laker.admin.infrastructure.idempotency.idempotent;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class RedisIdempotentHandler implements IdempotentHandler {

    private final StringRedisTemplate stringRedisTemplate;

    public RedisIdempotentHandler(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean checkAndSet(String key, long expireTime) {
        return stringRedisTemplate.opsForValue().setIfAbsent(key, "1", expireTime, TimeUnit.SECONDS);
    }

    @Override
    public void remove(String key) {
        stringRedisTemplate.delete(key);
    }
}
