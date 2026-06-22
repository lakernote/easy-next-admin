package com.laker.admin.module.workflow.purchase.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PurchaseApplyRequest {
    @NotBlank(message = "采购物品不能为空")
    @Size(max = 120, message = "采购物品不能超过120个字符")
    private String itemName;
    @NotBlank(message = "采购类别不能为空")
    @Pattern(regexp = "OFFICE_SUPPLIES|IT_EQUIPMENT|SOFTWARE_SERVICE|ADMIN_SERVICE", message = "采购类别不正确")
    private String category;
    @NotNull(message = "采购数量不能为空")
    @Min(value = 1, message = "采购数量至少为1")
    private Integer quantity;
    @NotNull(message = "预算金额不能为空")
    @DecimalMin(value = "0.01", message = "预算金额必须大于0")
    private BigDecimal estimatedAmount;
    @NotNull(message = "期望到货日期不能为空")
    private LocalDate requiredDate;
    @NotBlank(message = "采购事由不能为空")
    @Size(max = 500, message = "采购事由不能超过500个字符")
    private String reason;
}
