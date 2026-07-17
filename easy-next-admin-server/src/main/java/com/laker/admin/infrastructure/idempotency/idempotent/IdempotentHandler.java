package com.laker.admin.infrastructure.idempotency.idempotent;

public interface IdempotentHandler {
    boolean checkAndSet(String key, long expireTime);
    void remove(String key);
}
