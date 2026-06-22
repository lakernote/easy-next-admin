package com.laker.admin.module.system.service.profile;

import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.security.service.EasyAuthService;
import com.laker.admin.infrastructure.security.store.AuthSessionStore;
import com.laker.admin.infrastructure.security.support.EasyPasswordHasher;
import com.laker.admin.module.audit.service.IAuditLoginLogService;
import com.laker.admin.module.audit.service.SensitiveAuditService;
import com.laker.admin.module.system.dto.AuthProfileDto;
import com.laker.admin.module.system.dto.auth.AuthUserProfile;
import com.laker.admin.module.system.entity.SysFile;
import com.laker.admin.module.system.service.ISysUserService;
import com.laker.admin.module.system.service.storage.EasyStorageFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileSecurityServiceTest {

    @AfterEach
    void tearDown() {
        EasySecurityContext.clear();
    }

    @Test
    void uploadAvatarShouldInferFilenameExtensionWhenOriginalFilenameMissing() {
        EasyAuthService authService = mock(EasyAuthService.class);
        ISysUserService userService = mock(ISysUserService.class);
        EasyStorageFacade storageFacade = mock(EasyStorageFacade.class);
        ProfileSecurityService service = new ProfileSecurityService(
                authService,
                userService,
                mock(IAuditLoginLogService.class),
                mock(AuthSessionStore.class),
                mock(EasyPasswordHasher.class),
                mock(SensitiveAuditService.class),
                storageFacade);
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "", "image/jpeg", jpeg);
        SysFile stored = new SysFile();
        stored.setStorageType("LOCAL");
        stored.setStorageName("avatar.jpg");
        when(storageFacade.store(any(InputStream.class), eq((long) jpeg.length), eq("image/jpeg"), eq("avatar.jpg")))
                .thenReturn(stored);
        AuthUserProfile profile = AuthUserProfile.builder()
                .userId(1L)
                .avatar("/storage/avatar.jpg")
                .build();
        when(authService.getCurrentProfile()).thenReturn(AuthProfileDto.builder().user(profile).build());
        EasySecurityContext.setPrincipal(AuthPrincipal.builder().userId(1L).build());

        AuthUserProfile result = service.uploadAvatar(file);

        assertThat(result).isSameAs(profile);
        verify(userService).updateCurrentAvatar("/storage/avatar.jpg");
    }
}
