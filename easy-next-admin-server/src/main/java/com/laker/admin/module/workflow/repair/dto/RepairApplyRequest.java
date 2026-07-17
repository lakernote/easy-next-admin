package com.laker.admin.module.workflow.repair.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RepairApplyRequest {
    @NotBlank(message = "报修类型不能为空")
    @Pattern(regexp = "DEVICE|NETWORK|SOFTWARE|FACILITY", message = "报修类型不正确")
    private String repairType;
    @NotBlank(message = "报修对象不能为空")
    @Size(max = 120, message = "报修对象不能超过120个字符")
    private String assetName;
    @NotBlank(message = "紧急程度不能为空")
    @Pattern(regexp = "NORMAL|HIGH|URGENT", message = "紧急程度不正确")
    private String urgency;
    @NotNull(message = "故障时间不能为空")
    private LocalDateTime faultTime;
    @NotBlank(message = "所在位置不能为空")
    @Size(max = 120, message = "所在位置不能超过120个字符")
    private String location;
    @NotBlank(message = "问题描述不能为空")
    @Size(max = 500, message = "问题描述不能超过500个字符")
    private String description;
    @Size(max = 3, message = "报修图片最多上传3张")
    private List<@Valid RepairAttachmentView> attachments;
}
