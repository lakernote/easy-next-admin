package com.laker.observability.trace;

/** Describes the kind of work represented by a trace span. */
public enum SpanKind {
    HTTP("Http"),
    CONTROLLER("Controller"),
    SERVICE("Service"),
    DATABASE("Mapper"),
    REDIS("Redis"),
    TRANSACTION("Transaction"),
    REMOTE("Remote"),
    SCHEDULE("Schedule"),
    KAFKA("Kafka"),
    CODE("Code"),
    OTHER("Other");

    private final String displayName;

    SpanKind(String displayName) {
        this.displayName = displayName;
    }

    /** Human-readable label used in rendered trace trees. */
    public String displayName() {
        return displayName;
    }
}
