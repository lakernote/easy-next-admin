package com.laker.admin.module.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WfStartProcessRequest {
    private Long definitionId;
    private String processKey;
    @NotBlank(message = "业务类型不能为空")
    private String businessType;
    @NotBlank(message = "业务ID不能为空")
    private String businessId;
    @NotBlank(message = "流程标题不能为空")
    private String title;
    private Long assigneeId;
    private List<Long> ccUserIds;
    private Map<String, Object> variables;
    @Size(max = 1000, message = "提交说明不能超过1000个字符")
    private String comment;
}
