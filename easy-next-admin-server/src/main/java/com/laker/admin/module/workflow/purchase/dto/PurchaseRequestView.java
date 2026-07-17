package com.laker.admin.module.workflow.purchase.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class PurchaseRequestView {
    private Long id;
    private String requestNo;
    private Long applicantId;
    private String applicantName;
    private String itemName;
    private String category;
    private Integer quantity;
    private BigDecimal estimatedAmount;
    private LocalDate requiredDate;
    private String reason;
    private String status;
    private String workflowStatus;
    private Long workflowInstanceId;
    private LocalDateTime createdAt;
}
