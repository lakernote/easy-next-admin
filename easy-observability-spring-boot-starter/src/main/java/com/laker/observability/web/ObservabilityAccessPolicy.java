package com.laker.observability.web;

/** Host application hook for restricting access to dashboard data. */
@FunctionalInterface
public interface ObservabilityAccessPolicy {
    boolean canView();
}
