package com.laker.admin.module.system.dto.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CaptchaResponse {
    private String captchaId;
    private String imageBase64;
    private long expiresIn;
}
