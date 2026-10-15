package com.laker.admin.infrastructure.observability.remote.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.laker.admin.infrastructure.observability.remote.entity.RemoteCallLog;
import com.laker.admin.infrastructure.observability.remote.mapper.RemoteCallLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@ConditionalOnProperty(name = {"easy.features.monitor", "easy.monitor.diagnosis.enabled",
        "easy.monitor.diagnosis.remote-log-persistence-enabled"}, havingValue = "true")
@Slf4j
public class RemoteCallLogService extends ServiceImpl<RemoteCallLogMapper, RemoteCallLog> {
    private static final int MIN_RETENTION_DAYS = 30;
    static final int DELETE_BATCH_SIZE = 1_000;
    static final int MAX_DELETE_BATCHES_PER_RUN = 10;

    private final RemoteCallLogMapper remoteCallLogMapper;
    private final int retentionDays;
    private final Clock clock;

    @Autowired
    public RemoteCallLogService(RemoteCallLogMapper remoteCallLogMapper,
                                @Value("${easy.monitor.diagnosis.retention-days:30}") int retentionDays) {
        this(remoteCallLogMapper, retentionDays, Clock.systemDefaultZone());
    }

    RemoteCallLogService(RemoteCallLogMapper remoteCallLogMapper, int retentionDays, Clock clock) {
        this.remoteCallLogMapper = remoteCallLogMapper;
        this.baseMapper = remoteCallLogMapper;
        this.retentionDays = Math.max(MIN_RETENTION_DAYS, retentionDays);
        this.clock = clock;
    }

    /** Physically removes expired slow/error remote-call details once per day. */
    @Scheduled(cron = "${easy.monitor.diagnosis.remote-log-cleanup-cron:0 29 2 * * *}")
    public void pruneExpired() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(retentionDays);
        long totalDeleted = 0;
        try {
            for (int batch = 0; batch < MAX_DELETE_BATCHES_PER_RUN; batch++) {
                int deleted = remoteCallLogMapper.physicallyDeleteBefore(cutoff, DELETE_BATCH_SIZE);
                totalDeleted += deleted;
                if (deleted < DELETE_BATCH_SIZE) {
                    break;
                }
            }
            if (totalDeleted > 0) {
                log.info("分批清理过期远程调用诊断明细 {} 条，截止时间 {}", totalDeleted, cutoff);
            }
        } catch (RuntimeException exception) {
            // A cleanup failure must not affect remote-call execution; the next daily run retries it.
            log.warn("分批清理过期远程调用诊断明细失败，已清理 {} 条，截止时间 {}",
                    totalDeleted, cutoff, exception);
        }
    }
}
