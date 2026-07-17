package com.laker.admin.common.util;

import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.persistence.mybatis.UserInfoAndPermissions;
import lombok.extern.slf4j.Slf4j;

/**
 * @author easynext
 */
@Slf4j
public class EasyNextAdminSecurityUtils {
    private EasyNextAdminSecurityUtils() {
        // do nothing
    }

    public static UserInfoAndPermissions getCurrentUserInfo() {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        if (principal == null) {
            return null;
        }
        return UserInfoAndPermissions.builder()
                .userId(principal.getUserId())
                .userName(principal.getUserName())
                .nickName(principal.getNickName())
                .deptId(principal.getDeptId())
                .deptIds(principal.getDeptIds())
                .build();
    }

}
