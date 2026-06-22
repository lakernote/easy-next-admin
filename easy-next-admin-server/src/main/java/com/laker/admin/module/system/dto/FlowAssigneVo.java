package com.laker.admin.module.system.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FlowAssigneVo {
    private String name;
    private String value;
    private String avatar;
}
