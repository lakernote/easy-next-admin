package com.laker.admin.module.audit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.audit.entity.AuditErrorLog;
import com.laker.admin.module.audit.mapper.AuditErrorLogMapper;
import com.laker.admin.module.audit.service.IAuditErrorLogService;
import org.springframework.stereotype.Service;

@Service
public class AuditErrorLogServiceImpl
        extends ServiceImpl<AuditErrorLogMapper, AuditErrorLog>
        implements IAuditErrorLogService {
}
