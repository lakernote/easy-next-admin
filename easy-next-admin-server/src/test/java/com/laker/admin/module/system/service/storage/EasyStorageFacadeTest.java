package com.laker.admin.module.system.service.storage;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.system.entity.SysFile;
import com.laker.admin.module.system.service.ISysFileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EasyStorageFacadeTest {

    private ISysFileService sysFileService;
    private CapturingStorage storage;
    private EasyStorageFacade facade;

    @BeforeEach
    void setUp() {
        sysFileService = mock(ISysFileService.class);
        storage = new CapturingStorage();
        facade = new EasyStorageFacade(sysFileService, storage);
        when(sysFileService.save(any(SysFile.class))).thenAnswer(invocation -> {
            SysFile file = invocation.getArgument(0);
            file.setFileId(1001L);
            return true;
        });
        EasySecurityContext.setPrincipal(AuthPrincipal.builder()
                .userId(1L)
                .userName("admin")
                .nickName("管理员")
                .deptId(10L)
                .build());
    }

    @AfterEach
    void tearDown() {
        EasySecurityContext.clear();
    }

    @Test
    void storeShouldRejectExecutableContentDisguisedAsImage() {
        byte[] executableHeader = new byte[]{'M', 'Z', 0, 0, 0, 0};

        assertThatThrownBy(() -> facade.store(new ByteArrayInputStream(executableHeader),
                executableHeader.length, "image/png", "avatar.png"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件内容与扩展名不匹配");
    }

    @Test
    void storeShouldRejectExecutableContentDisguisedAsPdf() {
        byte[] executableHeader = new byte[]{'M', 'Z', 0, 0, 0, 0};

        assertThatThrownBy(() -> facade.store(new ByteArrayInputStream(executableHeader),
                executableHeader.length, "application/pdf", "contract.pdf"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件内容与扩展名不匹配");
    }

    @Test
    void storeShouldPreserveInputStreamAfterSignatureValidation() {
        byte[] pdf = "%PDF-1.7\ncontent".getBytes(StandardCharsets.UTF_8);

        SysFile stored = facade.store(new ByteArrayInputStream(pdf), pdf.length, "application/pdf", "contract.pdf");

        assertThat(stored.getFilePath()).isEqualTo("/api/system/files/1001/download");
        assertThat(storage.bytes.get()).isEqualTo(pdf);
        assertThat(storage.contentType.get()).isEqualTo("application/pdf");
    }

    private static class CapturingStorage implements EasyStorage {
        private final AtomicReference<byte[]> bytes = new AtomicReference<>();
        private final AtomicReference<String> contentType = new AtomicReference<>();

        @Override
        public String store(InputStream inputStream, long contentLength, String contentType, String fileName) {
            try {
                this.bytes.set(inputStream.readAllBytes());
                this.contentType.set(contentType);
                return "storage/" + fileName;
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public boolean exists(String filePath) {
            return true;
        }

        @Override
        public InputStream read(String filePath) {
            return new ByteArrayInputStream(bytes.get());
        }

        @Override
        public void delete(String filePath) {
        }

        @Override
        public String getUrl(String filePath) {
            return filePath;
        }
    }
}
