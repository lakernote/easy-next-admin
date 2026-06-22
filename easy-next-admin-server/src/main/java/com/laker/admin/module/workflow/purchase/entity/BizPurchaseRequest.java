package com.laker.admin.module.workflow.purchase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("biz_purchase_request")
public class BizPurchaseRequest {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private String requestNo;
    private Long applicantId;
    private Long applicantDeptId;
    private String itemName;
    private String category;
    private Integer quantity;
    private BigDecimal estimatedAmount;
    private LocalDate requiredDate;
    private String reason;
    private String status;
    private Long workflowInstanceId;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
