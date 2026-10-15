package com.laker.admin.module.monitor.controller;

import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.monitor.service.RuntimeStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeStatisticsControllerTest {

    @Test
    void shouldUseMonitorConditionAndDiagnosisPermission() {
        EasyPermission permission = RuntimeStatisticsController.class.getAnnotation(EasyPermission.class);
        ConditionalOnProperty condition = RuntimeStatisticsController.class.getAnnotation(ConditionalOnProperty.class);
        RequestMapping mapping = RuntimeStatisticsController.class.getAnnotation(RequestMapping.class);

        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactly(EasyPermissions.Monitor.DIAGNOSIS_VIEW);
        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("easy.features");
        assertThat(condition.name()).containsExactly("monitor");
        assertThat(condition.havingValue()).isEqualTo("true");
        assertThat(condition.matchIfMissing()).isTrue();
        assertThat(mapping.value()).containsExactly("/api/monitor/diagnosis/statistics");
    }

    @Test
    void openApiShouldDescribeIncidentOnlyCoverageForEveryOperationType() throws Exception {
        Operation operation = RuntimeStatisticsController.class
                .getMethod("statistics", String.class, int.class, int.class, String.class)
                .getAnnotation(Operation.class);

        assertThat(operation).isNotNull();
        assertThat(operation.description())
                .contains("所有类型均只覆盖慢调用、错误或包含慢错子调用的样本")
                .contains("easy.operations.* Metrics")
                .doesNotContain("SQL 和远程调用仅统计");
    }

    @Test
    void shouldReturnStandardResponseAndForwardQueryOptions() {
        RuntimeStatisticsService service = mock(RuntimeStatisticsService.class);
        LocalDateTime to = LocalDateTime.of(2026, 10, 14, 12, 0);
        RuntimeStatisticsService.StatisticsResult expected = new RuntimeStatisticsService.StatisticsResult(
                "SQL", 24, to.minusHours(24), to, 10, 10, "count,desc",
                50_000, 0, 0, false, false, true, "SLOW_OR_ERROR_ONLY",
                0, List.of(), List.of());
        when(service.statistics("SQL", 24, 10, "count,desc")).thenReturn(expected);
        RuntimeStatisticsController controller = new RuntimeStatisticsController(service);

        Response<RuntimeStatisticsService.StatisticsResult> response = controller.statistics(
                "SQL", 24, 10, "count,desc");

        assertThat(response.getCode()).isZero();
        assertThat(response.getData()).isSameAs(expected);
        verify(service).statistics("SQL", 24, 10, "count,desc");
    }
}
