package com.laker.admin.module.monitor.controller;

import com.laker.admin.infrastructure.observability.jfr.LocalJfrRecorder;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.module.monitor.service.JfrAnalysisService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

class JfrAnalysisControllerTest {

    @Test
    void shouldParseCsvFileNamesAndDelegateOneWindowAnalysis() {
        JfrAnalysisService service = mock(JfrAnalysisService.class);
        JfrAnalysisController controller = new JfrAnalysisController(service, mock(LocalJfrRecorder.class));
        Instant from = Instant.parse("2026-10-14T02:00:00Z");
        Instant to = Instant.parse("2026-10-14T02:00:01Z");

        controller.analyzeWindow(" first.jfr,second.jfr,first.jfr ", 30, "trace-1", from, to);

        verify(service).analyzeFiles(List.of("first.jfr", "second.jfr", "first.jfr"),
                30, "trace-1", from, to);
    }

    @Test
    void shouldProtectAndDelegateRecordingControls() throws Exception {
        JfrAnalysisService service = mock(JfrAnalysisService.class);
        LocalJfrRecorder recorder = mock(LocalJfrRecorder.class);
        JfrAnalysisController controller = new JfrAnalysisController(service, recorder);

        controller.startRecording(120);
        controller.stopRecording();

        verify(recorder).enable(120);
        verify(recorder).disable();
        EasyPermission startPermission = JfrAnalysisController.class
                .getMethod("startRecording", int.class).getAnnotation(EasyPermission.class);
        EasyPermission stopPermission = JfrAnalysisController.class
                .getMethod("stopRecording").getAnnotation(EasyPermission.class);
        assertThat(startPermission.value()).containsExactly(EasyPermissions.Monitor.DIAGNOSIS_JFR_CONTROL);
        assertThat(stopPermission.value()).containsExactly(EasyPermissions.Monitor.DIAGNOSIS_JFR_CONTROL);
    }
}
