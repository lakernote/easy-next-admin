package com.laker.admin.module.workflow.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.workflow.entity.WfCc;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程抄送响应模型。
 */
@Data
@Builder
public class WfCcView {
    private Long id;
    private Long instanceId;
    private String nodeKey;
    private String nodeName;
    private Long receiverId;
    private Integer readStatus;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime readAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    public static WfCcView from(WfCc cc) {
        if (cc == null) {
            return null;
        }
        return WfCcView.builder()
                .id(cc.getId())
                .instanceId(cc.getInstanceId())
                .nodeKey(cc.getNodeKey())
                .nodeName(cc.getNodeName())
                .receiverId(cc.getReceiverId())
                .readStatus(cc.getReadStatus())
                .readAt(cc.getReadAt())
                .createdAt(cc.getCreatedAt())
                .build();
    }

    public static List<WfCcView> fromList(List<WfCc> ccList) {
        return ccList == null ? List.of() : ccList.stream().map(WfCcView::from).toList();
    }
}
