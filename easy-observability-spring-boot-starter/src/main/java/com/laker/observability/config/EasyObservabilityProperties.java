package com.laker.observability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Public configuration for the Easy Observability Spring Boot Starter. */
@ConfigurationProperties("easy.observability")
public class EasyObservabilityProperties {
    private boolean enabled = true;
    private final Metrics metrics = new Metrics();
    private final Trace trace = new Trace();
    private final Web web = new Web();
    private final Redis redis = new Redis();
    private final Transaction transaction = new Transaction();
    private final Kafka kafka = new Kafka();
    private final Mybatis mybatis = new Mybatis();
    private final Scheduling scheduling = new Scheduling();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public Trace getTrace() {
        return trace;
    }

    public Web getWeb() {
        return web;
    }

    public Redis getRedis() { return redis; }
    public Transaction getTransaction() { return transaction; }
    public Kafka getKafka() { return kafka; }
    public Mybatis getMybatis() { return mybatis; }
    public Scheduling getScheduling() { return scheduling; }

    public static class Scheduling {
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Mybatis {
        private boolean enabled = true;
        private long slowThresholdMs = 250;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getSlowThresholdMs() { return slowThresholdMs; }
        public void setSlowThresholdMs(long slowThresholdMs) { this.slowThresholdMs = slowThresholdMs; }
    }

    public static class Kafka {
        private boolean enabled = true;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public static class Transaction {
        private boolean enabled = true;
        private long slowThresholdMs = 1_000;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getSlowThresholdMs() { return slowThresholdMs; }
        public void setSlowThresholdMs(long slowThresholdMs) { this.slowThresholdMs = slowThresholdMs; }
    }

    public static class Redis {
        private boolean enabled = true;
        private String serviceName = "easy-observability";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    }

    public static class Web {
        private boolean enabled = true;
        private boolean allowAnonymous;
        private int traceLimit = 500;
        private int operationLimit = 4_000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isAllowAnonymous() { return allowAnonymous; }
        public void setAllowAnonymous(boolean allowAnonymous) { this.allowAnonymous = allowAnonymous; }
        public int getTraceLimit() { return traceLimit; }
        public void setTraceLimit(int traceLimit) { this.traceLimit = traceLimit; }
        public int getOperationLimit() { return operationLimit; }
        public void setOperationLimit(int operationLimit) { this.operationLimit = operationLimit; }
    }

    public static class Metrics {
        private boolean enabled = true;
        private final History history = new History();
        private final Executor executor = new Executor();
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        public History getHistory() { return history; }
        public Executor getExecutor() { return executor; }

        public static class History {
            private boolean enabled = true;
            private String storageDir = "./data/easy-observability/metrics";
            private int retentionDays = 30;
            private long intervalMs = 60_000;
            private long maxStorageBytes = 64L * 1024 * 1024;
            private long maxSeriesBytes = 8L * 1024 * 1024;
            private int maxSeries = 512;
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public String getStorageDir() { return storageDir; }
            public void setStorageDir(String storageDir) { this.storageDir = storageDir; }
            public int getRetentionDays() { return retentionDays; }
            public void setRetentionDays(int retentionDays) { this.retentionDays = retentionDays; }
            public long getIntervalMs() { return intervalMs; }
            public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
            public long getMaxStorageBytes() { return maxStorageBytes; }
            public void setMaxStorageBytes(long maxStorageBytes) { this.maxStorageBytes = maxStorageBytes; }
            public long getMaxSeriesBytes() { return maxSeriesBytes; }
            public void setMaxSeriesBytes(long maxSeriesBytes) { this.maxSeriesBytes = maxSeriesBytes; }
            public int getMaxSeries() { return maxSeries; }
            public void setMaxSeries(int maxSeries) { this.maxSeries = maxSeries; }
        }

        public static class Executor {
            private boolean enabled = true;
            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
        }
    }

    public static class Trace {
        private boolean enabled = true;
        private boolean httpServerObservationEnabled = true;
        private boolean redisObservationEnabled = true;
        private boolean databaseObservationEnabled = true;
        private String headerName = "X-Trace-Id";
        private long redisSlowThresholdMs = 250;
        private long jobSlowThresholdMs = 1_000;
        private long slowThresholdMs = 250;
        private boolean resourceSamplingEnabled = true;
        private String historyFile = "./data/easy-observability/incidents.jsonl";
        private String archiveFile = "./data/easy-observability/traces.jsonl";
        private int historyLimit = 10_000;
        private long historyMaxBytes = 32L * 1024 * 1024;
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        public boolean isHttpServerObservationEnabled() { return httpServerObservationEnabled; }
        public void setHttpServerObservationEnabled(boolean enabled) { this.httpServerObservationEnabled = enabled; }
        public boolean isRedisObservationEnabled() { return redisObservationEnabled; }
        public void setRedisObservationEnabled(boolean enabled) { this.redisObservationEnabled = enabled; }
        public boolean isDatabaseObservationEnabled() { return databaseObservationEnabled; }
        public void setDatabaseObservationEnabled(boolean enabled) { this.databaseObservationEnabled = enabled; }
        public String getHeaderName() { return headerName; }
        public void setHeaderName(String headerName) {
            this.headerName = headerName == null || headerName.isBlank() ? "X-Trace-Id" : headerName;
        }
        public long getRedisSlowThresholdMs() { return redisSlowThresholdMs; }
        public void setRedisSlowThresholdMs(long redisSlowThresholdMs) { this.redisSlowThresholdMs = redisSlowThresholdMs; }
        public long getJobSlowThresholdMs() { return jobSlowThresholdMs; }
        public void setJobSlowThresholdMs(long jobSlowThresholdMs) { this.jobSlowThresholdMs = jobSlowThresholdMs; }
        public long getSlowThresholdMs() { return slowThresholdMs; }
        public void setSlowThresholdMs(long slowThresholdMs) { this.slowThresholdMs = slowThresholdMs; }
        public boolean isResourceSamplingEnabled() { return resourceSamplingEnabled; }
        public void setResourceSamplingEnabled(boolean resourceSamplingEnabled) {
            this.resourceSamplingEnabled = resourceSamplingEnabled;
        }
        public String getHistoryFile() { return historyFile; }
        public void setHistoryFile(String historyFile) { this.historyFile = historyFile; }
        public String getArchiveFile() { return archiveFile; }
        public void setArchiveFile(String archiveFile) { this.archiveFile = archiveFile; }
        public int getHistoryLimit() { return historyLimit; }
        public void setHistoryLimit(int historyLimit) { this.historyLimit = historyLimit; }
        public long getHistoryMaxBytes() { return historyMaxBytes; }
        public void setHistoryMaxBytes(long historyMaxBytes) { this.historyMaxBytes = historyMaxBytes; }
    }
}
