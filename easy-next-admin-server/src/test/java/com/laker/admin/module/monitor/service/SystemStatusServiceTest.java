package com.laker.admin.module.monitor.service;

import com.laker.admin.module.monitor.dto.SystemStatusOverview;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SystemStatusServiceTest {

    @Test
    void overviewShouldCollectRuntimeMetrics() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.application.name", "easy-next-admin-test");
        SystemStatusService service = new SystemStatusService(environment);

        SystemStatusOverview overview = service.overview();

        assertThat(overview.getStatus()).isEqualTo("UP");
        assertThat(overview.getHealthy()).isTrue();
        assertThat(overview.getApplicationName()).isEqualTo("easy-next-admin-test");
        assertThat(overview.getCpu().getProcessors()).isPositive();
        assertThat(overview.getMemory().getHeapUsedBytes()).isNotNegative();
        assertThat(overview.getThreads().getLive()).isPositive();
        assertThat(overview.getDisks()).isNotNull();
        assertThat(overview.getRuntime()).extracting(SystemStatusOverview.RuntimeInfo::getLabel)
                .contains("Java 版本", "JVM 进程");
    }
}
