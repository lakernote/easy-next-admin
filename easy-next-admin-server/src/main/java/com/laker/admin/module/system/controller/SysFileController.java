package com.laker.admin.module.system.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.laker.admin.common.model.PageResponse;
import com.laker.admin.common.model.Response;
import com.laker.admin.infrastructure.audit.EasyAudit;
import com.laker.admin.infrastructure.observability.trace.EasyTraceIdContext;
import com.laker.admin.infrastructure.security.annotation.EasyPermission;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.permission.EasyPermissions;
import com.laker.admin.infrastructure.web.context.EasyRequestContext;
import com.laker.admin.module.audit.entity.AuditApiLog;
import com.laker.admin.module.audit.service.IAuditApiLogService;
import com.laker.admin.module.system.dto.SysFileView;
import com.laker.admin.module.system.entity.SysFile;
import com.laker.admin.module.system.service.storage.EasyStorageFacade;
import io.swagger.v3.oas.annotations.Operation;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author easynext
 * @since 2022-02-21
 */
@Slf4j
@RestController
@RequestMapping("/api/system/files")
public class SysFileController {
    final EasyStorageFacade easyStorageFacade;
    final IAuditApiLogService auditApiLogService;

    public SysFileController(EasyStorageFacade easyStorageFacade, IAuditApiLogService auditApiLogService) {
        this.easyStorageFacade = easyStorageFacade;
        this.auditApiLogService = auditApiLogService;
    }

    @GetMapping
    @EasyPermission(EasyPermissions.System.FILE_LIST)
    @Operation(summary = "分页查询")
    public PageResponse<SysFileView> pageAll(@RequestParam(required = false, defaultValue = "1") long page,
                                             @RequestParam(required = false, defaultValue = "10") long limit,
                                             String keyWord) {
        Page<SysFile> query = new Page<>(page, limit);
        Page<SysFile> pageList = easyStorageFacade.page(query, keyWord);
        return PageResponse.ok(SysFileView.fromList(pageList.getRecords()), pageList.getTotal());
    }

    @SneakyThrows
    @PostMapping("/upload")
    @EasyPermission(EasyPermissions.System.FILE_UPLOAD)
    @EasyAudit(module = "文件中心", action = "上传文件", dataChange = true, bizType = "SYS_FILE", changeType = "UPLOAD")
    public Response<SysFileView> upload(@RequestParam("file") MultipartFile file) {
        SysFile store = easyStorageFacade.store(file.getInputStream(), file.getSize(),
                file.getContentType(), file.getOriginalFilename());
        return Response.ok(SysFileView.from(store));
    }

    @GetMapping("/{id}/download")
    @EasyPermission(EasyPermissions.System.FILE_LIST)
    @Operation(summary = "鉴权下载文件")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
        long start = System.currentTimeMillis();
        SysFile sysFile = null;
        try {
            sysFile = easyStorageFacade.getFile(id);
            InputStream inputStream = easyStorageFacade.read(sysFile);
            recordDownloadAudit(id, sysFile, true, System.currentTimeMillis() - start, null);
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                    .contentType(resolveMediaType(sysFile.getContentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(resolveDownloadName(sysFile), StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .header("X-Content-Type-Options", "nosniff")
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                            String.join(", ", HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.CONTENT_LENGTH, HttpHeaders.CONTENT_TYPE));
            if (sysFile.getFileSize() != null && sysFile.getFileSize() >= 0) {
                builder.contentLength(sysFile.getFileSize());
            }
            return builder.body(new InputStreamResource(inputStream));
        } catch (RuntimeException ex) {
            recordDownloadAudit(id, sysFile, false, System.currentTimeMillis() - start, ex.getMessage());
            throw ex;
        }
    }


    @DeleteMapping("/{id}")
    @EasyPermission(EasyPermissions.System.FILE_DELETE)
    @Operation(summary = "根据id删除")
    @EasyAudit(module = "文件中心", action = "删除文件", dataChange = true, bizType = "SYS_FILE", bizId = "#id", changeType = "DELETE")
    public Response<Void> delete(@PathVariable Long id) {
        easyStorageFacade.delete(id);
        return Response.ok();
    }

    @DeleteMapping("/batch/{ids}")
    @EasyPermission(EasyPermissions.System.FILE_DELETE)
    @Operation(summary = "根据批量删除ids删除")
    @EasyAudit(module = "文件中心", action = "批量删除文件", dataChange = true, bizType = "SYS_FILE", changeType = "BATCH_DELETE")
    public Response<Void> batchRemove(@PathVariable Long[] ids) {
        easyStorageFacade.delete(ids);
        return Response.ok();
    }

    private MediaType resolveMediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String resolveDownloadName(SysFile sysFile) {
        if (StringUtils.hasText(sysFile.getOriginalName())) {
            return sysFile.getOriginalName();
        }
        if (StringUtils.hasText(sysFile.getFileName())) {
            return sysFile.getFileName();
        }
        if (StringUtils.hasText(sysFile.getStorageName())) {
            return sysFile.getStorageName();
        }
        return "file-" + sysFile.getFileId();
    }

    private void recordDownloadAudit(Long fileId, SysFile sysFile, boolean success, long costMs, String errorMessage) {
        AuditApiLog auditLog = new AuditApiLog();
        auditLog.setTraceId(EasyTraceIdContext.getOrCreateTraceId());
        auditLog.setUserId(EasySecurityContext.getUserId());
        auditLog.setIp(EasyRequestContext.currentRemoteIp());
        auditLog.setClient(EasyRequestContext.currentUserAgentSummary());
        auditLog.setUri(EasyRequestContext.currentRequestUri());
        auditLog.setMethod(EasyRequestContext.currentRequestMethod());
        auditLog.setStatus(success);
        auditLog.setCost((int) Math.min(Math.max(costMs, 0), Integer.MAX_VALUE));
        auditLog.setCreateTime(LocalDateTime.now());
        auditLog.setRequest(truncateAuditText("fileId=" + fileId + ", fileName=" + (sysFile == null ? "-" : resolveDownloadName(sysFile))));
        auditLog.setResponse(success ? "文件下载成功" : truncateAuditText("文件下载失败：" + errorMessage));
        auditLog.setRemark(truncateAuditText("文件中心下载审计"));
        try {
            auditApiLogService.save(auditLog);
        } catch (Exception ex) {
            log.warn("文件下载审计记录写入失败，fileId={}", fileId, ex);
        }
    }

    private String truncateAuditText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
