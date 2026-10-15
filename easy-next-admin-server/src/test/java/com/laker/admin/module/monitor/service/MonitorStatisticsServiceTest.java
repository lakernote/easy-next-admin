package com.laker.admin.module.monitor.service;

import com.laker.admin.infrastructure.security.service.EasyAuthService;
import com.laker.admin.module.audit.mapper.AuditApiLogMapper;
import com.laker.admin.module.schedule.mapper.ScheduleJobLogMapper;
import com.laker.admin.module.schedule.mapper.ScheduleJobMapper;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MonitorStatisticsServiceTest {

    @Test
    void remoteCallOverviewUsesAllMicrometerSamples() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        Timer success = Timer.builder("easy.remote.calls")
                .tags("target", "FileClient", "method", "download", "result", "success")
                .register(meters);
        Timer failure = Timer.builder("easy.remote.calls")
                .tags("target", "FileClient", "method", "download", "result", "failure")
                .register(meters);
        success.record(10, TimeUnit.MILLISECONDS);
        success.record(20, TimeUnit.MILLISECONDS);
        failure.record(40, TimeUnit.MILLISECONDS);

        MonitorStatisticsService service = new MonitorStatisticsService(
                mock(AuditApiLogMapper.class), mock(ScheduleJobMapper.class),
                mock(ScheduleJobLogMapper.class), mock(EasyAuthService.class), meters);

        assertThat(service.remoteCallStatistics()).singleElement().satisfies(statistics -> {
            assertThat(statistics.getTarget()).isEqualTo("FileClient");
            assertThat(statistics.getMethod()).isEqualTo("download");
            assertThat(statistics.getTotalCount()).isEqualTo(3);
            assertThat(statistics.getSuccessCount()).isEqualTo(2);
            assertThat(statistics.getFailureCount()).isEqualTo(1);
            assertThat(statistics.getAvgCostMs()).isEqualTo(23.33);
            assertThat(statistics.getMaxCostMs()).isEqualTo(40.0);
            assertThat(statistics.getSuccessRate()).isEqualTo(67);
        });
    }
}
