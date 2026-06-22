package com.laker.admin.infrastructure.security.store;

import com.laker.admin.infrastructure.security.model.AuthSession;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class MemoryAuthSessionStore implements AuthSessionStore {
    private final ConcurrentMap<Long, AuthSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void save(AuthSession session, Duration sessionTtl) {
        cleanupExpired();
        sessions.put(session.getSessionId(), session);
    }

    @Override
    public void update(AuthSession session, Duration sessionTtl) {
        sessions.put(session.getSessionId(), session);
    }

    @Override
    public Optional<AuthSession> findByAccessTokenHash(String accessTokenHash) {
        cleanupExpired();
        return sessions.values().stream()
                .filter(session -> AuthSession.STATUS_ACTIVE.equals(session.getStatus()))
                .filter(session -> accessTokenHash.equals(session.getAccessTokenHash()))
                .findFirst();
    }

    @Override
    public Optional<AuthSession> findBySessionId(Long sessionId) {
        cleanupExpired();
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public void revoke(Long sessionId, String status) {
        AuthSession session = sessions.get(sessionId);
        if (session != null) {
            session.setStatus(status);
            session.setLastActiveTime(LocalDateTime.now());
        }
    }

    @Override
    public List<AuthSession> listActive(int page, int pageSize) {
        cleanupExpired();
        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int from = (safePage - 1) * safePageSize;
        return sessions.values().stream()
                .filter(session -> AuthSession.STATUS_ACTIVE.equals(session.getStatus()))
                .sorted(Comparator.comparing(AuthSession::getLastActiveTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .skip(from)
                .limit(safePageSize)
                .toList();
    }

    @Override
    public long countActive() {
        cleanupExpired();
        return sessions.values().stream()
                .filter(session -> AuthSession.STATUS_ACTIVE.equals(session.getStatus()))
                .count();
    }

    private void cleanupExpired() {
        LocalDateTime now = LocalDateTime.now();
        sessions.values().removeIf(session -> session.getAccessExpireTime() != null
                && session.getAccessExpireTime().isBefore(now));
    }
}
