package com.laker.admin.module.audit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.infrastructure.audit.AuditLogCollector;
import com.laker.admin.module.audit.entity.AuditLoginLog;
import com.laker.admin.module.audit.mapper.AuditLoginLogMapper;
import com.laker.admin.module.audit.service.IAuditLoginLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class AuditLoginLogServiceImpl
        extends ServiceImpl<AuditLoginLogMapper, AuditLoginLog>
        implements IAuditLoginLogService {

    private final AuditLogCollector auditLogCollector;

    public AuditLoginLogServiceImpl(AuditLogCollector auditLogCollector) {
        this.auditLogCollector = auditLogCollector;
    }

    @Override
    public void recordLogin(Long userId,
                            String userName,
                            boolean success,
                            String failReason,
                            HttpServletRequest request) {
        auditLogCollector.recordLogin(userId, userName, success, failReason, request);
    }
}
