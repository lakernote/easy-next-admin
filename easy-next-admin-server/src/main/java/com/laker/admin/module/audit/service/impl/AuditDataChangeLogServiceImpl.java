package com.laker.admin.module.audit.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.module.audit.entity.AuditDataChangeLog;
import com.laker.admin.module.audit.mapper.AuditDataChangeLogMapper;
import com.laker.admin.module.audit.service.IAuditDataChangeLogService;
import org.springframework.stereotype.Service;

@Service
public class AuditDataChangeLogServiceImpl
        extends ServiceImpl<AuditDataChangeLogMapper, AuditDataChangeLog>
        implements IAuditDataChangeLogService {
}
