package com.laker.admin.module.system.controller;

import com.laker.admin.infrastructure.security.service.EasyAuthService;
import com.laker.admin.infrastructure.security.service.EasyCaptchaService;
import com.laker.admin.module.system.dto.auth.DemoAccountResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AuthControllerTest {

    @Test
    void shouldReturnDemoAccountsForLocalProfile() {
        AuthController controller = authController("local");

        List<DemoAccountResponse> accounts = controller.listDemoAccounts().getData();

        assertThat(accounts)
                .extracting(DemoAccountResponse::username)
                .containsExactly("admin", "manager", "staff", "auditor");
    }

    @Test
    void shouldHideDemoAccountsForProductionProfile() {
        AuthController controller = authController("prod");

        assertThat(controller.listDemoAccounts().getData()).isEmpty();
    }

    private static AuthController authController(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return new AuthController(
                mock(EasyAuthService.class),
                mock(EasyCaptchaService.class),
                environment
        );
    }
}
