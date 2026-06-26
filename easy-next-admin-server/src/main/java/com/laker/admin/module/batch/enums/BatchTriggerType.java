package com.laker.admin.module.batch.enums;

public enum BatchTriggerType {
    MANUAL,
    API,
    JOB,
    MESSAGE,
    SYSTEM;

    public String code() {
        return name();
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return MANUAL.code();
        }
        for (BatchTriggerType triggerType : values()) {
            if (triggerType.name().equalsIgnoreCase(value.trim())) {
                return triggerType.code();
            }
        }
        return MANUAL.code();
    }
}
