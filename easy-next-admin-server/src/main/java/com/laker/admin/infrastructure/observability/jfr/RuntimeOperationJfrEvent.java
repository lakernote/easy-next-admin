package com.laker.admin.infrastructure.observability.jfr;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

@Name("com.laker.admin.RuntimeOperation")
@Label("EasyNextAdmin Runtime Operation")
@Category({"EasyNextAdmin", "Runtime"})
@StackTrace(false)
public class RuntimeOperationJfrEvent extends Event {
    @Label("Type") public String type;
    @Label("Trace ID") public String traceId;
    @Label("Operation") public String operation;
    @Label("Detail") public String detail;
    @Label("Outcome") public String outcome;
    @Label("Error Type") public String errorType;
    @Label("CPU Time (ms)") public long cpuTimeMs;
    @Label("Allocated Bytes") public long allocatedBytes;
    @Label("Blocked Count") public long blockedCount;
    @Label("Waited Count") public long waitedCount;
}
