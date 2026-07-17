package com.laker.admin.module.audit.dto;

import lombok.Data;

@Data
public class AuditApiDailyVisitView {
    private String date;
    private Integer value;
}
