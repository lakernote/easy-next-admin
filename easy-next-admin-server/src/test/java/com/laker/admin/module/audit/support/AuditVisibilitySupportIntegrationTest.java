package com.laker.admin.module.audit.support;

import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.datascope.model.DataScopeType;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Transactional
class AuditVisibilitySupportIntegrationTest {
    private static final long CURRENT_USER = 8401L;
    private static final long SAME_DEPT_USER = 8402L;
    private static final long OTHER_DEPT_USER = 8403L;
    private static final long CURRENT_DEPT = 202604280103000001L;
    private static final long OTHER_DEPT = 202604280103000002L;

    @Autowired
    private AuditVisibilitySupport auditVisibilitySupport;

    @Autowired
    private ISysUserService sysUserService;

    @AfterEach
    void clearSecurityContext() {
        EasySecurityContext.clear();
    }

    @Test
    void visibleUserIdsShouldUseResolvedScopeWithoutTriggeringNestedDataScopeRewrite() {
        sysUserService.save(activeUser(CURRENT_USER, "audit_current_user", CURRENT_DEPT));
        sysUserService.save(activeUser(SAME_DEPT_USER, "audit_same_dept_user", CURRENT_DEPT));
        sysUserService.save(activeUser(OTHER_DEPT_USER, "audit_other_dept_user", OTHER_DEPT));
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(CURRENT_USER)
                .userName("audit_current_user")
                .deptId(CURRENT_DEPT)
                .deptIds(Set.of(CURRENT_DEPT))
                .dataScopes(List.of(DataScopeType.DEPT))
                .superAdmin(false)
                .build());

        Optional<Set<Long>> visibleUserIds = auditVisibilitySupport.visibleUserIds();

        assertThat(visibleUserIds).isPresent();
        assertThat(visibleUserIds.orElseThrow())
                .contains(CURRENT_USER, SAME_DEPT_USER)
                .doesNotContain(OTHER_DEPT_USER);
    }

    private SysUser activeUser(long userId, String userName, long deptId) {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName(userName);
        user.setNickName(userName);
        user.setDeptId(deptId);
        user.setEnable(1);
        user.setDeleted(0);
        user.setVersion(0);
        user.setPermissionVersion(1L);
        user.setEmployeeNo("AUD" + userId);
        return user;
    }
}
