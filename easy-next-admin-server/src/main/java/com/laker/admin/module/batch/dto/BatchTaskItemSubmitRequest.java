package com.laker.admin.module.batch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BatchTaskItemSubmitRequest {
    @NotBlank(message = "明细业务键不能为空")
    @Size(max = 128, message = "明细业务键不能超过 128 个字符")
    private String itemKey;
    @Size(max = 200, message = "明细名称不能超过 200 个字符")
    private String itemName;
    @Size(max = 32, message = "工作单元类型不能超过 32 个字符")
    private String workType;
    private String inputJson;
    @Size(max = 500, message = "说明不能超过 500 个字符")
    private String remark;
}
