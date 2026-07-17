package com.laker.admin.module.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WfProcessDefinitionRequest {
    private Long id;
    @NotBlank(message = "流程标识不能为空")
    private String processKey;
    @NotBlank(message = "流程名称不能为空")
    private String processName;
    private String status;
    private String remark;
    private String graphJson;
}
