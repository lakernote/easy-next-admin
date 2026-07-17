package com.laker.admin.module.batch.runner;

import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * 批处理业务幂等键工具。
 *
 * <p>固定账期按业务周期生成稳定 key，例如每日对账、每周汇总。
 * 首次执行和补跑通过 {@code runNo} 区分，不修改历史执行。</p>
 */
public final class EasyBatchBusinessKeys {
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final WeekFields ISO_WEEK = WeekFields.ISO;

    private EasyBatchBusinessKeys() {
    }

    public static String dailyPeriod(String taskType, LocalDate date) {
        LocalDate actualDate = date == null ? LocalDate.now() : date;
        return "%s:DAY:%s".formatted(normalizeTaskType(taskType), actualDate.format(DAY_FORMATTER));
    }

    public static String weeklyPeriod(String taskType, LocalDate dateInWeek) {
        LocalDate actualDate = dateInWeek == null ? LocalDate.now() : dateInWeek;
        int weekYear = actualDate.get(ISO_WEEK.weekBasedYear());
        int weekNumber = actualDate.get(ISO_WEEK.weekOfWeekBasedYear());
        return "%s:WEEK:%dW%02d".formatted(normalizeTaskType(taskType), weekYear, weekNumber);
    }

    private static String normalizeTaskType(String taskType) {
        if (!StringUtils.hasText(taskType)) {
            throw new IllegalArgumentException("批处理任务类型不能为空");
        }
        return taskType.trim().toUpperCase(Locale.ROOT);
    }
}
