package com.laker.admin.config.web;

import com.laker.admin.config.properties.EasyNextAdminConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class EasyStaticResourceConfig implements WebMvcConfigurer {

    private final EasyNextAdminConfig easyNextAdminConfig;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String storagePath = easyNextAdminConfig.getStorage().getLocal().getStoragePath();
        Path storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
        createStorageRoot(storageRoot);

        registry.addResourceHandler("/" + resourceUrlPrefix(storagePath) + "/**")
                .addResourceLocations(resourceLocation(storageRoot));
        log.info("Static storage resources registered: storage={}", storageRoot);
    }

    private void createStorageRoot(Path storageRoot) {
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("无法创建本地文件存储目录：" + storageRoot, ex);
        }
    }

    private String resourceUrlPrefix(String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            throw new IllegalStateException("easy.storage.local.storage-path 不能为空");
        }
        String normalized = storagePath.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!StringUtils.hasText(normalized) || normalized.contains("..")) {
            throw new IllegalStateException("easy.storage.local.storage-path 不能包含上级目录：" + storagePath);
        }
        return normalized;
    }

    private String resourceLocation(Path path) {
        String location = path.toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }
}
