package com.laker.admin.infrastructure.web.filter;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.util.CollectionUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @see org.springframework.web.filter.CorsFilter
 */
@Slf4j
public class EasyCorsFilter extends OncePerRequestFilter {
    private final CorsConfiguration corsConfiguration;

    public EasyCorsFilter(EasyNextAdminConfig.Web.Cors cors) {
        this.corsConfiguration = buildCorsConfiguration(cors);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws IOException, ServletException {
        boolean isValid = this.processRequest(request, response);
        if (!isValid || CorsUtils.isPreFlightRequest(request)) {
            return;
        }
        filterChain.doFilter(request, response);
    }


    private boolean processRequest(HttpServletRequest request,
                                   HttpServletResponse response) throws IOException {

        Collection<String> varyHeaders = response.getHeaders(HttpHeaders.VARY);
        if (!varyHeaders.contains(HttpHeaders.ORIGIN)) {
            response.addHeader(HttpHeaders.VARY, HttpHeaders.ORIGIN);
        }
        if (!varyHeaders.contains(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD)) {
            response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
        }
        if (!varyHeaders.contains(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS)) {
            response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        }

        if (!CorsUtils.isCorsRequest(request)) {
            return true;
        }

        if (response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN) != null) {
            logger.trace("Skip: response already contains \"Access-Control-Allow-Origin\"");
            return true;
        }

        boolean preFlightRequest = CorsUtils.isPreFlightRequest(request);

        return handleInternal(new ServletServerHttpRequest(request), response, preFlightRequest);
    }

    protected boolean handleInternal(ServerHttpRequest request,
                                     HttpServletResponse response,
                                     boolean preFlightRequest) throws IOException {

        String requestOrigin = request.getHeaders().getOrigin();
        String allowedOrigin = corsConfiguration.checkOrigin(requestOrigin);
        if (allowedOrigin == null) {
            return reject(response);
        }

        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, allowedOrigin);
        setCommaDelimitedHeader(response, HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                corsConfiguration.getExposedHeaders());
        if (Boolean.TRUE.equals(corsConfiguration.getAllowCredentials())) {
            response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, Boolean.TRUE.toString());
        }
        if (preFlightRequest) {
            HttpMethod requestMethod = getMethodToUse(request, true);
            List<HttpMethod> allowMethods = corsConfiguration.checkHttpMethod(requestMethod);
            if (allowMethods == null) {
                return reject(response);
            }

            List<String> requestHeaders = getHeadersToUse(request, true);
            List<String> allowHeaders = corsConfiguration.checkHeaders(requestHeaders);
            if (allowHeaders == null) {
                return reject(response);
            }

            setCommaDelimitedHeader(response, HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                    allowMethods.stream().map(HttpMethod::name).toList());
            setCommaDelimitedHeader(response, HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, allowHeaders);
            if (corsConfiguration.getMaxAge() != null) {
                response.setHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, String.valueOf(corsConfiguration.getMaxAge()));
            }
            response.flushBuffer();
        }
        return true;
    }

    private boolean reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.flushBuffer();
        return false;
    }

    private HttpMethod getMethodToUse(ServerHttpRequest request, boolean isPreFlight) {
        return (isPreFlight ? request.getHeaders().getAccessControlRequestMethod() : request.getMethod());
    }

    private List<String> getHeadersToUse(ServerHttpRequest request, boolean isPreFlight) {
        HttpHeaders headers = request.getHeaders();
        return (isPreFlight ? headers.getAccessControlRequestHeaders() : new ArrayList<>(headers.keySet()));
    }

    private CorsConfiguration buildCorsConfiguration(EasyNextAdminConfig.Web.Cors cors) {
        EasyNextAdminConfig.Web.Cors source = cors == null ? new EasyNextAdminConfig.Web.Cors() : cors;
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(emptyToNull(source.getAllowedOrigins()));
        configuration.setAllowedOriginPatterns(emptyToNull(source.getAllowedOriginPatterns()));
        configuration.setAllowedMethods(source.getAllowedMethods());
        configuration.setAllowedHeaders(source.getAllowedHeaders());
        configuration.setExposedHeaders(source.getExposedHeaders());
        configuration.setAllowCredentials(source.isAllowCredentials());
        configuration.setMaxAge(source.getMaxAgeSeconds());
        return configuration;
    }

    private void setCommaDelimitedHeader(HttpServletResponse response, String headerName, List<String> values) {
        if (!CollectionUtils.isEmpty(values)) {
            response.setHeader(headerName, String.join(", ", values));
        }
    }

    private <T> List<T> emptyToNull(List<T> values) {
        return CollectionUtils.isEmpty(values) ? null : values;
    }
}
