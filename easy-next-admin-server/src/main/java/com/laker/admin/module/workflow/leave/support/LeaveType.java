package com.laker.admin.module.workflow.leave.support;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 请假类型领域枚举。
 *
 * <p>code 用于 API、流程变量和数据库，label/aliases 只用于中文展示和 Agent 原文证据校验。</p>
 */
public enum LeaveType {
    ANNUAL("年假"),
    PERSONAL("事假"),
    SICK("病假"),
    COMPENSATORY("调休", "补休"),
    MARRIAGE("婚假"),
    MATERNITY("产假"),
    PATERNITY("陪产假"),
    BEREAVEMENT("丧假"),
    OTHER("其他");

    private static final List<LeaveType> AGENT_VALUES = List.of(
            ANNUAL,
            PERSONAL,
            SICK,
            COMPENSATORY,
            MARRIAGE,
            MATERNITY,
            PATERNITY,
            BEREAVEMENT
    );

    private final String label;
    private final List<String> explicitLabels;

    LeaveType(String label, String... aliases) {
        this.label = label;
        this.explicitLabels = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(label),
                        Arrays.stream(aliases)
                )
                .distinct()
                .toList();
    }

    public String code() {
        return name();
    }

    public String label() {
        return label;
    }

    public boolean isExplicitlyMentionedIn(String message) {
        return message != null && explicitLabels.stream().anyMatch(message::contains);
    }

    public static Optional<LeaveType> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        String normalized = code.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(type -> type.code().equals(normalized))
                .findFirst();
    }

    public static List<String> agentCodes() {
        return AGENT_VALUES.stream().map(LeaveType::code).toList();
    }

    public static Map<String, String> agentLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        AGENT_VALUES.forEach(type -> labels.put(type.code(), type.label()));
        return Map.copyOf(labels);
    }

    public static String agentSchemaDescription() {
        return AGENT_VALUES.stream()
                .map(type -> type.code() + "=" + String.join("/", type.explicitLabels))
                .collect(java.util.stream.Collectors.joining("，"));
    }

    public static String displayLabel(String code) {
        return fromCode(code).map(LeaveType::label).orElse(code == null ? "" : code);
    }
}
