package com.laker.admin.config.thread;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "easy.thread-pool")
public class EasyThreadPoolProperties {
    private Scheduler scheduler = new Scheduler();
    private Business business = new Business();
    private BusinessMdc businessMdc = new BusinessMdc();
    private Lock lock = new Lock();

    @Data
    public static class Scheduler {
        private int poolSize = 20;
        private String threadNamePrefix = "easy-job-";
        private boolean removeOnCancelPolicy = true;
        private boolean executeExistingDelayedTasksAfterShutdownPolicy = false;
        private boolean continueExistingPeriodicTasksAfterShutdownPolicy = false;
        private boolean waitForTasksToCompleteOnShutdown = false;
        private int awaitTerminationSeconds = 10;
    }

    @Data
    public static class Business {
        private int coreSize = 20;
        private int maxSize = 100;
        private int queueCapacity = 100;
        private String threadNamePrefix = "easy-executor-";
        private boolean waitForTasksToCompleteOnShutdown = true;
        private int awaitTerminationSeconds = 60;
    }

    @Data
    public static class BusinessMdc {
        private int poolSize = 20;
        private int queueSize = 100;
        private String threadNamePrefix = "business";
    }

    @Data
    public static class Lock {
        private int poolSize = 5;
        private String threadNamePrefix = "easy-lock-";
        private boolean waitForTasksToCompleteOnShutdown = true;
        private int awaitTerminationSeconds = 60;
    }
}
