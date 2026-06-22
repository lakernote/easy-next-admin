package com.laker.admin.module.audit.dto;

import lombok.Data;

@Data
public class AuditApiTopIpView {
    private String ip;
    private String city;
    private Integer value;
}
