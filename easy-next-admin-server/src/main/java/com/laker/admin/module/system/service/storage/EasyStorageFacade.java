package com.laker.admin.module.system.service.storage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.common.util.EasyNextAdminSecurityUtils;
import com.laker.admin.infrastructure.id.EasyIdGenerator;
import com.laker.admin.infrastructure.persistence.mybatis.UserInfoAndPowers;
import com.laker.admin.module.system.entity.SysFile;
import com.laker.admin.module.system.service.ISysFileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.StringUtils;

@Service
public class EasyStorageFacade {

    private static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp",
            "pdf", "txt", "csv",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "zip"
    );
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "application/pdf", "text/plain", "text/csv",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/zip",
            "application/x-zip-compressed"
    );
    private static final Map<String, String> EXTENSION_CONTENT_TYPE_FALLBACKS = Map.of(
            "csv", "text/plain",
            "txt", "text/plain"
    );
    private static final int FILE_SIGNATURE_READ_LIMIT = 16;

    final ISysFileService sysFileService;
    final EasyStorage easyStorage;

    public EasyStorageFacade(ISysFileService sysFileService, EasyStorage easyStorage) {
        this.sysFileService = sysFileService;
        this.easyStorage = easyStorage;
    }

    public SysFile store(InputStream inputStream, long contentLength, String contentType, String originalFilename) {
        String normalizedContentType = validateFile(contentLength, contentType, originalFilename);
        String suffix = getSuffix(originalFilename);
        InputStream checkedInputStream = validateFileSignature(inputStream, suffix);
        String storageName = EasyIdGenerator.uuid32() + "." + suffix;
        String safeOriginalName = sanitizeFilename(originalFilename);
        String filePath = easyStorage.store(checkedInputStream, contentLength, normalizedContentType, storageName);
        SysFile sysFile = new SysFile();
        UserInfoAndPowers currentUserInfo = EasyNextAdminSecurityUtils.getCurrentUserInfo();
        sysFile.setUserId(currentUserInfo.getUserId());
        sysFile.setNickName(currentUserInfo.getNickName());
        sysFile.setFilePath(filePath);
        sysFile.setFileName(safeOriginalName);
        sysFile.setOriginalName(safeOriginalName);
        sysFile.setStorageName(storageName);
        sysFile.setStorageType("LOCAL");
        sysFile.setFileSize(contentLength);
        sysFile.setContentType(normalizedContentType);
        sysFile.setCreateTime(LocalDateTime.now());
        sysFileService.save(sysFile);
        sysFile.setFilePath(downloadPath(sysFile.getFileId()));
        return sysFile;
    }

    public Page<SysFile> page(Page<SysFile> page, String keyWord) {
        LambdaQueryWrapper<SysFile> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyWord)) {
            queryWrapper.like(SysFile::getFileName, keyWord);
        }
        queryWrapper.orderByDesc(SysFile::getCreateTime);
        Page<SysFile> pageList = sysFileService.page(page, queryWrapper);
        List<SysFile> records = page.getRecords();
        records.forEach(sysFile -> {
            sysFile.setFilePath(downloadPath(sysFile.getFileId()));
        });
        return pageList;
    }

    public SysFile getFile(Long id) {
        SysFile sysFile = sysFileService.getById(id);
        if (sysFile == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在");
        }
        return sysFile;
    }

    public InputStream read(Long id) {
        SysFile sysFile = getFile(id);
        return read(sysFile);
    }

    public InputStream read(SysFile sysFile) {
        validateStoredObject(sysFile);
        return easyStorage.read(sysFile.getFilePath());
    }

    @Transactional
    public void delete(Long id) {
        SysFile sysFile = sysFileService.getById(id);
        if (sysFile == null) {
            return;
        }
        sysFileService.removeById(id);
        easyStorage.delete(sysFile.getFilePath());
    }

    @Transactional
    public void delete(Long[] ids) {
        if (ids == null) {
            return;
        }
        for (Long id : ids) {
            delete(id);
        }
    }

    private String getSuffix(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        return index >= 0 && index < filename.length() - 1
                ? filename.substring(index + 1).toLowerCase(Locale.ROOT)
                : "";
    }

    private String validateFile(long contentLength, String contentType, String originalFilename) {
        if (contentLength <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不能为空");
        }
        if (contentLength > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "文件大小不能超过 20MB");
        }
        String suffix = getSuffix(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(suffix)) {
            throw new BusinessException(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED, "不支持的文件类型");
        }
        String normalizedContentType = normalizeContentType(contentType, suffix);
        if (!StringUtils.hasText(normalizedContentType) || !ALLOWED_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new BusinessException(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED, "不支持的文件 MIME 类型");
        }
        return normalizedContentType;
    }

    private String normalizeContentType(String contentType, String suffix) {
        if (!StringUtils.hasText(contentType)) {
            return EXTENSION_CONTENT_TYPE_FALLBACKS.get(suffix);
        }
        int separator = contentType.indexOf(';');
        String mediaType = separator >= 0 ? contentType.substring(0, separator) : contentType;
        return mediaType.trim().toLowerCase(Locale.ROOT);
    }

    private InputStream validateFileSignature(InputStream inputStream, String suffix) {
        if (inputStream == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件不能为空");
        }
        PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream, FILE_SIGNATURE_READ_LIMIT);
        byte[] header;
        try {
            header = pushbackInputStream.readNBytes(FILE_SIGNATURE_READ_LIMIT);
            if (header.length > 0) {
                pushbackInputStream.unread(header);
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "读取文件内容失败", ex);
        }
        if (!matchesAllowedSignature(suffix, header)) {
            throw new BusinessException(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED, "文件内容与扩展名不匹配");
        }
        return pushbackInputStream;
    }

    private boolean matchesAllowedSignature(String suffix, byte[] header) {
        if (header == null || header.length == 0) {
            return false;
        }
        if (startsWith(header, "MZ".getBytes(StandardCharsets.US_ASCII))) {
            return false;
        }
        return switch (suffix) {
            case "jpg", "jpeg" -> startsWith(header, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "png" -> startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case "gif" -> startsWith(header, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                    || startsWith(header, "GIF89a".getBytes(StandardCharsets.US_ASCII));
            case "webp" -> startsWith(header, "RIFF".getBytes(StandardCharsets.US_ASCII))
                    && header.length >= 12
                    && startsWith(slice(header, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII));
            case "pdf" -> startsWith(header, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "zip", "docx", "xlsx", "pptx" -> startsWith(header, new byte[]{0x50, 0x4B, 0x03, 0x04})
                    || startsWith(header, new byte[]{0x50, 0x4B, 0x05, 0x06})
                    || startsWith(header, new byte[]{0x50, 0x4B, 0x07, 0x08});
            case "doc", "xls", "ppt" -> startsWith(header, new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                    (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1});
            case "txt", "csv" -> looksLikeText(header);
            default -> false;
        };
    }

    private boolean looksLikeText(byte[] header) {
        for (byte value : header) {
            int unsigned = value & 0xFF;
            if (unsigned == 0) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (value[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private byte[] slice(byte[] value, int start, int end) {
        int safeStart = Math.max(0, start);
        int safeEnd = Math.min(value.length, end);
        if (safeStart >= safeEnd) {
            return new byte[0];
        }
        byte[] result = new byte[safeEnd - safeStart];
        System.arraycopy(value, safeStart, result, 0, result.length);
        return result;
    }

    private void validateStoredObject(SysFile sysFile) {
        if (!StringUtils.hasText(sysFile.getFilePath()) || !easyStorage.exists(sysFile.getFilePath())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "文件存储对象不存在");
        }
    }

    private String sanitizeFilename(String originalFilename) {
        String filename = StringUtils.hasText(originalFilename) ? originalFilename : "unknown";
        filename = filename.replace('\\', '/');
        int index = filename.lastIndexOf('/');
        if (index >= 0) {
            filename = filename.substring(index + 1);
        }
        filename = filename.replaceAll("[\\r\\n\\t]", "_").trim();
        if (filename.length() > 180) {
            filename = filename.substring(filename.length() - 180);
        }
        return StringUtils.hasText(filename) ? filename : "unknown";
    }

    private String downloadPath(Long fileId) {
        return "/api/system/files/" + fileId + "/download";
    }
}
