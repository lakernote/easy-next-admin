package com.laker.admin.module.schedule.core;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.laker.admin.infrastructure.lock.IEasyLocker;
import com.laker.admin.infrastructure.lock.base.EasyLocker;
import com.laker.admin.infrastructure.id.EasyIdGenerator;
import com.laker.admin.module.schedule.enums.JobExecutionMode;
import com.laker.admin.module.schedule.enums.JobStateEnum;
import com.laker.admin.module.schedule.entity.ScheduleJob;
import com.laker.admin.module.schedule.service.IScheduleJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "easy.features", name = "scheduler", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ScheduleJobManager implements CommandLineRunner {
    private static final String LOCK_KEY_PREFIX = "schedule:job:";
    private static final int DEFAULT_LOCK_LEASE_SECONDS = 300;
    private static final int MIN_LOCK_LEASE_SECONDS = 30;
    private static final int MAX_LOCK_LEASE_SECONDS = 3600;
    private static final Duration CLUSTER_SYNC_INTERVAL = Duration.ofSeconds(15);

    private final ThreadPoolTaskScheduler easyTaskThreadPool;
    private final ScheduleJobStore scheduleJobStore;
    private final List<ScheduleJobCallback> jobCallbacks;
    private final IScheduleJobService scheduleJobService;
    private final Map<String, EasyJobHandler> jobHandlers;
    private final IEasyLocker easyLocker;
    private final ScheduleInstanceRegistry instanceRegistry;

    /** 当前 JVM 内已经注册的动态任务句柄，用于启动、暂停和查看运行态。 */
    private final Map<String, RegisteredJob> jvmRunningJobs = new ConcurrentHashMap<>();
    private ScheduledFuture<?> clusterSyncFuture;

    public ScheduleJobManager(ThreadPoolTaskScheduler easyTaskThreadPool,
                              ScheduleJobStore scheduleJobStore,
                              List<ScheduleJobCallback> jobCallbacks,
                              IScheduleJobService scheduleJobService,
                              Map<String, EasyJobHandler> jobHandlers,
                              IEasyLocker easyLocker,
                              ScheduleInstanceRegistry instanceRegistry) {
        this.easyTaskThreadPool = easyTaskThreadPool;
        this.scheduleJobStore = scheduleJobStore;
        this.jobCallbacks = jobCallbacks;
        this.scheduleJobService = scheduleJobService;
        this.jobHandlers = jobHandlers;
        this.easyLocker = easyLocker;
        this.instanceRegistry = instanceRegistry;
    }

    @Override
    public void run(String... args) {
        // 启动时只扫描 @EasyJob 声明，最终是否调度以数据库 schedule_job 的启停状态为准。
        if (CollectionUtils.isEmpty(jobHandlers)) {
            log.warn("未查询到 EasyJobHandler 实现类");
        } else {
            registerAnnotatedJobs();
        }
        heartbeat();
        startClusterSyncLoop();
    }

    private void registerAnnotatedJobs() {
        jobHandlers.forEach((s, job) -> {
            EasyJob annotation = AnnotationUtils.findAnnotation(job.getClass(), EasyJob.class);
            if (annotation != null) {
                String cron = annotation.cron();
                String jobName = annotation.jobName();
                String jobCode = annotation.jobCode();
                ScheduleJobDefinition jobDefinition = ScheduleJobDefinition.builder()
                        .jobCode(jobCode)
                        .jobName(jobName)
                        .cronExpression(cron)
                        .lockLeaseSeconds(normalizeLeaseSeconds(annotation.lockLeaseSeconds()))
                        .executionMode(annotation.executionMode())
                        .jobClassName(job.getClass().getName()).build();
                scheduleJobStore.saveDefinition(jobDefinition);
                ScheduleJobDefinition storedJobDefinition = scheduleJobStore.findByJobCode(jobCode);
                syncJvmJob(storedJobDefinition, null);
            }
        });
    }

    /** 将数据库中启用的任务注册到当前 JVM 调度器。 */
    private void startJvmJob(ScheduleJobDefinition jobDefinition, Map<String, Object> param) {
        String cronExpression = jobDefinition.getCronExpression();
        if (StringUtils.hasText(cronExpression) && !Objects.equals(cronExpression, "-")) {
            ScheduledFuture<?> future = easyTaskThreadPool
                    .schedule(() -> executeJvmJob(jobDefinition, param),
                            new CronTrigger(cronExpression));
            jvmRunningJobs.put(jobDefinition.getJobCode(), new RegisteredJob(future, signature(jobDefinition)));
        } else {
            log.warn("cron表达式为：{}，Job信息：{}，不予启动任务。", cronExpression, jobDefinition);
        }
    }

    private void executeJvmJob(ScheduleJobDefinition registeredDefinition, Map<String, Object> param) {
        ScheduleJobDefinition currentDefinition = registeredDefinition;
        EasyLocker lock = null;
        boolean callbackStarted = false;
        ScheduleJobRunContext.set(ScheduleJobRunContext.builder()
                .runId(EasyIdGenerator.uuid32())
                .instanceId(instanceRegistry.getInstanceId())
                .triggerTime(LocalDateTime.now())
                .build());
        try {
            currentDefinition = scheduleJobStore.findByJobCode(registeredDefinition.getJobCode());
            if (!runnable(currentDefinition)) {
                log.debug("Skip schedule job because database state is not runnable, code={}, state={}, enable={}",
                        registeredDefinition.getJobCode(), currentDefinition.getJobState(), currentDefinition.getEnable());
                return;
            }
            if (resolveExecutionMode(currentDefinition) == JobExecutionMode.SINGLETON) {
                lock = easyLocker.tryAcquire(LOCK_KEY_PREFIX + currentDefinition.getJobCode(),
                        Duration.ofSeconds(resolveLeaseSeconds(currentDefinition)));
                if (lock == null) {
                    log.debug("Skip schedule job because another instance owns the lock, code={}, name={}",
                            currentDefinition.getJobCode(), currentDefinition.getJobName());
                    return;
                }
                ScheduleJobRunContext.current().setLockToken(lock.getToken());
            }
            ScheduleJobDefinition definition = currentDefinition;
            callbackStarted = true;
            jobCallbacks.forEach(jobCallback -> jobCallback.start(definition));
            EasyJobHandler jobHandler = findJobHandler(currentDefinition.getJobClassName());
            jobHandler.execute(param == null ? Map.of() : param);
        } catch (Exception e) {
            if (callbackStarted) {
                ScheduleJobDefinition definition = currentDefinition;
                jobCallbacks.forEach(jobCallback -> jobCallback.exception(definition, e));
            } else {
                log.error("Schedule job failed before callback started, code={}",
                        registeredDefinition.getJobCode(), e);
            }
        } finally {
            if (callbackStarted) {
                ScheduleJobDefinition definition = currentDefinition;
                jobCallbacks.forEach(jobCallback -> jobCallback.end(definition));
            }
            if (lock != null) {
                easyLocker.release(lock);
            }
            ScheduleJobRunContext.clear();
        }
    }

    private void removeJvmJob(String jobCode) {
        RegisteredJob registeredJob = jvmRunningJobs.get(jobCode);
        if (registeredJob != null) {
            registeredJob.future().cancel(true);
        }
        jvmRunningJobs.remove(jobCode);
    }

    public List<String> jvmJobList() {
        List<String> res = new ArrayList<>();
        res.addAll(jvmRunningJobs.keySet());
        return res;
    }


    public synchronized void startJob(String jobCode) {
        ScheduleJobDefinition jobDefinition = scheduleJobStore.findByJobCode(jobCode);
        if (Boolean.TRUE.equals(jobDefinition.getEnable())) {
            scheduleJobService.update(Wrappers.<ScheduleJob>lambdaUpdate().set(ScheduleJob::getJobState, JobStateEnum.START).eq(ScheduleJob::getJobCode, jobCode));
            syncOneJob(jobCode);
        }

    }


    public synchronized void stopJob(String jobCode) {
        scheduleJobService.update(Wrappers.<ScheduleJob>lambdaUpdate().set(ScheduleJob::getJobState, JobStateEnum.STOP).eq(ScheduleJob::getJobCode, jobCode));
        removeJvmJob(jobCode);
    }

    public synchronized void reconcileNow() {
        heartbeatAndReconcile();
    }

    private EasyJobHandler findJobHandler(String jobClassName) {
        for (EasyJobHandler jobHandler : jobHandlers.values()) {
            if (Objects.equals(jobClassName, jobHandler.getClass().getName())) {
                return jobHandler;
            }
        }
        throw new IllegalStateException("未找到任务执行器：" + jobClassName);
    }

    private void startClusterSyncLoop() {
        if (clusterSyncFuture != null) {
            return;
        }
        clusterSyncFuture = easyTaskThreadPool.scheduleWithFixedDelay(this::safeHeartbeatAndReconcile, CLUSTER_SYNC_INTERVAL);
    }

    private void safeHeartbeatAndReconcile() {
        try {
            heartbeatAndReconcile();
        } catch (Exception ex) {
            log.warn("Schedule cluster reconcile failed", ex);
        }
    }

    private synchronized void heartbeatAndReconcile() {
        heartbeat();
        Set<String> seenJobCodes = new HashSet<>();
        for (ScheduleJobDefinition definition : scheduleJobStore.findAll()) {
            seenJobCodes.add(definition.getJobCode());
            syncJvmJob(definition, null);
        }
        for (String jobCode : List.copyOf(jvmRunningJobs.keySet())) {
            if (!seenJobCodes.contains(jobCode)) {
                removeJvmJob(jobCode);
            }
        }
    }

    private void heartbeat() {
        try {
            instanceRegistry.heartbeat();
        } catch (Exception ex) {
            log.warn("Schedule instance heartbeat failed, instanceId={}", instanceRegistry.getInstanceId(), ex);
        }
    }

    private void syncOneJob(String jobCode) {
        syncJvmJob(scheduleJobStore.findByJobCode(jobCode), null);
    }

    private void syncJvmJob(ScheduleJobDefinition jobDefinition, Map<String, Object> param) {
        if (!runnable(jobDefinition)) {
            removeJvmJob(jobDefinition.getJobCode());
            return;
        }
        String signature = signature(jobDefinition);
        RegisteredJob registeredJob = jvmRunningJobs.get(jobDefinition.getJobCode());
        if (registeredJob != null && Objects.equals(registeredJob.signature(), signature)) {
            return;
        }
        removeJvmJob(jobDefinition.getJobCode());
        try {
            findJobHandler(jobDefinition.getJobClassName());
            startJvmJob(jobDefinition, param);
        } catch (Exception ex) {
            log.warn("Schedule job registration skipped, code={}, className={}, reason={}",
                    jobDefinition.getJobCode(), jobDefinition.getJobClassName(), ex.getMessage());
            log.debug("Schedule job registration skip detail", ex);
        }
    }

    private boolean runnable(ScheduleJobDefinition jobDefinition) {
        return jobDefinition != null
                && Boolean.TRUE.equals(jobDefinition.getEnable())
                && JobStateEnum.START.equals(jobDefinition.getJobState())
                && StringUtils.hasText(jobDefinition.getCronExpression())
                && !Objects.equals(jobDefinition.getCronExpression(), "-");
    }

    private String signature(ScheduleJobDefinition jobDefinition) {
        return "%s|%s|%s|%s".formatted(
                jobDefinition.getJobClassName(),
                jobDefinition.getCronExpression(),
                resolveLeaseSeconds(jobDefinition),
                resolveExecutionMode(jobDefinition));
    }

    private JobExecutionMode resolveExecutionMode(ScheduleJobDefinition jobDefinition) {
        return JobExecutionMode.normalize(jobDefinition == null ? null : jobDefinition.getExecutionMode());
    }

    private int resolveLeaseSeconds(ScheduleJobDefinition jobDefinition) {
        return normalizeLeaseSeconds(jobDefinition == null ? null : jobDefinition.getLockLeaseSeconds());
    }

    private int normalizeLeaseSeconds(Long lockLeaseSeconds) {
        if (lockLeaseSeconds == null) {
            return DEFAULT_LOCK_LEASE_SECONDS;
        }
        return normalizeLeaseSeconds(lockLeaseSeconds.intValue());
    }

    private int normalizeLeaseSeconds(Integer lockLeaseSeconds) {
        if (lockLeaseSeconds == null || lockLeaseSeconds <= 0) {
            return DEFAULT_LOCK_LEASE_SECONDS;
        }
        return Math.max(MIN_LOCK_LEASE_SECONDS, Math.min(MAX_LOCK_LEASE_SECONDS, lockLeaseSeconds));
    }

    private record RegisteredJob(ScheduledFuture<?> future, String signature) {
    }

}
