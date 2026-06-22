package com.laker.admin.infrastructure.audit;

import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.masking.EasySensitiveDataMasker;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.infrastructure.web.context.EasyRequestContext;
import com.laker.admin.module.audit.entity.AuditDataChangeLog;
import com.laker.admin.module.audit.entity.AuditErrorLog;
import com.laker.admin.module.audit.entity.AuditLoginLog;
import com.laker.admin.module.audit.entity.AuditOperationLog;
import com.laker.admin.module.audit.mapper.AuditDataChangeLogMapper;
import com.laker.admin.module.audit.mapper.AuditErrorLogMapper;
import com.laker.admin.module.audit.mapper.AuditLoginLogMapper;
import com.laker.admin.module.audit.mapper.AuditOperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

@Component
@Slf4j
public class AuditLogCollector {
    private static final int MAX_SHORT_TEXT = 255;
    private static final int MAX_MESSAGE = 1000;
    private static final int MAX_STACK_TRACE = 12000;
    private static final int MAX_USER_AGENT = 500;

    private final AuditLoginLogMapper loginLogMapper;
    private final AuditOperationLogMapper operationLogMapper;
    private final AuditDataChangeLogMapper dataChangeLogMapper;
    private final AuditErrorLogMapper errorLogMapper;
    private final EasySensitiveDataMasker masker;

    public AuditLogCollector(AuditLoginLogMapper loginLogMapper,
                             AuditOperationLogMapper operationLogMapper,
                             AuditDataChangeLogMapper dataChangeLogMapper,
                             AuditErrorLogMapper errorLogMapper,
                             EasySensitiveDataMasker masker) {
        this.loginLogMapper = loginLogMapper;
        this.operationLogMapper = operationLogMapper;
        this.dataChangeLogMapper = dataChangeLogMapper;
        this.errorLogMapper = errorLogMapper;
        this.masker = masker;
    }

    public void recordLogin(Long userId,
                            String userName,
                            boolean success,
                            String failReason,
                            HttpServletRequest request) {
        AuditLoginLog loginLog = new AuditLoginLog();
        loginLog.setUserId(userId);
        loginLog.setUserName(masker.truncate(userName, 80));
        loginLog.setLoginResult(success ? "SUCCESS" : "FAIL");
        loginLog.setFailReason(masker.truncate(masker.maskText(failReason), MAX_SHORT_TEXT));
        loginLog.setIp(request == null ? EasyRequestContext.currentRemoteIp() : EasyRequestContext.remoteIp(request));
        loginLog.setUserAgent(masker.truncate(request == null ? null : request.getHeader("User-Agent"), MAX_USER_AGENT));
        loginLog.setClientType("web");
        loginLog.setTraceId(EasyTraceIdContext.getOrCreateTraceId());
        loginLog.setLoginTime(LocalDateTime.now());
        insertSafely("login", () -> loginLogMapper.insert(loginLog));
    }

    public void recordOperation(AuditOperationLog operationLog) {
        if (operationLog == null) {
            return;
        }
        fillOperator(operationLog);
        operationLog.setTraceId(defaultText(operationLog.getTraceId(), EasyTraceIdContext.getOrCreateTraceId()));
        operationLog.setRequestMethod(defaultText(operationLog.getRequestMethod(), EasyRequestContext.currentRequestMethod()));
        operationLog.setRequestUri(masker.truncate(masker.maskUri(defaultText(operationLog.getRequestUri(), EasyRequestContext.currentRequestUri())), MAX_SHORT_TEXT));
        operationLog.setRequestParams(masker.truncate(masker.sanitizeJsonText(operationLog.getRequestParams()), EasySensitiveDataMasker.DEFAULT_TEXT_LIMIT));
        operationLog.setErrorMessage(masker.truncate(masker.maskText(operationLog.getErrorMessage()), MAX_MESSAGE));
        operationLog.setIp(defaultText(operationLog.getIp(), EasyRequestContext.currentRemoteIp()));
        operationLog.setUserAgent(masker.truncate(defaultText(operationLog.getUserAgent(), currentUserAgent()), MAX_USER_AGENT));
        operationLog.setModule(masker.truncate(operationLog.getModule(), 80));
        operationLog.setAction(masker.truncate(operationLog.getAction(), 80));
        operationLog.setResponseStatus(masker.truncate(operationLog.getResponseStatus(), 20));
        if (operationLog.getCreatedAt() == null) {
            operationLog.setCreatedAt(LocalDateTime.now());
        }
        insertSafely("operation", () -> operationLogMapper.insert(operationLog));
    }

