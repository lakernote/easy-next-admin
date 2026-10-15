package com.laker.admin.infrastructure.observability.trace;

/** Prevents the diagnostics endpoints from creating diagnostics about their own reads. */
public final class ObservationSuppression {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private ObservationSuppression() { }

    public static boolean active() { return DEPTH.get() > 0; }

    public static Scope enter() {
        DEPTH.set(DEPTH.get() + 1);
        return new Scope();
    }

    public static final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() { }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            int remaining = DEPTH.get() - 1;
            if (remaining <= 0) DEPTH.remove();
            else DEPTH.set(remaining);
        }
    }
}
