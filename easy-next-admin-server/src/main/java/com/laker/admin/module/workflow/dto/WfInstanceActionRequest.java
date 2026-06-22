package com.laker.admin.module.workflow.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WfInstanceActionRequest {
    @Size(max = 1000, message = "处理说明不能超过1000个字符")
    private String comment;
}
