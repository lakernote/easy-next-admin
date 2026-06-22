package com.laker.admin.infrastructure.security.store;

import com.laker.admin.infrastructure.security.model.CaptchaChallenge;

import java.time.Duration;
import java.util.Optional;

/**
 * 验证码与登录风险标记存储端口。
 *
 * <p>验证码是一次性挑战，consume 后必须失效；riskKey 用于记录失败后需要验证码的用户名/IP 摘要。</p>
 */
public interface CaptchaStore {
    void save(CaptchaChallenge challenge, Duration ttl);

    Optional<CaptchaChallenge> consume(String captchaId);

    void markRequired(String riskKey, Duration ttl);

    boolean isRequired(String riskKey);

    void clearRequired(String riskKey);
}
