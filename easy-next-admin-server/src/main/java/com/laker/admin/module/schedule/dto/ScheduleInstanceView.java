package com.laker.admin.module.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.schedule.entity.ScheduleInstance;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调度实例对外响应模型。
 */
@Data
@Builder
public class ScheduleInstanceView {
    private String instanceId;
    private String appName;
    private String hostName;
    private String ipAddress;
    private String processId;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startedAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime heartbeatAt;

    public static ScheduleInstanceView from(ScheduleInstance instance) {
        if (instance == null) {
            return null;
        }
        return ScheduleInstanceView.builder()
                .instanceId(instance.getInstanceId())
                .appName(instance.getAppName())
                .hostName(instance.getHostName())
                .ipAddress(instance.getIpAddress())
                .processId(instance.getProcessId())
                .status(instance.getStatus())
                .startedAt(instance.getStartedAt())
                .heartbeatAt(instance.getHeartbeatAt())
                .build();
    }
}
