package com.laker.admin.module.schedule.core;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.infrastructure.id.EasyIdGenerator;
import com.laker.admin.infrastructure.security.datascope.context.EasyDataScopeContext;
import com.laker.admin.module.schedule.entity.ScheduleInstance;
import com.laker.admin.module.schedule.service.IScheduleInstanceService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 当前应用进程在调度模块中的运行身份。
 */
@Component
@ConditionalOnProperty(prefix = "easy.features", name = "scheduler", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ScheduleInstanceRegistry {
    private static final String ONLINE = "ONLINE";

    private final IScheduleInstanceService scheduleInstanceService;
    private final String appName;
    private final String hostName;
    private final String ipAddress;
    private final String processId;
    private final LocalDateTime startedAt = LocalDateTime.now();
    @Getter
    private final String instanceId;

    public ScheduleInstanceRegistry(IScheduleInstanceService scheduleInstanceService,
                                    @Value("${spring.application.name:easy-next-admin}") String appName) {
        this.scheduleInstanceService = scheduleInstanceService;
        this.appName = appName;
        HostInfo hostInfo = resolveHostInfo();
        this.hostName = hostInfo.hostName();
        this.ipAddress = hostInfo.ipAddress();
        this.processId = ManagementFactory.getRuntimeMXBean().getName();
        this.instanceId = "%s:%s:%s".formatted(appName, hostInfo.hostName(), EasyIdGenerator.uuid32().substring(0, 8));
    }

    public void heartbeat() {
        ScheduleInstance instance = new ScheduleInstance();
        instance.setInstanceId(instanceId);
        instance.setAppName(appName);
        instance.setHostName(hostName);
        instance.setIpAddress(ipAddress);
        instance.setProcessId(processId);
        instance.setStatus(ONLINE);
        instance.setStartedAt(startedAt);
        instance.setHeartbeatAt(LocalDateTime.now());
        EasyDataScopeContext.ignore(() -> scheduleInstanceService.saveOrUpdate(instance));
    }

    public List<ScheduleInstance> listInstances() {
        return EasyDataScopeContext.ignore(() -> scheduleInstanceService.list(
                Wrappers.<ScheduleInstance>lambdaQuery().orderByDesc(ScheduleInstance::getHeartbeatAt)));
    }

    private HostInfo resolveHostInfo() {
        try {
            InetAddress address = InetAddress.getLocalHost();
            return new HostInfo(address.getHostName(), address.getHostAddress());
        } catch (Exception ex) {
            log.warn("Resolve schedule instance host info failed", ex);
            return new HostInfo("unknown-host", "unknown-ip");
        }
    }

    private record HostInfo(String hostName, String ipAddress) {
    }
}
