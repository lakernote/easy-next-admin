package com.laker.admin.infrastructure.security.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaChallenge {
    private String captchaId;
    private String codeHash;
    private LocalDateTime expireTime;
}
