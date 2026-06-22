package com.laker.admin.module.audit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.audit.entity.AuditOperationLog;
import com.laker.admin.module.audit.mapper.AuditOperationLogMapper;
import com.laker.admin.module.audit.service.IAuditOperationLogService;
import org.springframework.stereotype.Service;

@Service
public class AuditOperationLogServiceImpl
        extends ServiceImpl<AuditOperationLogMapper, AuditOperationLog>
        implements IAuditOperationLogService {
}
