package com.laker.observability.integration.job;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.Objects;
import java.util.function.Supplier;

/** Explicit observation adapter for Quartz and application-owned scheduled jobs. */
public final class JobObservation {
    public static final String OBSERVATION_NAME = "tasks.scheduled.execution";
    private final ObservationRegistry registry;

    public JobObservation(ObservationRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public void observe(String jobName, Runnable job) {
        Objects.requireNonNull(job, "job");
        Observation.createNotStarted(OBSERVATION_NAME, registry)
                .contextualName(safeName(jobName))
                .observe(job);
    }

    public <T> T observe(String jobName, Supplier<T> job) {
        Objects.requireNonNull(job, "job");
        return Observation.createNotStarted(OBSERVATION_NAME, registry)
                .contextualName(safeName(jobName))
                .observe(job);
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) return "scheduled-job";
        String clean = name.replace('\r', ' ').replace('\n', ' ').trim();
        return clean.length() <= 200 ? clean : clean.substring(0, 200);
    }
}
