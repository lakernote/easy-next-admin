package com.laker.admin.infrastructure.security.masking;

import org.springframework.util.StringUtils;

final class EasyMaskingSupport {
    static final String MASK = "******";

    private EasyMaskingSupport() {
    }

    static String mask(EasyMaskType type, String value, int prefix, int suffix) {
        if (value == null || !StringUtils.hasText(value)) {
            return value;
        }
        return switch (type) {
            case PHONE -> maskPhone(value);
            case EMAIL -> maskEmail(value);
            case NAME -> maskName(value);
            case ID_CARD -> maskKeepBoth(value, 6, 4);
            case BANK_CARD -> maskKeepBoth(value, 4, 4);
            case CUSTOM -> maskKeepBoth(value, prefix, suffix);
            case FULL -> MASK;
        };
    }

    static String maskPhone(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.length() >= 7) {
            return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
        }
        return MASK;
    }

    static String maskEmail(String value) {
        int atIndex = value.indexOf('@');
        if (atIndex <= 0) {
            return MASK;
        }
        String prefix = value.substring(0, atIndex);
        String domain = value.substring(atIndex);
        return prefix.charAt(0) + "***" + domain;
    }

    private static String maskName(String value) {
        int firstCodePoint = value.codePointAt(0);
        return new String(Character.toChars(firstCodePoint)) + "*";
    }

    private static String maskKeepBoth(String value, int prefixLength, int suffixLength) {
        if (prefixLength < 0 || suffixLength < 0 || prefixLength + suffixLength >= value.length()) {
            return MASK;
        }
        return value.substring(0, prefixLength)
                + "*".repeat(value.length() - prefixLength - suffixLength)
                + value.substring(value.length() - suffixLength);
    }
}
