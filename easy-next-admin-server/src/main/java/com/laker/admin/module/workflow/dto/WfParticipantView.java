package com.laker.admin.module.workflow.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WfParticipantView {
    private String name;
    private String value;
    private String userName;
    private String avatar;
}
