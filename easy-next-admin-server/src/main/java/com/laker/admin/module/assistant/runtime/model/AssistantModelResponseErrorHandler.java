package com.laker.admin.module.assistant.runtime.model;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/** 将 OpenAI-compatible HTTP 状态转换为稳定、可判定是否重试的模型异常。 */
public final class AssistantModelResponseErrorHandler implements ResponseErrorHandler {
    private static final int MAX_ERROR_BODY_LENGTH = 2_000;

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        return response.getStatusCode().isError();
    }

    @Override
    public void handleError(URI url,
                            HttpMethod method,
                            ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        String body = limit(StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8));
        String message = status.value() + (body.isBlank() ? "" : " - " + body);
        if (status.value() == 401 || status.value() == 403) {
            throw ModelCallException.authentication(message);
        }
        if (status.value() == 429) {
            if (isQuotaExceeded(body)) {
                throw ModelCallException.clientError(message);
            }
            throw ModelCallException.rateLimited(message, retryAfter(response));
        }
        if (status.value() == 408 || status.value() == 425 || status.is5xxServerError()) {
            throw ModelCallException.serverError(message);
        }
        throw ModelCallException.clientError(message);
    }

    private static boolean isQuotaExceeded(String body) {
        String normalized = body == null ? "" : body.toLowerCase(Locale.ROOT);
        return normalized.contains("insufficient_quota")
                || normalized.contains("exceeded your current quota");
    }

    private static Duration retryAfter(ClientHttpResponse response) {
        String value = response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return Duration.ofMillis(500);
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return seconds <= 0 ? Duration.ofMillis(500) : Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            return Duration.ofMillis(500);
        }
    }

    private static String limit(String value) {
        if (value == null || value.length() <= MAX_ERROR_BODY_LENGTH) {
            return value == null ? "" : value;
        }
        return value.substring(0, MAX_ERROR_BODY_LENGTH) + "…";
    }
}
