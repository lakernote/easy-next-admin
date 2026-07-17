package com.laker.admin.module.workflow.leave.support;

import com.laker.admin.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeavePeriodCalculatorTest {

    private final LeavePeriodCalculator calculator = new LeavePeriodCalculator();

    @Test
    void calculatesNaturalHalfDaySlots() {
        assertThat(calculator.calculate(
                LocalDateTime.of(2026, 7, 1, 13, 30),
                LocalDateTime.of(2026, 7, 1, 18, 0)))
                .isEqualByComparingTo("0.5");
        assertThat(calculator.calculate(
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 2, 18, 0)))
                .isEqualByComparingTo("2.0");
    }

    @Test
    void treatsNoonAsTheNaturalHalfDayBoundary() {
        assertThat(calculator.calculate(
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 12, 0)))
                .isEqualByComparingTo("0.5");
        assertThat(calculator.calculate(
                LocalDateTime.of(2026, 7, 1, 12, 0),
                LocalDateTime.of(2026, 7, 1, 18, 0)))
                .isEqualByComparingTo("0.5");
        assertThat(calculator.calculate(
                LocalDateTime.of(2026, 7, 1, 9, 0),
                LocalDateTime.of(2026, 7, 1, 12, 1)))
                .isEqualByComparingTo("1.0");
    }

    @Test
    void rejectsMissingOrNonIncreasingPeriods() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 9, 0);

        assertThatThrownBy(() -> calculator.calculate(null, start))
                .isInstanceOf(BusinessException.class)
                .hasMessage("结束时间必须晚于开始时间");
        assertThatThrownBy(() -> calculator.calculate(start, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("结束时间必须晚于开始时间");
        assertThatThrownBy(() -> calculator.calculate(start, start))
                .isInstanceOf(BusinessException.class)
                .hasMessage("结束时间必须晚于开始时间");
        assertThatThrownBy(() -> calculator.calculate(start, start.minusMinutes(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("结束时间必须晚于开始时间");
    }
}
