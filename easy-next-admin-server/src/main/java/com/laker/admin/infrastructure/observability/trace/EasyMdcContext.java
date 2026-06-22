package com.laker.admin.infrastructure.observability.trace;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * MDC 上下文快照与恢复工具。
 */
public final class EasyMdcContext {

    private EasyMdcContext() {
    }

    public static Map<String, String> copy() {
        return MDC.getCopyOfContextMap();
    }

    public static String get(String key) {
        return MDC.get(key);
    }

    public static void putOrRemove(String key, String value) {
        if (StringUtils.hasText(value)) {
            MDC.put(key, value);
        } else {
            MDC.remove(key);
        }
    }

    public static void remove(String key) {
        MDC.remove(key);
    }

    public static Scope scope() {
        return new Scope(copy());
    }

    public static Scope withContext(Map<String, String> contextMap) {
        Map<String, String> previousContext = copy();
        restore(contextMap);
        return new Scope(previousContext);
    }

    public static Runnable wrap(Runnable runnable, Map<String, String> contextMap) {
        return () -> {
            try (Scope ignored = withContext(contextMap)) {
                runnable.run();
            }
        };
    }

    private static void restore(Map<String, String> contextMap) {
        if (contextMap == null || contextMap.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(contextMap);
        }
    }

    public static final class Scope implements AutoCloseable {
        private final Map<String, String> previousContext;
        private boolean closed;

        private Scope(Map<String, String> previousContext) {
            this.previousContext = previousContext;
        }

        @Override
        public void close() {
            if (!closed) {
                restore(previousContext);
                closed = true;
            }
        }
    }
}
