package com.laker.admin.module.business.number.support;

import com.laker.admin.common.exception.BusinessException;
import com.laker.admin.common.exception.ErrorCode;
import com.laker.admin.module.business.number.entity.BusinessNumberRule;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 业务编号格式化器。
 *
 * <p>只处理规则到可读编号的转换，不访问数据库，方便业务和测试复用。</p>
 */
public final class BusinessNumberFormatter {
    public static final String DATE_PATTERN_NONE = "NONE";
    public static final String DATE_PATTERN_YEAR = "yyyy";
    public static final String DATE_PATTERN_MONTH = "yyyyMM";
    public static final String DATE_PATTERN_DAY = "yyyyMMdd";

    private static final Set<String> SUPPORTED_DATE_PATTERNS = Set.of(
            DATE_PATTERN_NONE,
            DATE_PATTERN_YEAR,
            DATE_PATTERN_MONTH,
            DATE_PATTERN_DAY
    );

    private BusinessNumberFormatter() {
    }

    public static String format(BusinessNumberRule rule, LocalDate date, long sequenceValue) {
        if (rule == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "编号规则不能为空");
        }
        if (sequenceValue < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "编号流水值不能为负数");
        }
        String prefix = trim(rule.getPrefix());
        String dateSegment = segment(rule, date);
        String sequence = String.format(Locale.ROOT, "%0" + rule.getSequenceWidth() + "d", sequenceValue);
        String separator = normalizeSeparator(rule.getNumberSeparator());
        List<String> parts = DATE_PATTERN_NONE.equals(dateSegment)
                ? List.of(prefix, sequence)
                : List.of(prefix, dateSegment, sequence);
        return separator.isEmpty() ? String.join("", parts) : String.join(separator, parts);
    }

    public static String segment(BusinessNumberRule rule, LocalDate date) {
        String pattern = normalizeDatePattern(rule == null ? null : rule.getDatePattern());
        if (DATE_PATTERN_NONE.equals(pattern)) {
            return DATE_PATTERN_NONE;
        }
        LocalDate actualDate = date == null ? LocalDate.now() : date;
        return DateTimeFormatter.ofPattern(pattern).format(actualDate);
    }

    public static String sequenceKey(String ruleCode, String segment) {
        if (!StringUtils.hasText(ruleCode)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "编号规则编码不能为空");
        }
        return ruleCode.trim() + ":" + (StringUtils.hasText(segment) ? segment.trim() : DATE_PATTERN_NONE);
    }

    public static String datePatternName(String datePattern) {
        return switch (normalizeDatePattern(datePattern)) {
            case DATE_PATTERN_DAY -> "按日重置";
            case DATE_PATTERN_MONTH -> "按月重置";
            case DATE_PATTERN_YEAR -> "按年重置";
            case DATE_PATTERN_NONE -> "永续流水";
            default -> throw new BusinessException("编号日期规则不支持");
        };
    }

    public static String normalizeDatePattern(String datePattern) {
        String pattern = StringUtils.hasText(datePattern) ? datePattern.trim() : DATE_PATTERN_DAY;
        if (!SUPPORTED_DATE_PATTERNS.contains(pattern)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "编号日期规则不支持：" + pattern);
        }
        return pattern;
    }

    public static String normalizeSeparator(String separator) {
        return separator == null ? "-" : separator.trim();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
