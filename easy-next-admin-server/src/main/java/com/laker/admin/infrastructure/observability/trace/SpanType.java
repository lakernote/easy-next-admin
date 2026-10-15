package com.laker.admin.infrastructure.observability.trace;

public enum SpanType {
    Http,
    Controller,
    Service,
    Mapper,
    Transaction,
    Remote,
    Schedule,
    Kafka,
    CodeBlock,
    Others
}
