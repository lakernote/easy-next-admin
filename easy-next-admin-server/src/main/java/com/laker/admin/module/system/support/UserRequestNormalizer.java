package com.laker.admin.module.system.support;

import com.laker.admin.module.system.dto.user.UserRequest;

public final class UserRequestNormalizer {
    private UserRequestNormalizer() {
    }

    public static void normalize(UserRequest request) {
        if (request == null) {
            return;
        }
        request.setUserName(blankToNull(request.getUserName()));
        request.setPassword(blankToNull(request.getPassword()));
        request.setRoleIds(blankToNull(request.getRoleIds()));
        request.setNickName(blankToNull(request.getNickName()));
        request.setRealName(blankToNull(request.getRealName()));
        request.setEmployeeNo(blankToNull(request.getEmployeeNo()));
        request.setPositionName(blankToNull(request.getPositionName()));
        request.setPhone(blankToNull(request.getPhone()));
        request.setEmail(blankToNull(request.getEmail()));
        request.setAvatar(blankToNull(request.getAvatar()));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
