package com.laker.admin.common.constant;

import io.github.lakernote.meerkat.trace.TraceIdContext;

public interface EasyNextAdminConstants {
    String TRACE_ID = TraceIdContext.MDC_KEY;
    String USER_ID = "userId";
    String TRACE_ID_HEADER = "X-Trace-Id";
}
