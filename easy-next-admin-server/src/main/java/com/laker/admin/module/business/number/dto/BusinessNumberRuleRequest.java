package com.laker.admin.module.business.number.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BusinessNumberRuleRequest {
    private Long id;
    @NotBlank(message = "规则编码不能为空")
    @Size(max = 64, message = "规则编码不能超过 64 个字符")
    private String ruleCode;
    @NotBlank(message = "规则名称不能为空")
    @Size(max = 100, message = "规则名称不能超过 100 个字符")
    private String ruleName;
    @NotBlank(message = "编号前缀不能为空")
    @Size(max = 16, message = "编号前缀不能超过 16 个字符")
    private String prefix;
    @NotBlank(message = "日期规则不能为空")
    private String datePattern;
    @Size(max = 4, message = "分隔符不能超过 4 个字符")
    private String separator;
    @Min(value = 1, message = "流水位数不能小于 1")
    @Max(value = 12, message = "流水位数不能大于 12")
    private Integer sequenceWidth;
    @Min(value = 1, message = "递增步长不能小于 1")
    @Max(value = 1000, message = "递增步长不能大于 1000")
    private Integer sequenceStep;
    @Min(value = 0, message = "初始当前值不能小于 0")
    private Long initialValue;
    private Boolean enable;
    @Size(max = 500, message = "说明不能超过 500 个字符")
    private String remark;
}
