package com.laker.admin.infrastructure.security.store;

import com.laker.admin.infrastructure.security.model.AuthSession;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 登录会话存储端口。
 *
 * <p>业务层只依赖该接口，不关心底层是 Redis 还是内存。实现类必须只保存 token 摘要，
 * 不持久化 accessToken 原文。</p>
 */
public interface AuthSessionStore {
    void save(AuthSession session, Duration sessionTtl);

    void update(AuthSession session, Duration sessionTtl);

    Optional<AuthSession> findByAccessTokenHash(String accessTokenHash);

    Optional<AuthSession> findBySessionId(Long sessionId);

    void revoke(Long sessionId, String status);

    List<AuthSession> listActive(int page, int pageSize);

    long countActive();
}
