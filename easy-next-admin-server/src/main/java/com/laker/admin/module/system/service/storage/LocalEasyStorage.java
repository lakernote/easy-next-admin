package com.laker.admin.module.system.service.storage;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
public class LocalEasyStorage implements EasyStorage {

    private final String storagePath;
    private final String address;

    public LocalEasyStorage(EasyNextAdminConfig easyConfig) {
        EasyNextAdminConfig.Local local = easyConfig.getStorage().getLocal();
        storagePath = local.getStoragePath();
        address = local.getAddress();
    }

    @Override
    public String store(InputStream inputStream, long contentLength, String contentType, String filename) {
        try {
            File target = new File(storagePath, filename);
            Files.createDirectories(target.getParentFile().toPath());
            Files.copy(inputStream, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("本地文件保存失败", e);
        }
        return storagePath + "/" + filename;
    }

    @Override
    public boolean exists(String filePath) {
        return Files.isRegularFile(Path.of(filePath));
    }

    @Override
    public InputStream read(String filePath) {
        try {
            return Files.newInputStream(Path.of(filePath));
        } catch (IOException e) {
            throw new IllegalStateException("本地文件读取失败", e);
        }
    }

    @Override
    public void delete(String filePath) {
        try {
            Files.deleteIfExists(new File(filePath).toPath());
        } catch (IOException e) {
            log.warn("本地文件删除失败: {}", filePath, e);
        }
    }

    @Override
    public String getUrl(String filePath) {
        return address + "/" + filePath;
    }
}
