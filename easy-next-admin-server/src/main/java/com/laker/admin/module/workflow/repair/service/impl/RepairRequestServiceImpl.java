package com.laker.admin.module.workflow.repair.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.infrastructure.json.EasyJsonCodec;
import com.laker.admin.infrastructure.security.context.EasySecurityContext;
import com.laker.admin.infrastructure.security.exception.EasyForbiddenException;
import com.laker.admin.infrastructure.security.model.AuthPrincipal;
import com.laker.admin.module.system.entity.SysFile;
import com.laker.admin.module.system.entity.SysUser;
import com.laker.admin.module.system.service.ISysFileService;
import com.laker.admin.module.system.service.ISysUserService;
import com.laker.admin.module.system.service.storage.EasyStorageFacade;
import com.laker.admin.module.workflow.dto.WfProcessInstanceDetail;
import com.laker.admin.module.workflow.dto.WfStartProcessRequest;
import com.laker.admin.module.workflow.entity.WfProcessInstance;
import com.laker.admin.module.workflow.event.WfProcessInstanceStatusChangedEvent;
import com.laker.admin.module.workflow.repair.dto.RepairAttachmentResource;
import com.laker.admin.module.workflow.repair.dto.RepairAttachmentView;
import com.laker.admin.module.business.number.service.BusinessNumberService;
import com.laker.admin.module.workflow.service.IWfProcessInstanceService;
import com.laker.admin.module.workflow.service.IWfWorkflowRuntimeService;
import com.laker.admin.module.workflow.support.WorkflowInstanceStatus;
import com.laker.admin.module.workflow.repair.dto.RepairApplyRequest;
import com.laker.admin.module.workflow.repair.dto.RepairRequestView;
import com.laker.admin.module.workflow.repair.entity.BizRepairRequest;
import com.laker.admin.module.workflow.repair.mapper.BizRepairRequestMapper;
import com.laker.admin.module.workflow.repair.service.IRepairRequestService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RepairRequestServiceImpl extends ServiceImpl<BizRepairRequestMapper, BizRepairRequest>
        implements IRepairRequestService {
    private static final String PROCESS_KEY = "repair_approval";
    private static final String BUSINESS_TYPE = "repair";
    private static final String NUMBER_RULE_CODE = "REPAIR_REQUEST";
    private static final int MAX_ATTACHMENT_COUNT = 3;
    private static final long MAX_ATTACHMENT_SIZE = 5L * 1024L * 1024L;
    private static final Set<String> REPAIR_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final TypeReference<List<RepairAttachmentView>> ATTACHMENTS_TYPE = new TypeReference<>() {
    };

    private final IWfWorkflowRuntimeService workflowRuntimeService;
    private final IWfProcessInstanceService processInstanceService;
    private final ISysUserService userService;
    private final ISysFileService sysFileService;
    private final EasyStorageFacade storageFacade;
    private final EasyJsonCodec jsonCodec;
    private final BusinessNumberService businessNumberService;

    public RepairRequestServiceImpl(IWfWorkflowRuntimeService workflowRuntimeService,
                                      IWfProcessInstanceService processInstanceService,
                                      ISysUserService userService,
                                      ISysFileService sysFileService,
                                      EasyStorageFacade storageFacade,
                                      EasyJsonCodec jsonCodec,
                                      BusinessNumberService businessNumberService) {
        this.workflowRuntimeService = workflowRuntimeService;
        this.processInstanceService = processInstanceService;
        this.userService = userService;
        this.sysFileService = sysFileService;
        this.storageFacade = storageFacade;
        this.jsonCodec = jsonCodec;
        this.businessNumberService = businessNumberService;
    }

    @Override
    public RepairAttachmentView uploadAttachment(MultipartFile file) {
        validateRepairImage(file);
        try {
            SysFile stored = storageFacade.store(file.getInputStream(), file.getSize(),
                    file.getContentType(), file.getOriginalFilename());
            return toAttachment(stored);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "报修图片上传失败", e);
        }
    }

    @Override
    public RepairAttachmentResource readAttachment(Long fileId) {
        SysFile file = getRepairImage(fileId);
        AuthPrincipal principal = currentPrincipal();
        if (!canReadAttachment(principal, file)) {
            throw new EasyForbiddenException("无权查看报修图片");
        }
        return new RepairAttachmentResource(
                file.getFileId(),
                displayFileName(file),
                file.getContentType(),
                file.getFileSize(),
                storageFacade.read(file)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RepairRequestView apply(RepairApplyRequest request) {
        AuthPrincipal principal = currentPrincipal();
        LocalDateTime now = LocalDateTime.now();
        List<RepairAttachmentView> attachments = normalizeAttachments(principal, request.getAttachments());

        BizRepairRequest repairRequest = new BizRepairRequest();
        repairRequest.setApplicantId(principal.getUserId());
        repairRequest.setApplicantDeptId(principal.getDeptId());
        repairRequest.setRequestNo(businessNumberService.nextNumber(NUMBER_RULE_CODE));
        repairRequest.setRepairType(request.getRepairType());
        repairRequest.setAssetName(request.getAssetName());
        repairRequest.setUrgency(request.getUrgency());
        repairRequest.setFaultTime(request.getFaultTime());
        repairRequest.setLocation(request.getLocation());
        repairRequest.setDescription(request.getDescription());
        repairRequest.setAttachmentsJson(writeAttachments(attachments));
        repairRequest.setStatus("DRAFT");
        repairRequest.setCreatedBy(principal.getUserId());
        repairRequest.setCreatedAt(now);
        repairRequest.setUpdatedBy(principal.getUserId());
        repairRequest.setUpdatedAt(now);
        save(repairRequest);
        bindAttachments(repairRequest.getId(), attachments);

        WfStartProcessRequest startProcessRequest = new WfStartProcessRequest();
        startProcessRequest.setProcessKey(PROCESS_KEY);
        startProcessRequest.setBusinessType(BUSINESS_TYPE);
        startProcessRequest.setBusinessId(repairRequest.getRequestNo());
        String displayName = StringUtils.hasText(principal.getNickName()) ? principal.getNickName() : principal.getUserName();
        startProcessRequest.setTitle(displayName + "报修申请");
        startProcessRequest.setComment(request.getDescription());
        startProcessRequest.setVariables(startVariables(principal, request, repairRequest, attachments));
        WfProcessInstanceDetail workflowDetail = workflowRuntimeService.start(startProcessRequest);

        repairRequest.setWorkflowInstanceId(workflowDetail.getInstance().getId());
        repairRequest.setStatus(statusFromWorkflow(workflowDetail.getInstance().getStatus()));
        repairRequest.setUpdatedAt(LocalDateTime.now());
        updateById(repairRequest);
        return toView(repairRequest);
    }

    @Override
    public RepairRequestView detail(Long id) {
        BizRepairRequest repairRequest = getById(id);
        if (repairRequest == null) {
            throw new BusinessException("报修申请不存在");
        }
        AuthPrincipal principal = currentPrincipal();
        assertRepairVisible(principal, repairRequest);
        return toView(repairRequest);
    }

    @Override
    public RepairRequestView detailByWorkflowInstance(Long workflowInstanceId) {
        if (workflowInstanceId == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "流程实例不存在");
        }
        BizRepairRequest repairRequest = lambdaQuery()
                .eq(BizRepairRequest::getWorkflowInstanceId, workflowInstanceId)
                .one();
        if (repairRequest == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报修申请不存在");
        }
        AuthPrincipal principal = currentPrincipal();
        assertRepairVisible(principal, repairRequest);
        return toView(repairRequest);
    }

    @EventListener
    public void syncStatusWhenWorkflowFinished(WfProcessInstanceStatusChangedEvent event) {
        if (!BUSINESS_TYPE.equals(event.businessType()) || !StringUtils.hasText(event.businessId())) {
            return;
        }
        String repairStatus = statusFromWorkflow(event.status());
        lambdaUpdate()
                .eq(BizRepairRequest::getRequestNo, event.businessId())
                .set(BizRepairRequest::getStatus, repairStatus)
                .set(BizRepairRequest::getWorkflowInstanceId, event.instanceId())
                .set(BizRepairRequest::getUpdatedAt, LocalDateTime.now())
                .update();
    }

    private Map<String, Object> startVariables(AuthPrincipal principal,
                                               RepairApplyRequest request,
                                               BizRepairRequest repairRequest,
                                               List<RepairAttachmentView> attachments) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("repairRequestId", repairRequest.getId());
        variables.put("repairType", request.getRepairType());
        variables.put("assetName", request.getAssetName());
        variables.put("urgency", request.getUrgency());
        variables.put("faultTime", request.getFaultTime().toString());
        variables.put("location", request.getLocation());
        variables.put("description", request.getDescription());
        variables.put("attachmentCount", attachments.size());
        variables.put("repairAttachments", attachments);
        variables.put("applicantDeptId", principal.getDeptId() == null ? "" : principal.getDeptId());
        return variables;
    }

    private void assertRepairVisible(AuthPrincipal principal, BizRepairRequest repairRequest) {
        if (principal.isSuperAdmin() || principal.getUserId().equals(repairRequest.getApplicantId())) {
            return;
        }
        if (repairRequest.getWorkflowInstanceId() == null) {
            throw new EasyForbiddenException("无权访问报修申请");
        }
        workflowRuntimeService.detail(repairRequest.getWorkflowInstanceId());
    }

    private RepairRequestView toView(BizRepairRequest repairRequest) {
        RepairRequestView view = new RepairRequestView();
        view.setId(repairRequest.getId());
        view.setRequestNo(repairRequest.getRequestNo());
        view.setApplicantId(repairRequest.getApplicantId());
        view.setRepairType(repairRequest.getRepairType());
        view.setAssetName(repairRequest.getAssetName());
        view.setUrgency(repairRequest.getUrgency());
        view.setFaultTime(repairRequest.getFaultTime());
        view.setLocation(repairRequest.getLocation());
        view.setDescription(repairRequest.getDescription());
        view.setAttachments(readAttachments(repairRequest.getAttachmentsJson()));
        view.setWorkflowInstanceId(repairRequest.getWorkflowInstanceId());
        view.setCreatedAt(repairRequest.getCreatedAt());
        fillApplicant(view, repairRequest);
        fillWorkflowStatus(view, repairRequest);
        return view;
    }

    private void fillApplicant(RepairRequestView view, BizRepairRequest repairRequest) {
        SysUser user = userService.getById(repairRequest.getApplicantId());
        if (user != null) {
            view.setApplicantName(StringUtils.hasText(user.getNickName()) ? user.getNickName() : user.getUserName());
        }
    }

    private void fillWorkflowStatus(RepairRequestView view, BizRepairRequest repairRequest) {
        String workflowStatus = null;
        if (repairRequest.getWorkflowInstanceId() != null) {
            WfProcessInstance instance = processInstanceService.getById(repairRequest.getWorkflowInstanceId());
            if (instance != null) {
                workflowStatus = instance.getStatus();
            }
        }
        view.setWorkflowStatus(workflowStatus);
        view.setStatus(StringUtils.hasText(workflowStatus) ? statusFromWorkflow(workflowStatus) : repairRequest.getStatus());
    }

    private String statusFromWorkflow(String workflowStatus) {
        if (WorkflowInstanceStatus.RUNNING.equalsCode(workflowStatus)) {
            return "APPROVING";
        }
        if (WorkflowInstanceStatus.APPROVED.equalsCode(workflowStatus)) {
            return "APPROVED";
        }
        if (WorkflowInstanceStatus.REJECTED.equalsCode(workflowStatus)) {
            return "REJECTED";
        }
        if (WorkflowInstanceStatus.REVOKED.equalsCode(workflowStatus)) {
            return "REVOKED";
        }
        if (WorkflowInstanceStatus.TERMINATED.equalsCode(workflowStatus)) {
            return "TERMINATED";
        }
        return workflowStatus;
    }

    private void validateRepairImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报修图片不能为空");
        }
        if (file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE, "报修图片不能超过 5MB");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (!REPAIR_IMAGE_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED, "仅支持 JPG、PNG、WEBP 报修图片");
        }
    }

    private List<RepairAttachmentView> normalizeAttachments(AuthPrincipal principal, List<RepairAttachmentView> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        if (attachments.size() > MAX_ATTACHMENT_COUNT) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "报修图片最多上传3张");
        }
        Map<Long, RepairAttachmentView> normalized = new LinkedHashMap<>();
        attachments.forEach(attachment -> {
            SysFile file = getRepairImage(attachment.getFileId());
            if (!principal.isSuperAdmin() && !principal.getUserId().equals(file.getUserId())) {
                throw new EasyForbiddenException("只能提交本人上传的报修图片");
            }
            normalized.putIfAbsent(file.getFileId(), toAttachment(file));
        });
        return List.copyOf(normalized.values());
    }

    private void bindAttachments(Long repairRequestId, List<RepairAttachmentView> attachments) {
        if (attachments.isEmpty()) {
            return;
        }
        List<Long> fileIds = attachments.stream().map(RepairAttachmentView::getFileId).toList();
        sysFileService.lambdaUpdate()
                .in(SysFile::getFileId, fileIds)
                .set(SysFile::getBusinessType, BUSINESS_TYPE)
                .set(SysFile::getBusinessId, repairRequestId)
                .update();
    }

    private SysFile getRepairImage(Long fileId) {
        if (fileId == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报修图片不存在");
        }
        SysFile file = sysFileService.getById(fileId);
        if (file == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "报修图片不存在");
        }
        if (!REPAIR_IMAGE_TYPES.contains(normalizeContentType(file.getContentType()))) {
            throw new BusinessException(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED, "报修附件必须是图片");
        }
        return file;
    }

    private boolean canReadAttachment(AuthPrincipal principal, SysFile file) {
        if (principal.isSuperAdmin() || principal.getUserId().equals(file.getUserId())) {
            return true;
        }
        if (!BUSINESS_TYPE.equals(file.getBusinessType()) || file.getBusinessId() == null) {
            return false;
        }
        BizRepairRequest repairRequest = getById(file.getBusinessId());
        if (repairRequest == null) {
            return false;
        }
        if (principal.getUserId().equals(repairRequest.getApplicantId())) {
            return true;
        }
        if (repairRequest.getWorkflowInstanceId() == null) {
            return false;
        }
        try {
            workflowRuntimeService.detail(repairRequest.getWorkflowInstanceId());
            return true;
        } catch (BusinessException ignored) {
            return false;
        }
    }

    private RepairAttachmentView toAttachment(SysFile file) {
        RepairAttachmentView attachment = new RepairAttachmentView();
        attachment.setFileId(file.getFileId());
        attachment.setFileName(displayFileName(file));
        attachment.setContentType(file.getContentType());
        attachment.setFileSize(file.getFileSize());
        attachment.setUrl("/api/workflow/repair/requests/attachments/" + file.getFileId());
        return attachment;
    }

    private String writeAttachments(List<RepairAttachmentView> attachments) {
        return attachments.isEmpty() ? "[]" : jsonCodec.toJson(attachments);
    }

    private List<RepairAttachmentView> readAttachments(String attachmentsJson) {
        if (!StringUtils.hasText(attachmentsJson)) {
            return List.of();
        }
        return jsonCodec.fromJson(attachmentsJson, ATTACHMENTS_TYPE);
    }

    private String displayFileName(SysFile file) {
        if (StringUtils.hasText(file.getOriginalName())) {
            return file.getOriginalName();
        }
        if (StringUtils.hasText(file.getFileName())) {
            return file.getFileName();
        }
        return file.getStorageName();
    }

    private String normalizeContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "";
        }
        int separator = contentType.indexOf(';');
        return (separator >= 0 ? contentType.substring(0, separator) : contentType).trim().toLowerCase();
    }

    private AuthPrincipal currentPrincipal() {
        AuthPrincipal principal = EasySecurityContext.getPrincipal();
        if (principal == null || principal.getUserId() == null) {
            throw new BusinessException("未获取到当前登录用户");
        }
        return principal;
    }
}
