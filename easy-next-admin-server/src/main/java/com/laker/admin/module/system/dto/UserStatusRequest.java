package com.laker.admin.module.system.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户启停请求。
 */
@Data
public class UserStatusRequest {
    @NotNull(message = "用户不存在")
    private Long userId;
    @NotNull(message = "用户状态不能为空")
    private Integer enable;
}
