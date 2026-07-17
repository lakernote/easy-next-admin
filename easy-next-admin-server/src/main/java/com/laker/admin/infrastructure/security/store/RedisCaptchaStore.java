package com.laker.admin.infrastructure.security.store;

import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.json.EasyJsonException;
import com.laker.admin.infrastructure.security.model.CaptchaChallenge;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

public class RedisCaptchaStore implements CaptchaStore {
    private static final String CAPTCHA_PREFIX = "easy:auth:captcha:";
    private static final String REQUIRED_PREFIX = "easy:auth:captcha:required:";

    private final StringRedisTemplate redisTemplate;
    private final EasyJsonCodec jsonCodec;

    public RedisCaptchaStore(StringRedisTemplate redisTemplate, EasyJsonCodec jsonCodec) {
        this.redisTemplate = redisTemplate;
        this.jsonCodec = jsonCodec;
    }

    @Override
    public void save(CaptchaChallenge challenge, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(CAPTCHA_PREFIX + challenge.getCaptchaId(), jsonCodec.toJson(challenge), ttl);
        } catch (EasyJsonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "验证码存储失败");
        }
    }

    @Override
    public Optional<CaptchaChallenge> consume(String captchaId) {
        String key = CAPTCHA_PREFIX + captchaId;
        String payload = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);
        if (payload == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(jsonCodec.fromJson(payload, CaptchaChallenge.class));
        } catch (EasyJsonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "验证码读取失败");
        }
    }

    @Override
    public void markRequired(String riskKey, Duration ttl) {
        redisTemplate.opsForValue().set(REQUIRED_PREFIX + riskKey, "1", ttl);
    }

    @Override
    public boolean isRequired(String riskKey) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REQUIRED_PREFIX + riskKey));
    }

    @Override
    public void clearRequired(String riskKey) {
        redisTemplate.delete(REQUIRED_PREFIX + riskKey);
    }
}
