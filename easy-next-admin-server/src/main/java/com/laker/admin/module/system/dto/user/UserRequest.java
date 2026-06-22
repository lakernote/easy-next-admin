package com.laker.admin.module.system.dto.user;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserRequest extends BaseUser {
    private Long userId;
    private String userName;
    private String password;
    private Long deptId;
    private Long managerUserId;
    private String roleIds;
}
