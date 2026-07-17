package com.laker.admin.infrastructure.web.error;

import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.common.model.Response;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class EasyDefaultUncaughtErrorController implements ErrorController {

    @RequestMapping("/error")
    public Response<Void> error(HttpServletRequest request, HttpServletResponse response) {
        HttpStatus statusCode = getHttpStatusCode(request);
        String uri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        log.error("code:{},msg:{},uri:{}", statusCode.value(), statusCode.isError(), uri);
        ErrorCode errorCode = ErrorCode.fromHttpStatus(statusCode.value());
        response.setStatus(errorCode.getHttpStatus().value());
        return Response.error(errorCode, statusCode.getReasonPhrase());
    }

    private HttpStatus getHttpStatusCode(HttpServletRequest request) {
        try {
            Integer statusCode = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
            if (statusCode == null) {
                return HttpStatus.INTERNAL_SERVER_ERROR;
            }
            return HttpStatus.valueOf(statusCode);
        } catch (Exception e) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
}
