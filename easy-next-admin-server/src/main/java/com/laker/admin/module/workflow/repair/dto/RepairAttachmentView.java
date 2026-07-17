package com.laker.admin.module.workflow.repair.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RepairAttachmentView {
    @NotNull(message = "图片文件ID不能为空")
    private Long fileId;
    @Size(max = 180, message = "图片名称不能超过180个字符")
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String url;
}
