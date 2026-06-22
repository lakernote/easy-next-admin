package com.laker.admin.module.system.dto.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileUpdateRequest {
    @NotBlank(message = "姓名/昵称不能为空")
    @Size(min = 2, max = 30, message = "姓名/昵称长度应为 2-30 个字符")
    private String nickName;
    private String realName;
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    @Email(message = "邮箱格式不正确")
    private String email;
    private String avatar;
}
