package com.laker.admin.module.system.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.system.entity.SysFile;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件中心对外响应模型，隐藏审计列、软删标记和乐观锁等内部字段。
 */
@Data
@Builder
public class SysFileView {
    private Long fileId;
    private String filePath;
    private String fileName;
    private String originalName;
    private String storageName;
    private String storageType;
    private Long fileSize;
    private String contentType;
    private String businessType;
    private Long businessId;
    private Long userId;
    private String nickName;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    public static SysFileView from(SysFile file) {
        if (file == null) {
            return null;
        }
        return SysFileView.builder()
                .fileId(file.getFileId())
                .filePath(file.getFilePath())
                .fileName(file.getFileName())
                .originalName(file.getOriginalName())
                .storageName(file.getStorageName())
                .storageType(file.getStorageType())
                .fileSize(file.getFileSize())
                .contentType(file.getContentType())
                .businessType(file.getBusinessType())
                .businessId(file.getBusinessId())
                .userId(file.getUserId())
                .nickName(file.getNickName())
                .createTime(file.getCreateTime())
                .updateTime(file.getUpdateTime())
                .build();
    }

    public static List<SysFileView> fromList(List<SysFile> files) {
        return files == null ? List.of() : files.stream().map(SysFileView::from).toList();
    }
}
