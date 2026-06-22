package com.laker.admin.infrastructure.security.datascope.context;

import java.util.function.Supplier;

public final class EasyDataScopeContext {
    /**
     * 使用深度计数支持内部查询嵌套绕过，避免外层 ignore 结束前被内层提前清理。
     */
    private static final ThreadLocal<Integer> IGNORE_DEPTH = ThreadLocal.withInitial(() -> 0);

    private EasyDataScopeContext() {
    }

    public static boolean ignored() {
        return IGNORE_DEPTH.get() > 0;
    }

    public static <T> T ignore(Supplier<T> supplier) {
        int previous = IGNORE_DEPTH.get();
        IGNORE_DEPTH.set(previous + 1);
        try {
            return supplier.get();
        } finally {
            restore(previous);
        }
    }

    private static void restore(int previous) {
        if (previous == 0) {
            IGNORE_DEPTH.remove();
        } else {
            IGNORE_DEPTH.set(previous);
        }
    }
}
