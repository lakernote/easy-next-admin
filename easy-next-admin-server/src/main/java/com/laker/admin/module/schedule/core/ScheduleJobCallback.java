package com.laker.admin.module.schedule.core;

public interface ScheduleJobCallback {
    void start(ScheduleJobDefinition jobDefinition);

    /**
     * Receives every execution failure, including JVM {@link Error}s. Implementations must keep this callback
     * best effort: the manager rethrows {@code Error} after callbacks so monitoring can never hide a fatal fault.
     */
    void exception(ScheduleJobDefinition jobDefinition, Throwable failure);

    void end(ScheduleJobDefinition jobDefinition);
}
