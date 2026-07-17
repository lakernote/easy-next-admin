package com.laker.admin.module.business.number.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.business.number.entity.BusinessNumberRule;
import com.laker.admin.module.business.number.support.BusinessNumberFormatter;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class BusinessNumberRuleView {
    private Long id;
    private String ruleCode;
    private String ruleName;
    private String prefix;
    private String datePattern;
    private String datePatternName;
    private String separator;
    private Integer sequenceWidth;
    private Integer sequenceStep;
    private Long initialValue;
    private Boolean enable;
    private String sampleNumber;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;

    public static BusinessNumberRuleView from(BusinessNumberRule rule) {
        long sampleValue = Math.max(1L, rule.getInitialValue() + rule.getSequenceStep());
        return BusinessNumberRuleView.builder()
                .id(rule.getId())
                .ruleCode(rule.getRuleCode())
                .ruleName(rule.getRuleName())
                .prefix(rule.getPrefix())
                .datePattern(rule.getDatePattern())
                .datePatternName(BusinessNumberFormatter.datePatternName(rule.getDatePattern()))
                .separator(rule.getNumberSeparator())
                .sequenceWidth(rule.getSequenceWidth())
                .sequenceStep(rule.getSequenceStep())
                .initialValue(rule.getInitialValue())
                .enable(rule.getEnable())
                .sampleNumber(BusinessNumberFormatter.format(rule, LocalDate.now(), sampleValue))
                .remark(rule.getRemark())
                .createTime(rule.getCreateTime())
                .updateTime(rule.getUpdateTime())
                .build();
    }
}
