package com.laker.admin.infrastructure.web.waf;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * web防火墙
 */
@Slf4j
public class WafFilter implements Filter {
    private final List<String> excludes;
    private final boolean xssEnabled;
    private final boolean sqlEnabled;
    private final EasyNextAdminConfig.Web.SecurityHeaders securityHeaders;

    public WafFilter(String excludes, boolean xssEnabled, boolean sqlEnabled) {
        this(excludes, xssEnabled, sqlEnabled, new EasyNextAdminConfig.Web.SecurityHeaders());
    }

    public WafFilter(String excludes, boolean xssEnabled, boolean sqlEnabled,
                     EasyNextAdminConfig.Web.SecurityHeaders securityHeaders) {
        this.excludes = parseExcludes(excludes);
        this.xssEnabled = xssEnabled;
        this.sqlEnabled = sqlEnabled;
        this.securityHeaders = securityHeaders == null ? new EasyNextAdminConfig.Web.SecurityHeaders() : securityHeaders;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException,
            ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        // 1.设置安全响应头
        setSecurityHeaders(res);
        // 2.处理xss请求
        if (handle(req)) {
            chain.doFilter(new WafRequestWrapper(req, xssEnabled, sqlEnabled), response);
            return;
        }
        chain.doFilter(request, response);
    }

    private void setSecurityHeaders(HttpServletResponse response) {
        // 是否可以在iframe显示视图： DENY=不可以 | SAMEORIGIN=同域下可以 | ALLOW-FROM uri=指定域名下可以
        // 避免被iframe 点击劫持 造成ClickJacking
        setHeaderIfText(response, "X-Frame-Options", securityHeaders.getFrameOptions());
        // 禁用浏览器内容嗅探
        // 防止浏览器嗅探文件类型MIME 类型嗅探（MIME sniffing）
        // 防止攻击者上传恶意脚本文件（如 .jpg 文件中隐藏 JavaScript 代码），然后利用浏览器自动嗅探并执行该脚本。
        response.setHeader("X-Content-Type-Options", "nosniff");
        // 是否启用浏览器默认XSS防护： 0=禁用 | 1=启用 | 1; mode=block 启用, 并在检查到XSS攻击时，停止渲染页面
        // 防止XSS攻击 如果 URL 或输入框中有恶意 JavaScript 代码注入，浏览器会阻止页面加载，避免 XSS 攻击生效。
        // 现代浏览器已经默认移除了 X-XSS-Protection，推荐使用 Content Security Policy (CSP) 进行更强的防护。
        response.setHeader("X-XSS-Protection", "1; mode=block");
        // 控制哪些资源可以被加载，防止 XSS 和数据注入攻击。
        // 要所有内容均来自站点的同一个源（不包括其子域名）
        // 有助于检测和减轻某些类型的攻击 例如跨站脚本（XSS）数据注入攻击
        // 只允许加载同源（self）的资源（如 JS、CSS、图片等）。
        setHeaderIfText(response, "Content-Security-Policy", securityHeaders.getContentSecurityPolicy());
        // 强制浏览器在指定时间内（max-age）仅使用 HTTPS 访问网站，防止中间人攻击（MITM）。
        if (securityHeaders.isHstsEnabled()) {
            setHeaderIfText(response, "Strict-Transport-Security", securityHeaders.getHsts());
        }
        setHeaderIfText(response, "Referrer-Policy", securityHeaders.getReferrerPolicy());
        setHeaderIfText(response, "Permissions-Policy", securityHeaders.getPermissionsPolicy());
        // 防止泄露服务器信息，减少攻击者的侦察范围。
        response.setHeader("Server", "");

    }

    private void setHeaderIfText(HttpServletResponse response, String name, String value) {
        if (StringUtils.hasText(value)) {
            response.setHeader(name, value);
        }
    }

    @Override
    public void destroy() {
        log.warn("WafFilter destroy.");
    }


    private boolean handle(HttpServletRequest request) {
        if (!xssEnabled && !sqlEnabled) {
            return false;
        }

        if (excludes == null || excludes.isEmpty()) {
            return true;
        }
        String url = request.getServletPath();
        for (String pattern : excludes) {
            Pattern p = Pattern.compile("^" + pattern);
            if (p.matcher(url).find()) {
                return false;
            }
        }
        return true;
    }

    private List<String> parseExcludes(String excludesUrls) {
        if (!StringUtils.hasText(excludesUrls)) {
            return List.of();
        }
        return Arrays.stream(excludesUrls.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }
}
