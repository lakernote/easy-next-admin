package com.laker.observability.trace;

import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/** Propagates trace ID and logging context across Spring-managed thread-pool tasks. */
public final class TraceContextTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> context = MdcContext.copy();
        String traceId = TraceIdContext.currentTraceId();
        return () -> {
            try (MdcContext.Scope ignored = MdcContext.withContext(context)) {
                if (TraceIdContext.isValidTraceId(traceId)) {
                    TraceIdContext.putTraceId(traceId);
                } else {
                    TraceIdContext.getOrCreateTraceId();
                }
                try {
                    task.run();
                } finally {
                    TraceIdContext.clear();
                }
            }
        };
    }
}
