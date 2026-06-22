package com.laker.admin.infrastructure.security.store;

import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.json.EasyJsonException;
import com.laker.admin.infrastructure.security.model.AuthSession;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class RedisAuthSessionStore implements AuthSessionStore {
    private static final Duration DEFAULT_SESSION_TTL = Duration.ofMinutes(30);
    private static final String SESSION_PREFIX = "easy:auth:session:";
    private static final String ACCESS_PREFIX = "easy:auth:session:access:";
    private static final String ACTIVE_ZSET = "easy:auth:session:active";

    private final StringRedisTemplate redisTemplate;
    private final EasyJsonCodec jsonCodec;
    private final Duration fallbackSessionTtl;

    public RedisAuthSessionStore(StringRedisTemplate redisTemplate, EasyJsonCodec jsonCodec) {
        this(redisTemplate, jsonCodec, DEFAULT_SESSION_TTL);
    }

    public RedisAuthSessionStore(StringRedisTemplate redisTemplate, EasyJsonCodec jsonCodec, Duration fallbackSessionTtl) {
        this.redisTemplate = redisTemplate;
        this.jsonCodec = jsonCodec;
        this.fallbackSessionTtl = fallbackSessionTtl == null || fallbackSessionTtl.isZero() || fallbackSessionTtl.isNegative()
                ? DEFAULT_SESSION_TTL
                : fallbackSessionTtl;
    }

    @Override
    public void save(AuthSession session, Duration sessionTtl) {
        write(session, sessionTtl);
    }

    @Override
    public void update(AuthSession session, Duration sessionTtl) {
        write(session, sessionTtl);
    }

    @Override
    public Optional<AuthSession> findByAccessTokenHash(String accessTokenHash) {
        return findByIndex(ACCESS_PREFIX + accessTokenHash)
                .filter(session -> accessTokenHash.equals(session.getAccessTokenHash()));
    }

    @Override
    public Optional<AuthSession> findBySessionId(Long sessionId) {
        String payload = redisTemplate.opsForValue().get(SESSION_PREFIX + sessionId);
        if (payload == null) {
            return Optional.empty();
        }
        return Optional.of(read(payload));
    }

    @Override
    public void revoke(Long sessionId, String status) {
        findBySessionId(sessionId).ifPresent(session -> {
            session.setStatus(status);
            session.setLastActiveTime(LocalDateTime.now());
            Duration ttl = remaining(session.getAccessExpireTime());
            redisTemplate.opsForValue().set(SESSION_PREFIX + sessionId, writeValue(session), ttl);
            redisTemplate.opsForZSet().remove(ACTIVE_ZSET, String.valueOf(sessionId));
        });
    }

    @Override
    public List<AuthSession> listActive(int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        long start = (long) (safePage - 1) * safePageSize;
        long end = start + safePageSize - 1L;
        Set<String> sessionIds = redisTemplate.opsForZSet().reverseRange(ACTIVE_ZSET, start, end);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return List.of();
        }
        return sessionIds.stream()
                .map(Long::valueOf)
                .map(this::findBySessionId)
                .flatMap(Optional::stream)
                .filter(session -> AuthSession.STATUS_ACTIVE.equals(session.getStatus()))
                .toList();
    }

    @Override
    public long countActive() {
        Set<String> sessionIds = redisTemplate.opsForZSet().range(ACTIVE_ZSET, 0, -1);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return 0L;
        }
        long active = 0L;
        for (String sessionId : sessionIds) {
            Optional<AuthSession> session = findBySessionId(Long.valueOf(sessionId));
            if (session.isPresent() && AuthSession.STATUS_ACTIVE.equals(session.get().getStatus())) {
                active++;
            } else {
                redisTemplate.opsForZSet().remove(ACTIVE_ZSET, sessionId);
            }
        }
        return active;
    }

    private void write(AuthSession session, Duration sessionTtl) {
        redisTemplate.opsForValue().set(SESSION_PREFIX + session.getSessionId(), writeValue(session), sessionTtl);
        redisTemplate.opsForValue().set(ACCESS_PREFIX + session.getAccessTokenHash(), String.valueOf(session.getSessionId()), sessionTtl);
        if (AuthSession.STATUS_ACTIVE.equals(session.getStatus())) {
            redisTemplate.opsForZSet().add(ACTIVE_ZSET, String.valueOf(session.getSessionId()), score(session.getLastActiveTime()));
        } else {
            redisTemplate.opsForZSet().remove(ACTIVE_ZSET, String.valueOf(session.getSessionId()));
        }
    }

    private Optional<AuthSession> findByIndex(String key) {
        String sessionId = redisTemplate.opsForValue().get(key);
        if (sessionId == null) {
            return Optional.empty();
        }
        return findBySessionId(Long.valueOf(sessionId))
                .filter(session -> AuthSession.STATUS_ACTIVE.equals(session.getStatus()));
    }

    private String writeValue(AuthSession session) {
        try {
            return jsonCodec.toJson(session);
        } catch (EasyJsonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "会话序列化失败");
        }
    }

    private AuthSession read(String payload) {
        try {
            return jsonCodec.fromJson(payload, AuthSession.class);
        } catch (EasyJsonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "会话反序列化失败");
        }
    }

    private Duration remaining(LocalDateTime expireTime) {
        if (expireTime == null) {
            return fallbackSessionTtl;
        }
        Duration ttl = Duration.between(LocalDateTime.now(), expireTime);
        return ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(1) : ttl;
    }

    private double score(LocalDateTime time) {
        LocalDateTime value = time == null ? LocalDateTime.now() : time;
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
