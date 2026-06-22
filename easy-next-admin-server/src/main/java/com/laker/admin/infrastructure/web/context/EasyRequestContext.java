package com.laker.admin.infrastructure.web.context;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 当前 HTTP 请求上下文门面。
 *
 * <p>业务和基础设施代码从这里读取 IP、URI、Header、UserAgent 等信息，不直接依赖
 * RequestContextHolder。没有请求上下文时返回安全默认值，避免异步线程、异常处理链路出现空指针。</p>
 */
@Slf4j
public final class EasyRequestContext {
    private static final String EMPTY_VALUE = "-";

    private EasyRequestContext() {
    }

    public static Optional<HttpServletRequest> currentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return Optional.empty();
        }
        return Optional.of(servletRequestAttributes.getRequest());
    }

    public static HttpServletRequest requiredRequest() {
        return currentRequest().orElseThrow(() -> new IllegalStateException("requestAttributes is null"));
    }

    public static String currentRequestUri() {
        return currentRequest().map(EasyRequestContext::requestUri).orElse(EMPTY_VALUE);
    }

    public static String currentRequestMethod() {
        return currentRequest().map(HttpServletRequest::getMethod).orElse(EMPTY_VALUE);
    }

    public static String requestUri(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (!StringUtils.hasText(queryString)) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + queryString;
    }

    public static String currentUserAgentSummary() {
        return currentRequest()
                .map(request -> userAgentSummary(request.getHeader("User-Agent")))
                .orElse("Unknown.Unknown");
    }

    public static String currentRemoteIp() {
        return currentRequest().map(EasyRequestContext::remoteIp).orElse(EMPTY_VALUE);
    }

    public static String remoteIp(HttpServletRequest request) {
        if (request == null) {
            return EMPTY_VALUE;
        }
        final String unknown = "unknown";
        String ip = request.getHeader("x-forwarded-for");
        if (!StringUtils.hasText(ip) || unknown.equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (!StringUtils.hasText(ip) || unknown.equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Forwarded-For");
        }
        if (!StringUtils.hasText(ip) || unknown.equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (!StringUtils.hasText(ip) || unknown.equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip) || unknown.equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (!StringUtils.hasText(ip) || unknown.equalsIgnoreCase(ip)) {
            return EMPTY_VALUE;
        }
        int index = ip.indexOf(',');
        if (index != -1) {
            ip = ip.substring(0, index);
        }
        return "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : ip.trim();
    }

    public static Map<String, String> currentHeaders() {
        return currentRequest().map(EasyRequestContext::headers).orElseGet(Map::of);
    }

    public static Map<String, String> headers(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>(32);
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String key = headerNames.nextElement();
            map.put(key, request.getHeader(key));
        }
        return map;
    }

    public static String currentBody() {
        return currentRequest().map(EasyRequestContext::body).orElse("");
    }

    public static String body(HttpServletRequest request) {
        try (InputStream inputStream = request.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("read http request body failed", e);
            return "";
        }
    }

    public static String currentRequestInfo() {
        String lineSeparator = System.lineSeparator();
        return "请求详情为：" + lineSeparator +
                "RemoteAddress: " + currentRemoteIp() + lineSeparator +
                "Method: " + currentRequest().map(HttpServletRequest::getMethod).orElse(EMPTY_VALUE) + lineSeparator +
                "URI: " + currentRequestUri() + lineSeparator +
                "Headers: " + String.join(lineSeparator + "         ", mapToList(currentHeaders())) + lineSeparator +
                "Body: " + currentBody() + lineSeparator;
    }

    private static List<String> mapToList(Map<String, String> parameters) {
        List<String> parametersList = new ArrayList<>();
        parameters.forEach((name, value) -> parametersList.add(name + "=" + value));
        return parametersList;
    }

    private static String userAgentSummary(String userAgent) {
        if (!StringUtils.hasText(userAgent)) {
            return "Unknown.Unknown";
        }
        String source = userAgent.toLowerCase(Locale.ROOT);
        return detectOperatingSystem(source) + "." + detectBrowser(source);
    }

    private static String detectOperatingSystem(String userAgent) {
        if (userAgent.contains("windows")) {
            return "Windows";
        }
        if (userAgent.contains("mac os x") || userAgent.contains("macintosh")) {
            return "macOS";
        }
        if (userAgent.contains("android")) {
            return "Android";
        }
        if (userAgent.contains("iphone") || userAgent.contains("ipad")) {
            return "iOS";
        }
        if (userAgent.contains("linux")) {
            return "Linux";
        }
        return "Unknown";
    }

    private static String detectBrowser(String userAgent) {
        if (userAgent.contains("edg/")) {
            return "Edge";
        }
        if (userAgent.contains("chrome/") || userAgent.contains("crios/")) {
            return "Chrome";
        }
        if (userAgent.contains("firefox/") || userAgent.contains("fxios/")) {
            return "Firefox";
        }
        if (userAgent.contains("safari/")) {
            return "Safari";
        }
        return "Unknown";
    }
}
