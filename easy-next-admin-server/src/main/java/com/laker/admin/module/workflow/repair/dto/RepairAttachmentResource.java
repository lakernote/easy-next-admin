package com.laker.admin.module.workflow.repair.dto;

import java.io.InputStream;

public record RepairAttachmentResource(
        Long fileId,
        String fileName,
        String contentType,
        Long fileSize,
        InputStream inputStream
) {
}
