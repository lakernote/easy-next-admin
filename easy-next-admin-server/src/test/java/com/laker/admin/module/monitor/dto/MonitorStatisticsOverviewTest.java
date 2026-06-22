package com.laker.admin.module.monitor.dto;

import com.laker.admin.module.monitor.controller.MonitorStatisticsController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MonitorStatisticsOverviewTest {

    @Test
    void overviewContractDoesNotExposeSlowRequestAuditList() {
        assertThat(Arrays.stream(MonitorStatisticsOverview.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("slowRequests");
        assertThat(Arrays.stream(MonitorStatisticsOverview.class.getDeclaredClasses()).map(Class::getSimpleName))
                .doesNotContain("SlowRequestRecord");
    }

    @Test
    void monitorStatisticsControllerDoesNotExposeSlowRequestAuditEndpoint() {
        assertThat(Arrays.stream(MonitorStatisticsController.class.getDeclaredMethods()).map(Method::getName))
                .doesNotContain("slowRequests");

        assertThat(Arrays.stream(MonitorStatisticsController.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getAnnotationsByType(GetMapping.class)))
                .flatMap(mapping -> Arrays.stream(mapping.value())))
                .doesNotContain("/slow-requests");
    }
}
