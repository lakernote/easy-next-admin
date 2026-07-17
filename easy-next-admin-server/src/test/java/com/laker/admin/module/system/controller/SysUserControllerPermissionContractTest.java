package com.laker.admin.module.system.controller;

import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SysUserControllerPermissionContractTest {

    @Test
    void userCreateEditAndResetPasswordUseSeparatePermissionCodes() throws Exception {
        Method create = SysUserController.class.getDeclaredMethod("createUser", com.laker.admin.module.system.dto.user.UserRequest.class);
        Method update = Arrays.stream(SysUserController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("updateUser"))
                .findFirst()
                .orElseThrow();
        Method reset = SysUserController.class.getDeclaredMethod("resetPwd", Long.class);

        assertThat(create.getAnnotation(PostMapping.class)).isNotNull();
        assertThat(permissionCodes(create)).containsExactly("sys:user:add");
        assertThat(update.getAnnotation(PutMapping.class)).isNotNull();
        assertThat(permissionCodes(update)).containsExactly("sys:user:edit");
        assertThat(permissionCodes(reset)).containsExactly("sys:user:reset-password");
    }

    private static String[] permissionCodes(Method method) {
        EasyPermission permission = method.getAnnotation(EasyPermission.class);
        assertThat(permission).isNotNull();
        return permission.value();
    }
}
