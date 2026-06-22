package com.laker.admin.infrastructure.security.store;

import com.laker.admin.infrastructure.security.model.CaptchaChallenge;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MemoryCaptchaStore implements CaptchaStore {
    private final ConcurrentMap<String, CaptchaChallenge> challenges = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, LocalDateTime> requiredKeys = new ConcurrentHashMap<>();

    @Override
    public void save(CaptchaChallenge challenge, Duration ttl) {
        cleanupExpired();
        challenges.put(challenge.getCaptchaId(), challenge);
    }

    @Override
    public Optional<CaptchaChallenge> consume(String captchaId) {
        cleanupExpired();
        return Optional.ofNullable(challenges.remove(captchaId));
    }

    @Override
    public void markRequired(String riskKey, Duration ttl) {
        cleanupExpired();
        requiredKeys.put(riskKey, LocalDateTime.now().plus(ttl));
    }

    @Override
    public boolean isRequired(String riskKey) {
        cleanupExpired();
        return requiredKeys.containsKey(riskKey);
    }

    @Override
    public void clearRequired(String riskKey) {
        requiredKeys.remove(riskKey);
    }

    private void cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        challenges.entrySet().removeIf(entry -> entry.getValue().getExpireTime().isBefore(now));
        requiredKeys.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
