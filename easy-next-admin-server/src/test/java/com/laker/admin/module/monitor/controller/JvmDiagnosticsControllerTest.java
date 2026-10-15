package com.laker.admin.module.monitor.controller;

import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.monitor.service.JvmDiagnosticsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JvmDiagnosticsControllerTest {

    @Test
    void shouldExposeDiagnosticsWithTheDedicatedPermission() {
        EasyPermission permission = JvmDiagnosticsController.class.getAnnotation(EasyPermission.class);

        assertThat(permission).isNotNull();
        assertThat(permission.value()).containsExactly(EasyPermissions.Monitor.DIAGNOSIS_ADVANCED_VIEW);
    }

    @Test
    void endpointsShouldUseTheStandardResponseContract() {
        JvmDiagnosticsController controller = new JvmDiagnosticsController(
                new JvmDiagnosticsService(new SimpleMeterRegistry()));

        assertThat(controller.threads(5, 2).getData().returnedThreads()).isBetween(1, 5);
        assertThat(controller.deadlocks(5, 2).getData()).isNotNull();
        assertThat(controller.pools().getData().pools()).isEmpty();
        assertThat(controller.heapHistogramCapability().getData().status()).isIn("AVAILABLE", "UNAVAILABLE");
    }

    @Test
    void heapHistogramShouldRemainAnExplicitOnDemandEndpoint() {
        JvmDiagnosticsService service = mock(JvmDiagnosticsService.class);
        JvmDiagnosticsService.HeapHistogram expected = new JvmDiagnosticsService.HeapHistogram(
                "2026-10-14T10:00:00Z", "READY", true, true, "high", "done",
                25, 2, 2, false, 30, 4096, List.of());
        when(service.heapHistogram(25)).thenReturn(expected);

        JvmDiagnosticsController controller = new JvmDiagnosticsController(service);

        assertThat(controller.heapHistogram(25).getData()).isSameAs(expected);
        try {
            PostMapping mapping = JvmDiagnosticsController.class
                    .getMethod("heapHistogram", int.class).getAnnotation(PostMapping.class);
            EasyPermission permission = JvmDiagnosticsController.class
                    .getMethod("heapHistogram", int.class).getAnnotation(EasyPermission.class);
            assertThat(mapping).isNotNull();
            assertThat(mapping.value()).containsExactly("/heap-histogram");
            assertThat(permission).isNotNull();
            assertThat(permission.value()).containsExactly(EasyPermissions.Monitor.DIAGNOSIS_CAPTURE);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(exception);
        }
    }
}
