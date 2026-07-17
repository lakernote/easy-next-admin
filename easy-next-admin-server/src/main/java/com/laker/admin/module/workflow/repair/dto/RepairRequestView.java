package com.laker.admin.module.workflow.repair.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RepairRequestView {
    private Long id;
    private String requestNo;
    private Long applicantId;
    private String applicantName;
    private String repairType;
    private String assetName;
    private String urgency;
    private LocalDateTime faultTime;
    private String location;
    private String description;
    private List<RepairAttachmentView> attachments;
    private String status;
    private String workflowStatus;
    private Long workflowInstanceId;
    private LocalDateTime createdAt;
}
