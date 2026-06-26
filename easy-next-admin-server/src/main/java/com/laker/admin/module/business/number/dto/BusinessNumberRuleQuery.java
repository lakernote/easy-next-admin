package com.laker.admin.module.business.number.dto;

import lombok.Data;

@Data
public class BusinessNumberRuleQuery {
    private long page = 1;
    private long limit = 10;
    private String keyword;
    private Boolean enable;
}
