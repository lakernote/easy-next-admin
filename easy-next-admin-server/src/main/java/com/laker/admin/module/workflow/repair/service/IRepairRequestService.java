package com.laker.admin.module.workflow.repair.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.laker.admin.module.workflow.repair.dto.RepairAttachmentResource;
import com.laker.admin.module.workflow.repair.dto.RepairAttachmentView;
import com.laker.admin.module.workflow.repair.dto.RepairApplyRequest;
import com.laker.admin.module.workflow.repair.dto.RepairRequestView;
import com.laker.admin.module.workflow.repair.entity.BizRepairRequest;
import org.springframework.web.multipart.MultipartFile;

public interface IRepairRequestService extends IService<BizRepairRequest> {
    RepairAttachmentView uploadAttachment(MultipartFile file);

    RepairAttachmentResource readAttachment(Long fileId);

    RepairRequestView apply(RepairApplyRequest request);

    RepairRequestView detail(Long id);

    RepairRequestView detailByWorkflowInstance(Long workflowInstanceId);
}
