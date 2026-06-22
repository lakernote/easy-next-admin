package com.laker.admin.infrastructure.observability.trace;

public enum SpanType {
    Http,
    Controller,
    Service,
    Mapper,
    Remote,
    Schedule,
    Kafka,
    CodeBlock,
    Others
}
