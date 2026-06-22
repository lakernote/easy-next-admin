package com.laker.admin.module.schedule.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.laker.admin.module.schedule.entity.ScheduleJobLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务执行日志对外响应模型。
 */
@Data
@Builder
public class ScheduleJobLogView {
    private Long jobLogId;
    private String jobCode;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
    private Integer status;
    private Integer cost;
    private String threadName;

    public static ScheduleJobLogView from(ScheduleJobLog log) {
        if (log == null) {
            return null;
        }
        return ScheduleJobLogView.builder()
                .jobLogId(log.getJobLogId())
                .jobCode(log.getJobCode())
                .startTime(log.getStartTime())
                .endTime(log.getEndTime())
                .status(log.getStatus())
                .cost(log.getCost())
                .threadName(log.getThreadName())
                .build();
    }

    public static List<ScheduleJobLogView> fromList(List<ScheduleJobLog> logs) {
        return logs == null ? List.of() : logs.stream().map(ScheduleJobLogView::from).toList();
    }
}
