package com.laker.admin.infrastructure.security.support;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * 平台自研密码哈希组件。
 *
 * <p>数据库只保存 BCrypt 哈希，不保存明文或可逆加密结果。这里不引入 Spring Security
 * 过滤链，只复用成熟的 BCrypt 算法实现，认证流程仍由 EasyNextAdmin 自己控制。</p>
 */
@Component
public class EasyPasswordHasher {
    private static final int BCRYPT_LOG_ROUNDS = 12;
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private final SecureRandom secureRandom = new SecureRandom();
    private final BCryptPasswordEncoder passwordEncoder;

    public EasyPasswordHasher() {
        this.passwordEncoder = new BCryptPasswordEncoder(BCryptVersion.$2A, BCRYPT_LOG_ROUNDS, secureRandom);
    }

    public String hash(String plainPassword) {
        validatePlainPassword(plainPassword);
        return passwordEncoder.encode(plainPassword);
    }

    public boolean matches(String plainPassword, String encodedPassword) {
        if (!StringUtils.hasText(plainPassword) || isLongerThanBCryptLimit(plainPassword) || !isBCryptHash(encodedPassword)) {
            return false;
        }
        try {
            return passwordEncoder.matches(plainPassword, encodedPassword);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private void validatePlainPassword(String plainPassword) {
        if (!StringUtils.hasText(plainPassword)) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (isLongerThanBCryptLimit(plainPassword)) {
            throw new IllegalArgumentException("BCrypt 密码长度不能超过 72 字节");
        }
    }

    private boolean isLongerThanBCryptLimit(String plainPassword) {
        return plainPassword.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES;
    }

    private boolean isBCryptHash(String encodedPassword) {
        return StringUtils.hasText(encodedPassword) && BCRYPT_PATTERN.matcher(encodedPassword).matches();
    }
}
