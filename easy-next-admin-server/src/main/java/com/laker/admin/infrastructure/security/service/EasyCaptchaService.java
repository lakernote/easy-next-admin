package com.laker.admin.infrastructure.security.service;

import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.security.exception.EasyAuthException;
import com.laker.admin.infrastructure.security.model.CaptchaChallenge;
import com.laker.admin.infrastructure.security.store.CaptchaStore;
import com.laker.admin.infrastructure.security.support.EasySecurityToken;
import com.laker.admin.infrastructure.web.context.EasyRequestContext;
import com.laker.admin.module.system.dto.auth.CaptchaResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 登录验证码服务。
 *
 * <p>验证码本身是短生命周期挑战，登录失败风险标记按“用户名 + IP”维度保存。
 * 这样默认登录路径保持简单，同时能在失败后增加一次人机校验。</p>
 */
@Service
@Slf4j
public class EasyCaptchaService {
    private static final String CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final Duration TTL = Duration.ofMinutes(2);
    private static final Duration REQUIRED_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final CaptchaStore captchaStore;

    public EasyCaptchaService(CaptchaStore captchaStore) {
        this.captchaStore = captchaStore;
    }

    public CaptchaResponse generate() {
        String code = randomCode();
        String captchaId = EasySecurityToken.randomToken();
        captchaStore.save(CaptchaChallenge.builder()
                .captchaId(captchaId)
                .codeHash(EasySecurityToken.sha256(code.toUpperCase(Locale.ROOT)))
                .expireTime(LocalDateTime.now().plus(TTL))
                .build(), TTL);
        return CaptchaResponse.builder()
                .captchaId(captchaId)
                .imageBase64("data:image/png;base64," + render(code))
                .expiresIn(TTL.toSeconds())
                .build();
    }

    public void validate(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new EasyAuthException(ErrorCode.AUTH_CAPTCHA_INVALID, "请输入验证码");
        }
        CaptchaChallenge challenge = captchaStore.consume(captchaId).orElse(null);
        if (challenge == null) {
            throw new EasyAuthException(ErrorCode.AUTH_CAPTCHA_INVALID, "验证码已失效，请重新获取");
        }
        if (challenge.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new EasyAuthException(ErrorCode.AUTH_CAPTCHA_INVALID, "验证码已过期，请重新获取");
        }
        String actualHash = EasySecurityToken.sha256(captchaCode.toUpperCase(Locale.ROOT));
        if (!challenge.getCodeHash().equals(actualHash)) {
            throw new EasyAuthException(ErrorCode.AUTH_CAPTCHA_INVALID, "验证码不正确");
        }
    }

    public boolean isRequired(String username, HttpServletRequest request) {
        return captchaStore.isRequired(riskKey(username, request));
    }

    public void markRequired(String username, HttpServletRequest request) {
        captchaStore.markRequired(riskKey(username, request), REQUIRED_TTL);
    }

    public void clearRequired(String username, HttpServletRequest request) {
        captchaStore.clearRequired(riskKey(username, request));
    }

    private String riskKey(String username, HttpServletRequest request) {
        // 风险标记只存摘要，避免把用户名/IP 原文写入 Redis key。
        String normalizedUsername = nullToDefault(username, "").trim().toLowerCase(Locale.ROOT);
        String ip = request == null ? EasyRequestContext.currentRemoteIp() : EasyRequestContext.remoteIp(request);
        return EasySecurityToken.sha256(normalizedUsername + "|" + nullToDefault(ip, "unknown"));
    }

    private String nullToDefault(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String randomCode() {
        StringBuilder builder = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            builder.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return builder.toString();
    }

    private String render(String code) {
        int width = 132;
        int height = 44;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(246, 249, 252));
        g.fillRoundRect(0, 0, width, height, 8, 8);
        for (int i = 0; i < 8; i++) {
            g.setColor(new Color(160 + RANDOM.nextInt(55), 175 + RANDOM.nextInt(45), 195 + RANDOM.nextInt(45)));
            int x1 = RANDOM.nextInt(width);
            int y1 = RANDOM.nextInt(height);
            int x2 = RANDOM.nextInt(width);
            int y2 = RANDOM.nextInt(height);
            g.drawLine(x1, y1, x2, y2);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
        for (int i = 0; i < code.length(); i++) {
            AffineTransform transform = g.getTransform();
            g.setColor(new Color(25 + RANDOM.nextInt(60), 70 + RANDOM.nextInt(80), 130 + RANDOM.nextInt(70)));
            int x = 18 + i * 27;
            int y = 30 + RANDOM.nextInt(5);
            g.rotate(Math.toRadians(RANDOM.nextInt(18) - 9), x, y);
            g.drawString(String.valueOf(code.charAt(i)), x, y);
            g.setTransform(transform);
        }
        g.dispose();
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (Exception e) {
            log.error("captcha render failed", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "验证码生成失败");
        }
    }

}
