package com.laker.admin.module.business.number.support;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.module.business.number.entity.BusinessNumberRule;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessNumberFormatterTest {

    @Test
    void formatsDailyNumberWithSeparatorAndZeroPadding() {
        BusinessNumberRule rule = rule("PR", "yyyyMMdd", "-", 6);

        String number = BusinessNumberFormatter.format(rule, LocalDate.of(2026, 6, 25), 12);

        assertThat(number).isEqualTo("PR-20260625-000012");
    }

    @Test
    void formatsContinuousNumberWithoutDateSegment() {
        BusinessNumberRule rule = rule("HT", BusinessNumberFormatter.DATE_PATTERN_NONE, "", 5);

        String number = BusinessNumberFormatter.format(rule, LocalDate.of(2026, 6, 25), 7);

        assertThat(number).isEqualTo("HT00007");
    }

    @Test
    void rejectsUnsupportedDatePattern() {
        BusinessNumberRule rule = rule("PO", "ddMMyyyy", "-", 4);

        assertThatThrownBy(() -> BusinessNumberFormatter.segment(rule, LocalDate.of(2026, 6, 25)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("编号日期规则不支持");
    }

    private static BusinessNumberRule rule(String prefix, String datePattern, String separator, int sequenceWidth) {
        BusinessNumberRule rule = new BusinessNumberRule();
        rule.setPrefix(prefix);
        rule.setDatePattern(datePattern);
        rule.setNumberSeparator(separator);
        rule.setSequenceWidth(sequenceWidth);
        return rule;
    }
}
