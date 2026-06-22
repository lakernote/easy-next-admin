package com.laker.admin.infrastructure.observability.remote.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.infrastructure.observability.remote.entity.RemoteCallLog;
import com.laker.admin.infrastructure.observability.remote.mapper.RemoteCallLogMapper;
import org.springframework.stereotype.Service;

@Service
public class RemoteCallLogService extends ServiceImpl<RemoteCallLogMapper, RemoteCallLog> {
}