    public void recordDataChange(AuditDataChangeLog dataChangeLog) {
        if (dataChangeLog == null) {
            return;
        }
        dataChangeLog.setTraceId(defaultText(dataChangeLog.getTraceId(), EasyTraceIdContext.getOrCreateTraceId()));
        if (dataChangeLog.getOperatorId() == null) {
            dataChangeLog.setOperatorId(EasySecurityContext.getUserId());
        }
        dataChangeLog.setBizType(masker.truncate(dataChangeLog.getBizType(), 80));
        dataChangeLog.setBizId(masker.truncate(dataChangeLog.getBizId(), 80));
        dataChangeLog.setTableName(masker.truncate(dataChangeLog.getTableName(), 80));
        dataChangeLog.setChangeType(masker.truncate(defaultText(dataChangeLog.getChangeType(), "UPDATE"), 20));
        dataChangeLog.setBeforeJson(masker.sanitizeJsonText(dataChangeLog.getBeforeJson()));
        dataChangeLog.setAfterJson(masker.sanitizeJsonText(dataChangeLog.getAfterJson()));
        if (!StringUtils.hasText(dataChangeLog.getChangedFields())) {
            dataChangeLog.setChangedFields(masker.changedFields(dataChangeLog.getBeforeJson(), dataChangeLog.getAfterJson()));
        }
        dataChangeLog.setChangedFields(masker.truncate(dataChangeLog.getChangedFields(), MAX_MESSAGE));
        if (dataChangeLog.getCreatedAt() == null) {
            dataChangeLog.setCreatedAt(LocalDateTime.now());
        }
        insertSafely("data-change", () -> dataChangeLogMapper.insert(dataChangeLog));
    }

    public void recordDataChange(String bizType,
                                 String bizId,
                                 String tableName,
                                 String changeType,
                                 Object before,
                                 Object after) {
        AuditDataChangeLog dataChangeLog = new AuditDataChangeLog();
        dataChangeLog.setBizType(bizType);
        dataChangeLog.setBizId(bizId);
        dataChangeLog.setTableName(tableName);
        dataChangeLog.setChangeType(changeType);
        dataChangeLog.setBeforeJson(before == null ? null : masker.toSanitizedJson(before));
        dataChangeLog.setAfterJson(after == null ? null : masker.toSanitizedJson(after));
        recordDataChange(dataChangeLog);
    }

    public void recordError(Throwable throwable) {
        AuditErrorLog errorLog = new AuditErrorLog();
        errorLog.setErrorType(throwable == null ? null : throwable.getClass().getName());
        errorLog.setErrorMessage(throwable == null ? null : throwable.getMessage());
        errorLog.setStackTrace(stackTrace(throwable));
        recordError(errorLog);
    }

    public void recordError(AuditErrorLog errorLog) {
        if (errorLog == null) {
            return;
        }
        errorLog.setTraceId(defaultText(errorLog.getTraceId(), EasyTraceIdContext.getOrCreateTraceId()));
        errorLog.setRequestUri(masker.truncate(masker.maskUri(defaultText(errorLog.getRequestUri(), EasyRequestContext.currentRequestUri())), MAX_SHORT_TEXT));
        errorLog.setRequestMethod(defaultText(errorLog.getRequestMethod(), EasyRequestContext.currentRequestMethod()));
        errorLog.setErrorType(masker.truncate(errorLog.getErrorType(), MAX_SHORT_TEXT));
        errorLog.setErrorMessage(masker.truncate(masker.maskText(errorLog.getErrorMessage()), MAX_MESSAGE));
        errorLog.setStackTrace(masker.truncate(masker.maskText(errorLog.getStackTrace()), MAX_STACK_TRACE));
        if (errorLog.getOperatorId() == null) {
            errorLog.setOperatorId(EasySecurityContext.getUserId());
        }
        if (errorLog.getCreatedAt() == null) {
            errorLog.setCreatedAt(LocalDateTime.now());
        }
        insertSafely("error", () -> errorLogMapper.insert(errorLog));
    }

    private void fillOperator(AuditOperationLog operationLog) {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        if (operationLog.getOperatorId() == null) {
            operationLog.setOperatorId(principal == null ? null : principal.getUserId());
        }
        if (!StringUtils.hasText(operationLog.getOperatorName())) {
            operationLog.setOperatorName(masker.truncate(principal == null ? null : principal.getUserName(), 80));
        }
    }

    private String stackTrace(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return masker.truncate(writer.toString(), MAX_STACK_TRACE);
    }

    private String currentUserAgent() {
        return EasyRequestContext.currentRequest()
                .map(request -> request.getHeader("User-Agent"))
                .orElse(null);
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void insertSafely(String type, Runnable insertAction) {
        try {
            insertAction.run();
        } catch (Exception e) {
            log.warn("save {} audit log failed: {}", type, e.getMessage());
        }
    }
}
