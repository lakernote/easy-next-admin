package com.laker.admin.module.audit.service.impl;

import com.laker.admin.module.audit.entity.AuditApiLog;
import com.laker.admin.module.audit.mapper.AuditApiLogMapper;
import com.laker.admin.module.audit.service.IAuditApiLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.infrastructure.security.masking.EasySensitiveDataMasker;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 日志 服务实现类
 * </p>
 *
 * @author easynext
 * @since 2021-08-16
 */
@Service
public class AuditApiLogServiceImpl extends ServiceImpl<AuditApiLogMapper, AuditApiLog> implements IAuditApiLogService {
    private final EasySensitiveDataMasker masker;

    public AuditApiLogServiceImpl(EasySensitiveDataMasker masker) {
        this.masker = masker;
    }

    @Override
    public boolean save(AuditApiLog entity) {
        if (entity != null) {
            entity.setUri(masker.maskUri(entity.getUri()));
            entity.setRequest(masker.sanitizeJsonText(entity.getRequest(), 500));
            entity.setResponse(masker.sanitizeJsonText(entity.getResponse(), 500));
        }
        return super.save(entity);
    }
}
