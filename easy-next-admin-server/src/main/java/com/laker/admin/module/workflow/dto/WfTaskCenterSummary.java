package com.laker.admin.module.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WfTaskCenterSummary {
    private long pendingTotal;
    private long doneTotal;
    private long startedTotal;
    private long ccTotal;
}
