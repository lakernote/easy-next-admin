package com.laker.admin.module.workflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.workflow.entity.WfEvent;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程事件响应模型。
 */
@Data
@Builder
public class WfEventView {
    private Long id;
    private Long instanceId;
    private Long taskId;
    private Long operatorId;
    private String action;
    private String fromNodeKey;
    private String toNodeKey;
    private Long targetUserId;
    private String comment;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    public static WfEventView from(WfEvent event) {
        if (event == null) {
            return null;
        }
        return WfEventView.builder()
                .id(event.getId())
                .instanceId(event.getInstanceId())
                .taskId(event.getTaskId())
                .operatorId(event.getOperatorId())
                .action(event.getAction())
                .fromNodeKey(event.getFromNodeKey())
                .toNodeKey(event.getToNodeKey())
                .targetUserId(event.getTargetUserId())
                .comment(event.getComment())
                .createdAt(event.getCreatedAt())
                .build();
    }

    public static List<WfEventView> fromList(List<WfEvent> events) {
        return events == null ? List.of() : events.stream().map(WfEventView::from).toList();
    }
}
