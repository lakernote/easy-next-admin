package com.laker.admin.infrastructure.observability.remote.service;

import com.laker.admin.infrastructure.observability.remote.mapper.RemoteCallLogMapper;
import org.apache.ibatis.annotations.Delete;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteCallLogServiceTest {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T04:00:00Z"), ZONE);

    @Test
    void pruneExpiredDeletesMultipleBoundedBatchesUntilTheLastPartialBatch() {
        RemoteCallLogMapper mapper = mock(RemoteCallLogMapper.class);
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 15, 12, 0);
        when(mapper.physicallyDeleteBefore(cutoff, RemoteCallLogService.DELETE_BATCH_SIZE))
                .thenReturn(RemoteCallLogService.DELETE_BATCH_SIZE,
                        RemoteCallLogService.DELETE_BATCH_SIZE,
                        3);
        RemoteCallLogService service = new RemoteCallLogService(mapper, 30, CLOCK);

        service.pruneExpired();

        verify(mapper, times(3)).physicallyDeleteBefore(cutoff, RemoteCallLogService.DELETE_BATCH_SIZE);
    }

    @Test
    void pruneExpiredDoesNotAllowRetentionShorterThanThirtyDays() {
        RemoteCallLogMapper mapper = mock(RemoteCallLogMapper.class);
        RemoteCallLogService service = new RemoteCallLogService(mapper, 7, CLOCK);

        service.pruneExpired();

        verify(mapper).physicallyDeleteBefore(
                LocalDateTime.of(2026, 9, 15, 12, 0), RemoteCallLogService.DELETE_BATCH_SIZE);
    }

    @Test
    void pruneExpiredStopsAfterTheMaximumNumberOfBatches() {
        RemoteCallLogMapper mapper = mock(RemoteCallLogMapper.class);
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 15, 12, 0);
        when(mapper.physicallyDeleteBefore(cutoff, RemoteCallLogService.DELETE_BATCH_SIZE))
                .thenReturn(RemoteCallLogService.DELETE_BATCH_SIZE);
        RemoteCallLogService service = new RemoteCallLogService(mapper, 30, CLOCK);

        service.pruneExpired();

        verify(mapper, times(RemoteCallLogService.MAX_DELETE_BATCHES_PER_RUN))
                .physicallyDeleteBefore(cutoff, RemoteCallLogService.DELETE_BATCH_SIZE);
    }

    @Test
    void pruneExpiredStopsImmediatelyWhenThereAreNoExpiredRows() {
        RemoteCallLogMapper mapper = mock(RemoteCallLogMapper.class);
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 15, 12, 0);
        when(mapper.physicallyDeleteBefore(cutoff, RemoteCallLogService.DELETE_BATCH_SIZE)).thenReturn(0);
        RemoteCallLogService service = new RemoteCallLogService(mapper, 30, CLOCK);

        service.pruneExpired();

        verify(mapper).physicallyDeleteBefore(cutoff, RemoteCallLogService.DELETE_BATCH_SIZE);
    }

    @Test
    void mapperDeleteStatementBypassesLogicalDeletion() throws Exception {
        Method method = RemoteCallLogMapper.class.getMethod("physicallyDeleteBefore", LocalDateTime.class, int.class);
        Delete delete = method.getAnnotation(Delete.class);

        assertThat(delete).isNotNull();
        assertThat(String.join(" ", delete.value()).replaceAll("\\s+", " ").trim())
                .isEqualTo("DELETE FROM observability_remote_call_log "
                        + "WHERE create_time < #{cutoff} ORDER BY create_time LIMIT #{batchSize}");
    }

    @Test
    void cleanupIsDailyAndRequiresExplicitCompatibilityPersistenceOptIn() throws Exception {
        ConditionalOnProperty condition = RemoteCallLogService.class.getAnnotation(ConditionalOnProperty.class);
        Scheduled scheduled = RemoteCallLogService.class.getMethod("pruneExpired").getAnnotation(Scheduled.class);

        assertThat(condition.name())
                .containsExactly("easy.features.monitor", "easy.monitor.diagnosis.enabled",
                        "easy.monitor.diagnosis.remote-log-persistence-enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isFalse();
        assertThat(scheduled.cron())
                .isEqualTo("${easy.monitor.diagnosis.remote-log-cleanup-cron:0 29 2 * * *}");
    }

    @Test
    void cleanupFailureIsContainedUntilTheNextScheduledRun() {
        RemoteCallLogMapper mapper = mock(RemoteCallLogMapper.class);
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 15, 12, 0);
        when(mapper.physicallyDeleteBefore(cutoff, RemoteCallLogService.DELETE_BATCH_SIZE))
                .thenReturn(RemoteCallLogService.DELETE_BATCH_SIZE)
                .thenThrow(new IllegalStateException("database unavailable"));
        RemoteCallLogService service = new RemoteCallLogService(mapper, 30, CLOCK);

        service.pruneExpired();

        verify(mapper, times(2)).physicallyDeleteBefore(cutoff, RemoteCallLogService.DELETE_BATCH_SIZE);
    }
}
