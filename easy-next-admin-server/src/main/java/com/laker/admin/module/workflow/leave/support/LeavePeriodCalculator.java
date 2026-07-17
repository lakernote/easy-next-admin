package com.laker.admin.module.workflow.leave.support;

import com.laker.admin.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

@Component
public class LeavePeriodCalculator {

    private static final BigDecimal HALF_DAY = new BigDecimal("0.5");

    public BigDecimal calculate(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new BusinessException("结束时间必须晚于开始时间");
        }
        long dayOffset = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate());
        int startHalf = start.toLocalTime().compareTo(LocalTime.NOON) >= 0 ? 1 : 0;
        int endHalf = end.toLocalTime().compareTo(LocalTime.NOON) <= 0 ? 0 : 1;
        long halfDays = dayOffset * 2 + endHalf - startHalf + 1;
        if (halfDays <= 0) {
            throw new BusinessException("请假时间范围无效");
        }
        return BigDecimal.valueOf(halfDays).multiply(HALF_DAY);
    }
}
