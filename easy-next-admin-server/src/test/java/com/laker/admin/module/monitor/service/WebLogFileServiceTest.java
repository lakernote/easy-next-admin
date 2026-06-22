package com.laker.admin.module.monitor.service;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import com.laker.admin.module.monitor.dto.WebLogFileSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WebLogFileServiceTest {

    @Test
    void snapshotShouldTailCurrentLogFileAndApplyFilters(@TempDir Path tempDir) throws IOException {
        Path logFile = tempDir.resolve("easy-next-admin.log");
        Files.write(logFile, List.of(
                "12:00:01.001 [main] INFO  [- trace-a] com.laker.admin.Auth:10 - login ok",
                "12:00:02.001 [main] ERROR [- trace-b] com.laker.admin.Invoice:20 - invoice failed",
                "12:00:03.001 [main] WARN  [- trace-c] com.laker.admin.Cache:30 - cache miss",
                "12:00:04.001 [main] ERROR [- trace-d] com.laker.admin.Invoice:40 - invoice retry failed"
        ), StandardCharsets.UTF_8);
        WebLogFileService service = new WebLogFileService(new EasyNextAdminConfig());

        WebLogFileSnapshot snapshot = service.snapshot(logFile, 1, "invoice", "ERROR");

        assertThat(snapshot.isReadable()).isTrue();
        assertThat(snapshot.getFileName()).isEqualTo("easy-next-admin.log");
        assertThat(snapshot.getRequestedLines()).isEqualTo(1);
        assertThat(snapshot.getReturnedLines()).isEqualTo(1);
        assertThat(snapshot.isTruncated()).isTrue();
        assertThat(snapshot.getLines()).containsExactly(
                "12:00:04.001 [main] ERROR [- trace-d] com.laker.admin.Invoice:40 - invoice retry failed"
        );
    }

    @Test
    void snapshotShouldReturnReadableMessageWhenLogFileIsMissing(@TempDir Path tempDir) {
        Path missingFile = tempDir.resolve("missing.log");
        WebLogFileService service = new WebLogFileService(new EasyNextAdminConfig());

        WebLogFileSnapshot snapshot = service.snapshot(missingFile, 300, "", "");

        assertThat(snapshot.isReadable()).isFalse();
        assertThat(snapshot.getReturnedLines()).isZero();
        assertThat(snapshot.getLines()).isEmpty();
        assertThat(snapshot.getMessage()).contains("不可读");
    }
}
