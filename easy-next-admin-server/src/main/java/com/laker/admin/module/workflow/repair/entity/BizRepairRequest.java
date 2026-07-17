package com.laker.admin.module.workflow.repair.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_repair_request")
public class BizRepairRequest {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private String requestNo;
    private Long applicantId;
    private Long applicantDeptId;
    private String repairType;
    private String assetName;
    private String urgency;
    private LocalDateTime faultTime;
    private String location;
    private String description;
    private String attachmentsJson;
    private String status;
    private Long workflowInstanceId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
