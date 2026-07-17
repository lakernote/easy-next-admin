package com.laker.admin.module.system.service;

import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.model.AuthSession;
import com.laker.admin.infrastructure.security.store.AuthSessionStore;
import com.laker.admin.module.system.dto.profile.ProfileSecurityOverview;
import com.laker.admin.module.system.service.profile.ProfileSecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class ProfileSecurityServiceIntegrationTest {

    @Autowired
    private ProfileSecurityService profileSecurityService;
    @Autowired
    private AuthSessionStore authSessionStore;

    @BeforeEach
    void setPrincipal() {
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .sessionId(91001L)
                .userId(202604280101000001L)
                .userName("admin")
                .nickName("超级管理员")
                .superAdmin(true)
                .build());
        saveSession(91001L, 202604280101000001L);
        saveSession(91002L, 202604280101000001L);
        saveSession(91003L, 202604280101000026L);
    }

    @AfterEach
    void clearSecurityContext() {
        EasySecurityContext.clear();
    }

    @Test
    void shouldReturnCurrentProfileSecurityOverview() {
        ProfileSecurityOverview overview = profileSecurityService.overview();

        assertThat(overview.getUser().getUserName()).isEqualTo("admin");
        assertThat(overview.getActiveSessions()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(overview.getLoginHistory()).isNotEmpty();
    }

    @Test
    void shouldRevokeOtherSessionsOnlyForCurrentUser() {
        profileSecurityService.revokeOtherSessions();

        assertThat(authSessionStore.findBySessionId(91001L)).get().extracting(AuthSession::getStatus).isEqualTo(AuthSession.STATUS_ACTIVE);
        assertThat(authSessionStore.findBySessionId(91002L)).get().extracting(AuthSession::getStatus).isEqualTo(AuthSession.STATUS_KICKED);
        assertThat(authSessionStore.findBySessionId(91003L)).get().extracting(AuthSession::getStatus).isEqualTo(AuthSession.STATUS_ACTIVE);
    }

    private void saveSession(Long sessionId, Long userId) {
        AuthSession session = AuthSession.builder()
                .sessionId(sessionId)
                .userId(userId)
                .accessTokenHash("access-" + sessionId)
                .clientType("web")
                .clientVersion("test")
                .ip("127.0.0.1")
                .userAgent("JUnit")
                .loginTime(LocalDateTime.now().minusMinutes(10))
                .lastActiveTime(LocalDateTime.now())
                .accessExpireTime(LocalDateTime.now().plusMinutes(30))
                .status(AuthSession.STATUS_ACTIVE)
                .build();
        authSessionStore.save(session, Duration.ofMinutes(30));
    }
}
