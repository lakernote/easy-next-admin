package com.laker.admin.module.audit.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.audit.entity.AuditApiLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 接口访问审计对外响应模型。
 */
@Data
@Builder
public class AuditApiLogView {
    private Long logId;
    private Long userId;
    private AuditUserView user;
    private String traceId;
    private String ip;
    private String city;
    private String client;
    private String uri;
    private String method;
    private String request;
    private String response;
    private Boolean status;
    private Integer cost;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    public static AuditApiLogView from(AuditApiLog log) {
        if (log == null) {
            return null;
        }
        return AuditApiLogView.builder()
                .logId(log.getLogId())
                .userId(log.getUserId())
                .user(AuditUserView.from(log.getUser()))
                .traceId(log.getTraceId())
                .ip(log.getIp())
                .city(log.getCity())
                .client(log.getClient())
                .uri(log.getUri())
                .method(log.getMethod())
                .request(log.getRequest())
                .response(log.getResponse())
                .status(log.getStatus())
                .cost(log.getCost())
                .createTime(log.getCreateTime())
                .build();
    }

    public static List<AuditApiLogView> fromList(List<AuditApiLog> logs) {
        return logs == null ? List.of() : logs.stream().map(AuditApiLogView::from).toList();
    }
}
