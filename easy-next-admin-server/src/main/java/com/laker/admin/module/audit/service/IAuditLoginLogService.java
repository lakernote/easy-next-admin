package com.laker.admin.module.audit.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.module.audit.entity.AuditLoginLog;
import jakarta.servlet.http.HttpServletRequest;

public interface IAuditLoginLogService extends IService<AuditLoginLog> {

    void recordLogin(Long userId,
                     String userName,
                     boolean success,
                     String failReason,
                     HttpServletRequest request);
}
