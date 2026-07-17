package com.laker.admin.module.system.dto.user;

import lombok.Data;

@Data
public abstract class BaseUser {
    private Long userId;

    private String nickName;

    private String phone;

    private Integer enable;

    private String email;

    private String avatar;

    private String employeeNo;

    private String realName;

    private String positionName;
}
