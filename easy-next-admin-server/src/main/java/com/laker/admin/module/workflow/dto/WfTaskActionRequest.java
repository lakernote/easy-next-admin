package com.laker.admin.module.workflow.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class WfTaskActionRequest {
    @Size(max = 1000, message = "审批意见不能超过1000个字符")
    private String comment;
    private Long nextAssigneeId;
    private Long targetUserId;
    private String returnNodeKey;
    private Long returnAssigneeId;
    private List<Long> ccUserIds;
    private List<Long> addSignUserIds;
    private Map<String, Object> variables;
}
